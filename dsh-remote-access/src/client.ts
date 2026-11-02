// @ts-nocheck
import { en, zh, detailKey } from './client-copy.js';
import { settingsCss } from './client-style.js';
import { request, accessErrorKey } from './client-request.js';

window.__ModuleLoader__.load({
  id: 'dsh-remote-access',
  factory(require) {
    const React = require('react');
    const { createElement: h, useState, useEffect, useRef } = React;
    const { Button, Switch, Modal } = require('@deepseek-ai/dsh-client-ui-primitives');
    const NS = 'dsh-remote-access';
    const time = seconds => `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    const button = (text, props = {}) => h(Button, { size: 'sm', variant: 'outline', ...props }, text);
    const muted = text => h('p', { className: 'remote-muted' }, text);
    const failure = text => text ? h('p', { className: 'remote-error', role: 'alert' }, text) : null;
    function Icon({ name }) {
      const paths = {
        computer: 'M4 4h16v12H4z M2 19h20 M8 16v3 M16 16v3',
        device: 'M8 2h8v20H8z M11 18h2',
        server: 'M3 4h18v6H3z M3 14h18v6H3z M6 7h1 M6 17h1',
        refresh: 'M20 7a8 8 0 0 0-14-2L3 8 M3 3v5h5 M4 17a8 8 0 0 0 14 2l3-3 M21 21v-5h-5',
        edit: 'm4 16 12-12 4 4-12 12H4z M14 6l4 4',
        sun: 'M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
        globe: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 5-5 13 0 18 M12 3c5 5 5 13 0 18',
      };
      return h('svg', { className: 'remote-icon', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }, h('path', { d: paths[name] }));
    }
    function lastConnected(t, at) {
      if (!at) return t('neverConnected');
      const minutes = Math.max(0, Math.floor((Date.now() - at) / 60000));
      if (!minutes) return t('justConnected');
      const key = minutes < 60 ? 'minutesAgo' : minutes < 1440 ? 'hoursAgo' : 'daysAgo';
      return t('lastConnected', { time: t(key, { count: minutes < 60 ? minutes : minutes < 1440 ? Math.floor(minutes / 60) : Math.floor(minutes / 1440) }) });
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
    function RelaySection({ t, onState, allowConnections }) {
      const [config, setConfig] = useState(null);
      const [live, setLive] = useState(null);
      const [loadFailed, setLoadFailed] = useState(false);
      const [busy, setBusy] = useState(false);
      const [error, setError] = useState('');
      const [saved, setSaved] = useState(false);
      const mounted = useRef(false);
      const loading = useRef(false);
      async function refresh(initial = false) {
        if (loading.current) return;
        loading.current = true;
        try {
          const r = await request('relay/config');
          if (!mounted.current) return;
          setConfig(c => initial || !c ? r : c); setLive(r); onState(r); setLoadFailed(false);
        } catch { if (mounted.current) { setLoadFailed(true); onState(null); } }
        finally { loading.current = false; }
      }
      useEffect(() => {
        mounted.current = true; refresh(true);
        const timer = setInterval(() => refresh(), 3000);
        return () => { mounted.current = false; clearInterval(timer); };
      }, []);
      function change(key, value) {
        setConfig(c => ({ ...c, [key]: value })); setSaved(false); setError('');
      }
      async function save() {
        setBusy(true); setError(''); setSaved(false);
        try {
          const r = await request('relay/config', {
            enabled: !!config.serverUrl.trim(), serverUrl: config.serverUrl,
            caFile: config.caFile, allowInsecureLoopback: config.allowInsecureLoopback,
          });
          if (!mounted.current) return;
          setLive(r); setSaved(true); await refresh(true);
        } catch { if (mounted.current) setError('saveFailed'); }
        finally { if (mounted.current) setBusy(false); }
      }
      const state = allowConnections === false && live?.enabled ? 'paused' : live?.state || 'disabled';
      const knownState = ['disabled', 'paused', 'registering', 'connecting', 'connected', 'reconnecting', 'error'].includes(state) ? state : 'error';
      return h(Section, { title: t('server') },
        !config && !loadFailed && muted(t('loading')),
        loadFailed && h('div', {}, failure(t('loadFailed')), button(t('retry'), { onClick: () => refresh() })),
        config && h('div', { className: 'remote-card remote-form' },
          h(Field, { title: t('address'), name: 'server', value: config.serverUrl, disabled: busy,
            placeholder: 'https://example.com', onChange: v => change('serverUrl', v) }),
          h(Field, { title: t('ca'), name: 'ca', value: config.caFile, disabled: busy, hint: t('caHint'), onChange: v => change('caFile', v) }),
          h('div', { className: 'remote-row' },
            h('span', { className: 'remote-status', 'data-state': knownState, role: 'status' }, t(knownState)),
            h('div', { className: 'remote-actions' }, saved && h('span', { className: 'remote-muted', role: 'status' }, t('saved')),
              button(t(busy ? 'saving' : 'save'), { disabled: busy, onClick: save }))),
          live?.detail && failure(t(detailKey(live.detail)))), failure(error && t(error)));
    }
    function AddressPicker({ t, value, addresses, disabled, onChange, onCommit }) {
      const [open, setOpen] = useState(false), [active, setActive] = useState(-1);
      const input = useRef(null);
      const choose = address => { onChange(address); onCommit(address); setOpen(false); setActive(-1); input.current?.focus(); };
      return h('div', { className: 'remote-address-picker', onBlur: e => {
        if (!e.currentTarget.contains(e.relatedTarget)) { setOpen(false); setActive(-1); if (value.trim()) onCommit(value.trim()); }
      } },
        h('div', { className: 'remote-address-control' },
          h('input', { ref: input, id: 'dsh-direct-address', role: 'combobox', autoComplete: 'off', spellCheck: false,
            'aria-expanded': open && addresses.length > 0, 'aria-controls': 'dsh-direct-options',
            'aria-autocomplete': 'none', 'aria-activedescendant': open && active >= 0 ? 'dsh-direct-option-' + active : undefined,
            value, placeholder: 'https://example.com', disabled,
            onChange: e => { onChange(e.target.value); setOpen(false); setActive(-1); },
            onKeyDown: e => {
              if (e.key === 'Escape' && open) { e.preventDefault(); e.stopPropagation(); setOpen(false); return; }
              if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && addresses.length) {
                e.preventDefault(); setOpen(true);
                setActive(i => i < 0 ? (e.key === 'ArrowDown' ? 0 : addresses.length - 1) : (i + (e.key === 'ArrowDown' ? 1 : addresses.length - 1)) % addresses.length);
              }
              if (e.key === 'Enter') { e.preventDefault(); if (open && active >= 0) choose(addresses[active]); else if (value.trim()) onCommit(value.trim()); }
            } }),
          h('button', { type: 'button', className: 'remote-address-toggle', disabled: disabled || !addresses.length,
            'aria-label': t('chooseAddress'), 'aria-expanded': open, onClick: () => { setOpen(!open); setActive(Math.max(0, addresses.indexOf(value))); input.current?.focus(); } },
            h('svg', { viewBox: '0 0 20 20', width: 16, height: 16, fill: 'none', stroke: 'currentColor', strokeWidth: 1.5, 'aria-hidden': true },
              h('path', { d: open ? 'm5 12 5-5 5 5' : 'm5 8 5 5 5-5' })))),
        open && addresses.length > 0 && h('div', { id: 'dsh-direct-options', role: 'listbox', 'aria-label': t('chooseAddress'), className: 'remote-address-menu' },
          addresses.map((address, i) => h('button', { type: 'button', key: address, id: 'dsh-direct-option-' + i,
            role: 'option', 'aria-selected': address === value, tabIndex: -1, 'data-active': i === active,
            onMouseDown: e => e.preventDefault(), onMouseEnter: () => setActive(i), onClick: () => choose(address) },
            h('span', {}, address), address === value && h('span', { 'aria-hidden': true }, '✓')))));
    }
    function AddDevice({ t, server, onClose }) {
      const [mode, setMode] = useState(''), [pairing, setPairing] = useState(null);
      const [addresses, setAddresses] = useState([]), [directAddress, setDirectAddress] = useState('');
      const [directQr, setDirectQr] = useState(null), [qrBusy, setQrBusy] = useState(false);
      const [busy, setBusy] = useState(false), [error, setError] = useState(''), [copied, setCopied] = useState(false);
      const [now, setNow] = useState(Date.now());
      const generation = useRef(0), qrGeneration = useRef(0), discoverySession = useRef(null);
      function stopDiscovery() {
        const session = discoverySession.current; discoverySession.current = null;
        if (session) void request('discovery/stop', { session }).catch(() => {});
      }
      useEffect(() => {
        const clock = setInterval(() => setNow(Date.now()), 1000);
        let renewing = false;
        const heartbeat = setInterval(async () => {
          const session = discoverySession.current;
          if (!session || renewing) return;
          renewing = true;
          try {
            const value = await request('discovery/renew', { session });
            if (discoverySession.current === session) setPairing({ ...value, expiresAt: Date.now() + value.expiresInSec * 1000 });
          } catch {
            if (discoverySession.current === session) { stopDiscovery(); setPairing(null); setError('discoveryEnded'); }
          } finally { renewing = false; }
        }, 10000);
        return () => { generation.current++; qrGeneration.current++; stopDiscovery(); clearInterval(clock); clearInterval(heartbeat); };
      }, []);
      async function showDirectQr(address) {
        const current = ++qrGeneration.current;
        setDirectQr(null); setQrBusy(true); setError('');
        try {
          const value = await request('direct/qr', { address });
          if (current === qrGeneration.current) {
            setDirectQr(value);
            try { localStorage.setItem('dsh-remote.last-direct-address', address); } catch {}
          }
        } catch { if (current === qrGeneration.current) setError('requestFailed'); }
        finally { if (current === qrGeneration.current) setQrBusy(false); }
      }
      async function load(nextMode) {
        const current = ++generation.current;
        qrGeneration.current++; stopDiscovery(); setMode(nextMode); setPairing(null); setDirectQr(null);
        setError(''); setCopied(false); setBusy(true); setQrBusy(false);
        try {
          if (nextMode === 'server' || nextMode === 'code') {
            const value = await request(nextMode === 'server' ? 'relay/pairing' : 'discovery/start', {});
            if (generation.current !== current) {
              if (value.session) void request('discovery/stop', { session: value.session }).catch(() => {});
              return;
            }
            if (nextMode === 'code') discoverySession.current = value.session;
            setPairing({ ...value, expiresAt: value.expiresAt || Date.now() + value.expiresInSec * 1000 }); setNow(Date.now());
          } else if (nextMode === 'direct') {
            const info = await request('direct/info');
            if (generation.current !== current) return;
            const list = info.addresses || []; setAddresses(list);
            let last = ''; try { last = localStorage.getItem('dsh-remote.last-direct-address') || ''; } catch {}
            const address = list.includes(last) ? last : list.find(a => /^https?:\/\/192\.168\./.test(a)) || list[0] || '';
            setDirectAddress(address); if (address) void showDirectQr(address);
          }
        } catch { if (generation.current === current) setError('requestFailed'); }
        finally { if (generation.current === current) setBusy(false); }
      }
      async function copy(value) {
        try { await navigator.clipboard.writeText(value); setCopied(true); }
        catch { setError('copyFailed'); }
      }
      const seconds = pairing ? Math.max(0, Math.min(mode === 'server' ? 120 : 600, Math.ceil((pairing.expiresAt - now) / 1000))) : 0;
      const codeDisplay = pairing && h('div', { className: 'remote-code-row' },
        h('div', {}, h('div', { className: 'remote-code' }, seconds > 0 ? pairing.code : '— — —'),
          muted(seconds > 0 ? t('expires', { time: time(seconds) }) : t('qrExpired'))),
        button(t('regenerate'), { disabled: busy, onClick: () => load('code') }));
      const choice = (key, icon, hint, target, disabled = false) => button(h(React.Fragment, {},
        h('div', { className: 'remote-choice-title' }, h(Icon, { name: icon }), h('strong', {}, t(key))), muted(t(hint))),
        { className: 'remote-choice', disabled, onClick: () => load(target) });
      return h(Modal, { className: 'dsh-remote', open: true, title: mode === 'direct' ? t('direct') : mode === 'code' ? t('codeDirect') : t('addDevice'), closeLabel: t('close'), onClose,
        footer: h('div', { className: 'remote-add-footer' },
          (mode === 'direct' || mode === 'server') && h('p', { className: 'remote-muted remote-footer-hint' }, t(mode === 'direct' ? 'approvalInstructions' : 'serverApprovalInstructions')),
          h('div', { className: 'remote-footer-row' },
            (mode === 'direct' || mode === 'server') && button(t(copied ? 'copied' : 'copyAddress'), {
              className: 'remote-footer-copy', disabled: busy || (mode === 'direct' ? !directQr || qrBusy : !pairing || seconds <= 0),
              onClick: () => copy(mode === 'direct' ? directQr.link : pairing.link) }),
            h('div', { className: 'remote-actions remote-footer-actions' }, mode && button(t('back'), { onClick: () => {
              generation.current++; qrGeneration.current++; stopDiscovery(); setMode(''); setPairing(null); setBusy(false); setError('');
            } }), button(t('close'), { onClick: onClose })))) },
        h('div', { className: 'dsh-remote remote-modal-copy remote-add-body' },
          !mode && h('div', { className: 'remote-choices' },
            choice('direct', 'computer', 'directChoice', 'direct'),
            choice('codeDirect', 'device', 'codeDirectChoice', 'code'),
            choice('serverConnection', 'server', 'serverChoice', 'server', server?.state !== 'connected'),
            server?.state !== 'connected' && muted(t('connectFirst'))),
          busy && muted(t('loading')),
          mode === 'server' && !busy && (pairing && seconds > 0 ? h(React.Fragment, {}, muted(t('qrHint')),
            h('img', { className: 'remote-qr', src: pairing.image, alt: t('qrAlt') }),
            h('div', { className: 'remote-qr-meta' }, muted(t('expires', { time: time(seconds) }))))
            : button(t('regenerate'), { onClick: () => load('server') })),
          mode === 'code' && !busy && h(React.Fragment, {}, muted(t('discoveryInstructions')),
            pairing && h('div', { className: 'remote-discovery-computer' }, h(Icon, { name: 'computer' }),
              h('strong', {}, pairing.name), muted(t('discoveryMarker', { id: pairing.session?.slice(-6) }))),
            codeDisplay,
            pairing && h('p', { className: 'remote-muted', role: 'status' }, t(pairing.discoveryFailed ? 'discoveryFailed' : 'discoveryActive')),
            !pairing && button(t('regenerate'), { onClick: () => load('code') })),
          mode === 'direct' && h(React.Fragment, {}, muted(t('directAddressHint')),
            h('label', { className: 'remote-address-label', htmlFor: 'dsh-direct-address' }, t('directUrl')),
            h(AddressPicker, { t, value: directAddress, addresses, disabled: busy,
              onChange: value => { if (value === directAddress) return; qrGeneration.current++; setDirectAddress(value); setDirectQr(null); setQrBusy(false); setCopied(false); },
              onCommit: address => { if (address && directQr?.link !== address) void showDirectQr(address); } }),
            !addresses.length && !busy && muted(t('noAddress')),
            qrBusy && muted(t('generating')),
            directQr && h('img', { className: 'remote-qr', src: directQr.image, alt: t('directQr') })),
          failure(error && t(error))));
    }
    function DevicesSection({ t, access, updateAccess, accessBusy, server }) {
      const [adding, setAdding] = useState(false);
      useEffect(() => { if (!access?.allowConnections) setAdding(false); }, [access?.allowConnections]);
      const [devices, setDevices] = useState(null), [pending, setPending] = useState(null);
      const [computer, setComputer] = useState(null), [editing, setEditing] = useState(null);
      const [resetName, setResetName] = useState(false);
      const [name, setName] = useState(''), [renaming, setRenaming] = useState(false), [renameError, setRenameError] = useState('');
      const [busyId, setBusyId] = useState(null), [responding, setResponding] = useState(false);
      const [error, setError] = useState(false), [responseError, setResponseError] = useState(false);
      const mounted = useRef(false), polling = useRef(false);
      async function refresh() {
        if (polling.current) return;
        polling.current = true;
        try {
          const [list, status] = await Promise.all([request('pair/list'), request('pair/status')]);
          if (!mounted.current) return;
          setDevices(list.devices || []); setComputer(list.computer); setError(false);
          setPending(status.state === 'pending' ? status.pendingDevice : null);
          if (status.state === 'pending') setAdding(false);
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
      function edit(device, isComputer = false) {
        setEditing({ ...device, isComputer }); setName(device.name); setResetName(false); setRenameError('');
      }
      async function rename() {
        if (!editing || renaming) return;
        if (!name.trim() || name.trim().length > 64 || /[\x00-\x1f\x7f]/.test(name)) { setRenameError('invalidName'); return; }
        setRenaming(true); setRenameError('');
        try {
          await request(editing.isComputer ? 'computer/rename' : 'pair/rename', { deviceId: editing.deviceId, ...(editing.isComputer && resetName ? { reset: true } : { name: name.trim() }) });
          if (mounted.current) setEditing(null);
          await refresh();
        } catch { if (mounted.current) setRenameError('requestFailed'); }
        finally { if (mounted.current) setRenaming(false); }
      }
      return h(Section, { title: t('devices'), action: h('div', { className: 'remote-actions' },
        button(h(Icon, { name: 'refresh' }), { variant: 'ghost', 'aria-label': t('refresh'), onClick: refresh }),
        button(t('add'), { variant: 'primary', disabled: !access?.allowConnections || accessBusy, onClick: () => setAdding(true) })) },
        computer && h('div', { className: 'remote-computer' }, h('span', {}, t('thisComputer') + ' · ' + computer.name),
          button(h(Icon, { name: 'edit' }), { variant: 'ghost', 'aria-label': t('renameComputer'), onClick: () => edit(computer, true) })),
        h('div', { className: 'remote-card remote-devices' },
          h('div', { className: 'remote-device-row' }, h('span', {}, t('allowConnections')),
            h(Switch, { checked: !!access?.allowConnections, disabled: !access || accessBusy, label: t('allowConnections'), onChange: v => updateAccess({ allowConnections: v }) })),
          !devices ? muted(t('loading')) : devices.length ? devices.map(d => h('div', { key: d.deviceId, className: 'remote-device-row' },
            h('div', { className: 'remote-device-info' }, h(Icon, { name: 'device' }),
              h('div', { className: 'remote-name' }, h('strong', {}, d.name || d.deviceId), muted(lastConnected(t, d.lastConnectedAt)))),
            h('div', { className: 'remote-actions' },
              button(h(Icon, { name: 'edit' }), { variant: 'ghost', 'aria-label': t('renameNamed', { name: d.name }), disabled: !!busyId, onClick: () => edit(d) }),
              button(t(busyId === d.deviceId ? 'revoking' : 'revoke'), { variant: 'ghost', className: 'remote-pill', disabled: !!busyId, onClick: () => revoke(d.deviceId) }))))
            : h('p', { className: 'remote-empty' }, t('noDevices'))),
        failure(error && t('requestFailed')),
        adding && access?.allowConnections && h(AddDevice, { t, server, onClose: () => setAdding(false) }),
        h(Modal, { className: 'dsh-remote', open: !!editing, title: t('renameDevice'), closeLabel: t('cancel'),
          onClose: () => { if (!renaming) setEditing(null); },
          footer: h('div', { className: 'remote-actions' }, button(t('cancel'), { disabled: renaming, onClick: () => setEditing(null) }),
            editing?.isComputer && button(t('reset'), { disabled: renaming || !computer?.defaultName, onClick: () => { setName(computer.defaultName); setResetName(true); setRenameError(''); } }),
            button(t(renaming ? 'saving' : 'save'), { variant: 'primary', disabled: renaming || !name.trim(), onClick: rename })) },
          h('form', { className: 'dsh-remote', onSubmit: e => { e.preventDefault(); rename(); } },
            h(Field, { title: t('deviceName'), name: 'device-name', value: name, disabled: renaming, onChange: v => { setName(v); setResetName(false); setRenameError(''); } }),
            failure(renameError && t(renameError)))),
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
          !hosts ? muted(t('loading')) : !hosts.length ? h('p', { className: 'remote-empty' }, t('noHosts')) :
            h('ul', { className: 'remote-list' }, hosts.map(host => h('li', { key: host }, h('span', { className: 'remote-name' }, host),
              button(t('remove'), { disabled: busy, onClick: () => mutate('remove', host) })))),
          h('form', { className: 'remote-host-form', onSubmit: e => { e.preventDefault(); if (!busy && input.trim()) mutate('add', input.trim()); } },
            h('input', { className: 'remote-input', value: input, disabled: busy, placeholder: 'tunnel.example.com', 'aria-label': t('domain'),
              autoComplete: 'off', spellCheck: false, onChange: e => setInput(e.target.value) }),
            button(t(busy ? 'working' : 'add'), { type: 'submit', disabled: busy || !input.trim() })),
          muted(t('hostFormat')), failure(error && t(error)),
          error === 'loadFailed' && button(t('retry'), { onClick: refresh }));
    }
    function Settings({ t }) {
      const [access, setAccess] = useState(null), [server, setServer] = useState(null);
      const [busy, setBusy] = useState(false), [loadError, setLoadError] = useState(''), [saveError, setSaveError] = useState(''), [hostsOpen, setHostsOpen] = useState(false);
      const mounted = useRef(false), updating = useRef(false), revision = useRef(0), loading = useRef(false);
      async function refresh() {
        if (updating.current || loading.current) return;
        loading.current = true;
        const current = revision.current;
        try { const r = await request('access/settings'); if (mounted.current && current === revision.current) { setAccess(r); setLoadError(''); } }
        catch (e) { if (mounted.current && current === revision.current) setLoadError(accessErrorKey(e)); }
        finally { loading.current = false; }
      }
      useEffect(() => { mounted.current = true; refresh(); const timer = setInterval(refresh, 2000); return () => { mounted.current = false; clearInterval(timer); }; }, []);
      async function updateAccess(patch) {
        if (updating.current) return;
        revision.current++; updating.current = true; setBusy(true); setSaveError('');
        try { const r = await request('access/settings', patch); if (mounted.current) { setAccess(r); setLoadError(''); } }
        catch (e) { if (mounted.current) setSaveError(accessErrorKey(e, true)); }
        finally { updating.current = false; if (mounted.current) setBusy(false); }
      }
      return h('div', { className: 'dsh-remote' },
        h('header', { className: 'remote-intro' }, h('h2', {}, t('connections'))),
        h(RelaySection, { t, onState: setServer, allowConnections: access?.allowConnections }),
        failure(loadError && t(loadError)), loadError && button(t('retry'), { onClick: refresh }),
        failure(saveError && t(saveError)),
        h(DevicesSection, { t, access, updateAccess, accessBusy: busy || !!loadError, server }),
        h(Section, { title: t('otherSettings') }, h('div', { className: 'remote-card' },
          h('div', { className: 'remote-device-row' },
            h('div', { className: 'remote-device-info' }, h(Icon, { name: 'sun' }),
              h('div', {}, h('strong', {}, t('keepAwake')), muted(t('awakeHint')),
                access?.wake?.state === 'unsupported' && muted(t('awakeUnsupported')),
                access?.keepAwake && access?.allowConnections && access?.wake?.state === 'battery' && muted(t('awakeBattery')),
                access?.keepAwake && access?.allowConnections && access?.wake?.state === 'starting' && muted(t('awakeStarting')),
                access?.wake?.state === 'error' && failure(t('awakeFailed')))),
            h(Switch, { checked: !!access?.keepAwake, disabled: busy || !!loadError || !access || (!access.wake?.supported && !access.keepAwake), label: t('keepAwake'), onChange: v => updateAccess({ keepAwake: v }) })),
          h('div', { className: 'remote-device-row' }, h('div', { className: 'remote-device-info' }, h(Icon, { name: 'globe' }),
            h('div', {}, h('strong', {}, t('hosts')), muted(t('hostsHint')))), button(t('manage'), { variant: 'ghost', className: 'remote-pill', onClick: () => setHostsOpen(true) })))),
        h(Modal, { className: 'dsh-remote', open: hostsOpen, title: t('hosts'), closeLabel: t('close'), onClose: () => setHostsOpen(false) },
          hostsOpen && h('div', { className: 'dsh-remote remote-hosts-modal' }, h(HostsSection, { t }))));
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
