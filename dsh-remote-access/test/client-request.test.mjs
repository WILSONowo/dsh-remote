import test from 'node:test';
import assert from 'node:assert/strict';
import { request, accessErrorKey } from '../lib/client-request.js';

test('connection settings identify missing backend, denied access and invalid responses without exposing raw errors', async t => {
  for (const [status, body, key] of [
    [404, 'Not found', 'accessRestart'],
    [405, '', 'accessRestart'],
    [200, '<html>old host page</html>', 'accessRestart'],
    [200, '{"ok":true}', 'accessRestart'],
    [401, '', 'accessDenied'],
    [403, '{"error":"private diagnostic"}', 'accessDenied'],
    [503, '{"ok":false,"error":"private path"}', 'accessLoadFailed'],
  ]) {
    const mock = t.mock.method(globalThis, 'fetch', async () => new Response(body, { status }));
    await assert.rejects(request('access/settings'), e => accessErrorKey(e) === key);
    mock.mock.restore();
  }
  assert.equal(accessErrorKey(new Error('private network details'), true), 'accessSaveFailed');
});

test('connection settings accept wake state and send a boolean update to the same local endpoint', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/remote-access/access/settings');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(options.body), { keepAwake: true });
    return Response.json({ ok: true, allowConnections: true, keepAwake: true, wake: { supported: true, state: 'starting' } });
  });
  assert.equal((await request('access/settings', { keepAwake: true })).keepAwake, true);
  mock.mock.restore();
});
