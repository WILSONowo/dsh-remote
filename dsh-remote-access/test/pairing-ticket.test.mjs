import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import http from 'node:http';
import { once } from 'node:events';
import os, { tmpdir } from 'node:os';
import { syncBuiltinESMExports } from 'node:module';
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
  assert.equal((await call('pair/request', { deviceId: 'manual' })).error, 'pairing_ticket_required');
  assert.equal((await call('pair/code/verify', { deviceId: 'manual', code: '123456' })).error, 'relay_requires_scan');
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

test('installation identity survives rename and reload; server changes get new registration credentials', t => {
  let systemName = 'Synthetic-PC';
  t.mock.method(os, 'hostname', () => systemName); syncBuiltinESMExports();
  t.after(() => { t.mock.restoreAll(); syncBuiltinESMExports(); });
  const home = mkdtempSync(join(tmpdir(), 'dsh-identity-'));
  try {
    const initial = computerIdentity(home);
    assert.equal(initial.name, 'Synthetic-PC');
    systemName = 'Synthetic-PC-2';
    assert.equal(computerIdentity(home).name, systemName);
    const renamed = computerIdentity(home, 'Synthetic computer');
    systemName = 'Synthetic-PC-3';
    assert.equal(initial.id, renamed.id);
    assert.deepEqual(computerIdentity(home), renamed);
    for (const name of ['', '  ', 'x'.repeat(65), 'bad\nname', null, 42]) assert.throws(() => computerIdentity(home, name));
    assert.deepEqual(computerIdentity(home), renamed);
    const file = join(home, 'remote-access', 'identity.json');
    writeFileSync(file, JSON.stringify({ id: initial.id, name: '电脑' }));
    assert.deepEqual(computerIdentity(home), { id: initial.id, name: systemName });
    writeFileSync(file, JSON.stringify({ id: initial.id, name: 'Existing custom name' }));
    assert.deepEqual(computerIdentity(home), { id: initial.id, name: 'Existing custom name' });
    const first = validateRelayConfig({ enabled: true, serverUrl: 'https://relay.example.test' });
    const saved = { ...first, hostId: 'a'.repeat(32) };
    const again = validateRelayConfig({ enabled: true, serverUrl: saved.serverUrl }, saved);
    assert.equal(again.registrationToken, first.registrationToken); assert.equal(again.hostId, saved.hostId);
    assert.deepEqual(validateRelayConfig(saved, undefined, true), saved);
    assert.throws(() => validateRelayConfig({ ...saved, registrationMode: 'manual' }, undefined, true), /手动/);
    assert.throws(() => validateRelayConfig({ ...saved, registrationMode: undefined }, undefined, true), /手动/);
    const changed = validateRelayConfig({ enabled: true, serverUrl: 'https://other.example.test' }, saved);
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
