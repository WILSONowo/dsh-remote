import http from 'node:http';
import https from 'node:https';
import { randomBytes } from 'node:crypto';
import net from 'node:net';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import WebSocket from 'ws';
import { Tunnel, MAX_FRAME } from './tunnel.js';
import { RELAY_REQUEST, bodyJson, isLocalAdmin, reply } from './security.js';
import { computerIdentity } from './identity.js';
import QRCode from 'qrcode';

type Config = { enabled: boolean; serverUrl: string; hostId: string; registrationToken: string; registrationMode: 'automatic'; caFile: string; allowInsecureLoopback: boolean };
const empty: Config = { enabled: false, serverUrl: '', hostId: '', registrationToken: '', registrationMode: 'automatic', caFile: '', allowInsecureLoopback: false };
export function validateRelayConfig(input: any, previous: Config = empty, persisted = false): Config {
  if (!persisted && ['hostId', 'registrationToken', 'registrationMode'].some(key => Object.hasOwn(input, key))) throw new Error('不再支持手动中转配置');
  if (persisted && input.registrationMode !== 'automatic') throw new Error('旧版手动配置已停用，请重新启用自动注册');
  const serverUrl = String(input.serverUrl || '').trim().replace(/\/$/, '');
  const sameServer = serverUrl === previous.serverUrl;
  const c: Config = { enabled: input.enabled === true, serverUrl,
    hostId: persisted ? input.hostId : sameServer ? previous.hostId : '',
    registrationToken: persisted ? input.registrationToken : sameServer ? previous.registrationToken : '',
    registrationMode: 'automatic', caFile: String(input.caFile || '').trim(), allowInsecureLoopback: input.allowInsecureLoopback === true };
  if (!c.enabled && !c.serverUrl) return c;
  const url = new URL(c.serverUrl);
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('服务器地址只填写协议、域名和端口');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback && c.allowInsecureLoopback)) throw new Error('公网中转必须使用 HTTPS');
  if (!persisted && !c.registrationToken) c.registrationToken = randomBytes(32).toString('hex');
  if (typeof c.hostId !== 'string' || (c.hostId && !/^[a-f0-9]{32}$/.test(c.hostId))) throw new Error('自动注册身份无效');
  if (typeof c.registrationToken !== 'string' || !/^[a-f0-9]{64}$/.test(c.registrationToken)) throw new Error('自动注册凭证无效');
  return c;
}

