// @ts-nocheck
import { en, zh, detailKey } from './client-copy.js';
import { settingsCss } from './client-style.js';

window.__ModuleLoader__.load({
  id: 'dsh-remote-access',
  factory(require) {
    const React = require('react');
    const { createElement: h, useState, useEffect, useRef } = React;
    const { Button, Switch, Modal } = require('@deepseek-ai/dsh-client-ui-primitives');
    const NS = 'dsh-remote-access';
    const path = '/api/remote-access/';
    const time = seconds => `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    const button = (text, props = {}) => h(Button, { size: 'sm', variant: 'outline', ...props }, text);
    const muted = text => h('p', { className: 'remote-muted' }, text);
    const failure = text => text ? h('p', { className: 'remote-error', role: 'alert' }, text) : null;
    async function request(route, body, signal) {
      const response = await fetch(path + route, {
        method: body === undefined ? 'GET' : 'POST', signal,
        ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
      });
      const value = await response.json();
      if (!response.ok || !value?.ok) throw new Error(value?.error || 'request_failed');
      return value;
    }
    function Section({ title, hint, action, children }) {
      return h('section', { className: 'remote-section' },
        h('div', { className: 'remote-head' }, h('div', {}, h('h3', {}, title), hint && muted(hint)), action), children);
    }
    function Field({ title, name, value, onChange, disabled, placeholder, hint }) {
      const id = 'dsh-remote-' + name;
      return h('div', { className: 'remote-field' }, h('label', { htmlFor: id }, title),
        h('div', {}, h('input', { id, className: 'remote-input', value: value || '', disabled, placeholder,
          autoComplete: 'off', spellCheck: false, onChange: e => onChange(e.target.value) }), hint && muted(hint)));
    }
    function RelaySection({ t }) {
      const [config, setConfig] = useState(null);
      const [live, setLive] = useState(null);
      const [loadFailed, setLoadFailed] = useState(false);
      const [busy, setBusy] = useState(false);
      const [error, setError] = useState('');
      const [saved, setSaved] = useState(false);
      const [pairing, setPairing] = useState(null);
      const [now, setNow] = useState(Date.now());
      const [copyState, setCopyState] = useState('');
      const mounted = useRef(false);
      const loading = useRef(false);
      async function refresh(initial = false) {
        if (loading.current) return;
        loading.current = true;
        try {
          const r = await request('relay/config');
          if (!mounted.current) return;
          setConfig(c => initial || !c ? r : c); setLive(r); setLoadFailed(false);
        } catch { if (mounted.current) setLoadFailed(true); }
        finally { loading.current = false; }
      }
      useEffect(() => {
        mounted.current = true; refresh(true);
        const timer = setInterval(() => refresh(), 3000);
        return () => { mounted.current = false; clearInterval(timer); };
      }, []);
      useEffect(() => {
        if (!pairing) return;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
      }, [pairing]);
      function change(key, value) {
        setConfig(c => ({ ...c, [key]: value })); setSaved(false); setError(''); setPairing(null);
      }
      async function save() {
        setBusy(true); setError(''); setSaved(false); setPairing(null);
        try {
          const r = await request('relay/config', {
            enabled: config.enabled, serverUrl: config.serverUrl, computerName: config.computerName,
            caFile: config.caFile, allowInsecureLoopback: config.allowInsecureLoopback,
          });
          if (!mounted.current) return;
          setLive(r); setSaved(true); await refresh(true);
        } catch { if (mounted.current) setError('saveFailed'); }
        finally { if (mounted.current) setBusy(false); }
      }
      async function addPhone() {
        setBusy(true); setError(''); setCopyState('');
        try {
          const r = await request('relay/pairing', {});
          if (mounted.current) { setPairing(r); setNow(Date.now()); }
        } catch { if (mounted.current) setError('connectFirst'); }
        finally { if (mounted.current) setBusy(false); }
      }
      async function copy() {
        try { await navigator.clipboard.writeText(pairing.link); setCopyState('copied'); }
        catch { setCopyState('copyFailed'); }
      }
      const seconds = pairing ? Math.max(0, Math.ceil((pairing.expiresAt - now) / 1000)) : 0;
      const state = live?.state || 'disabled';
      const knownState = ['disabled', 'registering', 'connecting', 'connected', 'reconnecting', 'error'].includes(state) ? state : 'error';
      return h(Section, { title: t('server'), hint: t('serverHint'),
        action: config && h(Switch, { checked: config.enabled, disabled: busy, label: t('enable'), onChange: v => change('enabled', v) }) },
        !config && !loadFailed && muted(t('loading')),
        loadFailed && h('div', {}, failure(t('loadFailed')), button(t('retry'), { onClick: () => refresh() })),
        config && h('div', { className: 'remote-form' },
          h(Field, { title: t('address'), name: 'server', value: config.serverUrl, disabled: busy,
            placeholder: 'https://relay.example.com', onChange: v => change('serverUrl', v) }),
          h(Field, { title: t('computerName'), name: 'name', value: config.computerName, disabled: busy,
            placeholder: t('computer'), onChange: v => change('computerName', v) }),
          h('details', {}, h('summary', {}, t('advanced')),
            h(Field, { title: t('ca'), name: 'ca', value: config.caFile, disabled: busy, hint: t('caHint'), onChange: v => change('caFile', v) })),
          h('div', { className: 'remote-row' },
            h('span', { className: 'remote-status', 'data-state': knownState, role: 'status' }, t(knownState)),
            h('div', { className: 'remote-actions' }, saved && h('span', { className: 'remote-muted', role: 'status' }, t('saved')),
              button(t(busy ? 'saving' : 'save'), { disabled: busy, onClick: save }),
              button(t('addPhone'), { variant: 'primary', disabled: busy || loadFailed || state !== 'connected' || !config.enabled, onClick: addPhone }))),
          live?.detail && failure(t(detailKey(live.detail)))),
        failure(error && t(error)),
        h(Modal, { className: 'dsh-remote', open: !!pairing, onClose: () => setPairing(null), title: t('qrTitle'), closeLabel: t('close'),
          footer: h('div', { className: 'remote-actions' }, button(t('close'), { onClick: () => setPairing(null) }),
            seconds > 0 ? button(t(copyState === 'copied' ? 'copied' : 'copyLink'), { variant: 'primary', onClick: copy })
              : button(t('regenerate'), { variant: 'primary', disabled: busy, onClick: addPhone })) },
          h('div', { className: 'dsh-remote remote-modal-copy' },
            seconds > 0 && pairing ? h(React.Fragment, {}, muted(t('qrHint')),
              h('img', { className: 'remote-qr', src: pairing.image, alt: t('qrAlt') }),
              h('div', { className: 'remote-qr-meta' }, muted(t('qrExpiry', { time: time(seconds) }))),
              h('details', { open: copyState === 'copyFailed' || undefined }, h('summary', {}, t('connectionInfo')),
                h('textarea', { className: 'remote-input remote-link', readOnly: true, value: pairing.link, 'aria-label': t('connectionInfo'), onFocus: e => e.target.select() }), muted(t('pasteHint'))),
              failure(copyState === 'copyFailed' && t('copyFailed'))) : muted(t('qrExpired')))));
    }
    function DirectSection({ t }) {
      const [pc, setPc] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(false);
      const [now, setNow] = useState(Date.now());
      const mounted = useRef(false);
      async function load(generate = false) {
        setBusy(true); setError(false);
        try {
          const r = await request(generate ? 'pair/code/generate' : 'pair/code/current', generate ? {} : undefined);
          if (mounted.current) { const at = Date.now(); setNow(at); setPc({ code: r.code, expiresAt: at + r.expiresInSec * 1000 }); }
        } catch { if (mounted.current) setError(true); }
        finally { if (mounted.current) setBusy(false); }
      }
      useEffect(() => {
        mounted.current = true; load();
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => { mounted.current = false; clearInterval(timer); };
      }, []);
      const seconds = pc ? Math.max(0, Math.ceil((pc.expiresAt - now) / 1000)) : 0;
      useEffect(() => { if (pc && seconds === 0 && !busy && !error) load(); }, [seconds, pc, busy, error]);
      return h(Section, { title: t('direct'), hint: t('directHint') },
        h('div', { className: 'remote-code-row' },
          h('div', {}, h('div', { className: 'remote-code', 'aria-label': t('code') }, pc && seconds > 0 ? pc.code : '— — —'),
            muted(pc && seconds > 0 ? t('expires', { time: time(seconds) }) : t('codeRules'))),
          button(t(busy ? 'generating' : pc ? 'regenerate' : 'generate'), { disabled: busy, onClick: () => load(true) })),
        failure(error && t('requestFailed')));
    }
    function DevicesSection({ t }) {
      const [devices, setDevices] = useState(null), [pending, setPending] = useState(null);
      const [busyId, setBusyId] = useState(null), [responding, setResponding] = useState(false);
      const [error, setError] = useState(false), [responseError, setResponseError] = useState(false);
      const mounted = useRef(false), polling = useRef(false);
      async function refresh() {
        if (polling.current) return;
        polling.current = true;
        try {
          const [list, status] = await Promise.all([request('pair/list'), request('pair/status')]);
          if (!mounted.current) return;
          setDevices(list.devices || []); setError(false);
          setPending(status.state === 'pending' ? status.pendingDevice : null);
        } catch { if (mounted.current) setError(true); }
        finally { polling.current = false; }
      }
      useEffect(() => {
        mounted.current = true; refresh();
        const timer = setInterval(refresh, 2000);
        return () => { mounted.current = false; clearInterval(timer); };
      }, []);
      async function revoke(id) {
        setBusyId(id); setError(false);
        try { await request('pair/revoke', { deviceId: id }); await refresh(); }
        catch { if (mounted.current) setError(true); }
        finally { if (mounted.current) setBusyId(null); }
      }
      async function respond(outcome) {
        if (!pending || responding) return;
        setResponding(true); setResponseError(false);
        try {
          await request('pair/respond', { deviceId: pending.deviceId, outcome });
          if (mounted.current) setPending(null);
          await refresh();
        } catch { if (mounted.current) setResponseError(true); }
        finally { if (mounted.current) setResponding(false); }
      }
      return h(Section, { title: t('devices'), hint: t('devicesHint') },
        !devices ? muted(t('loading')) : devices.length ? h('ul', { className: 'remote-list' }, devices.map(d =>
          h('li', { key: d.deviceId }, h('span', { className: 'remote-name' }, d.name || d.deviceId),
            button(t(busyId === d.deviceId ? 'revoking' : 'revoke'), { disabled: !!busyId, onClick: () => revoke(d.deviceId) }))))
          : h('p', { className: 'remote-empty' }, t('noDevices')),
        failure(error && t('requestFailed')),
        h(Modal, { className: 'dsh-remote', open: !!pending, title: t('approveTitle', { name: pending?.name || '' }), description: t('approveHint'),
          closeLabel: t('deny'), onClose: () => respond('deny'),
          footer: h(React.Fragment, {}, button(t('deny'), { disabled: responding, onClick: () => respond('deny') }),
            button(t(responding ? 'responding' : 'allow'), { variant: 'primary', disabled: responding, onClick: () => respond('approve') })) },
          h('div', { className: 'dsh-remote' }, failure(responseError && t('requestFailed')))));
    }
    function HostsSection({ t }) {
      const [hosts, setHosts] = useState(null), [input, setInput] = useState('');
      const [busy, setBusy] = useState(false), [error, setError] = useState('');
      const mounted = useRef(false);
      async function refresh() {
        try { const r = await request('trusted-hosts'); if (mounted.current) { setHosts(r.hosts || []); setError(''); } }
        catch { if (mounted.current) setError('loadFailed'); }
      }
      useEffect(() => { mounted.current = true; refresh(); return () => { mounted.current = false; }; }, []);
      async function mutate(action, host) {
        setBusy(true); setError('');
        try {
          const r = await request('trusted-hosts', { action, host });
          if (mounted.current) { setHosts(r.hosts || []); if (action === 'add') setInput(''); }
        } catch (e) { if (mounted.current) setError(/invalid|无效|域名|host/i.test(e.message) ? 'invalidDomain' : 'requestFailed'); }
        finally { if (mounted.current) setBusy(false); }
      }
      return h(Section, { title: t('hosts'), hint: t('hostsHint') },
        h('details', {}, h('summary', {}, t('advanced')),
          !hosts ? muted(t('loading')) : !hosts.length ? h('p', { className: 'remote-empty' }, t('noHosts')) :
            h('ul', { className: 'remote-list' }, hosts.map(host => h('li', { key: host }, h('span', { className: 'remote-name' }, host),
              button(t('remove'), { disabled: busy, onClick: () => mutate('remove', host) })))),
          h('form', { className: 'remote-host-form', onSubmit: e => { e.preventDefault(); if (!busy && input.trim()) mutate('add', input.trim()); } },
            h('input', { className: 'remote-input', value: input, disabled: busy, placeholder: 'tunnel.example.com', 'aria-label': t('domain'),
              autoComplete: 'off', spellCheck: false, onChange: e => setInput(e.target.value) }),
            button(t(busy ? 'working' : 'add'), { type: 'submit', disabled: busy || !input.trim() })),
          muted(t('hostFormat')), failure(error && t(error)),
          error === 'loadFailed' && button(t('retry'), { onClick: refresh })));
    }
    function Settings({ t }) {
      return h('div', { className: 'dsh-remote' },
        h('header', { className: 'remote-intro' }, h('h2', {}, t('nav')), muted(t('intro'))),
        h(RelaySection, { t }), h(DevicesSection, { t }), h(DirectSection, { t }), h(HostsSection, { t }));
    }
    function apply(ctx) {
      ctx.effect(() => ctx.locale.register(NS, { en, zh }));
      ctx.effect(() => {
        const style = document.createElement('style'); style.dataset.plugin = NS; style.textContent = settingsCss;
        document.head.appendChild(style); return () => style.remove();
      });
      const t = ctx.locale.bind(NS);
      ctx.slots.inject('settings.section', () => ctx.slots.register({
        name: 'settings.section', id: NS, order: 40, label: () => t('nav'), locale: NS,
      }, Settings));
    }
    return { apply, inject: ['slots', 'locale'] };
  },
});
