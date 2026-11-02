import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { computerIdentity, deviceName } from './identity.js';

// Only the in-process relay gateway can set this marker. HTTP headers cannot impersonate it.
export const RELAY_REQUEST = Symbol('dsh.relay.request');
export const DEVICE_ID = Symbol('dsh.device.id');
export const VALID_ID = /^[A-Za-z0-9_-]{1,64}$/;
export type Request = IncomingMessage & { [RELAY_REQUEST]?: boolean; [DEVICE_ID]?: string };
type Device = { deviceId: string; name: string; at: number; tokenHash: string };
type Route = { kind: string; path: string; handler: (req: Request, res: ServerResponse) => unknown };
const hash = (s: string) => createHash('sha256').update(s).digest('hex');
const equal = (a: string, b: string) => timingSafeEqual(Buffer.from(hash(a)), Buffer.from(hash(b)));
const fresh = () => randomBytes(32).toString('hex');
export const reply = (res: ServerResponse, status: number, body: unknown) => {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
};
export async function bodyJson(req: IncomingMessage, limit = 16384): Promise<any> {
  if (!/^application\/json(?:;|$)/i.test(String(req.headers['content-type'] || ''))) throw new Error('json_required');
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw new Error('body_too_large');
    chunks.push(Buffer.from(chunk));
  }
  const result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('invalid_body');
  return result;
}
const loopback = (s: string) => ['127.0.0.1', '::1', '::ffff:127.0.0.1', '::ffff:7f00:1'].includes(s);
export function isLocalAdmin(req: Request): boolean {
  if (req[RELAY_REQUEST] || !loopback(req.socket.remoteAddress || '')) return false;
  if (['forwarded', 'x-forwarded-for', 'x-real-ip', 'x-forwarded-host'].some(h => req.headers[h])) return false;
  try {
    const host = new URL('http://' + req.headers.host);
    if (!['localhost', '127.0.0.1', '[::1]'].includes(host.hostname)) return false;
    if (req.headers.origin && new URL(req.headers.origin).host !== host.host) return false;
    if (req.headers['sec-fetch-site'] === 'cross-site') return false;
    return true;
  } catch { return false; }
}