/** A private gateway dispatches only /api routes and always marks requests as remote. */
export function createGateway(webServer: any) {
  const match = (table: Map<string, any>, path: string, prefixes = false) => {
    if (!prefixes) return table?.get(path);
    return [...(table || [])].sort((a, b) => b[0].length - a[0].length).find(([key]) => path.startsWith(key))?.[1];
  };
  const sockets = new Set<net.Socket>();
  const gateway = http.createServer((req, res) => {
    (req as any)[RELAY_REQUEST] = true;
    const path = new URL(req.url!, 'http://localhost').pathname;
    if (!/^\/api\//.test(path)) return reply(res, 404, { error: 'not_found' });
    const route = match(webServer.exact, path) || match(webServer.prefixes, path, true);
    if (!route) return reply(res, 404, { error: 'not_found' });
    Promise.resolve().then(() => route.handler(req, res)).catch(() => {
      if (!res.headersSent) reply(res, 500, { error: 'upstream_error' }); else res.destroy();
    });
  });
  gateway.on('connection', socket => { sockets.add(socket); socket.once('close', () => sockets.delete(socket)); });
  gateway.on('upgrade', (req, socket, head) => {
    (req as any)[RELAY_REQUEST] = true;
    socket.on('error', () => {});
    const path = new URL(req.url!, 'http://localhost').pathname;
    const route = /^\/api\/events\.(mux|host)$/.test(path) && match(webServer.upgrades, path);
    if (!route) { socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n'); return; }
    Promise.resolve().then(() => route.handler(req, socket, head)).catch(() => socket.destroy());
  });
  gateway.on('clientError', (_, socket) => socket.destroy());
  return { gateway, async close() { for (const s of sockets) s.destroy(); await new Promise<void>(r => gateway.close(() => r())); } };
}

export function createRelayManager(home: string, webServer: any, register: (route: any) => void, generatePairTicket?: () => { ticket: string; expiresAt: number }) {
  const file = join(home, 'remote-access', 'relay.json');
  let config = { ...empty };
  let state = 'disabled', detail = '';
  try { if (existsSync(file)) config = validateRelayConfig(JSON.parse(readFileSync(file, 'utf8')), undefined, true); }
  catch { detail = '中转配置已失效或属于旧版手动方案，请填写服务器地址重新启用，再让手机扫码连接'; }
  let generation = 0;
  let local: ReturnType<typeof createGateway> | undefined;
  let ws: WebSocket | undefined;
  let retry: NodeJS.Timeout | undefined;
  let backoff = 1000;
  const persist = () => {
    mkdirSync(join(home, 'remote-access'), { recursive: true, mode: 0o700 });
    writeFileSync(file + '.tmp', JSON.stringify(config), { mode: 0o600 });
    renameSync(file + '.tmp', file);
  };
  let registrationRequest: http.ClientRequest | undefined;
  const stop = async () => {
    generation++; clearTimeout(retry); retry = undefined;
    registrationRequest?.destroy(); registrationRequest = undefined;
    ws?.terminate(); ws = undefined;
    const old = local; local = undefined;
    if (old) await old.close();
    state = 'disabled';
  };
  const start = async () => {
    await stop();
    if (!config.enabled) return;
    const current = generation;
    let ca: Buffer | undefined;
    try { if (config.caFile) ca = readFileSync(config.caFile); }
    catch { state = 'error'; detail = '无法读取 CA 证书'; return; }
    { // Automatic registration is the only enrollment path.
      state = 'registering'; detail = '';
      try {
        persist(); // Save the random credential before sending it; retry is idempotent.
        const result = await new Promise<any>((resolve, reject) => {
          const req = (config.serverUrl.startsWith('https:') ? https : http).request(config.serverUrl + '/relay/v1/register', {
            method: 'POST', ca, rejectUnauthorized: true,
            headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + config.registrationToken },
          }, res => {
            let data = '';
            res.on('data', chunk => { data += chunk; if (data.length > 8192) req.destroy(new Error('注册响应异常')); });
            res.on('error', reject);
            res.on('end', () => {
              // Closing enrollment must not disable computers already registered.
              if (res.statusCode === 403 && config.hostId) { resolve({ ok: true, hostId: config.hostId }); return; }
              if (res.statusCode !== 200) {
                const messages: Record<number, string> = { 403: '服务器未启用自动注册，请联系服务器维护者', 404: '服务器版本不支持自动注册，请更新后端', 429: '注册请求过于频繁，请稍后重试', 503: '服务器暂不可用或已达到设备容量上限' };
                reject(new Error(messages[res.statusCode || 0] || '电脑注册失败，请检查服务器状态')); return;
              }
              try { resolve(JSON.parse(data)); } catch { reject(new Error('注册响应异常')); }
            });
          });
          registrationRequest = req;
          const deadline = setTimeout(() => req.destroy(new Error('注册超时，请检查网络和服务器地址')), 15000);
          deadline.unref();
          req.once('close', () => clearTimeout(deadline));
          req.on('error', reject);
          req.end(JSON.stringify({ name: computerIdentity(home).name }));
        });
        if (generation !== current) return;
        if (!result?.ok || typeof result.hostId !== 'string' || !/^[a-f0-9]{32}$/.test(result.hostId) || (config.hostId && config.hostId !== result.hostId)) throw new Error('服务器返回的电脑身份不一致');
        config.hostId = result.hostId; persist();
      } catch (error) {
        if (generation !== current) return;
        state = 'error'; detail = error instanceof Error && !/certificate|connect|ENOTFOUND|ECONN/i.test(error.message) ? error.message : '注册失败，请检查网络、服务器地址和 TLS 证书';
        retry = setTimeout(() => { void start(); }, 30000); retry.unref();
        return;
      } finally { if (generation === current) registrationRequest = undefined; }
    }
    const instance = createGateway(webServer); local = instance;
    try { await new Promise<void>((resolve, reject) => { instance.gateway.once('error', reject); instance.gateway.listen(0, '127.0.0.1', resolve); }); }
    catch { state = 'error'; detail = '无法启动本地中转通道'; return; }
    if (generation !== current) { await instance.close(); return; }
    const port = (instance.gateway.address() as net.AddressInfo).port;
    const connect = () => {
      if (generation !== current) return;
      state = 'connecting'; detail = '';
      const url = config.serverUrl.replace(/^http/, 'ws') + '/relay/v1/agent/' + config.hostId;
      const client = new WebSocket(url, { headers: { Authorization: 'Bearer ' + config.registrationToken }, ca, rejectUnauthorized: true, followRedirects: false, handshakeTimeout: 15000, maxPayload: MAX_FRAME, perMessageDeflate: false });
      ws = client;
      let denied = false;
      client.on('open', () => {
        if (generation !== current) { client.terminate(); return; }
        state = 'connected'; backoff = 1000;
        new Tunnel(client, channel => {
          const socket = net.connect({ host: '127.0.0.1', port });
          socket.on('error', () => channel.destroy());
          socket.on('close', hadError => { if (hadError || !socket.readableEnded) channel.destroy(); });
          channel.on('close', () => socket.destroy());
          channel.pipe(socket).pipe(channel);
        });
      });
      client.on('unexpected-response', (_, response) => {
        denied = [401, 403, 409].includes(response.statusCode || 0);
        detail = denied ? '电脑连接被拒绝：设备登记无效或已有相同身份在线，请检查服务器状态及重复运行的插件' : '中转服务器暂不可用';
        response.resume(); client.terminate();
      });
      client.on('error', () => { if (generation === current && !denied) detail = '连接失败，请检查网络、地址和 TLS 证书'; });
      client.on('close', () => {
        if (generation !== current) return;
        state = denied ? 'error' : 'reconnecting';
        if (!denied) { retry = setTimeout(connect, backoff + Math.floor(Math.random() * 500)); backoff = Math.min(backoff * 2, 30000); retry.unref(); }
      });
    };
    connect();
  };
  let saving = false;
  register({ kind: 'exact', path: '/api/remote-access/relay/config', handler: async (req: any, res: any) => {
    if (!isLocalAdmin(req)) return reply(res, 401, { error: 'local_admin_required' });
    if (req.method === 'GET') {
      const { hostId, registrationToken, registrationMode, ...publicConfig } = config;
      return reply(res, 200, { ok: true, ...publicConfig, computerName: computerIdentity(home).name, state, detail });
    }
    if (req.method !== 'POST') return reply(res, 405, { error: 'method_not_allowed' });
    if (saving) return reply(res, 409, { ok: false, error: '配置正在保存，请稍后重试' });
    saving = true;
    try {
      const input = await bodyJson(req), next = validateRelayConfig(input, config);
      computerIdentity(home, input.computerName);
      await stop(); config = next; persist();
      await start();
      return reply(res, 200, { ok: true, state, detail });
    } catch { return reply(res, 400, { ok: false, error: '保存失败：仅支持自动注册，请检查服务器 HTTPS 地址、电脑名称、证书和文件权限' }); }
    finally { saving = false; }
  } });
  register({ kind: 'exact', path: '/api/remote-access/relay/pairing', handler: async (req: any, res: any) => {
    if (!isLocalAdmin(req)) return reply(res, 401, { error: 'local_admin_required' });
    if (req.method !== 'POST') return reply(res, 405, { error: 'method_not_allowed' });
    if (state !== 'connected' || !generatePairTicket) return reply(res, 409, { ok: false, error: '请先启用服务器连接并等待连接成功' });
    try {
      await bodyJson(req);
      const identity = computerIdentity(home);
      const payload = { v: 1, server: config.serverUrl, hostId: config.hostId, computerId: identity.id, name: identity.name, ...generatePairTicket() };
      const link = 'dshremote://pair#' + Buffer.from(JSON.stringify(payload)).toString('base64url');
      const image = await QRCode.toDataURL(link, { errorCorrectionLevel: 'M', margin: 4, width: 400 });
      return reply(res, 200, { ok: true, link, image, expiresAt: payload.expiresAt });
    } catch { return reply(res, 503, { ok: false, error: '无法生成配对二维码，请重试' }); }
  } });
  return { start, stop, status: () => ({ state, detail }) };
}
