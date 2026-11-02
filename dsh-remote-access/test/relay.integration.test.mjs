import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import https from 'node:https';
import { execFileSync } from 'node:child_process';
import { once } from 'node:events';
import { createHash, randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import WebSocket, { WebSocketServer } from 'ws';
import { apply } from '../lib/index.js';
import { pathToFileURL } from 'node:url';
import { listenLoopback } from './loopback.mjs';
const backend = process.env.DSH_RELAY_BACKEND;
const createRelayServer = backend ? (await import(pathToFileURL(join(backend, 'src/server.mjs')).href)).createRelayServer : null;

async function until(fn, timeout = 5000) {
  const end = Date.now() + timeout;
  while (!fn()) { if (Date.now() > end) throw new Error('Condition timeout'); await new Promise(r => setTimeout(r, 20)); }
}
async function fixture(t, secure = false) {
  const home = mkdtempSync(join(tmpdir(), 'dsh-relay-test-'));
  const previous = process.env.DSH_HOME; process.env.DSH_HOME = home;
  const cleanups = [];
  const webServer = {
    exact: new Map(), prefixes: new Map(), upgrades: new Map(),
    register(route) {
      const table = route.kind === 'upgrade' ? this.upgrades : route.kind === 'exact' ? this.exact : this.prefixes;
      table.set(route.path, route); return () => table.delete(route.path);
    },
  };
  apply({ webServer, effect(fn) { const stop = fn(); if (stop) cleanups.push(stop); }, get() {} });
  let executions = 0;
  webServer.register({ kind: 'exact', path: '/api/echo', handler: async (req, res) => {
    const chunks = []; for await (const c of req) chunks.push(c);
    executions++; res.setHeader('Content-Type', 'application/octet-stream'); res.end(Buffer.concat(chunks));
  } });
  webServer.register({ kind: 'exact', path: '/api/host.describe', handler: (_, res) => res.end('{"version":"test"}') });
  webServer.register({ kind: 'exact', path: '/api/events.host', handler: (_, res) => {
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    res.write('data: first\n\n');
    const timer = setInterval(() => res.write('data: live\n\n'), 30);
    res.once('close', () => clearInterval(timer));
  } });
  const wss = new WebSocketServer({ noServer: true });
  webServer.register({ kind: 'upgrade', path: '/api/events.mux', handler: (req, socket, head) => {
    wss.handleUpgrade(req, socket, head, ws => { ws.on('message', (data, binary) => ws.send(data, { binary })); });
  } });
  const direct = http.createServer((req, res) => {
    const route = webServer.exact.get(new URL(req.url, 'http://localhost').pathname);
    if (route) route.handler(req, res); else { res.statusCode = 404; res.end(); }
  });
  direct.on('upgrade', (req, socket, head) => webServer.upgrades.get(req.url)?.handler(req, socket, head));
  await listenLoopback(direct);
  const localUrl = `http://127.0.0.1:${direct.address().port}`;
  const token = randomBytes(32).toString('hex');
  let tls, caFile = '';
  if (secure) {
    caFile = join(home, 'test-cert.pem'); const key = join(home, 'test-key.pem');
    const openssl = process.env.OPENSSL_BIN || 'openssl';
    execFileSync(openssl, ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=localhost',
      '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1', '-keyout', key, '-out', caFile], { stdio: 'ignore', windowsHide: true });
    tls = { key: readFileSync(key), cert: readFileSync(caFile) };
  }
  const relay = createRelayServer({ hosts: { desktop: createHash('sha256').update(token).digest('hex') }, tls, allowInsecureLoopback: !secure });
  await listenLoopback(relay.server);
  const url = `${secure ? 'https' : 'http'}://127.0.0.1:${relay.server.address().port}`;
  async function json(base, path, body, auth, extra = {}) {
    if (base.startsWith('https:')) return new Promise((resolve, reject) => {
      const req = https.request(base + path, { ca: tls.cert, method: body === undefined ? 'GET' : 'POST',
        headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(auth ? { Authorization: 'Bearer ' + auth } : {}), ...extra } }, res => {
        let data = ''; res.on('data', chunk => data += chunk); res.on('end', () => { try { resolve({ status: res.statusCode, body: JSON.parse(data) }); } catch(e) { reject(e); } });
      }); req.on('error', reject); req.end(body === undefined ? undefined : JSON.stringify(body));
    });
    const r = await fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(auth ? { Authorization: 'Bearer ' + auth } : {}), ...extra }, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: r.status, body: await r.json() };
  }
  async function configure(enabled = true, overrides = {}) {
    return json(localUrl, '/api/remote-access/relay/config', { enabled, serverUrl: url, hostId: 'desktop', registrationToken: token, caFile, allowInsecureLoopback: !secure, ...overrides });
  }
  t.after(async () => {
    await configure(false);
    for (const ws of wss.clients) ws.terminate(); wss.close();
    await relay.close(); direct.closeAllConnections(); await new Promise(r => direct.close(r));
    for (const stop of cleanups.reverse()) stop();
    if (previous === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previous;
    rmSync(home, { recursive: true, force: true });
  });
  assert.equal((await configure()).body.ok, true);
  await until(() => relay.online.has('desktop'));
  const base = url + '/h/desktop';
  async function pair(id) {
    const code = await json(localUrl, '/api/remote-access/pair/code/generate', {});
    const result = await json(base, '/api/remote-access/pair/code/verify', { deviceId: id, deviceName: id, code: code.body.code });
    assert.equal(result.body.ok, true); return result.body.token;
  }
  return { home, relay, url, localUrl, base, json, pair, configure, tls, registrationToken: token, executions: () => executions };
}

