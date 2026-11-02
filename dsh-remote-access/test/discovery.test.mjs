import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { mkdtempSync, rmSync, readFileSync } from 'node:fs';
import os, { tmpdir } from 'node:os';
import { syncBuiltinESMExports } from 'node:module';
import { join } from 'node:path';
import { createSecurity, RELAY_REQUEST } from '../lib/security.js';

// No multicast sockets: only synthetic advertisements and in-memory HTTP requests.
function fixture(t) {
  t.mock.method(os, 'hostname', () => 'Synthetic-PC'); syncBuiltinESMExports();
  t.after(() => { t.mock.restoreAll(); syncBuiltinESMExports(); });
  const home = mkdtempSync(join(tmpdir(), 'dsh-discovery-')), routes = new Map(), advertisements = [];
  let stops = 0, now = Date.now();
  t.mock.method(Date, 'now', () => now);
  const security = createSecurity(home, route => routes.set(route.path, route), {
    wake: { status: () => ({ supported: false, state: 'off' }), setEnabled() {}, dispose() {} },
    publishDiscovery(value, failed) { advertisements.push({ ...value, failed }); return () => { stops++; }; },
  });
  t.after(() => { security.dispose(); rmSync(home, { recursive: true, force: true }); });
  async function call(path, body, remote = false, relay = false) {
    const req = Readable.from(body === undefined ? [] : [Buffer.from(JSON.stringify(body))]);
    Object.assign(req, { method: body === undefined ? 'GET' : 'POST', url: '/api/remote-access/' + path,
      socket: { remoteAddress: remote ? '192.0.2.2' : '127.0.0.1', localPort: 7941 }, [RELAY_REQUEST]: relay,
      headers: { host: remote ? '192.0.2.1:7941' : 'localhost:7941', 'content-type': 'application/json' } });
    let status, value;
    await routes.get(req.url.split('?')[0]).handler(req, { writeHead(v) { status = v; }, end(v) { value = JSON.parse(v); } });
    return { status, ...value };
  }
  return { call, advertisements, stops: () => stops, advance: ms => { now += ms; }, home };
}

test('discovery is local-admin controlled, exposes no credentials, and stale sessions cannot use or close new codes', async t => {
  const f = fixture(t);
  assert.equal((await f.call('discovery/start', {}, true)).status, 401);
  const first = await f.call('discovery/start', {});
  assert.match(first.session, /^[a-f0-9]{32}$/); assert.match(first.code, /^\d{6}$/);
  assert.equal(f.advertisements[0].port, 7941);
  assert.equal('code' in f.advertisements[0], false);
  assert.equal('computerId' in f.advertisements[0], false);
  const info = await f.call('discovery/info?session=' + first.session, undefined, true);
  assert.deepEqual(Object.keys(info).sort(), ['name', 'ok', 'session', 'status', 'v']);
  assert.equal((await f.call('discovery/info?session=' + first.session, undefined, true, true)).ok, false);
  const second = await f.call('discovery/start', {});
  assert.notEqual(first.session, second.session);
  await f.call('discovery/stop', { session: first.session });
  assert.equal((await f.call('discovery/renew', { session: second.session })).ok, true);
  const device = { deviceId: 'synthetic-a', deviceName: 'Synthetic device', code: second.code };
  for (let i = 0; i < 6; i++) assert.equal((await f.call('pair/code/verify', { ...device, discoverySession: first.session }, true)).error, 'discovery_expired');
  const paired = await f.call('pair/code/verify', { ...device, discoverySession: second.session }, true);
  assert.match(paired.token, /^[a-f0-9]{64}$/);
  assert.equal((await f.call('discovery/renew', { session: second.session })).ok, false);
  assert.equal(f.stops(), 2);
  const third = await f.call('discovery/start', {});
  const other = await f.call('pair/code/verify', { deviceId: 'synthetic-b', code: third.code, discoverySession: third.session }, true);
  assert.match(other.token, /^[a-f0-9]{64}$/); assert.notEqual(other.token, paired.token);
  const saved = JSON.parse(readFileSync(join(f.home, 'remote-access/devices-v2.json')));
  assert.equal(saved.devices.length, 2);
});

test('discovery stops on lease expiry, code lockout, pause and manual-code replacement', async t => {
  const f = fixture(t);
  const first = await f.call('discovery/start', {});
  f.advance(30001);
  assert.equal((await f.call('discovery/renew', { session: first.session })).error, 'discovery_expired');
  assert.equal((await f.call('pair/code/verify', { deviceId: 'synthetic-a', code: first.code }, true)).ok, false);
  const second = await f.call('discovery/start', {});
  const wrong = second.code === '000000' ? '111111' : '000000';
  for (let i = 0; i < 5; i++) assert.equal((await f.call('pair/code/verify', { deviceId: 'synthetic-a', code: wrong }, true)).attemptsLeft, 4 - i);
  assert.equal((await f.call('discovery/renew', { session: second.session })).ok, false);
  await f.call('discovery/start', {});
  await f.call('access/settings', { allowConnections: false });
  assert.equal((await f.call('discovery/start', {})).ok, false);
  await f.call('access/settings', { allowConnections: true });
  const last = await f.call('discovery/start', {});
  await f.call('pair/code/generate', {});
  assert.equal((await f.call('discovery/renew', { session: last.session })).ok, false);
  assert.equal(f.stops(), 4);
});

test('publication errors are visible without disabling ordinary IP pairing', async t => {
  const f = fixture(t), value = await f.call('discovery/start', {});
  f.advertisements[0].failed();
  assert.equal((await f.call('discovery/renew', { session: value.session })).discoveryFailed, true);
  assert.equal((await f.call('pair/code/verify', { deviceId: 'synthetic-a', code: value.code }, true)).ok, true);
});

test('renewing the page lease never extends the ten-minute code lifetime', async t => {
  const f = fixture(t), value = await f.call('discovery/start', {});
  for (let i = 0; i < 29; i++) {
    f.advance(20000);
    const renewed = await f.call('discovery/renew', { session: value.session });
    assert.equal(renewed.ok, true);
    assert.equal(renewed.expiresInSec, 600 - (i + 1) * 20);
  }
  f.advance(20000);
  assert.equal((await f.call('discovery/renew', { session: value.session })).error, 'discovery_expired');
  assert.equal(f.stops(), 1);
});
