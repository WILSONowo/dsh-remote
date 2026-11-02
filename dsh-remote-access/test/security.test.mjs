import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createSecurity, RELAY_REQUEST, isLocalAdmin } from '../lib/security.js';
import { validateRelayConfig } from '../lib/relay.js';

test('未知/损坏凭证状态拒绝启动，不能降级为免鉴权', () => {
  const home = mkdtempSync(join(tmpdir(), 'dsh-auth-fail-'));
  try {
    mkdirSync(join(home, 'remote-access'));
    writeFileSync(join(home, 'remote-access', 'devices-v2.json'), '{broken');
    assert.throws(() => createSecurity(home, () => {}));
  } finally { rmSync(home, { recursive: true, force: true }); }
});

test('中转请求即使来自 localhost 且没有代理头，也不能成为管理员', () => {
  const request = { socket: { remoteAddress: '127.0.0.1' }, headers: { host: 'localhost' } };
  assert.equal(isLocalAdmin(request), true);
  request[RELAY_REQUEST] = true;
  assert.equal(isLocalAdmin(request), false);
});

test('服务器地址校验阻止非 TLS 公网、URL 凭证、路径与主机 ID 注入', () => {
  const base = { enabled: true, serverUrl: 'https://relay.example' };
  assert.equal(validateRelayConfig(base).serverUrl, base.serverUrl);
  for (const serverUrl of ['http://relay.example', 'https://user:password@relay.example', 'https://relay.example/path', 'https://relay.example?token=x', 'wss://relay.example']) assert.throws(() => validateRelayConfig({ ...base, serverUrl }));
  for (const key of ['hostId', 'registrationToken', 'registrationMode']) assert.throws(() => validateRelayConfig({ ...base, [key]: '' }), /手动/);
  assert.throws(() => validateRelayConfig({ ...base, registrationToken: 'short' }));
  assert.throws(() => validateRelayConfig({ ...base, serverUrl: 'http://127.0.0.1' }));
  assert.equal(validateRelayConfig({ ...base, serverUrl: 'http://127.0.0.1', allowInsecureLoopback: true }).enabled, true);
});