test('本机 HTTP 中转：配对、未授权拦截、流式上传、保留直连与主机隔离', { skip: !backend && 'Set DSH_RELAY_BACKEND to an isolated backend checkout', timeout: 20000 }, async t => {
  const f = await fixture(t);
  assert.equal((await f.json(f.base, '/api/remote-access/pair/list')).status, 401);
  assert.equal((await f.json(f.base, '/api/remote-access/relay/config')).status, 401);
  const token = await f.pair('phone-a');
  assert.equal((await f.json(f.base, '/api/remote-access/pair/check?deviceId=phone-a')).body.paired, false);
  const data = randomBytes(3 * 1024 * 1024 + 27);
  const response = await fetch(f.base + '/api/echo', { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: data });
  assert.equal(response.status, 200);
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), data);
  const direct = await fetch(f.localUrl + '/api/echo', { method: 'POST', headers: { Authorization: 'Bearer ' + token, 'X-Forwarded-For': 'remote' }, body: 'direct' });
  assert.equal(await direct.text(), 'direct');
  assert.equal((await fetch(f.url + '/h/another/api/echo', { headers: { Authorization: 'Bearer ' + token } })).status, 503);
  assert.equal((await fetch(f.base + '/not-api')).status, 404);
  assert.equal(f.executions(), 2);
});

