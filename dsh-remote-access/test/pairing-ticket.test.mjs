import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import http from 'node:http';
import { once } from 'node:events';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createSecurity, RELAY_REQUEST } from '../lib/security.js';
import { computerIdentity } from '../lib/identity.js';
import { createRelayManager, validateRelayConfig } from '../lib/relay.js';

test('scan tickets expire, are replaced and consumed once; approval and claim proof are still required', async t => {
  const home = mkdtempSync(join(tmpdir(), 'dsh-qr-'));
  const routes = new Map();
  const security = createSecurity(home, route => routes.set(route.path, route));
  t.after(() => { security.dispose(); rmSync(home, { recursive: true, force: true }); });
  let now = Date.now(); t.mock.method(Date, 'now', () => now);
  const proof = randomBytes(32).toString('hex');
  async function call(path, body, admin = false, secret = proof) {
    const req = Readable.from([Buffer.from(JSON.stringify(body))]);
    Object.assign(req, { method: 'POST', url: '/api/remote-access/' + path,
      headers: { host: 'localhost', 'content-type': 'application/json', 'x-dsh-pairing-secret': secret },
      socket: { remoteAddress: '127.0.0.1' }, [RELAY_REQUEST]: !admin });
    let status, value;
    await routes.get(req.url).handler(req, { writeHead(code) { status = code; }, end(text) { value = JSON.parse(text); } });
    return { status, ...value };
  }
  const old = security.generatePairTicket(); now += 120001;
  assert.equal((await call('pair/request', { deviceId: 'a', ticket: old.ticket })).error, 'invalid_pairing_ticket');
  const replaced = security.generatePairTicket(), fresh = security.generatePairTicket();
  assert.equal((await call('pair/request', { deviceId: 'a', ticket: replaced.ticket })).ok, false);
  assert.equal((await call('pair/request', { deviceId: 'a', ticket: fresh.ticket })).state, 'pending');
  assert.equal((await call('pair/request', { deviceId: 'b', ticket: fresh.ticket })).ok, false);
  assert.equal((await call('pair/claim', { deviceId: 'a' })).ok, false);
  assert.equal((await call('pair/respond', { deviceId: 'a', outcome: 'approve' })).status, 401);
  await call('pair/respond', { deviceId: 'a', outcome: 'approve' }, true);
  assert.equal((await call('pair/claim', { deviceId: 'a' }, false, randomBytes(32).toString('hex'))).ok, false);
  assert.match((await call('pair/claim', { deviceId: 'a' })).token, /^[a-f0-9]{64}$/);
  assert.equal((await call('pair/claim', { deviceId: 'a' })).ok, false);
});

test('installation identity survives rename and reload; server changes get new registration credentials', () => {
  const home = mkdtempSync(join(tmpdir(), 'dsh-identity-'));
  try {
    const initial = computerIdentity(home), renamed = computerIdentity(home, 'Synthetic computer');
    assert.equal(initial.id, renamed.id);
    assert.deepEqual(computerIdentity(home), renamed);
    const first = validateRelayConfig({ enabled: true, serverUrl: 'https://relay.example.test' });
    const saved = { ...first, hostId: 'synthetic-host' };
    const again = validateRelayConfig({ ...saved, registrationToken: '' }, saved);
    assert.equal(again.registrationToken, first.registrationToken); assert.equal(again.hostId, saved.hostId);
    assert.deepEqual(validateRelayConfig(saved), saved);
    const changed = validateRelayConfig({ ...saved, serverUrl: 'https://other.example.test' }, saved);
    assert.notEqual(changed.registrationToken, saved.registrationToken); assert.equal(changed.hostId, '');
    writeFileSync(join(home, 'remote-access', 'identity.json'), '{broken');
    assert.throws(() => computerIdentity(home));
  } finally { rmSync(home, { recursive: true, force: true }); }
});

test('malformed registration responses do not persist an invalid host ID', { timeout: 5000 }, async t => {
  const home = mkdtempSync(join(tmpdir(), 'dsh-register-response-'));
  const server = http.createServer((req, res) => { req.resume(); res.end('{"ok":true}'); });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const routes = new Map();
  const manager = createRelayManager(home, {}, route => routes.set(route.path, route));
  t.after(async () => { await manager.stop(); server.closeAllConnections(); await new Promise(r => server.close(r)); rmSync(home, { recursive: true, force: true }); });
  const body = { enabled: true, serverUrl: 'http://127.0.0.1:' + server.address().port, allowInsecureLoopback: true };
  const req = Readable.from([Buffer.from(JSON.stringify(body))]);
  Object.assign(req, { method: 'POST', url: '/api/remote-access/relay/config',
    headers: { host: 'localhost', 'content-type': 'application/json' }, socket: { remoteAddress: '127.0.0.1' } });
  let response;
  await routes.get(req.url).handler(req, { writeHead() {}, end(text) { response = JSON.parse(text); } });
  assert.equal(response.state, 'error');
  assert.match(response.detail, /身份不一致/);
  assert.equal(JSON.parse(readFileSync(join(home, 'remote-access', 'relay.json'))).hostId, '');
});
