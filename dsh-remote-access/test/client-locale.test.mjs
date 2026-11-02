import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { en, zh, detailKey } from '../lib/client-copy.js';

test('settings dictionaries cover both languages and interpolate the same values', () => {
  assert.deepEqual(Object.keys(zh).sort(), Object.keys(en).sort());
  for (const key of Object.keys(en)) {
    assert.ok(en[key].trim() && zh[key].trim(), key);
    assert.doesNotMatch(en[key], /[\u3400-\u9fff]/, key);
    assert.deepEqual(en[key].match(/\{\w+\}/g), zh[key].match(/\{\w+\}/g), key);
  }
  assert.equal(detailKey('无法读取 CA 证书'), 'caFailed');
  assert.equal(detailKey('服务器未启用自动注册，请联系服务器维护者'), 'registrationDisabled');
  assert.equal(detailKey('arbitrary private diagnostic'), 'certificateFailed');
});

test('0.2.1-rc.5 host locale drives navigation, settings, errors and interpolation without remounting', {
  skip: !process.env.DSH_TEST_RUNTIME && 'Set DSH_TEST_RUNTIME to an isolated SDK checkout',
}, () => {
  let localeExports, plugin;
  const styles = new Set(), disposers = [];
  const document = { createElement() { return { dataset: {}, remove() { styles.delete(this); } }; },
    head: { appendChild(style) { styles.add(style); } }, querySelector() { return null; } };
  const context = vm.createContext({ document, navigator: { languages: ['en'] }, console,
    window: { __ModuleLoader__: { load(definition) {
      const result = definition.factory(() => ({}));
      if (definition.id === 'dsh-remote-access') plugin = result; else localeExports = result;
    } } } });
  vm.runInContext(readFileSync(join(process.env.DSH_TEST_RUNTIME, 'dsh-client-locale/lib/client.js'), 'utf8'), context);
  vm.runInContext(readFileSync(new URL('../lib/client.js', import.meta.url), 'utf8'), context);
  const locale = new localeExports.LocaleRuntime({ emit() {} }, undefined, { languages: ['en'], preference: 'en' });
  let registration;
  plugin.apply({ locale, effect(fn) { disposers.push(fn()); }, slots: {
    inject(_, fn) { disposers.push(fn()); }, register(meta) { registration = meta; return () => {}; },
  } });
  const translate = locale.bind(registration.locale);
  assert.equal(registration.label(), 'Remote control');
  assert.equal(translate('approveTitle', { name: 'Synthetic phone' }), 'Pair “Synthetic phone”?');
  locale.setLocale('zh');
  assert.equal(registration.label(), '远程控制');
  assert.equal(translate('caFailed'), zh.caFailed);
  locale.setLocale('en');
  assert.equal(translate('caFailed'), en.caFailed);
  assert.equal([...styles].filter(s => s.dataset.plugin === 'dsh-remote-access').length, 1);
  for (const dispose of disposers.reverse()) dispose?.();
  assert.equal([...styles].filter(s => s.dataset.plugin === 'dsh-remote-access').length, 0);
});
