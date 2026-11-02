import http from 'node:http';
import net from 'node:net';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import WebSocket from 'ws';
import { Tunnel, MAX_FRAME } from './tunnel.js';
import { RELAY_REQUEST, VALID_ID, bodyJson, isLocalAdmin, reply } from './security.js';
const empty = { enabled: false, serverUrl: '', hostId: '', registrationToken: '', caFile: '', allowInsecureLoopback: false };
export function validateRelayConfig(input, previous = empty) {
    const c = { enabled: input.enabled === true, serverUrl: String(input.serverUrl || '').trim().replace(/\/$/, ''), hostId: String(input.hostId || '').trim(), registrationToken: String(input.registrationToken || previous.registrationToken), caFile: String(input.caFile || '').trim(), allowInsecureLoopback: input.allowInsecureLoopback === true };
    if (!c.enabled && !c.serverUrl)
        return c;
    const url = new URL(c.serverUrl);
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
    if (url.username || url.password || url.search || url.hash || url.pathname !== '/')
        throw new Error('服务器地址只填写协议、域名和端口');
    if (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback && c.allowInsecureLoopback))
        throw new Error('公网中转必须使用 HTTPS');
    if (!VALID_ID.test(c.hostId))
        throw new Error('主机 ID 只允许字母、数字、下划线及连字符，最多 64 字符');
    if (!/^[a-f0-9]{64}$/.test(c.registrationToken))
        throw new Error('注册凭证必须为服务器生成的 64 位十六进制字符串');
    return c;
}
/** A private gateway dispatches only /api routes and always marks requests as remote. */
export function createGateway(webServer) {
    const match = (table, path, prefixes = false) => {
        if (!prefixes)
            return table?.get(path);
        return [...(table || [])].sort((a, b) => b[0].length - a[0].length).find(([key]) => path.startsWith(key))?.[1];
    };
    const sockets = new Set();
    const gateway = http.createServer((req, res) => {
        req[RELAY_REQUEST] = true;
        const path = new URL(req.url, 'http://localhost').pathname;
        if (!/^\/api\//.test(path))
            return reply(res, 404, { error: 'not_found' });
        const route = match(webServer.exact, path) || match(webServer.prefixes, path, true);
        if (!route)
            return reply(res, 404, { error: 'not_found' });
        Promise.resolve().then(() => route.handler(req, res)).catch(() => {
            if (!res.headersSent)
                reply(res, 500, { error: 'upstream_error' });
            else
                res.destroy();
        });
    });
    gateway.on('connection', socket => { sockets.add(socket); socket.once('close', () => sockets.delete(socket)); });
    gateway.on('upgrade', (req, socket, head) => {
        req[RELAY_REQUEST] = true;
        socket.on('error', () => { });
        const path = new URL(req.url, 'http://localhost').pathname;
        const route = /^\/api\/events\.(mux|host)$/.test(path) && match(webServer.upgrades, path);
        if (!route) {
            socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
            return;
        }
        Promise.resolve().then(() => route.handler(req, socket, head)).catch(() => socket.destroy());
    });
    gateway.on('clientError', (_, socket) => socket.destroy());
    return { gateway, async close() { for (const s of sockets)
            s.destroy(); await new Promise(r => gateway.close(() => r())); } };
}
export function createRelayManager(home, webServer, register) {
    const file = join(home, 'remote-access', 'relay.json');
    let config = { ...empty };
    let state = 'disabled', detail = '';
    try {
        if (existsSync(file))
            config = validateRelayConfig(JSON.parse(readFileSync(file, 'utf8')));
    }
    catch {
        detail = '中转配置读取失败，请重新保存';
    }
    let generation = 0;
    let local;
    let ws;
    let retry;
    let backoff = 1000;
    const stop = async () => {
        generation++;
        clearTimeout(retry);
        retry = undefined;
        ws?.terminate();
        ws = undefined;
        const old = local;
        local = undefined;
        if (old)
            await old.close();
        state = 'disabled';
    };
    const start = async () => {
        await stop();
        if (!config.enabled)
            return;
        const current = generation;
        let ca;
        try {
            if (config.caFile)
                ca = readFileSync(config.caFile);
        }
        catch {
            state = 'error';
            detail = '无法读取 CA 证书';
            return;
        }
        const instance = createGateway(webServer);
        local = instance;
        try {
            await new Promise((resolve, reject) => { instance.gateway.once('error', reject); instance.gateway.listen(0, '127.0.0.1', resolve); });
        }
        catch {
            state = 'error';
            detail = '无法启动本地中转通道';
            return;
        }
        if (generation !== current) {
            await instance.close();
            return;
        }
        const port = instance.gateway.address().port;
        const connect = () => {
            if (generation !== current)
                return;
            state = 'connecting';
            detail = '';
            const url = config.serverUrl.replace(/^http/, 'ws') + '/relay/v1/agent/' + config.hostId;
            const client = new WebSocket(url, { headers: { Authorization: 'Bearer ' + config.registrationToken }, ca, rejectUnauthorized: true, followRedirects: false, handshakeTimeout: 15000, maxPayload: MAX_FRAME, perMessageDeflate: false });
            ws = client;
            let denied = false;
            client.on('open', () => {
                if (generation !== current) {
                    client.terminate();
                    return;
                }
                state = 'connected';
                backoff = 1000;
                new Tunnel(client, channel => {
                    const socket = net.connect({ host: '127.0.0.1', port });
                    socket.on('error', () => channel.destroy());
                    socket.on('close', hadError => { if (hadError || !socket.readableEnded)
                        channel.destroy(); });
                    channel.on('close', () => socket.destroy());
                    channel.pipe(socket).pipe(channel);
                });
            });
            client.on('unexpected-response', (_, response) => {
                denied = [401, 403, 409].includes(response.statusCode || 0);
                detail = denied ? '注册被拒绝，请核对主机 ID、凭证及重复连接' : '中转服务器暂不可用';
                response.resume();
                client.terminate();
            });
            client.on('error', () => { if (generation === current && !denied)
                detail = '连接失败，请检查网络、地址和 TLS 证书'; });
            client.on('close', () => {
                if (generation !== current)
                    return;
                state = denied ? 'error' : 'reconnecting';
                if (!denied) {
                    retry = setTimeout(connect, backoff + Math.floor(Math.random() * 500));
                    backoff = Math.min(backoff * 2, 30000);
                    retry.unref();
                }
            });
        };
        connect();
    };
    let saving = false;
    register({ kind: 'exact', path: '/api/remote-access/relay/config', handler: async (req, res) => {
            if (!isLocalAdmin(req))
                return reply(res, 401, { error: 'local_admin_required' });
            if (req.method === 'GET') {
                const { registrationToken, ...publicConfig } = config;
                return reply(res, 200, { ok: true, ...publicConfig, tokenConfigured: !!registrationToken, state, detail });
            }
            if (req.method !== 'POST')
                return reply(res, 405, { error: 'method_not_allowed' });
            if (saving)
                return reply(res, 409, { ok: false, error: '配置正在保存，请稍后重试' });
            saving = true;
            try {
                const next = validateRelayConfig(await bodyJson(req), config);
                mkdirSync(join(home, 'remote-access'), { recursive: true, mode: 0o700 });
                writeFileSync(file + '.tmp', JSON.stringify(next), { mode: 0o600 });
                renameSync(file + '.tmp', file);
                config = next;
                await start();
                return reply(res, 200, { ok: true, state, detail });
            }
            catch {
                return reply(res, 400, { ok: false, error: '保存失败：请核对 HTTPS 地址、主机 ID、64 位注册凭证和文件权限' });
            }
            finally {
                saving = false;
            }
        } });
    return { start, stop, status: () => ({ state, detail }) };
}
