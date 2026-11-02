(() => {
  // src/client-copy.ts
  var en = {
    nav: "Remote control",
    intro: "Connect your phone to this computer.",
    server: "Server relay",
    serverHint: "Connect through your own relay server.",
    enable: "Enable server relay",
    address: "Server address",
    computerName: "Computer name",
    computer: "Computer",
    advanced: "Advanced",
    ca: "CA certificate path",
    caHint: "Optional for a private certificate authority.",
    save: "Save",
    saving: "Saving\u2026",
    saved: "Saved",
    addPhone: "Add phone",
    disabled: "Off",
    registering: "Registering",
    connecting: "Connecting",
    connected: "Connected",
    reconnecting: "Reconnecting",
    error: "Connection failed",
    loading: "Loading\u2026",
    retry: "Retry",
    close: "Close",
    cancel: "Cancel",
    qrTitle: "Add a phone",
    qrHint: "Scan with DSH Remote, then approve the request on this computer.",
    qrAlt: "Phone pairing QR code",
    qrExpiry: "One phone \xB7 Expires in {time}",
    qrExpired: "Connection information expired. Generate a new code.",
    regenerate: "Generate again",
    copyLink: "Copy connection info",
    copied: "Copied",
    copyFailed: "Copy failed. Select and copy the connection info below.",
    connectionInfo: "Connection info",
    pasteHint: "On your phone, choose \u201CPaste connection info\u201D.",
    direct: "Direct connection",
    directHint: "Enter this code on a phone connected directly to this computer.",
    code: "Pairing code",
    codeRules: "Single use \xB7 10 minutes \xB7 5 attempts",
    expires: "Expires in {time}",
    generate: "Generate code",
    generating: "Generating\u2026",
    devices: "Paired devices",
    devicesHint: "Each phone has its own access. Revoking it disconnects that phone.",
    noDevices: "No paired devices",
    revoke: "Revoke",
    revoking: "Revoking\u2026",
    approveTitle: "Pair \u201C{name}\u201D?",
    approveHint: "Allow this phone to control this computer?",
    allow: "Allow",
    deny: "Deny",
    responding: "Responding\u2026",
    hosts: "Trusted domains",
    hostsHint: "For direct connections through a public tunnel. Server relay does not need this.",
    hostFormat: "Domain or domain:port, without https://. Use Punycode for international domains.",
    domain: "Domain",
    noHosts: "No trusted domains",
    add: "Add",
    remove: "Remove",
    working: "Working\u2026",
    loadFailed: "Could not load settings. Check the computer connection and try again.",
    requestFailed: "Request failed. Try again.",
    saveFailed: "Could not save. Check the server address, certificate and file permissions.",
    invalidDomain: "Enter a valid domain or domain:port, without a URL scheme.",
    connectFirst: "Save your settings and wait for the relay to connect.",
    legacyConfig: "Set the server address again, then pair your phone with a new QR code.",
    certificateFailed: "Check the server address, network and TLS certificate.",
    caFailed: "Could not read the CA certificate. Check its path and permissions.",
    registrationDisabled: "Computer registration is disabled. Contact the server administrator.",
    serverUpdate: "Update the relay server to support automatic registration.",
    rateLimited: "Too many registration requests. Try again later.",
    unavailable: "The server is unavailable or has reached its computer limit.",
    identityMismatch: "The server returned a different computer identity. Check the server configuration.",
    duplicate: "This computer could not connect. Check its registration and close any duplicate plugin instance."
  };
  var zh = {
    nav: "\u8FDC\u7A0B\u63A7\u5236",
    intro: "\u7528\u624B\u673A\u8FDE\u63A5\u5E76\u63A7\u5236\u8FD9\u53F0\u7535\u8111\u3002",
    server: "\u670D\u52A1\u5668\u4E2D\u8F6C",
    serverHint: "\u901A\u8FC7\u81EA\u5EFA\u670D\u52A1\u5668\u8FDE\u63A5\u3002",
    enable: "\u542F\u7528\u670D\u52A1\u5668\u4E2D\u8F6C",
    address: "\u670D\u52A1\u5668\u5730\u5740",
    computerName: "\u7535\u8111\u540D\u79F0",
    computer: "\u7535\u8111",
    advanced: "\u9AD8\u7EA7\u8BBE\u7F6E",
    ca: "CA \u8BC1\u4E66\u8DEF\u5F84",
    caHint: "\u4F7F\u7528\u79C1\u6709\u8BC1\u4E66\u9881\u53D1\u673A\u6784\u65F6\u586B\u5199\u3002",
    save: "\u4FDD\u5B58",
    saving: "\u4FDD\u5B58\u4E2D\u2026",
    saved: "\u5DF2\u4FDD\u5B58",
    addPhone: "\u6DFB\u52A0\u624B\u673A",
    disabled: "\u672A\u542F\u7528",
    registering: "\u6CE8\u518C\u4E2D",
    connecting: "\u8FDE\u63A5\u4E2D",
    connected: "\u5DF2\u8FDE\u63A5",
    reconnecting: "\u6B63\u5728\u91CD\u8FDE",
    error: "\u8FDE\u63A5\u5931\u8D25",
    loading: "\u52A0\u8F7D\u4E2D\u2026",
    retry: "\u91CD\u8BD5",
    close: "\u5173\u95ED",
    cancel: "\u53D6\u6D88",
    qrTitle: "\u6DFB\u52A0\u624B\u673A",
    qrHint: "\u7528 DSH Remote \u626B\u7801\uFF0C\u518D\u5728\u8FD9\u53F0\u7535\u8111\u4E0A\u786E\u8BA4\u3002",
    qrAlt: "\u624B\u673A\u914D\u5BF9\u4E8C\u7EF4\u7801",
    qrExpiry: "\u4EC5\u4F9B\u4E00\u53F0\u624B\u673A\u4F7F\u7528 \xB7 {time} \u540E\u8FC7\u671F",
    qrExpired: "\u8FDE\u63A5\u4FE1\u606F\u5DF2\u8FC7\u671F\uFF0C\u8BF7\u91CD\u65B0\u751F\u6210\u3002",
    regenerate: "\u91CD\u65B0\u751F\u6210",
    copyLink: "\u590D\u5236\u8FDE\u63A5\u4FE1\u606F",
    copied: "\u5DF2\u590D\u5236",
    copyFailed: "\u590D\u5236\u5931\u8D25\uFF0C\u8BF7\u9009\u4E2D\u4E0B\u65B9\u8FDE\u63A5\u4FE1\u606F\u624B\u52A8\u590D\u5236\u3002",
    connectionInfo: "\u8FDE\u63A5\u4FE1\u606F",
    pasteHint: "\u5728\u624B\u673A\u4E0A\u9009\u62E9\u201C\u7C98\u8D34\u8FDE\u63A5\u4FE1\u606F\u201D\u3002",
    direct: "\u76F4\u8FDE",
    directHint: "\u624B\u673A\u76F4\u8FDE\u8FD9\u53F0\u7535\u8111\u540E\uFF0C\u8F93\u5165\u4E0B\u65B9\u914D\u5BF9\u7801\u3002",
    code: "\u914D\u5BF9\u7801",
    codeRules: "\u4E00\u6B21\u6709\u6548 \xB7 10 \u5206\u949F \xB7 \u6700\u591A\u5C1D\u8BD5 5 \u6B21",
    expires: "{time} \u540E\u8FC7\u671F",
    generate: "\u751F\u6210\u914D\u5BF9\u7801",
    generating: "\u751F\u6210\u4E2D\u2026",
    devices: "\u5DF2\u914D\u5BF9\u8BBE\u5907",
    devicesHint: "\u6BCF\u53F0\u624B\u673A\u72EC\u7ACB\u6388\u6743\uFF0C\u64A4\u9500\u540E\u7ACB\u5373\u65AD\u5F00\u8BE5\u624B\u673A\u3002",
    noDevices: "\u6682\u65E0\u5DF2\u914D\u5BF9\u8BBE\u5907",
    revoke: "\u64A4\u9500",
    revoking: "\u64A4\u9500\u4E2D\u2026",
    approveTitle: "\u5141\u8BB8\u201C{name}\u201D\u914D\u5BF9\uFF1F",
    approveHint: "\u5141\u8BB8\u8FD9\u53F0\u624B\u673A\u8FDC\u7A0B\u63A7\u5236\u672C\u7535\u8111\uFF1F",
    allow: "\u5141\u8BB8",
    deny: "\u62D2\u7EDD",
    responding: "\u5904\u7406\u4E2D\u2026",
    hosts: "\u53EF\u4FE1\u57DF\u540D",
    hostsHint: "\u4EC5\u7528\u4E8E\u901A\u8FC7\u516C\u7F51\u96A7\u9053\u76F4\u8FDE\uFF0C\u670D\u52A1\u5668\u4E2D\u8F6C\u65E0\u9700\u8BBE\u7F6E\u3002",
    hostFormat: "\u586B\u5199\u57DF\u540D\u6216\u57DF\u540D:\u7AEF\u53E3\uFF0C\u4E0D\u5E26 https://\uFF1B\u4E2D\u6587\u57DF\u540D\u8BF7\u8F6C\u4E3A Punycode\u3002",
    domain: "\u57DF\u540D",
    noHosts: "\u6682\u65E0\u53EF\u4FE1\u57DF\u540D",
    add: "\u6DFB\u52A0",
    remove: "\u5220\u9664",
    working: "\u5904\u7406\u4E2D\u2026",
    loadFailed: "\u52A0\u8F7D\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u7535\u8111\u8FDE\u63A5\u540E\u91CD\u8BD5\u3002",
    requestFailed: "\u8BF7\u6C42\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5\u3002",
    saveFailed: "\u4FDD\u5B58\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u5730\u5740\u3001\u8BC1\u4E66\u548C\u6587\u4EF6\u6743\u9650\u3002",
    invalidDomain: "\u8BF7\u586B\u5199\u6709\u6548\u7684\u57DF\u540D\u6216\u57DF\u540D:\u7AEF\u53E3\uFF0C\u4E0D\u5E26\u534F\u8BAE\u524D\u7F00\u3002",
    connectFirst: "\u8BF7\u4FDD\u5B58\u8BBE\u7F6E\u5E76\u7B49\u5F85\u670D\u52A1\u5668\u8FDE\u63A5\u6210\u529F\u3002",
    legacyConfig: "\u8BF7\u91CD\u65B0\u586B\u5199\u670D\u52A1\u5668\u5730\u5740\uFF0C\u518D\u8BA9\u624B\u673A\u626B\u63CF\u65B0\u4E8C\u7EF4\u7801\u914D\u5BF9\u3002",
    certificateFailed: "\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u5730\u5740\u3001\u7F51\u7EDC\u548C TLS \u8BC1\u4E66\u3002",
    caFailed: "\u65E0\u6CD5\u8BFB\u53D6 CA \u8BC1\u4E66\uFF0C\u8BF7\u68C0\u67E5\u8DEF\u5F84\u548C\u6743\u9650\u3002",
    registrationDisabled: "\u670D\u52A1\u5668\u672A\u5F00\u653E\u7535\u8111\u6CE8\u518C\uFF0C\u8BF7\u8054\u7CFB\u7EF4\u62A4\u8005\u3002",
    serverUpdate: "\u8BF7\u66F4\u65B0\u670D\u52A1\u5668\u540E\u7AEF\u4EE5\u652F\u6301\u81EA\u52A8\u6CE8\u518C\u3002",
    rateLimited: "\u6CE8\u518C\u8BF7\u6C42\u8FC7\u4E8E\u9891\u7E41\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002",
    unavailable: "\u670D\u52A1\u5668\u6682\u4E0D\u53EF\u7528\u6216\u5DF2\u8FBE\u5230\u7535\u8111\u6570\u91CF\u4E0A\u9650\u3002",
    identityMismatch: "\u670D\u52A1\u5668\u8FD4\u56DE\u7684\u7535\u8111\u8EAB\u4EFD\u4E0D\u4E00\u81F4\uFF0C\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u914D\u7F6E\u3002",
    duplicate: "\u7535\u8111\u8FDE\u63A5\u88AB\u62D2\u7EDD\uFF0C\u8BF7\u68C0\u67E5\u767B\u8BB0\u72B6\u6001\u6216\u662F\u5426\u91CD\u590D\u8FD0\u884C\u63D2\u4EF6\u3002"
  };
  function detailKey(detail) {
    const value = String(detail || "");
    if (/旧版|手动方案|配置已失效/.test(value)) return "legacyConfig";
    if (/无法读取 CA/.test(value)) return "caFailed";
    if (/未启用自动注册/.test(value)) return "registrationDisabled";
    if (/版本不支持/.test(value)) return "serverUpdate";
    if (/过于频繁/.test(value)) return "rateLimited";
    if (/容量上限|暂不可用/.test(value)) return "unavailable";
    if (/身份不一致/.test(value)) return "identityMismatch";
    if (/连接被拒绝/.test(value)) return "duplicate";
    return "certificateFailed";
  }

  // src/client-style.ts
  var settingsCss = `
.dsh-remote { color:var(--dsw-alias-label-primary); font-family:inherit; font-size:14px; line-height:22px; min-width:0; }
.dsh-remote h2,.dsh-remote h3,.dsh-remote p { margin:0; }
.dsh-remote h2 { font-size:16px; font-weight:500; line-height:24px; }
.dsh-remote h3 { font-size:14px; font-weight:500; line-height:22px; }
.dsh-remote .remote-intro { padding:0 0 16px; }
.dsh-remote .remote-section { padding:20px 0; border-top:.5px solid var(--dsw-alias-border-l2); }
.dsh-remote .remote-head,.dsh-remote .remote-row { display:flex; align-items:center; justify-content:space-between; gap:24px; }
.dsh-remote .remote-head { margin-bottom:16px; }
.dsh-remote .remote-head:last-child { margin-bottom:0; }
.dsh-remote .remote-muted { color:var(--dsw-alias-label-secondary); font-size:12px; line-height:18px; margin-top:4px; }
.dsh-remote .remote-form { display:grid; gap:14px; }
.dsh-remote .remote-field { display:grid; grid-template-columns:minmax(110px,1fr) minmax(0,2fr); align-items:center; gap:20px; }
.dsh-remote .remote-input { box-sizing:border-box; width:100%; min-width:0; border:1px solid var(--dsw-alias-border-l2); border-radius:var(--dsw-radius-md); background:var(--dsw-alias-bg-layer-1); color:var(--dsw-alias-label-primary); padding:8px 12px; font:inherit; font-size:13px; line-height:20px; }
.dsh-remote .remote-input:hover { border-color:var(--dsw-alias-border-l1); }
.dsh-remote .remote-input:focus-visible,.dsh-remote summary:focus-visible { outline:2px solid var(--dsw-alias-brand-primary); outline-offset:2px; }
.dsh-remote .remote-input:disabled { opacity:.5; }
.dsh-remote .remote-input::placeholder { color:var(--dsw-alias-label-tertiary); }
.dsh-remote .remote-actions { display:flex; flex-wrap:wrap; align-items:center; justify-content:flex-end; gap:8px; }
.dsh-remote .remote-status { display:inline-flex; align-items:center; gap:6px; font-size:12px; color:var(--dsw-alias-label-secondary); white-space:nowrap; }
.dsh-remote .remote-status::before { content:''; width:6px; height:6px; border-radius:50%; background:currentColor; }
.dsh-remote .remote-status[data-state=connected] { color:var(--dsw-alias-state-success-primary); }
.dsh-remote .remote-status[data-state=error],.dsh-remote .remote-error { color:var(--dsw-alias-state-error-primary); }
.dsh-remote .remote-error { font-size:12px; line-height:18px; margin-top:8px; }
.dsh-remote .remote-list { margin:12px 0 0; padding:0; list-style:none; }
.dsh-remote .remote-list li { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:10px 0; border-bottom:.5px solid var(--dsw-alias-border-l2); }
.dsh-remote .remote-list li:last-child { border-bottom:0; }
.dsh-remote .remote-name { min-width:0; overflow-wrap:anywhere; }
.dsh-remote .remote-code { font:500 28px/40px ui-monospace,monospace; letter-spacing:.22em; font-variant-numeric:tabular-nums; }
.dsh-remote .remote-code-row { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-top:12px; }
.dsh-remote .remote-empty { padding:8px 0; color:var(--dsw-alias-label-secondary); font-size:13px; }
.dsh-remote details>summary { cursor:pointer; width:fit-content; font-size:13px; color:var(--dsw-alias-label-secondary); }
.dsh-remote details[open]>summary { margin-bottom:14px; }
.dsh-remote .remote-host-form { display:flex; gap:8px; margin-top:12px; }
.dsh-remote .remote-host-form .remote-input { flex:1; }
.dsh-remote .remote-qr { display:block; width:240px; max-width:100%; height:auto; margin:12px auto; border-radius:var(--dsw-radius-md); }
.dsh-remote .remote-qr-meta { text-align:center; margin:8px 0 16px; }
.dsh-remote .remote-link { resize:vertical; min-height:64px; max-height:160px; font:12px/18px ui-monospace,monospace; overflow-wrap:anywhere; }
.dsh-remote .remote-modal-copy { overflow-wrap:anywhere; }
@media(max-width:640px) {
 .dsh-remote .remote-field { grid-template-columns:1fr; gap:6px; }
 .dsh-remote .remote-head,.dsh-remote .remote-row { gap:12px; }
 .dsh-remote .remote-section { padding:16px 0; }
}
`;

  // src/client.ts
  window.__ModuleLoader__.load({
    id: "dsh-remote-access",
    factory(require2) {
      const React = require2("react");
      const { createElement: h, useState, useEffect, useRef } = React;
      const { Button, Switch, Modal } = require2("@deepseek-ai/dsh-client-ui-primitives");
      const NS = "dsh-remote-access";
      const path = "/api/remote-access/";
      const time = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
      const button = (text, props = {}) => h(Button, { size: "sm", variant: "outline", ...props }, text);
      const muted = (text) => h("p", { className: "remote-muted" }, text);
      const failure = (text) => text ? h("p", { className: "remote-error", role: "alert" }, text) : null;
      async function request(route, body, signal) {
        const response = await fetch(path + route, {
          method: body === void 0 ? "GET" : "POST",
          signal,
          ...body === void 0 ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
        });
        const value = await response.json();
        if (!response.ok || !value?.ok) throw new Error(value?.error || "request_failed");
        return value;
      }
      function Section({ title, hint, action, children }) {
        return h(
          "section",
          { className: "remote-section" },
          h("div", { className: "remote-head" }, h("div", {}, h("h3", {}, title), hint && muted(hint)), action),
          children
        );
      }
      function Field({ title, name, value, onChange, disabled, placeholder, hint }) {
        const id = "dsh-remote-" + name;
        return h(
          "div",
          { className: "remote-field" },
          h("label", { htmlFor: id }, title),
          h("div", {}, h("input", {
            id,
            className: "remote-input",
            value: value || "",
            disabled,
            placeholder,
            autoComplete: "off",
            spellCheck: false,
            onChange: (e) => onChange(e.target.value)
          }), hint && muted(hint))
        );
      }
      function RelaySection({ t }) {
        const [config, setConfig] = useState(null);
        const [live, setLive] = useState(null);
        const [loadFailed, setLoadFailed] = useState(false);
        const [busy, setBusy] = useState(false);
        const [error, setError] = useState("");
        const [saved, setSaved] = useState(false);
        const [pairing, setPairing] = useState(null);
        const [now, setNow] = useState(Date.now());
        const [copyState, setCopyState] = useState("");
        const mounted = useRef(false);
        const loading = useRef(false);
        async function refresh(initial = false) {
          if (loading.current) return;
          loading.current = true;
          try {
            const r = await request("relay/config");
            if (!mounted.current) return;
            setConfig((c) => initial || !c ? r : c);
            setLive(r);
            setLoadFailed(false);
          } catch {
            if (mounted.current) setLoadFailed(true);
          } finally {
            loading.current = false;
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh(true);
          const timer = setInterval(() => refresh(), 3e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        useEffect(() => {
          if (!pairing) return;
          const timer = setInterval(() => setNow(Date.now()), 1e3);
          return () => clearInterval(timer);
        }, [pairing]);
        function change(key, value) {
          setConfig((c) => ({ ...c, [key]: value }));
          setSaved(false);
          setError("");
          setPairing(null);
        }
        async function save() {
          setBusy(true);
          setError("");
          setSaved(false);
          setPairing(null);
          try {
            const r = await request("relay/config", {
              enabled: config.enabled,
              serverUrl: config.serverUrl,
              computerName: config.computerName,
              caFile: config.caFile,
              allowInsecureLoopback: config.allowInsecureLoopback
            });
            if (!mounted.current) return;
            setLive(r);
            setSaved(true);
            await refresh(true);
          } catch {
            if (mounted.current) setError("saveFailed");
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        async function addPhone() {
          setBusy(true);
          setError("");
          setCopyState("");
          try {
            const r = await request("relay/pairing", {});
            if (mounted.current) {
              setPairing(r);
              setNow(Date.now());
            }
          } catch {
            if (mounted.current) setError("connectFirst");
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        async function copy() {
          try {
            await navigator.clipboard.writeText(pairing.link);
            setCopyState("copied");
          } catch {
            setCopyState("copyFailed");
          }
        }
        const seconds = pairing ? Math.max(0, Math.ceil((pairing.expiresAt - now) / 1e3)) : 0;
        const state = live?.state || "disabled";
        const knownState = ["disabled", "registering", "connecting", "connected", "reconnecting", "error"].includes(state) ? state : "error";
        return h(
          Section,
          {
            title: t("server"),
            hint: t("serverHint"),
            action: config && h(Switch, { checked: config.enabled, disabled: busy, label: t("enable"), onChange: (v) => change("enabled", v) })
          },
          !config && !loadFailed && muted(t("loading")),
          loadFailed && h("div", {}, failure(t("loadFailed")), button(t("retry"), { onClick: () => refresh() })),
          config && h(
            "div",
            { className: "remote-form" },
            h(Field, {
              title: t("address"),
              name: "server",
              value: config.serverUrl,
              disabled: busy,
              placeholder: "https://relay.example.com",
              onChange: (v) => change("serverUrl", v)
            }),
            h(Field, {
              title: t("computerName"),
              name: "name",
              value: config.computerName,
              disabled: busy,
              placeholder: t("computer"),
              onChange: (v) => change("computerName", v)
            }),
            h(
              "details",
              {},
              h("summary", {}, t("advanced")),
              h(Field, { title: t("ca"), name: "ca", value: config.caFile, disabled: busy, hint: t("caHint"), onChange: (v) => change("caFile", v) })
            ),
            h(
              "div",
              { className: "remote-row" },
              h("span", { className: "remote-status", "data-state": knownState, role: "status" }, t(knownState)),
              h(
                "div",
                { className: "remote-actions" },
                saved && h("span", { className: "remote-muted", role: "status" }, t("saved")),
                button(t(busy ? "saving" : "save"), { disabled: busy, onClick: save }),
                button(t("addPhone"), { variant: "primary", disabled: busy || loadFailed || state !== "connected" || !config.enabled, onClick: addPhone })
              )
            ),
            live?.detail && failure(t(detailKey(live.detail)))
          ),
          failure(error && t(error)),
          h(
            Modal,
            {
              className: "dsh-remote",
              open: !!pairing,
              onClose: () => setPairing(null),
              title: t("qrTitle"),
              closeLabel: t("close"),
              footer: h(
                "div",
                { className: "remote-actions" },
                button(t("close"), { onClick: () => setPairing(null) }),
                seconds > 0 ? button(t(copyState === "copied" ? "copied" : "copyLink"), { variant: "primary", onClick: copy }) : button(t("regenerate"), { variant: "primary", disabled: busy, onClick: addPhone })
              )
            },
            h(
              "div",
              { className: "dsh-remote remote-modal-copy" },
              seconds > 0 && pairing ? h(
                React.Fragment,
                {},
                muted(t("qrHint")),
                h("img", { className: "remote-qr", src: pairing.image, alt: t("qrAlt") }),
                h("div", { className: "remote-qr-meta" }, muted(t("qrExpiry", { time: time(seconds) }))),
                h(
                  "details",
                  { open: copyState === "copyFailed" || void 0 },
                  h("summary", {}, t("connectionInfo")),
                  h("textarea", { className: "remote-input remote-link", readOnly: true, value: pairing.link, "aria-label": t("connectionInfo"), onFocus: (e) => e.target.select() }),
                  muted(t("pasteHint"))
                ),
                failure(copyState === "copyFailed" && t("copyFailed"))
              ) : muted(t("qrExpired"))
            )
          )
        );
      }
      function DirectSection({ t }) {
        const [pc, setPc] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(false);
        const [now, setNow] = useState(Date.now());
        const mounted = useRef(false);
        async function load(generate = false) {
          setBusy(true);
          setError(false);
          try {
            const r = await request(generate ? "pair/code/generate" : "pair/code/current", generate ? {} : void 0);
            if (mounted.current) {
              const at = Date.now();
              setNow(at);
              setPc({ code: r.code, expiresAt: at + r.expiresInSec * 1e3 });
            }
          } catch {
            if (mounted.current) setError(true);
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        useEffect(() => {
          mounted.current = true;
          load();
          const timer = setInterval(() => setNow(Date.now()), 1e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        const seconds = pc ? Math.max(0, Math.ceil((pc.expiresAt - now) / 1e3)) : 0;
        useEffect(() => {
          if (pc && seconds === 0 && !busy && !error) load();
        }, [seconds, pc, busy, error]);
        return h(
          Section,
          { title: t("direct"), hint: t("directHint") },
          h(
            "div",
            { className: "remote-code-row" },
            h(
              "div",
              {},
              h("div", { className: "remote-code", "aria-label": t("code") }, pc && seconds > 0 ? pc.code : "\u2014 \u2014 \u2014"),
              muted(pc && seconds > 0 ? t("expires", { time: time(seconds) }) : t("codeRules"))
            ),
            button(t(busy ? "generating" : pc ? "regenerate" : "generate"), { disabled: busy, onClick: () => load(true) })
          ),
          failure(error && t("requestFailed"))
        );
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
            const [list, status] = await Promise.all([request("pair/list"), request("pair/status")]);
            if (!mounted.current) return;
            setDevices(list.devices || []);
            setError(false);
            setPending(status.state === "pending" ? status.pendingDevice : null);
          } catch {
            if (mounted.current) setError(true);
          } finally {
            polling.current = false;
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh();
          const timer = setInterval(refresh, 2e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        async function revoke(id) {
          setBusyId(id);
          setError(false);
          try {
            await request("pair/revoke", { deviceId: id });
            await refresh();
          } catch {
            if (mounted.current) setError(true);
          } finally {
            if (mounted.current) setBusyId(null);
          }
        }
        async function respond(outcome) {
          if (!pending || responding) return;
          setResponding(true);
          setResponseError(false);
          try {
            await request("pair/respond", { deviceId: pending.deviceId, outcome });
            if (mounted.current) setPending(null);
            await refresh();
          } catch {
            if (mounted.current) setResponseError(true);
          } finally {
            if (mounted.current) setResponding(false);
          }
        }
        return h(
          Section,
          { title: t("devices"), hint: t("devicesHint") },
          !devices ? muted(t("loading")) : devices.length ? h("ul", { className: "remote-list" }, devices.map((d) => h(
            "li",
            { key: d.deviceId },
            h("span", { className: "remote-name" }, d.name || d.deviceId),
            button(t(busyId === d.deviceId ? "revoking" : "revoke"), { disabled: !!busyId, onClick: () => revoke(d.deviceId) })
          ))) : h("p", { className: "remote-empty" }, t("noDevices")),
          failure(error && t("requestFailed")),
          h(
            Modal,
            {
              className: "dsh-remote",
              open: !!pending,
              title: t("approveTitle", { name: pending?.name || "" }),
              description: t("approveHint"),
              closeLabel: t("deny"),
              onClose: () => respond("deny"),
              footer: h(
                React.Fragment,
                {},
                button(t("deny"), { disabled: responding, onClick: () => respond("deny") }),
                button(t(responding ? "responding" : "allow"), { variant: "primary", disabled: responding, onClick: () => respond("approve") })
              )
            },
            h("div", { className: "dsh-remote" }, failure(responseError && t("requestFailed")))
          )
        );
      }
      function HostsSection({ t }) {
        const [hosts, setHosts] = useState(null), [input, setInput] = useState("");
        const [busy, setBusy] = useState(false), [error, setError] = useState("");
        const mounted = useRef(false);
        async function refresh() {
          try {
            const r = await request("trusted-hosts");
            if (mounted.current) {
              setHosts(r.hosts || []);
              setError("");
            }
          } catch {
            if (mounted.current) setError("loadFailed");
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh();
          return () => {
            mounted.current = false;
          };
        }, []);
        async function mutate(action, host) {
          setBusy(true);
          setError("");
          try {
            const r = await request("trusted-hosts", { action, host });
            if (mounted.current) {
              setHosts(r.hosts || []);
              if (action === "add") setInput("");
            }
          } catch (e) {
            if (mounted.current) setError(/invalid|无效|域名|host/i.test(e.message) ? "invalidDomain" : "requestFailed");
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        return h(
          Section,
          { title: t("hosts"), hint: t("hostsHint") },
          h(
            "details",
            {},
            h("summary", {}, t("advanced")),
            !hosts ? muted(t("loading")) : !hosts.length ? h("p", { className: "remote-empty" }, t("noHosts")) : h("ul", { className: "remote-list" }, hosts.map((host) => h(
              "li",
              { key: host },
              h("span", { className: "remote-name" }, host),
              button(t("remove"), { disabled: busy, onClick: () => mutate("remove", host) })
            ))),
            h(
              "form",
              { className: "remote-host-form", onSubmit: (e) => {
                e.preventDefault();
                if (!busy && input.trim()) mutate("add", input.trim());
              } },
              h("input", {
                className: "remote-input",
                value: input,
                disabled: busy,
                placeholder: "tunnel.example.com",
                "aria-label": t("domain"),
                autoComplete: "off",
                spellCheck: false,
                onChange: (e) => setInput(e.target.value)
              }),
              button(t(busy ? "working" : "add"), { type: "submit", disabled: busy || !input.trim() })
            ),
            muted(t("hostFormat")),
            failure(error && t(error)),
            error === "loadFailed" && button(t("retry"), { onClick: refresh })
          )
        );
      }
      function Settings({ t }) {
        return h(
          "div",
          { className: "dsh-remote" },
          h("header", { className: "remote-intro" }, h("h2", {}, t("nav")), muted(t("intro"))),
          h(RelaySection, { t }),
          h(DevicesSection, { t }),
          h(DirectSection, { t }),
          h(HostsSection, { t })
        );
      }
      function apply(ctx) {
        ctx.effect(() => ctx.locale.register(NS, { en, zh }));
        ctx.effect(() => {
          const style = document.createElement("style");
          style.dataset.plugin = NS;
          style.textContent = settingsCss;
          document.head.appendChild(style);
          return () => style.remove();
        });
        const t = ctx.locale.bind(NS);
        ctx.slots.inject("settings.section", () => ctx.slots.register({
          name: "settings.section",
          id: NS,
          order: 40,
          label: () => t("nav"),
          locale: NS
        }, Settings));
      }
      return { apply, inject: ["slots", "locale"] };
    }
  });
})();
