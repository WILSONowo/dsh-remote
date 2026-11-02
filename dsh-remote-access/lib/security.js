import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { computerIdentity, deviceName, defaultComputerName } from './identity.js';
import QRCode from 'qrcode';
import { networkInterfaces } from 'node:os';
import { createWakeLock } from './wake.js';
// Only the in-process relay gateway can set this marker. HTTP headers cannot impersonate it.
export const RELAY_REQUEST = Symbol('dsh.relay.request');
export const DEVICE_ID = Symbol('dsh.device.id');
export const VALID_ID = /^[A-Za-z0-9_-]{1,64}$/;
const hash = (s) => createHash('sha256').update(s).digest('hex');
const equal = (a, b) => timingSafeEqual(Buffer.from(hash(a)), Buffer.from(hash(b)));
const fresh = () => randomBytes(32).toString('hex');
export const reply = (res, status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
};
export async function bodyJson(req, limit = 16384) {
    if (!/^application\/json(?:;|$)/i.test(String(req.headers['content-type'] || '')))
        throw new Error('json_required');
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
        size += chunk.length;
        if (size > limit)
            throw new Error('body_too_large');
        chunks.push(Buffer.from(chunk));
    }
    const result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!result || typeof result !== 'object' || Array.isArray(result))
        throw new Error('invalid_body');
    return result;
}
const loopback = (s) => ['127.0.0.1', '::1', '::ffff:127.0.0.1', '::ffff:7f00:1'].includes(s);
export function isLocalAdmin(req) {
    if (req[RELAY_REQUEST] || !loopback(req.socket.remoteAddress || ''))
        return false;
    if (['forwarded', 'x-forwarded-for', 'x-real-ip', 'x-forwarded-host'].some(h => req.headers[h]))
        return false;
    try {
        const host = new URL('http://' + req.headers.host);
        if (!['localhost', '127.0.0.1', '[::1]'].includes(host.hostname))
            return false;
        if (req.headers.origin && new URL(req.headers.origin).host !== host.host)
            return false;
        if (req.headers['sec-fetch-site'] === 'cross-site')
            return false;
        return true;
    }
    catch {
        return false;
    }
}
export function createSecurity(home, register, options = {}) {
    const directory = join(home, 'remote-access');
    const file = join(directory, 'devices-v2.json');
    const settingsFile = join(directory, 'settings.json');
    let settings = existsSync(settingsFile) ? JSON.parse(readFileSync(settingsFile, 'utf8')) : { allowConnections: true, keepAwake: false };
    if (typeof settings.allowConnections !== 'boolean' || typeof settings.keepAwake !== 'boolean')
        throw new Error('Invalid remote access settings');
    const wake = options.wake || createWakeLock();
    const syncWake = () => wake.setEnabled(settings.allowConnections && settings.keepAwake);
    let lastSeenTimer;
    // Legacy paired.json / channel-token are deliberately not accepted as credentials.
    let devices = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')).devices : [];
    if (!Array.isArray(devices) || devices.some(d => !VALID_ID.test(d.deviceId) || !/^[a-f0-9]{64}$/.test(d.tokenHash))) {
        throw new Error('Invalid device credential store; remote access refused');
    }
    const save = (next) => {
        mkdirSync(directory, { recursive: true, mode: 0o700 });
        const tmp = file + '.' + randomBytes(8).toString('hex') + '.tmp';
        try {
            writeFileSync(tmp, JSON.stringify({ version: 2, devices: next }), { mode: 0o600 });
            renameSync(tmp, file);
            devices = next;
        }
        finally {
            rmSync(tmp, { force: true });
        }
    };
    const touch = (device) => {
        device.lastConnectedAt = Date.now();
        if (!lastSeenTimer) {
            lastSeenTimer = setTimeout(() => { lastSeenTimer = undefined; try {
                save(devices);
            }
            catch { /* Access must not fail because activity metadata could not be saved. */ } }, 30000);
            lastSeenTimer.unref();
        }
    };
    const active = new Map();
    const disconnect = (id) => {
        const callbacks = active.get(id);
        active.delete(id);
        setImmediate(() => { for (const close of callbacks || [])
            close(); });
    };
    const issue = (id, name) => {
        const token = fresh();
        save([...devices.filter(d => d.deviceId !== id), { deviceId: id, name, at: Date.now(), tokenHash: hash(token) }]);
        disconnect(id);
        return token;
    };
    const authenticate = (req) => {
        const token = /^Bearer ([a-f0-9]{64})$/.exec(String(req.headers.authorization || ''))?.[1];
        const device = token && devices.find(d => equal(hash(token), d.tokenHash));
        if (device)
            req[DEVICE_ID] = device.deviceId;
        return device || undefined;
    };
    const bootstrap = new Set(['pair/request', 'pair/status', 'pair/check', 'pair/claim', 'pair/code/verify', 'discovery/info'].map(p => '/api/remote-access/' + p));
    const adminPaths = new Set(['access/settings', 'direct/info', 'direct/qr', 'discovery/start', 'discovery/renew', 'discovery/stop', 'pair/respond', 'pair/list', 'pair/rename', 'computer/rename', 'pair/revoke', 'pair/code/generate', 'pair/code/current', 'trusted-hosts', 'relay/config', 'relay/pairing'].map(p => '/api/remote-access/' + p));
    const authorize = (req) => {
        const path = new URL(req.url, 'http://localhost').pathname;
        // Static desktop UI and browser login remain owned by the host.
        if (path !== '/api' && !path.startsWith('/api/'))
            return true;
        if (isLocalAdmin(req))
            return true;
        if (!settings.allowConnections)
            return false;
        const device = authenticate(req);
        if (adminPaths.has(path))
            return false;
        if (device)
            touch(device);
        if (bootstrap.has(path) || path === '/api/host.describe')
            return true;
        return !!device;
    };
    const track = (req, target) => {
        const id = req[DEVICE_ID];
        if (!id)
            return;
        let set = active.get(id);
        if (!set)
            active.set(id, set = new Set());
        const close = () => target.destroy();
        set.add(close);
        const remove = () => { set.delete(close); if (!set.size && active.get(id) === set)
            active.delete(id); };
        target.once('close', remove);
        target.once('finish', remove);
    };
    const pending = new Map();
    let scanTicket;
    const generatePairTicket = () => {
        if (!settings.allowConnections)
            throw new Error('connections_disabled');
        const ticket = fresh(), expiresAt = Date.now() + 120000;
        scanTicket = { hash: hash(ticket), expiresAt };
        return { ticket, expiresAt };
    };
    const pendingNow = (id) => {
        const p = pending.get(id);
        if (p && Date.now() - p.at < 120000)
            return p;
        pending.delete(id);
    };
    let code;
    const makeCode = () => code = { value: String(randomInt(1000000)).padStart(6, '0'), expires: Date.now() + 600000, attempts: 0 };
    let discovery;
    let discoveryTimer;
    const stopDiscovery = () => {
        const previous = discovery;
        discovery = undefined;
        clearTimeout(discoveryTimer);
        discoveryTimer = undefined;
        if (previous) {
            code = undefined;
            try {
                previous.stop();
            }
            catch { /* Discovery cleanup must not interrupt credential issuance. */ }
        }
    };
    const activeDiscovery = () => {
        if (discovery && (!code || code.expires <= Date.now() || code.attempts >= 5 || discovery.lease <= Date.now()))
            stopDiscovery();
        return discovery;
    };
    const renewDiscovery = () => {
        discovery.lease = Date.now() + 30000;
        clearTimeout(discoveryTimer);
        discoveryTimer = setTimeout(stopDiscovery, Math.max(0, Math.min(code.expires, discovery.lease) - Date.now()));
        discoveryTimer.unref();
    };
    const discoveryResult = () => ({ ok: true, session: discovery.session, name: discovery.name,
        code: code.value, expiresInSec: Math.max(0, Math.ceil((code.expires - Date.now()) / 1000)),
        discoveryFailed: discovery.failed });
    const audit = [];
    const record = (action, deviceId) => { audit.push({ ts: Date.now(), action, deviceId }); if (audit.length > 200)
        audit.shift(); };
    const rates = new Map();
    const registerApi = (path, method, fn) => register({
        kind: 'exact', path: '/api/remote-access/' + path,
        handler: async (req, res) => {
            if (!(Array.isArray(method) ? method.includes(req.method || '') : req.method === method))
                return reply(res, 405, { error: 'method_not_allowed' });
            if (!settings.allowConnections && !isLocalAdmin(req))
                return reply(res, 503, { ok: false, error: 'connections_disabled' });
            if (!authorize(req))
                return reply(res, 401, { error: 'unauthorized' });
            if (!isLocalAdmin(req) && bootstrap.has('/api/remote-access/' + path)) {
                const now = Date.now();
                for (const [k, v] of rates)
                    if (now - v.at > 60000)
                        rates.delete(k);
                const key = req.socket.remoteAddress || 'remote';
                const rate = rates.get(key) || { at: now, count: 0 };
                if (rates.size > 1024 || ++rate.count > 120)
                    return reply(res, 429, { error: 'rate_limited' });
                rates.set(key, rate);
            }
            try {
                const body = req.method === 'POST' ? await bodyJson(req) : {};
                if (!settings.allowConnections && !isLocalAdmin(req))
                    return reply(res, 503, { ok: false, error: 'connections_disabled' });
                const value = await fn(req, body, new URL(req.url, 'http://localhost'));
                return reply(res, 200, value);
            }
            catch (e) {
                const input = ['invalid_name', 'invalid_device', 'invalid_secret', 'invalid_body', 'json_required', 'body_too_large'].includes(e.message) || e instanceof SyntaxError;
                reply(res, input ? 400 : 503, { ok: false, error: input ? 'invalid_request' : 'credential_store_unavailable' });
            }
        },
    });
    const identity = (body) => {
        if (typeof body.deviceId !== 'string' || !VALID_ID.test(body.deviceId))
            throw new Error('invalid_device');
        return { id: body.deviceId, name: String(body.deviceName || '设备').slice(0, 64) };
    };
    const secret = (req) => {
        const s = String(req.headers['x-dsh-pairing-secret'] || '');
        if (!/^[a-f0-9]{64}$/.test(s))
            throw new Error('invalid_secret');
        return s;
    };
    const publicSettings = () => ({ ok: true, ...settings, wake: wake.status() });
    registerApi('access/settings', ['GET', 'POST'], (req, body) => {
        if (req.method === 'GET')
            return publicSettings();
        if (Object.keys(body).some(k => !['allowConnections', 'keepAwake'].includes(k)) || Object.values(body).some(v => typeof v !== 'boolean'))
            throw new Error('invalid_body');
        const next = { ...settings, ...body };
        if (body.keepAwake === true && !wake.status().supported)
            return { ok: false, error: 'wake_unsupported' };
        mkdirSync(directory, { recursive: true, mode: 0o700 });
        writeFileSync(settingsFile + '.tmp', JSON.stringify(next), { mode: 0o600 });
        renameSync(settingsFile + '.tmp', settingsFile);
        const changed = settings.allowConnections !== next.allowConnections;
        settings = next;
        if (!settings.allowConnections) {
            stopDiscovery();
            pending.clear();
            scanTicket = undefined;
            code = undefined;
            for (const id of active.keys())
                disconnect(id);
        }
        syncWake();
        if (changed)
            options.onAccessChange?.(settings.allowConnections);
        return publicSettings();
    });
    registerApi('direct/info', 'GET', req => {
        const port = req.socket.localPort;
        const protocol = req.socket.encrypted ? 'https' : 'http';
        const addresses = port ? Object.values(networkInterfaces()).flatMap(list => (list || [])
            .filter(n => !n.internal && n.family === 'IPv4')
            .map(n => `${protocol}://${n.address}:${port}`)) : [];
        return { ok: true, addresses: [...new Set(addresses)] };
    });
    registerApi('direct/qr', 'POST', async (_, body) => {
        if (typeof body.address !== 'string' || body.address.length > 2048)
            throw new Error('invalid_body');
        let address;
        try {
            address = new URL(body.address);
        }
        catch {
            throw new Error('invalid_body');
        }
        if (!['http:', 'https:'].includes(address.protocol) || !address.hostname || address.username || address.password || address.search || address.hash)
            throw new Error('invalid_body');
        const link = address.href.replace(/\/$/, '');
        return { ok: true, link, image: await QRCode.toDataURL(link, { errorCorrectionLevel: 'M', margin: 4, scale: 8 }) };
    });
    registerApi('discovery/start', 'POST', req => {
        if (!settings.allowConnections)
            return { ok: false, error: 'connections_disabled' };
        if (!options.publishDiscovery || !req.socket.localPort)
            return { ok: false, error: 'discovery_unavailable' };
        stopDiscovery();
        makeCode();
        const session = randomBytes(16).toString('hex'), name = computerIdentity(home).name;
        discovery = { session, name, lease: 0, failed: false, stop: () => { } };
        try {
            discovery.stop = options.publishDiscovery({ session, name, port: req.socket.localPort, tls: !!req.socket.encrypted }, () => { if (discovery?.session === session)
                discovery.failed = true; });
        }
        catch {
            discovery.failed = true;
        }
        renewDiscovery();
        return discoveryResult();
    });
    registerApi('discovery/renew', 'POST', (_, body) => {
        if (!activeDiscovery() || body.session !== discovery.session)
            return { ok: false, error: 'discovery_expired' };
        renewDiscovery();
        return discoveryResult();
    });
    registerApi('discovery/stop', 'POST', (_, body) => {
        if (body.session === discovery?.session)
            stopDiscovery();
        return { ok: true };
    });
    registerApi('discovery/info', 'GET', (req, _, url) => {
        if (req[RELAY_REQUEST] || !activeDiscovery() || url.searchParams.get('session') !== discovery.session)
            return { ok: false, error: 'discovery_expired' };
        return { ok: true, v: 1, session: discovery.session, name: discovery.name };
    });
    registerApi('pair/request', 'POST', (req, b) => {
        const { id, name } = identity(b);
        if (authenticate(req)?.deviceId === id)
            return { ok: true, state: 'paired' };
        if (req[RELAY_REQUEST] && b.ticket === undefined)
            return { ok: false, state: 'expired', error: 'pairing_ticket_required' };
        const s = secret(req), old = pendingNow(id);
        if (old && !equal(old.secretHash, hash(s)))
            return { ok: false, state: 'busy' };
        for (const id of pending.keys())
            pendingNow(id);
        if (!old && pending.size >= 16)
            return { ok: false, state: 'busy' };
        if (b.ticket !== undefined && !old) {
            if (typeof b.ticket !== 'string' || !/^[a-f0-9]{64}$/.test(b.ticket) || !scanTicket || scanTicket.expiresAt <= Date.now() || !equal(hash(b.ticket), scanTicket.hash)) {
                return { ok: false, state: 'expired', error: 'invalid_pairing_ticket' };
            }
            scanTicket = undefined; // One device per QR. The computer still must approve.
        }
        if (!old)
            pending.set(id, { deviceId: id, name, secretHash: hash(s), at: Date.now(), state: 'pending' });
        return { ok: true, state: old?.state || 'pending' };
    });
    registerApi('pair/status', 'GET', (req, _, url) => {
        if (isLocalAdmin(req)) {
            const p = [...pending.keys()].map(pendingNow).find(p => p?.state === 'pending');
            return { ok: true, state: p ? 'pending' : 'none', pendingDevice: p ? { deviceId: p.deviceId, name: p.name, at: p.at } : null };
        }
        const p = pendingNow(url.searchParams.get('deviceId') || '');
        return { ok: true, state: p && equal(p.secretHash, hash(secret(req))) ? p.state : 'none' };
    });
    registerApi('pair/respond', 'POST', (_, b) => {
        if (!settings.allowConnections)
            return { ok: false, error: 'connections_disabled' };
        const p = pendingNow(String(b.deviceId));
        if (!p)
            return { ok: false, error: 'no_pending_request' };
        p.state = b.outcome === 'approve' ? 'approved' : 'denied';
        return { ok: true, state: p.state };
    });
    registerApi('pair/claim', 'POST', (req, b) => {
        const { id } = identity(b), p = pendingNow(id);
        if (!p || p.state !== 'approved' || !equal(p.secretHash, hash(secret(req))))
            return { ok: false, error: 'invalid_claim' };
        const token = issue(id, p.name);
        pending.delete(id);
        record('pair', id);
        return { ok: true, token };
    });
    registerApi('pair/check', 'GET', (req, _, url) => ({
        ok: true, paired: authenticate(req)?.deviceId === url.searchParams.get('deviceId'),
    }));
    registerApi('pair/list', 'GET', () => ({ ok: true, computer: { ...computerIdentity(home), defaultName: defaultComputerName() }, devices: devices.map(({ tokenHash, ...d }) => d) }));
    registerApi('computer/rename', 'POST', (_, b) => ({ ok: true, computer: computerIdentity(home, b.reset === true ? null : deviceName(b.name)) }));
    registerApi('pair/rename', 'POST', (_, b) => {
        const { id } = identity(b), name = deviceName(b.name);
        if (!devices.some(d => d.deviceId === id))
            return { ok: false, error: 'device_not_found' };
        save(devices.map(d => d.deviceId === id ? { ...d, name } : d));
        record('rename', id);
        return { ok: true };
    });
    registerApi('pair/revoke', 'POST', (_, b) => {
        const { id } = identity(b);
        save(devices.filter(d => d.deviceId !== id));
        pending.delete(id);
        disconnect(id);
        record('revoke', id);
        return { ok: true };
    });
    for (const [path, method] of [['generate', 'POST'], ['current', 'GET']])
        registerApi('pair/code/' + path, method, () => {
            if (!settings.allowConnections)
                return { ok: false, error: 'connections_disabled' };
            if (path === 'generate' || !code || code.expires < Date.now() || code.attempts >= 5) {
                stopDiscovery();
                makeCode();
            }
            return { ok: true, code: code.value, expiresInSec: Math.max(0, Math.floor((code.expires - Date.now()) / 1000)), maxAttempts: 5 };
        });
    registerApi('pair/code/verify', 'POST', (req, b) => {
        if (req[RELAY_REQUEST])
            return { ok: false, error: 'relay_requires_scan' };
        const { id, name } = identity(b);
        if (b.discoverySession !== undefined && (!activeDiscovery() || b.discoverySession !== discovery.session))
            return { ok: false, error: 'discovery_expired' };
        if (!code || code.expires < Date.now())
            return { ok: false, error: 'no_active_code' };
        if (code.attempts >= 5)
            return { ok: false, error: 'code_locked' };
        if (!equal(String(b.code || ''), code.value)) {
            const attemptsLeft = 5 - ++code.attempts;
            if (!attemptsLeft)
                stopDiscovery();
            return { ok: false, error: 'code_mismatch', attemptsLeft };
        }
        const token = issue(id, name);
        stopDiscovery();
        code = undefined;
        pending.delete(id);
        record('pair', id);
        return { ok: true, state: 'approved', token };
    });
    registerApi('token/rotate', 'POST', (req) => {
        const d = authenticate(req);
        if (!d)
            return { ok: false, error: 'device_token_required' };
        const token = issue(d.deviceId, d.name);
        record('rotate', d.deviceId);
        return { ok: true, token, rotatedAt: Date.now() };
    });
    registerApi('token/revoke', 'POST', (req) => {
        const d = authenticate(req);
        if (!d && !isLocalAdmin(req))
            return { ok: false, error: 'unauthorized' };
        const ids = d ? [d.deviceId] : devices.map(d => d.deviceId);
        save(devices.filter(d => !ids.includes(d.deviceId)));
        for (const id of ids) {
            disconnect(id);
            pending.delete(id);
            record('revoke', id);
        }
        return { ok: true, revokedAt: Date.now() };
    });
    registerApi('token/audit', 'GET', req => ({ ok: true, events: audit.filter(e => isLocalAdmin(req) || e.deviceId === req[DEVICE_ID]).slice(-50) }));
    syncWake();
    return { authorize, track, generatePairTicket, isEnabled: () => settings.allowConnections,
        remoteBlocked: (req) => !settings.allowConnections && /^\/api(?:\/|$)/.test(new URL(req.url, 'http://localhost').pathname) && !isLocalAdmin(req),
        dispose: () => {
            stopDiscovery();
            wake.dispose();
            clearTimeout(lastSeenTimer);
            if (lastSeenTimer) {
                try {
                    save(devices);
                }
                catch { }
            }
            pending.clear();
            scanTicket = undefined;
            code = undefined;
            for (const id of active.keys())
                disconnect(id);
        } };
}
