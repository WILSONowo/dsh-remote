// @ts-nocheck
// dsh-remote-access v2.4.1 TypeScript source (migrated from lib).
// Transitional: @ts-nocheck skips semantic checks; DSH ctx/webServer dynamic types added later.
// Syntax is still checked. npm run build emits to lib/; npm test smoke-tests the build output.

window.__ModuleLoader__.load({
  id: "dsh-remote-access",
  factory: function (require) {
    var module = { exports: {} };
    var exports = module.exports;
    var react = require("react");
    var h = react.createElement;
    var useState = react.useState;
    var useEffect = react.useEffect;
    var useRef = react.useRef;

    var S = {
      root: { display: "flex", flexDirection: "column", gap: "12px", padding: "4px 0" },
      card: {
        border: "1px solid var(--dsw-alias-border-l1)", borderRadius: "10px",
        background: "var(--dsw-alias-bg-layer-1)", padding: "14px",
      },
      row: { display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" },
      title: { fontSize: 13, fontWeight: 600, color: "var(--dsw-alias-label-primary)" },
      label: { fontSize: 12, color: "var(--dsw-alias-label-secondary)", lineHeight: 1.6 },
      sub: { fontSize: 12, color: "var(--dsw-alias-label-secondary)", lineHeight: 1.7 },
      mono: { fontFamily: "ui-monospace, monospace", fontSize: 12, color: "var(--dsw-alias-label-secondary)", wordBreak: "break-all" },
      input: {
        border: "1px solid var(--dsw-alias-border-l1)", borderRadius: "6px",
        background: "var(--dsw-alias-bg-layer-1)", color: "var(--dsw-alias-label-primary)",
        padding: "5px 10px", fontSize: 12, width: 140,
      },
      err: { fontSize: 12, color: "var(--dsw-alias-state-error-primary)", lineHeight: 1.6 },
      btn: {
        border: "1px solid var(--dsw-alias-border-l1)", borderRadius: "6px",
        background: "transparent", color: "var(--dsw-alias-label-primary)",
        padding: "5px 12px", fontSize: 12, cursor: "pointer",
      },
      btnPrimary: {
        border: "1px solid var(--dsw-alias-brand-primary)", borderRadius: "6px",
        background: "var(--dsw-alias-brand-primary)", color: "#0D1B2A",
        padding: "6px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer",
      },
      ok: { fontSize: 12, color: "var(--dsw-alias-state-success-primary)", lineHeight: 1.6 },
      badge: {
        display: "inline-block", padding: "2px 8px", borderRadius: "10px", fontSize: 11, fontWeight: 600,
      },
      divider: { border: "none", borderTop: "1px dashed var(--dsw-alias-border-l1)", margin: "4px 0 0" },
    };

    function fetchJson(path, opts) {
      return fetch(path, opts).then(function (r) { return r.json(); });
    }

    // ---------- 移动端 App 配对确认（全局对话框 + 配对管理） ----------
    var pairDialogEl = null;
    var pairDialogShown = false;

    function removePairDialog() {
      if (pairDialogEl && pairDialogEl.parentNode) pairDialogEl.parentNode.removeChild(pairDialogEl);
      pairDialogEl = null;
      pairDialogShown = false;
    }

    function showPairDialog(pendingDevice, respond) {
      removePairDialog();
      pairDialogShown = true;

      var overlay = document.createElement("div");
      overlay.setAttribute("data-dsh-pair-dialog", "1");
      overlay.style.cssText = [
        "position:fixed", "inset:0", "z-index:2147483000",
        "display:flex", "align-items:center", "justify-content:center",
        "background:rgba(13,27,42,.62)", "backdrop-filter:blur(4px)",
        "padding:20px",
      ].join(";");

      var card = document.createElement("div");
      card.style.cssText = [
        "background:var(--dsw-alias-bg-layer-1)", "border:1px solid var(--dsw-alias-border-l1)",
        "border-radius:14px", "padding:24px", "max-width:380px", "width:100%",
        "box-shadow:0 20px 60px rgba(0,0,0,.5)", "color:var(--dsw-alias-label-primary)",
      ].join(";");

      var title = document.createElement("div");
      title.textContent = "手机设备「" + pendingDevice.name + "」请求首次连接";
      title.style.cssText = "font-size:15px;font-weight:600;line-height:1.5;margin-bottom:8px";

      var sub = document.createElement("div");
      sub.textContent = "允许该设备远程控制这台电脑？";
      sub.style.cssText = "font-size:13px;color:var(--dsw-alias-label-secondary);line-height:1.6;margin-bottom:18px";

      var row = document.createElement("div");
      row.style.cssText = "display:flex;gap:10px;justify-content:flex-end";

      function mkBtn(label, primary) {
        var b = document.createElement("button");
        b.textContent = label;
        b.style.cssText = primary
          ? "border:1px solid var(--dsw-alias-brand-primary);border-radius:6px;background:var(--dsw-alias-brand-primary);color:#0D1B2A;padding:7px 16px;font-size:13px;font-weight:600;cursor:pointer"
          : "border:1px solid var(--dsw-alias-border-l1);border-radius:6px;background:transparent;color:var(--dsw-alias-label-primary);padding:7px 16px;font-size:13px;cursor:pointer";
        return b;
      }

      var allowBtn = mkBtn("允许", true);
      var denyBtn = mkBtn("拒绝", false);

      allowBtn.onclick = function () { allowBtn.disabled = true; denyBtn.disabled = true; respond(pendingDevice.deviceId, "approve"); };
      denyBtn.onclick = function () { allowBtn.disabled = true; denyBtn.disabled = true; respond(pendingDevice.deviceId, "deny"); };

      row.appendChild(allowBtn);
      row.appendChild(denyBtn);
      card.appendChild(title);
      card.appendChild(sub);
      card.appendChild(row);
      overlay.appendChild(card);
      document.body.appendChild(overlay);
      pairDialogEl = overlay;
    }

    function PairSection() {
      var [devices, setDevices] = useState(null);
      var [busyId, setBusyId] = useState(null);
      var pairTimer = useRef(null);

      function refreshList() {
        fetchJson("/api/remote-access/pair/list")
          .then(function (r) { if (r && r.ok) setDevices(r.devices || []); })
          .catch(function () {});
      }

      function respond(deviceId, outcome) {
        fetchJson("/api/remote-access/pair/respond", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ deviceId: deviceId, outcome: outcome }),
        })
          .then(function () { removePairDialog(); refreshList(); })
          .catch(function () { removePairDialog(); });
      }

      function pollStatus() {
        fetchJson("/api/remote-access/pair/status")
          .then(function (r) {
            if (!r || !r.ok) return;
            var pendingDevice = r.pendingDevice || null;
            if (r.state === "pending" && pendingDevice && !pairDialogShown) {
              showPairDialog(pendingDevice, respond);
            } else if (r.state !== "pending" && pairDialogShown) {
              removePairDialog();
            }
          })
          .catch(function () {});
      }

      useEffect(function () {
        refreshList();
        pollStatus();
        pairTimer.current = setInterval(pollStatus, 2000);
        return function () {
          clearInterval(pairTimer.current);
          removePairDialog();
        };
      }, []);

      function revoke(deviceId) {
        setBusyId(deviceId);
        fetchJson("/api/remote-access/pair/revoke", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ deviceId: deviceId }),
        })
          .then(function () { refreshList(); })
          .catch(function () {})
          .finally(function () { setBusyId(null); });
      }

      return h("div", { style: S.root },
        h("div", { style: S.title }, "配对设备管理"),
        h("div", { style: S.sub }, "手机通过服务器连接时，先扫码或粘贴连接信息，再在电脑确认；直连可使用配对码或本机确认。撤销设备后立即断开，需重新配对。"),
        h("div", { style: S.card },
          devices === null ? h("div", { style: S.sub }, "加载中…") :
            devices.length === 0 ? h("div", { style: S.sub }, "暂无已配对设备。") :
              devices.map(function (d) {
                return h("div", { key: d.deviceId, style: Object.assign({}, S.row, { justifyContent: "space-between", marginBottom: 8 }) },
                  h("div", { style: S.label }, d.name || d.deviceId),
                  h("button", { style: S.btn, disabled: busyId === d.deviceId, onClick: function () { revoke(d.deviceId); } }, busyId === d.deviceId ? "撤销中…" : "撤销"));
              })
        )
      );
    }

    // ---------- 配对码（v2.1.0）：PC 生成随机码，手机 App 输入即完成配对 ----------
    function PairCodeCard() {
      var [pc, setPc] = useState(null);
      var [busy, setBusy] = useState(false);
      var [left, setLeft] = useState(0);
      var timer = useRef(null);

      function arm(r) {
        setPc(r);
        setLeft(Math.floor(r.expiresInSec || 600));
        if (timer.current) clearInterval(timer.current);
        timer.current = setInterval(function () {
          setLeft(function (prev) {
            if (prev <= 1) {
              if (timer.current) clearInterval(timer.current);
              timer.current = null;
              // 服务端 ensureActiveCode 会自动生成新码：倒计时归零后自动拉当前码重新展示，
              // 保证设置页「始终有码可填」，不用手动刷新/点生成。
              fetchJson("/api/remote-access/pair/code/current")
                .then(function (nr) { if (nr && nr.ok && nr.code) arm(nr); })
                .catch(function () { setPc(null); });
              return 0;
            }
            return prev - 1;
          });
        }, 1000);
      }

      function generate() {
        setBusy(true);
        fetchJson("/api/remote-access/pair/code/generate", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{}",
        })
          .then(function (r) { if (r && r.ok && r.code) arm(r); })
          .catch(function () {})
          .finally(function () { setBusy(false); });
      }

      // 打开设置页即自动展示当前配对码（服务端 ensureActiveCode 保证始终有码）
      useEffect(function () {
        fetchJson("/api/remote-access/pair/code/current")
          .then(function (r) { if (r && r.ok && r.code) arm(r); })
          .catch(function () {});
        return function () { if (timer.current) clearInterval(timer.current); };
      }, []);

      var mm = String(Math.floor(left / 60)).padStart(2, "0");
      var ss = String(left % 60).padStart(2, "0");

      return h("div", { style: S.card },
        h("div", { style: S.row },
          h("div", { style: S.title }, "直连配对码"),
          pc ? h("span", { style: Object.assign({}, S.badge, { background: "rgba(52,199,89,.15)", color: "var(--dsw-alias-state-success-primary)" }) }, "有效 " + mm + ":" + ss) : null),
        h("div", { style: S.sub }, "生成一个 6 位随机配对码，手机直连本电脑后输入即可配对。服务器连接请使用“添加手机”。"),
        pc ? h("div", { style: Object.assign({}, S.mono, { fontSize: 30, fontWeight: 700, letterSpacing: "0.4em", color: "var(--dsw-alias-brand-primary)", marginTop: 8, marginBottom: 2 }) }, pc.code) : null,
        pc ? h("div", { style: S.sub }, "10 分钟有效 · 最多试错 5 次 · 验证通过立即作废") : null,
        h("div", { style: Object.assign({}, S.row, { marginTop: 10 }) },
          h("button", { style: S.btnPrimary, disabled: busy, onClick: generate }, busy ? "生成中…" : pc ? "重新生成" : "生成配对码"))
      );
    }

    // ---------- 公网域名信任白名单（v2.4.0）：写入 cordis.patch.yml 的 connection 行（!!js 拼接 ctx.webRuntime.trustedHosts） ----------
    function TrustedHostsCard() {
      var [hosts, setHosts] = useState(null);
      var [input, setInput] = useState("");
      var [busy, setBusy] = useState(false);
      var [err, setErr] = useState(null);

      function refresh() {
        fetchJson("/api/remote-access/trusted-hosts")
          .then(function (r) {
            if (r && r.ok) { setHosts(r.hosts || []); setErr(null); }
            else setErr((r && r.error) || "加载失败");
          })
          .catch(function () { setErr("无法连接插件后端（请检查 DeepSeek Harness 是否运行及插件是否已加载）"); });
      }
      useEffect(function () { refresh(); }, []);

      function mutate(action, host) {
        setBusy(true);
        setErr(null);
        fetchJson("/api/remote-access/trusted-hosts", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: action, host: host }),
        })
          .then(function (r) {
            if (r && r.ok) {
              setHosts(r.hosts || []);
              if (action === "add") setInput("");
            } else {
              setErr((r && r.error) || "操作失败");
            }
          })
          .catch(function () { setErr("请求失败"); })
          .finally(function () { setBusy(false); });
      }

      return h("div", { style: S.card },
        h("div", { style: S.row },
          h("div", { style: S.title }, "公网域名白名单"),
          hosts !== null ? h("span", { style: Object.assign({}, S.badge, { background: "rgba(142,142,147,.15)", color: "var(--dsw-alias-label-secondary)" }) }, "共 " + hosts.length + " 条") : null),
        h("div", { style: S.sub }, "手机通过公网隧道连接时，DeepSeek Harness 核心校验请求的 Host 域名；把隧道的公网域名加进来手机才能连上。填「域名」或「域名:端口」（不带 https://；中文域名请用 punycode）。"),
        hosts === null ? h("div", { style: S.sub }, "加载中…") :
          hosts.length === 0 ? h("div", { style: S.sub }, "暂无白名单条目。") :
            hosts.map(function (x) {
              return h("div", { key: x, style: Object.assign({}, S.row, { justifyContent: "space-between", marginBottom: 6 }) },
                h("span", { style: S.mono }, x),
                h("button", { style: S.btn, disabled: busy, onClick: function () { mutate("remove", x); } }, "删除"));
            }),
        h("div", { style: Object.assign({}, S.row, { marginTop: 10 }) },
          h("input", { style: Object.assign({}, S.input, { width: 230 }), placeholder: "如 tunnel.example.com 或 域名:8080", value: input, onChange: function (e) { setInput(e.target.value); } }),
          h("button", { style: S.btnPrimary, disabled: busy || !input.trim(), onClick: function () { mutate("add", input.trim()); } }, busy ? "处理中…" : "添加")),
        err ? h("div", { style: Object.assign({}, S.err, { marginTop: 6 }) }, err) : null,
        h("div", { style: S.sub, marginTop: 6 }, "白名单写入 connection 行的 trustedHosts（拼回 LAN 地址与 --trusted-host），修改后立即生效（DeepSeek Harness 热监视 cordis.patch.yml）；如未生效请重启 DeepSeek Harness。白名单只解决 Host 校验，手机请求仍需配对拿到通道 token。")
      );
    }

    function RelayCard() {
      var [config, setConfig] = useState(null);
      var [busy, setBusy] = useState(false);
      var [error, setError] = useState("");
      var [status, setStatus] = useState("");
      var [pairing, setPairing] = useState(null);
      var [now, setNow] = useState(Date.now());
      var [advanced, setAdvanced] = useState(false);
      var labels = {disabled:"未启用", registering:"正在注册电脑", connecting:"连接中", connected:"已连接", reconnecting:"正在重连", error:"连接失败"};
      function refresh(initial) {
        fetchJson("/api/remote-access/relay/config").then(function(r) {
          if (!r || !r.ok) throw new Error("无法读取中转设置");
          if (initial) setConfig(r);
          setStatus((labels[r.state] || r.state) + (r.detail ? " · " + r.detail : ""));
        }).catch(function() { setStatus("无法读取连接状态"); });
      }
      useEffect(function() {
        refresh(true);
        var timer = setInterval(function() { setNow(Date.now()); refresh(false); }, 3000);
        return function() { clearInterval(timer); };
      }, []);
      function change(key, value) { setConfig(function(c) { return Object.assign({}, c, { [key]: value }); }); }
      function save() {
        setBusy(true); setError(""); setPairing(null);
        fetchJson("/api/remote-access/relay/config", {
          method:"POST", headers:{"Content-Type":"application/json"},
          body: JSON.stringify({enabled:config.enabled, serverUrl:config.serverUrl, computerName:config.computerName,
            caFile:config.caFile, allowInsecureLoopback:config.allowInsecureLoopback})
        }).then(function(r) {
          if (!r || !r.ok) { setError(r && r.error || "保存失败"); return; }
          refresh(true);
        }).catch(function() { setError("保存失败，请检查电脑端连接"); }).finally(function() { setBusy(false); });
      }
      function addPhone() {
        setBusy(true); setError(""); setPairing(null);
        fetchJson("/api/remote-access/relay/pairing", {method:"POST", headers:{"Content-Type":"application/json"}, body:"{}"})
          .then(function(r) { if (!r || !r.ok) throw new Error(r && r.error || "无法生成二维码"); setPairing(r); setNow(Date.now()); })
          .catch(function(e) { setError(e.message || "无法生成二维码"); }).finally(function() { setBusy(false); });
      }
      function field(label, key, placeholder) {
        return h("label", {style:{display:"block", marginTop:10}},
          h("div", {style:S.sub}, label),
          h("input", {style:Object.assign({}, S.input, {width:"100%", boxSizing:"border-box"}),
            value:config[key] || "", placeholder:placeholder, disabled:busy,
            onChange:function(e) { change(key,e.target.value); }}));
      }
      return h("div", {style:S.card},
        h("div", {style:S.title}, "服务器"),
        h("div", {style:S.sub}, "填写服务器地址并启用，电脑身份自动注册。手机扫描“添加手机”生成的二维码，再在电脑确认。每台手机分别配对，可同时控制这台电脑。"),
        h("div", {style:S.sub, role:"status"}, status || "加载中…"),
        config ? h("div", {},
          h("label", {style:S.row}, h("input", {type:"checkbox", checked:config.enabled, disabled:busy,
            onChange:function(e) { change("enabled",e.target.checked); }}), "启用在线服务"),
          field("服务器地址", "serverUrl", "https://relay.example.com:8443"),
          field("电脑名称", "computerName", "电脑"),
          h("button", {style:S.btn, onClick:function() { setAdvanced(!advanced); }}, advanced ? "收起高级设置" : "高级设置"),
          advanced ? h("div", {},
          field("自定义 CA 证书路径（可选）", "caFile", "使用公开受信证书时留空")) : null,
          h("div", {style:Object.assign({},S.row,{marginTop:12})},
            h("button", {style:S.btnPrimary, disabled:busy, onClick:save}, busy ? "处理中…" : "保存中转设置"),
            h("button", {style:S.btn, disabled:busy, onClick:addPhone}, "添加手机")),
          pairing ? h("div", {style:{marginTop:12}},
            pairing.expiresAt > now ? h("div", {},
              h("img", {src:pairing.image, alt:"手机配对二维码", style:{width:300,maxWidth:"100%",height:"auto"}}),
              h("div", {style:S.sub}, "用手机 App 扫码，随后在电脑确认。每个二维码仅供一台手机使用，有效期 2 分钟。"),
              h("div", {style:S.sub}, "无法扫码时，复制下面的连接信息，在手机首页选择“粘贴连接信息”。"),
              h("textarea", {readOnly:true, value:pairing.link, "aria-label":"临时连接信息", style:Object.assign({},S.input,{width:"100%",boxSizing:"border-box"}), onFocus:function(e) { e.target.select(); }}))
              : h("div", {style:S.sub}, "二维码已过期，请重新点击“添加手机”。")) : null
        ) : null,
        error ? h("div", {style:S.err, role:"alert"}, error) : null);
    }

    // ---------- 设置页「远程控制」：远程互信认证 ----------
    function TrustSection() {
      return h("div", { style: S.root },
        h("div", { style: S.row },
          h("div", { style: S.title }, "远程身份认证"),
          h("span", { style: Object.assign({}, S.badge, { background: "rgba(52,199,89,.15)", color: "var(--dsw-alias-state-success-primary)" }) }, "● 已启用")),
        h("div", { style: S.sub }, "通过直连或服务器连接。服务器连接需扫码或粘贴临时连接信息后在电脑确认；直连可使用配对码或本机确认。"),
        h("div", { style: S.ok }, "设备管理和中转配置仅允许在本机操作；手机配对后获得独立访问权限。"),
        h(RelayCard, {}),
        h(PairCodeCard, {}),
        h(TrustedHostsCard, {}),
        h("hr", { style: S.divider }),
        h(PairSection, {})
      );
    }

    function apply(ctx) {
      ctx.slots.inject("settings.section", function () {
        return ctx.slots.register(
          { name: "settings.section", id: "dsh-remote-access", order: 40, label: function () { return "远程控制"; } },
          TrustSection
        );
      });
    }

    exports.apply = apply;
    exports.inject = ["slots"];
    return module.exports;
  }
});
