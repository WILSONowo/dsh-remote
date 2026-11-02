import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createServer, get as httpGet } from 'node:http';
import { once } from 'node:events';
import { createHash, randomBytes } from 'node:crypto';
import WebSocket from 'ws';
import { createModernAdapter, assistantBaseline } from '../lib/modern.js';
import { createSecurity, RELAY_REQUEST } from '../lib/security.js';
import * as plugin from '../lib/index.js';
import { listenLoopback } from './loopback.mjs';

// Explicit isolated SDK fixture; no auto-discovery of installed apps or user data.
const runtime = process.env.DSH_TEST_RUNTIME;
const backend = process.env.DSH_RELAY_BACKEND;
if (runtime && !existsSync(runtime)) throw new Error('DSH_TEST_RUNTIME must point to an isolated SDK directory');
const sdk = p => import(pathToFileURL(join(runtime, p, 'lib/index.js')).href);
const metadata = p => import(pathToFileURL(join(runtime, p, 'lib/typert.host.js')).href);
const untilAbort = signal => signal.aborted ? Promise.resolve() : new Promise(r => signal.addEventListener('abort', r, { once: true }));

test('0.2.1-rc.5 real Gateway + generated schemas: mobile RPC, history, WS, interactions and peer cleanup', { skip: !runtime && 'Set DSH_TEST_RUNTIME to an isolated SDK directory', timeout: 15000 }, async t => {
  const { Context } = await sdk('cordis');
  const { TypertRemoteService } = await sdk('dsh-typert-protocol');
  const { Peer } = await sdk('dsh-client-connection');
  const { AssistantStreamAccumulator } = await sdk('dsh-llm');
  const accumulated = new AssistantStreamAccumulator();
  accumulated.push({ time: 1000, chunk: { type: 'text-delta', index: 0, text: 'before ' } });
  accumulated.push({ time: 1001, chunk: { type: 'text-delta', index: 0, text: 'reconnect' } });
  assert.equal(assistantBaseline({ startedAfterSeq: 2, stream: accumulated.snapshot() }).text, 'before reconnect');
  const ctx = new Context();
  const temp = mkdtempSync(join(tmpdir(), 'dsh-modern-'));
  t.after(async () => { await ctx.fiber.dispose(); rmSync(temp, { recursive: true, force: true }); });
  ctx.plugin((await sdk('dsh-typert-registry')).default);
  ctx.plugin((await sdk('dsh-api-remote-access')).default);
  ctx.plugin((await sdk('dsh-api-gateway')).default, { websocketHeartbeatIntervalMs: 20000, streamInboxBytes: 1024 * 1024 });
  const registered = ['dsh-api-session-controller', 'dsh-api-workspace-controller', 'dsh-agent-preset-registry'];
  for (const p of registered) ctx.typert.register((await metadata(p)).TYPERT);
  const calls = [];
  const durable = [0, 1, 2].map(seq => ({ type: 'user/message', seq, time: 1000 + seq, data: { content: [{ type: 'text', text: 'message ' + seq }] } }));
  class Sessions extends TypertRemoteService {
    constructor(c) { super(c, 'sessionController', { namespace: 'session' }); }
    list(request) { calls.push({ method: 'list', request, principal: this.ctx.invocation.peer.principal }); return { items: [{ sessionId: 's1', updatedAt: 1002, running: false, blank: false }] }; }
    create() { return { sessionId: 's1' }; }
    prompt(request) { calls.push({ method: 'prompt', request }); return { accepted: true }; }
    modelCatalog() { return { default: { provider: 'test', model: 'test' }, routableProviders: ['test'], groups: [], failures: [] }; }
    projections() { return { kind: 'sequenced', asOfSeq: 2, values: {} }; }
    page(request) { calls.push({ method: 'page', request }); return { records: durable.filter(e => e.seq < request.beforeSeq).map(event => ({ type: 'event', event })), hasMore: false }; }
    async *follow(request, signal) {
      yield { type: 'snapshot', cursor: 2, records: [{ type: 'event', event: durable[2] }], hasMore: true, projections: { asOfSeq: 2, values: { title: 'SDK fixture' } } };
      if (request.assistantStream) {
        yield { type: 'assistant-stream', frame: { type: 'chunk', time: 1003, chunk: { type: 'text-delta', text: 'live' } } };
        yield { type: 'event', event: { ...durable[2], seq: 3 } };
      }
      await untilAbort(signal);
    }
  }
  class Workspaces extends TypertRemoteService {
    constructor(c) { super(c, 'workspaceController', { namespace: 'workspace' }); }
    async *follow(signal) { yield { type: 'baseline', value: { items: [], archivedSessionIds: [] } }; await untilAbort(signal); }
  }
  ctx.plugin(Sessions); ctx.plugin(Workspaces);
  const activePeers = new Set();
  // Real scoped Peers, but no browser authentication process or user home.
  const connection = { createPeer(options) {
    const p = new Peer(ctx, options); activePeers.add(p);
    p.signal.addEventListener('abort', () => activePeers.delete(p)); return p;
  }};
  let deliver;
  const remoteEvents = [];
  const stopEvents = ctx.typertGateway.registerRemoteEvents(async function* (signal) {
    while (!signal.aborted) {
      if (!remoteEvents.length) await Promise.race([new Promise(r => { deliver = r; }), untilAbort(signal)]);
      while (remoteEvents.length) yield remoteEvents.shift();
    }
  }, {});
  t.after(stopEvents);
  const exact = new Map(), upgrades = new Map();
  const register = route => (route.kind === 'upgrade' ? upgrades : exact).set(route.path, route);
  const security = createSecurity(temp, register);
  const bridge = createModernAdapter({ typertGateway: ctx.typertGateway, connection, webServer: { prefixes: new Map() } }, register);
  t.after(async () => { security.dispose(); await bridge.dispose(); });
  const server = createServer((req, res) => {
    if (!req.url.startsWith('/local/')) req[RELAY_REQUEST] = true;
    else req.url = req.url.slice(6);
    if (!security.authorize(req)) { res.writeHead(401); res.end(); return; }
    security.track(req, res);
    const route = exact.get(new URL(req.url, 'http://localhost').pathname);
    if (!route) { res.writeHead(404); res.end(); return; }
    void route.handler(req, res);
  });
  server.on('upgrade', (req, socket, head) => {
    req[RELAY_REQUEST] = true;
    if (!security.authorize(req)) { socket.destroy(); return; }
    security.track(req, socket); upgrades.get(req.url)?.handler(req, socket, head);
  });
  await listenLoopback(server);
  const url = `http://127.0.0.1:${server.address().port}`;
  t.after(() => { server.closeAllConnections(); server.close(); });
  const http = async (path, body, token) => {
    const response = await fetch(url + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
    return { status: response.status, body: response.status === 401 ? null : await response.json() };
  };
  const code = (await http('/local/api/remote-access/pair/code/generate', {})).body.code;
  const token = (await http('/api/remote-access/pair/code/verify', { deviceId: 'a', code })).body.token;
  assert.match(token, /^[a-f0-9]{64}$/);
  const rpc = async (method, payload = {}, auth = token) => {
    const response = await http('/api/' + method, { type: 'client-request', rpcId: 'req-test', method, payload }, auth);
    if (response.status === 200) assert.equal(response.body.result.ok, true, JSON.stringify(response.body));
    return response.body?.result.value;
  };
  assert.equal((await rpc('session.list')).items[0].sessionId, 's1');
  assert.equal(calls[0].principal.kind, 'owner');
  assert.deepEqual(await rpc('workspace.list'), { items: [], archivedSessionIds: [] });
  assert.equal((await rpc('session.models', { sessionId: 's1' })).current.provider, 'test');
  const history = await rpc('session.history', { sessionId: 's1', beforeSeq: 2 });
  assert.deepEqual(history.events.map(x => x.event.seq), [0, 1]);
  assert.equal(calls.find(x => x.method === 'page').request.throughSeq, 2);
  await rpc('session.prompt', { sessionId: 's1', mode: 'queue', content: [{ type: 'text', text: 'hello' }] });
  assert.equal(calls.find(x => x.method === 'prompt').request.requestId, 'req-test');
  const socket = new WebSocket(url.replace('http:', 'ws:') + '/api/events.mux', { headers: { Authorization: 'Bearer ' + token } });
  t.after(() => socket.terminate());
  const frames = []; socket.on('message', data => frames.push(JSON.parse(data)));
  await once(socket, 'open');
  const waitFor = async predicate => {
    for (let i = 0; i < 200; i++) { if (predicate()) return; await new Promise(r => setTimeout(r, 10)); }
    assert.fail('Timed out waiting for Host frame');
  };
  await waitFor(() => frames.some(f => f.payload.event?.type === 'assistant/chunk'));
  await waitFor(() => ctx.typertGateway.hasLiveClient());
  assert.ok(frames.some(f => f.payload.event?.data?.chunk?.text === 'live'));
  const actor = {};
  const sendInteraction = (event, request) => {
    const completion = Promise.withResolvers();
    remoteEvents.push({ event, request: { ...request, agent: actor },
      context: { value: ctx, subject: actor, agentId: 's1' },
      resolve: completion.resolve, reject: completion.reject });
    deliver?.(); return completion.promise;
  };
  const decision = sendInteraction('approval/request', { toolName: 'test', reason: 'fixture' });
  await waitFor(() => frames.some(f => f.payload.type === 'approval/requested'));
  const approval = frames.find(f => f.payload.type === 'approval/requested');
  const body = { type: 'client-response', rpcId: approval.rpcId, result: { ok: true, value: {
    sessionId: 's1', approvalId: approval.payload.approvalId, outcome: 'allowed-once',
  } } };
  const secondCode = (await http('/local/api/remote-access/pair/code/generate', {})).body.code;
  const tokenB = (await http('/api/remote-access/pair/code/verify', { deviceId: 'b', code: secondCode })).body.token;
  assert.equal((await http('/api/respond', body, tokenB)).status, 403);
  assert.equal((await http('/api/respond', body, token)).status, 200);
  assert.deepEqual(await decision, { kind: 'result', value: 'allowed-once' });
  assert.equal((await http('/api/respond', body, token)).status, 403);
  const questionResult = sendInteraction('user-questions/request', { questions: [{ id: 'q1', question: 'Test?', options: [] }] });
  await waitFor(() => frames.some(f => f.payload.type === 'question/requested'));
  const question = frames.find(f => f.payload.type === 'question/requested');
  const answer = { answers: [{ id: 'q1', selected: [], custom: 'test' }] };
  assert.equal((await http('/api/respond', { type: 'client-response', rpcId: question.rpcId,
    result: { ok: true, value: { sessionId: 's1', answer } } }, token)).status, 200);
  assert.deepEqual(await questionResult, { kind: 'result', value: answer });
  const closed = once(socket, 'close');
  await http('/local/api/remote-access/pair/revoke', { deviceId: 'a' });
  await closed;
  await waitFor(() => activePeers.size === 0);
  assert.equal((await http('/api/session.list', { type: 'client-request', rpcId: 'x', method: 'session.list', payload: {} }, token)).status, 401);
});

test('0.2.1-rc.5 actual WebServer/Connection: plugin mounting, native auth, relay RPC and hot-unload', { skip: (!runtime || !backend) && 'Set DSH_TEST_RUNTIME and DSH_RELAY_BACKEND', timeout: 15000 }, async t => {
  const { Context } = await sdk('cordis');
  const { HostConnectionService } = await sdk('dsh-client-connection');
  const ctx = new Context();
  const home = mkdtempSync(join(tmpdir(), 'dsh-mount-'));
  const oldHome = process.env.DSH_HOME; process.env.DSH_HOME = home;
  t.after(async () => { await ctx.fiber.dispose(); if (oldHome === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = oldHome; rmSync(home, { recursive: true, force: true }); });
  await ctx.plugin((await sdk('dsh-host-webserver')).default, { host: '127.0.0.1', port: 0, compression: 'none' }).await();
  await ctx.plugin(c => { new HostConnectionService(c, ['browser.test'], { isAuthenticated: req => req.headers.cookie === 'fixture=browser', authenticateApi: () => undefined }); }).await();
  ctx.plugin((await sdk('dsh-typert-registry')).default);
  ctx.plugin((await sdk('dsh-api-remote-access')).default);
  ctx.plugin((await sdk('dsh-api-gateway')).default, { websocketHeartbeatIntervalMs: 20000, streamInboxBytes: 1024 * 1024 });
  ctx.typert.register((await metadata('dsh-api-session-controller')).TYPERT);
  const { TypertRemoteService } = await sdk('dsh-typert-protocol');
  ctx.plugin(class extends TypertRemoteService {
    constructor(c) { super(c, 'sessionController', { namespace: 'session' }); }
    list() { return { items: [{ sessionId: 'relay-sdk', updatedAt: 1, blank: false, running: false }] }; }
  });
  const native = { kind: 'prefix', path: '/api', handler: (req, res) => {
    const rejection = ctx.connection.requestRejection(req);
    res.writeHead(rejection || 200); res.end(rejection ? 'unauthorized' : 'native browser');
  }};
  ctx.webServer.register(native);
  const staticRoute = { kind: 'exact', path: '/assets/test.js', handler: (_, res) => res.end('static') };
  ctx.webServer.register(staticRoute);
  const mounted = ctx.plugin(plugin); await mounted.await();
  assert.ok(ctx.connection, 'native Connection service must be ready');
  assert.ok(ctx.typertGateway, 'native Gateway service must be ready');
  for (let i = 0; i < 100 && !ctx.webServer.exact.has('/api/session.list'); i++) await new Promise(r => setTimeout(r, 10));
  assert.ok(ctx.webServer.exact.has('/api/session.list'), 'modern adapter must mount with actual Cordis injection');
  const base = ctx.webServer.getOrigin();
  const get = (path, cookie) => new Promise((resolve, reject) => {
    httpGet(base + path, { headers: { Host: 'browser.test', Origin: 'http://browser.test', ...(cookie ? { Cookie: cookie } : {}) } }, res => {
      let body = ''; res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, text: async () => body }));
    }).on('error', reject);
  });
  assert.equal((await get('/assets/test.js')).status, 200);
  assert.equal((await get('/api/session/list')).status, 401);
  assert.equal(await (await get('/api/session/list', 'fixture=browser')).text(), 'native browser');
  assert.equal(await (await get('/api/commands/list', 'fixture=browser')).text(), 'native browser');
  assert.equal((await get('/api/remote-access/pair/code/current', 'fixture=browser')).status, 401);
  const describe = await fetch(base + '/api/host.describe', { method: 'POST', headers: { 'Content-Type': 'application/json', Host: 'phone.test' }, body: JSON.stringify({ type: 'client-request', rpcId: 'describe', method: 'host.describe' }) });
  assert.equal((await describe.json()).result.value.hostProtocol, 'typert');
  const { createRelayServer } = await import(pathToFileURL(join(backend, 'src/server.mjs')).href);
  const registration = randomBytes(32).toString('hex');
  const relay = createRelayServer({ hosts: { sdk: createHash('sha256').update(registration).digest('hex') }, allowInsecureLoopback: true });
  await listenLoopback(relay.server);
  t.after(() => relay.close());
  const relayUrl = `http://127.0.0.1:${relay.server.address().port}`;
  const post = async (url, body, token) => {
    const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
    return { status: r.status, body: await r.json() };
  };
  assert.equal((await post(base + '/api/remote-access/relay/config', { enabled: true, serverUrl: relayUrl, hostId: 'sdk', registrationToken: registration, allowInsecureLoopback: true })).body.ok, true);
  for (let i = 0; i < 100 && !relay.online.has('sdk'); i++) await new Promise(r => setTimeout(r, 10));
  assert.ok(relay.online.has('sdk'));
  const code = (await post(base + '/api/remote-access/pair/code/generate', {})).body.code;
  const token = (await post(relayUrl + '/h/sdk/api/remote-access/pair/code/verify', { deviceId: 'sdk-phone', code })).body.token;
  assert.match(token, /^[a-f0-9]{64}$/);
  const result = await post(relayUrl + '/h/sdk/api/session.list', { type: 'client-request', rpcId: 'via-relay', method: 'session.list', payload: {} }, token);
  assert.equal(result.body.result.ok, true, JSON.stringify(result));
  assert.equal(result.body.result.value.items[0].sessionId, 'relay-sdk');
  assert.equal((await post(relayUrl + '/h/sdk/api/remote-access/pair/code/generate', {}, token)).status, 401);
  await mounted.dispose();
  assert.equal(ctx.webServer.prefixes.get('/api'), native, 'surviving host routes must not retain unloaded auth closures');
  assert.equal(ctx.webServer.exact.get('/assets/test.js'), staticRoute);
  assert.equal(ctx.webServer.exact.has('/api/session.list'), false);
});
