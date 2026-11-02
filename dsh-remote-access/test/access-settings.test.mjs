import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os, { tmpdir } from 'node:os';
import { syncBuiltinESMExports } from 'node:module';
import { join } from 'node:path';
import { createSecurity, RELAY_REQUEST } from '../lib/security.js';

const fakeWake = () => { let enabled = false; return { setEnabled(v) { enabled = v; }, status: () => ({ supported: true, state: enabled ? 'active' : 'off' }), dispose() { enabled = false; } }; };
test('connection switch persists, invalidates pending pairing, gates remote requests and preserves credentials', async t => {
  t.mock.method(os, 'hostname', () => 'Synthetic-PC'); syncBuiltinESMExports();
  t.after(() => { t.mock.restoreAll(); syncBuiltinESMExports(); });
  const home = mkdtempSync(join(tmpdir(), 'dsh-access-')), routes = new Map(), changes = [];
  let security = createSecurity(home, r => routes.set(r.path, r), { wake: fakeWake(), onAccessChange: value => changes.push(value) });
  t.after(() => { security.dispose(); rmSync(home, { recursive: true, force: true }); });
  async function call(path, body, admin = true, token) {
    const req = Readable.from(body === undefined ? [] : [Buffer.from(JSON.stringify(body))]);
    Object.assign(req, { method: body === undefined ? 'GET' : 'POST', url: '/api/remote-access/' + path,
      socket: { remoteAddress: '127.0.0.1' }, [RELAY_REQUEST]: !admin,
      headers: { host: 'localhost', 'content-type': 'application/json', 'x-dsh-pairing-secret': 'a'.repeat(64), ...(token ? { authorization: 'Bearer ' + token } : {}) } });
    let status, value;
    await routes.get(req.url.split('?')[0]).handler(req, { writeHead(v) { status = v; }, end(v) { value = JSON.parse(v); } });
    return { status, ...value };
  }
  const qr = security.generatePairTicket();
  await call('pair/request', { deviceId: 'phone-a', ticket: qr.ticket }, false);
  await call('pair/respond', { deviceId: 'phone-a', outcome: 'approve' });
  const { token } = await call('pair/claim', { deviceId: 'phone-a' }, false);
  const before = JSON.parse(readFileSync(join(home, 'remote-access/devices-v2.json'))).devices[0];
  const originalComputer = (await call('pair/list')).computer;
  await call('computer/rename', { name: 'Synthetic custom computer' });
  const resetComputer = (await call('computer/rename', { reset: true })).computer;
  assert.equal(resetComputer.name, originalComputer.defaultName);
  assert.equal(resetComputer.id, originalComputer.id);
  assert.equal((await call('computer/rename', { reset: true }, false, token)).status, 401);
  const directQr = await call('direct/qr', { address: 'https://direct.example.test:8443' });
  assert.equal(directQr.link, 'https://direct.example.test:8443');
  assert.match(directQr.image, /^data:image\/png;base64,/);
  assert.equal((await call('direct/qr', { address: 'https://direct.example.test' }, false, token)).status, 401);
  for (const address of ['javascript:alert(1)', 'https://user:password@example.test', 'https://example.test/?token=secret']) {
    assert.equal((await call('direct/qr', { address })).status, 400);
  }
  assert.equal((await call('access/settings', { allowConnections: false }, false, token)).status, 401);
  await call('pair/check?deviceId=phone-a', undefined, false, token);
  const last = (await call('pair/list')).devices[0].lastConnectedAt;
  assert.ok(last > 0);
  const code = (await call('pair/code/generate', {})).code;
  const pending = security.generatePairTicket();
  await call('pair/request', { deviceId: 'phone-b', ticket: pending.ticket }, false);
  const enabledWake = await call('access/settings', { keepAwake: true });
  assert.equal(enabledWake.wake.state, 'active');
  const off = await call('access/settings', { allowConnections: false });
  assert.equal(off.wake.state, 'off');
  assert.equal((await call('pair/status')).state, 'none');
  assert.equal((await call('pair/code/current')).ok, false);
  assert.throws(() => security.generatePairTicket(), /disabled/);
  assert.equal((await call('pair/check?deviceId=phone-a', undefined, false, token)).status, 503);
  assert.equal((await call('access/settings', { allowConnections: 'yes' })).status, 400);
  security.dispose(); routes.clear();
  security = createSecurity(home, r => routes.set(r.path, r), { wake: fakeWake() });
  assert.equal(security.isEnabled(), false);
  assert.equal((await call('access/settings')).wake.state, 'off');
  assert.equal((await call('pair/list')).devices[0].lastConnectedAt, last);
  await call('access/settings', { allowConnections: true });
  assert.equal((await call('pair/check?deviceId=phone-a', undefined, false, token)).paired, true);
  assert.equal((await call('pair/claim', { deviceId: 'phone-b' }, false)).ok, false);
  assert.equal((await call('pair/code/verify', { deviceId: 'phone-c', code }, true)).error, 'no_active_code');
  assert.equal((await call('pair/request', { deviceId: 'phone-b', ticket: pending.ticket }, false)).ok, false);
  const after = JSON.parse(readFileSync(join(home, 'remote-access/devices-v2.json'))).devices[0];
  assert.equal(after.tokenHash, before.tokenHash); assert.equal(after.deviceId, before.deviceId);
  assert.deepEqual(changes, [false]);
});

test('corrupt connection settings do not enable remote access', t => {
  const home = mkdtempSync(join(tmpdir(), 'dsh-access-corrupt-'));
  const security = createSecurity(home, () => {}, { wake: fakeWake() });
  security.dispose();
  mkdirSync(join(home, 'remote-access'), { recursive: true });
  writeFileSync(join(home, 'remote-access/settings.json'), '{broken');
  assert.throws(() => createSecurity(home, () => {}, { wake: fakeWake() }));
  t.after(() => rmSync(home, { recursive: true, force: true }));

});
