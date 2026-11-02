import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter, once } from 'node:events';
import { PassThrough } from 'node:stream';
import { spawn } from 'node:child_process';
import { createWakeLock } from '../lib/wake.js';

test('power helper handles AC/battery, shutdown, failures and unsupported systems', () => {
  const children = [];
  const launch = (command, args, options) => {
    assert.equal(options.windowsHide, true); assert.equal(options.shell, undefined);
    const child = new EventEmitter(); child.stdin = new PassThrough(); child.stdout = new PassThrough();
    child.kills = 0; child.kill = () => { child.kills++; }; child.unref = () => {};
    children.push(child); return child;
  };
  const wake = createWakeLock({ platform: 'win32', launch });
  assert.equal(wake.status().state, 'off'); assert.equal(children.length, 0);
  wake.setEnabled(true); assert.equal(wake.status().state, 'starting');
  const first = children[0]; first.stdout.write('act'); first.stdout.write('ive\r\n');
  assert.equal(wake.status().state, 'active');
  first.stdout.write('battery\n'); assert.equal(wake.status().state, 'battery');
  wake.setEnabled(true); assert.equal(children.length, 1);
  wake.setEnabled(false); assert.equal(first.kills, 1); assert.equal(wake.status().state, 'off');
  first.stdout.write('active\n'); assert.equal(wake.status().state, 'off');
  wake.setEnabled(true); children[1].emit('error', new Error('synthetic launch failure'));
  assert.equal(wake.status().state, 'error');
  wake.setEnabled(false); wake.setEnabled(true); children[2].stdout.write('active\n');
  wake.dispose(); assert.equal(children[2].kills, 1); assert.equal(wake.status().state, 'off');
  const unsupported = createWakeLock({ platform: 'unsupported-test', launch });
  unsupported.setEnabled(true); assert.equal(unsupported.status().supported, false); assert.equal(children.length, 3);
  unsupported.dispose();
});

test('opt-in Windows power API starts and releases its own hidden helper', {
  skip: process.env.DSH_TEST_POWER !== '1' || process.platform !== 'win32', timeout: 25000,
}, async t => {
  let child;
  const wake = createWakeLock({ launch: (...args) => child = spawn(...args) });
  t.after(() => wake.dispose());
  wake.setEnabled(true);
  const deadline = Date.now() + 18000;
  while (wake.status().state === 'starting' && Date.now() < deadline) await new Promise(r => setTimeout(r, 100));
  assert.ok(['active', 'battery'].includes(wake.status().state), JSON.stringify(wake.status()));
  const exited = once(child, 'exit'); wake.dispose(); await exited;
  assert.equal(wake.status().state, 'off');
});