export function createSecurity(home: string, register: (route: Route) => void) {
  const directory = join(home, 'remote-access');
  const file = join(directory, 'devices-v2.json');
  // Legacy paired.json / channel-token are deliberately not accepted as credentials.
  let devices: Device[] = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')).devices : [];
  if (!Array.isArray(devices) || devices.some(d => !VALID_ID.test(d.deviceId) || !/^[a-f0-9]{64}$/.test(d.tokenHash))) {
    throw new Error('Invalid device credential store; remote access refused');
  }
  const save = (next: Device[]) => {
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    const tmp = file + '.' + randomBytes(8).toString('hex') + '.tmp';
    try {
      writeFileSync(tmp, JSON.stringify({ version: 2, devices: next }), { mode: 0o600 });
      renameSync(tmp, file);
      devices = next;
    } finally { rmSync(tmp, { force: true }); }
  };
  const active = new Map<string, Set<() => void>>();
  const disconnect = (id: string) => {
    const callbacks = active.get(id);
    active.delete(id);
    setImmediate(() => { for (const close of callbacks || []) close(); });
  };
  const issue = (id: string, name: string) => {
    const token = fresh();
    save([...devices.filter(d => d.deviceId !== id), { deviceId: id, name, at: Date.now(), tokenHash: hash(token) }]);
    disconnect(id);
    return token;
  };
  const authenticate = (req: Request) => {
    const token = /^Bearer ([a-f0-9]{64})$/.exec(String(req.headers.authorization || ''))?.[1];
    const device = token && devices.find(d => equal(hash(token), d.tokenHash));
    if (device) req[DEVICE_ID] = device.deviceId;
    return device || undefined;
  };
  const bootstrap = new Set(['pair/request', 'pair/status', 'pair/check', 'pair/claim', 'pair/code/verify'].map(p => '/api/remote-access/' + p));
  const adminPaths = new Set(['pair/respond', 'pair/list', 'pair/rename', 'computer/rename', 'pair/revoke', 'pair/code/generate', 'pair/code/current', 'trusted-hosts', 'relay/config', 'relay/pairing'].map(p => '/api/remote-access/' + p));
  const authorize = (req: Request) => {
    const path = new URL(req.url!, 'http://localhost').pathname;
    // Static desktop UI and browser login remain owned by the host.
    if (path !== '/api' && !path.startsWith('/api/')) return true;
    const device = authenticate(req);
    if (isLocalAdmin(req)) return true;
    if (adminPaths.has(path)) return false;
    if (bootstrap.has(path) || path === '/api/host.describe') return true;
    return !!device;
  };
  const track = (req: Request, target: { once: Function; destroy: () => void }) => {
    const id = req[DEVICE_ID];
    if (!id) return;
    let set = active.get(id);
    if (!set) active.set(id, set = new Set());
    const close = () => target.destroy();
    set.add(close);
    const remove = () => { set!.delete(close); if (!set!.size && active.get(id) === set) active.delete(id); };
    target.once('close', remove);
    target.once('finish', remove);
  };
  type Pending = { deviceId: string; name: string; secretHash: string; at: number; state: string };
  const pending = new Map<string, Pending>();
  let scanTicket: { hash: string; expiresAt: number } | undefined;
  const generatePairTicket = () => {
    const ticket = fresh(), expiresAt = Date.now() + 120000;
    scanTicket = { hash: hash(ticket), expiresAt };
    return { ticket, expiresAt };
  };
  const pendingNow = (id: string) => {
    const p = pending.get(id);
    if (p && Date.now() - p.at < 120000) return p;
    pending.delete(id);
  };
  let code: { value: string; expires: number; attempts: number } | undefined;
  const makeCode = () => code = { value: String(randomInt(1000000)).padStart(6, '0'), expires: Date.now() + 600000, attempts: 0 };
  const audit: { ts: number; action: string; deviceId: string }[] = [];
  const record = (action: string, deviceId: string) => { audit.push({ ts: Date.now(), action, deviceId }); if (audit.length > 200) audit.shift(); };
  const rates = new Map<string, { at: number; count: number }>();
  const registerApi = (path: string, method: string, fn: (req: Request, body: any, url: URL) => any) => register({
    kind: 'exact', path: '/api/remote-access/' + path,
    handler: async (req, res) => {
      if (req.method !== method) return reply(res, 405, { error: 'method_not_allowed' });
      if (!authorize(req)) return reply(res, 401, { error: 'unauthorized' });
      if (!isLocalAdmin(req) && bootstrap.has('/api/remote-access/' + path)) {
        const now = Date.now();
        for (const [k, v] of rates) if (now - v.at > 60000) rates.delete(k);
        const key = req.socket.remoteAddress || 'remote';
        const rate = rates.get(key) || { at: now, count: 0 };
        if (rates.size > 1024 || ++rate.count > 120) return reply(res, 429, { error: 'rate_limited' });
        rates.set(key, rate);
      }
      try {
        const body = method === 'POST' ? await bodyJson(req) : {};
        const value = fn(req, body, new URL(req.url!, 'http://localhost'));
        return reply(res, 200, value);
      } catch (e: any) {
        const input = ['invalid_name', 'invalid_device', 'invalid_secret', 'invalid_body', 'json_required', 'body_too_large'].includes(e.message) || e instanceof SyntaxError;
        reply(res, input ? 400 : 503, { ok: false, error: input ? 'invalid_request' : 'credential_store_unavailable' });
      }
    },
  });
  const identity = (body: any) => {
    if (typeof body.deviceId !== 'string' || !VALID_ID.test(body.deviceId)) throw new Error('invalid_device');
    return { id: body.deviceId, name: String(body.deviceName || '手机').slice(0, 64) };
  };
  const secret = (req: Request) => {
    const s = String(req.headers['x-dsh-pairing-secret'] || '');
    if (!/^[a-f0-9]{64}$/.test(s)) throw new Error('invalid_secret');
    return s;
  };
  registerApi('pair/request', 'POST', (req, b) => {
    const { id, name } = identity(b);
    if (authenticate(req)?.deviceId === id) return { ok: true, state: 'paired' };
    if (req[RELAY_REQUEST] && b.ticket === undefined) return { ok: false, state: 'expired', error: 'pairing_ticket_required' };
    const s = secret(req), old = pendingNow(id);
    if (old && !equal(old.secretHash, hash(s))) return { ok: false, state: 'busy' };
    for (const id of pending.keys()) pendingNow(id);
    if (!old && pending.size >= 16) return { ok: false, state: 'busy' };
    if (b.ticket !== undefined && !old) {
      if (typeof b.ticket !== 'string' || !/^[a-f0-9]{64}$/.test(b.ticket) || !scanTicket || scanTicket.expiresAt <= Date.now() || !equal(hash(b.ticket), scanTicket.hash)) {
        return { ok: false, state: 'expired', error: 'invalid_pairing_ticket' };
      }
      scanTicket = undefined; // One phone per QR. The computer still must approve.
    }
    if (!old) pending.set(id, { deviceId: id, name, secretHash: hash(s), at: Date.now(), state: 'pending' });
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
    const p = pendingNow(String(b.deviceId));
    if (!p) return { ok: false, error: 'no_pending_request' };
    p.state = b.outcome === 'approve' ? 'approved' : 'denied';
    return { ok: true, state: p.state };
  });
  registerApi('pair/claim', 'POST', (req, b) => {
    const { id } = identity(b), p = pendingNow(id);
    if (!p || p.state !== 'approved' || !equal(p.secretHash, hash(secret(req)))) return { ok: false, error: 'invalid_claim' };
    const token = issue(id, p.name);
    pending.delete(id);
    record('pair', id);
    return { ok: true, token };
  });
  registerApi('pair/check', 'GET', (req, _, url) => ({
    ok: true, paired: authenticate(req)?.deviceId === url.searchParams.get('deviceId'),
  }));
  registerApi('pair/list', 'GET', () => ({ ok: true, computer: computerIdentity(home), devices: devices.map(({ tokenHash, ...d }) => d) }));
  registerApi('computer/rename', 'POST', (_, b) => ({ ok: true, computer: computerIdentity(home, deviceName(b.name)) }));
  registerApi('pair/rename', 'POST', (_, b) => {
    const { id } = identity(b), name = deviceName(b.name);
    if (!devices.some(d => d.deviceId === id)) return { ok: false, error: 'device_not_found' };
    save(devices.map(d => d.deviceId === id ? { ...d, name } : d));
    record('rename', id);
    return { ok: true };
  });
  registerApi('pair/revoke', 'POST', (_, b) => {
    const { id } = identity(b);
    save(devices.filter(d => d.deviceId !== id)); pending.delete(id); disconnect(id); record('revoke', id);
    return { ok: true };
  });
  for (const [path, method] of [['generate', 'POST'], ['current', 'GET']]) registerApi('pair/code/' + path, method, () => {
    if (path === 'generate' || !code || code.expires < Date.now() || code.attempts >= 5) makeCode();
    return { ok: true, code: code!.value, expiresInSec: Math.max(0, Math.floor((code!.expires - Date.now()) / 1000)), maxAttempts: 5 };
  });
  registerApi('pair/code/verify', 'POST', (req, b) => {
    if (req[RELAY_REQUEST]) return { ok: false, error: 'relay_requires_scan' };
    const { id, name } = identity(b);
    if (!code || code.expires < Date.now()) return { ok: false, error: 'no_active_code' };
    if (code.attempts >= 5) return { ok: false, error: 'code_locked' };
    if (!equal(String(b.code || ''), code.value)) return { ok: false, error: 'code_mismatch', attemptsLeft: 5 - ++code.attempts };
    const token = issue(id, name); code = undefined; pending.delete(id); record('pair', id);
    return { ok: true, state: 'approved', token };
  });
  registerApi('token/rotate', 'POST', (req) => {
    const d = authenticate(req);
    if (!d) return { ok: false, error: 'device_token_required' };
    const token = issue(d.deviceId, d.name); record('rotate', d.deviceId);
    return { ok: true, token, rotatedAt: Date.now() };
  });
  registerApi('token/revoke', 'POST', (req) => {
    const d = authenticate(req);
    if (!d && !isLocalAdmin(req)) return { ok: false, error: 'unauthorized' };
    const ids = d ? [d.deviceId] : devices.map(d => d.deviceId);
    save(devices.filter(d => !ids.includes(d.deviceId)));
    for (const id of ids) { disconnect(id); pending.delete(id); record('revoke', id); }
    return { ok: true, revokedAt: Date.now() };
  });
  registerApi('token/audit', 'GET', req => ({ ok: true, events: audit.filter(e => isLocalAdmin(req) || e.deviceId === req[DEVICE_ID]).slice(-50) }));
  return { authorize, track, generatePairTicket, dispose: () => { scanTicket = undefined; for (const id of active.keys()) disconnect(id); } };
}