test('HTTPS/WSS：CA 验证、主机注册鉴权、重复注册与设备配对', { skip: !backend && 'Set DSH_RELAY_BACKEND to an isolated backend checkout', timeout: 20000 }, async t => {
  const f = await fixture(t, true);
  await assert.rejects(new Promise((resolve, reject) => {
    https.get(f.url + '/healthz', resolve).on('error', reject);
  }), /certificate|self.signed/i);
  assert.equal((await f.json(f.url, '/healthz')).body.protocol, 1);
  const rejection = token => new Promise((resolve, reject) => {
    const ws = new WebSocket(f.url.replace('https:', 'wss:') + '/relay/v1/agent/desktop', { ca: f.tls.cert, headers: { Authorization: 'Bearer ' + token } });
    ws.on('unexpected-response', (_, res) => { res.resume(); resolve(res.statusCode); ws.terminate(); });
    ws.on('error', e => { if (!/closed before/.test(e.message)) reject(e); });
  });
  assert.equal(await rejection('0'.repeat(64)), 401);
  assert.equal(await rejection(f.registrationToken), 409);
  const device = await f.pair('tls-phone');
  assert.equal((await f.json(f.base, '/api/remote-access/pair/check?deviceId=tls-phone', undefined, device)).body.paired, true);
  await f.configure(true, { caFile: '' });
  await until(() => !f.relay.online.has('desktop'));
  let state;
  for (let i = 0; i < 100; i++) {
    state = await f.json(f.localUrl, '/api/remote-access/relay/config');
    if (state.body.detail.includes('TLS')) break;
    await new Promise(r => setTimeout(r, 20));
  }
  assert.match(state.body.detail, /TLS/);
  assert.equal(f.relay.online.has('desktop'), false);
  await f.configure(); await until(() => f.relay.online.has('desktop'));
});

test('真实 WebSocket 和 SSE：按设备撤销立即关闭流，其他设备继续连接', { skip: !backend && 'Set DSH_RELAY_BACKEND to an isolated backend checkout', timeout: 20000 }, async t => {
  const f = await fixture(t), a = await f.pair('stream-a'), b = await f.pair('stream-b');
  const open = async token => {
    const ws = new WebSocket(f.base.replace('http:', 'ws:') + '/api/events.mux', { headers: { Authorization: 'Bearer ' + token } });
    await once(ws, 'open'); return ws;
  };
  const wa = await open(a), wb = await open(b);
  t.after(() => { wa.terminate(); wb.terminate(); });
  const msg = once(wa, 'message'); wa.send('event'); assert.equal((await msg)[0].toString(), 'event');
  const controller = new AbortController(); t.after(() => controller.abort());
  const sse = await fetch(f.base + '/api/events.host', { headers: { Authorization: 'Bearer ' + a }, signal: controller.signal });
  const reader = sse.body.getReader(); assert.match(new TextDecoder().decode((await reader.read()).value), /data: first/);
  const closed = once(wa, 'close');
  await f.json(f.localUrl, '/api/remote-access/pair/revoke', { deviceId: 'stream-a' });
  await closed;
  await assert.rejects(async () => { while (!(await reader.read()).done) {} throw new Error('stream ended'); });
  const still = once(wb, 'message'); wb.send('still-connected'); assert.equal((await still)[0].toString(), 'still-connected');
  assert.equal((await f.json(f.base, '/api/remote-access/device/info', undefined, a)).status, 401);
});

test('中转断线会关闭活动请求，重连不重放写操作；禁用中转后直连保留', { skip: !backend && 'Set DSH_RELAY_BACKEND to an isolated backend checkout', timeout: 20000 }, async t => {
  const f = await fixture(t), token = await f.pair('reconnect-phone');
  const headers = { Authorization: 'Bearer ' + token };
  assert.equal(await (await fetch(f.base + '/api/echo', { method: 'POST', headers, body: 'once' })).text(), 'once');
  f.relay.online.get('desktop').ws.terminate();
  await until(() => !f.relay.online.has('desktop'));
  assert.equal((await fetch(f.base + '/api/echo', { method: 'POST', headers, body: 'offline' })).status, 503);
  await until(() => f.relay.online.has('desktop'), 8000);
  assert.equal(f.executions(), 1);
  assert.equal(await (await fetch(f.base + '/api/echo', { method: 'POST', headers, body: 'new' })).text(), 'new');
  await f.configure(false); await until(() => !f.relay.online.has('desktop'));
  assert.equal((await fetch(f.base + '/api/echo')).status, 503);
  assert.equal(await (await fetch(f.localUrl + '/api/echo', { method: 'POST', headers: { ...headers, 'X-Forwarded-For': 'remote' }, body: 'direct' })).text(), 'direct');
});
