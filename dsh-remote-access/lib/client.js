(() => {
  // src/client.ts
  window.__ModuleLoader__.load({
    id: "dsh-remote-access",
    factory: function(require2) {
      var module = { exports: {} };
      var exports = module.exports;
      var react = require2("react");
      var h = react.createElement;
      var useState = react.useState;
      var useEffect = react.useEffect;
      var useRef = react.useRef;
      var S = {
        root: { display: "flex", flexDirection: "column", gap: "12px", padding: "4px 0" },
        card: {
          border: "1px solid var(--dsw-alias-border-l1)",
          borderRadius: "10px",
          background: "var(--dsw-alias-bg-layer-1)",
          padding: "14px"
        },
        row: { display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" },
        title: { fontSize: 13, fontWeight: 600, color: "var(--dsw-alias-label-primary)" },
        label: { fontSize: 12, color: "var(--dsw-alias-label-secondary)", lineHeight: 1.6 },
        sub: { fontSize: 12, color: "var(--dsw-alias-label-secondary)", lineHeight: 1.7 },
        mono: { fontFamily: "ui-monospace, monospace", fontSize: 12, color: "var(--dsw-alias-label-secondary)", wordBreak: "break-all" },
        input: {
          border: "1px solid var(--dsw-alias-border-l1)",
          borderRadius: "6px",
          background: "var(--dsw-alias-bg-layer-1)",
          color: "var(--dsw-alias-label-primary)",
          padding: "5px 10px",
          fontSize: 12,
          width: 140
        },
        err: { fontSize: 12, color: "var(--dsw-alias-state-error-primary)", lineHeight: 1.6 },
        btn: {
          border: "1px solid var(--dsw-alias-border-l1)",
          borderRadius: "6px",
          background: "transparent",
          color: "var(--dsw-alias-label-primary)",
          padding: "5px 12px",
          fontSize: 12,
          cursor: "pointer"
        },
        btnPrimary: {
          border: "1px solid var(--dsw-alias-brand-primary)",
          borderRadius: "6px",
          background: "var(--dsw-alias-brand-primary)",
          color: "#0D1B2A",
          padding: "6px 14px",
          fontSize: 12,
          fontWeight: 600,
          cursor: "pointer"
        },
        ok: { fontSize: 12, color: "var(--dsw-alias-state-success-primary)", lineHeight: 1.6 },
        badge: {
          display: "inline-block",
          padding: "2px 8px",
          borderRadius: "10px",
          fontSize: 11,
          fontWeight: 600
        },
        divider: { border: "none", borderTop: "1px dashed var(--dsw-alias-border-l1)", margin: "4px 0 0" }
      };
      function fetchJson(path, opts) {
        return fetch(path, opts).then(function(r) {
          return r.json();
        });
      }
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
          "position:fixed",
          "inset:0",
          "z-index:2147483000",
          "display:flex",
          "align-items:center",
          "justify-content:center",
          "background:rgba(13,27,42,.62)",
          "backdrop-filter:blur(4px)",
          "padding:20px"
        ].join(";");
        var card = document.createElement("div");
        card.style.cssText = [
          "background:var(--dsw-alias-bg-layer-1)",
          "border:1px solid var(--dsw-alias-border-l1)",
          "border-radius:14px",
          "padding:24px",
          "max-width:380px",
          "width:100%",
          "box-shadow:0 20px 60px rgba(0,0,0,.5)",
          "color:var(--dsw-alias-label-primary)"
        ].join(";");
        var title = document.createElement("div");
        title.textContent = "\u624B\u673A\u8BBE\u5907\u300C" + pendingDevice.name + "\u300D\u8BF7\u6C42\u9996\u6B21\u8FDE\u63A5";
        title.style.cssText = "font-size:15px;font-weight:600;line-height:1.5;margin-bottom:8px";
        var sub = document.createElement("div");
        sub.textContent = "\u5141\u8BB8\u8BE5\u8BBE\u5907\u8FDC\u7A0B\u63A7\u5236\u8FD9\u53F0\u7535\u8111\uFF1F";
        sub.style.cssText = "font-size:13px;color:var(--dsw-alias-label-secondary);line-height:1.6;margin-bottom:18px";
        var row = document.createElement("div");
        row.style.cssText = "display:flex;gap:10px;justify-content:flex-end";
        function mkBtn(label, primary) {
          var b = document.createElement("button");
          b.textContent = label;
          b.style.cssText = primary ? "border:1px solid var(--dsw-alias-brand-primary);border-radius:6px;background:var(--dsw-alias-brand-primary);color:#0D1B2A;padding:7px 16px;font-size:13px;font-weight:600;cursor:pointer" : "border:1px solid var(--dsw-alias-border-l1);border-radius:6px;background:transparent;color:var(--dsw-alias-label-primary);padding:7px 16px;font-size:13px;cursor:pointer";
          return b;
        }
        var allowBtn = mkBtn("\u5141\u8BB8", true);
        var denyBtn = mkBtn("\u62D2\u7EDD", false);
        allowBtn.onclick = function() {
          allowBtn.disabled = true;
          denyBtn.disabled = true;
          respond(pendingDevice.deviceId, "approve");
        };
        denyBtn.onclick = function() {
          allowBtn.disabled = true;
          denyBtn.disabled = true;
          respond(pendingDevice.deviceId, "deny");
        };
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
          fetchJson("/api/remote-access/pair/list").then(function(r) {
            if (r && r.ok) setDevices(r.devices || []);
          }).catch(function() {
          });
        }
        function respond(deviceId, outcome) {
          fetchJson("/api/remote-access/pair/respond", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ deviceId, outcome })
          }).then(function() {
            removePairDialog();
            refreshList();
          }).catch(function() {
            removePairDialog();
          });
        }
        function pollStatus() {
          fetchJson("/api/remote-access/pair/status").then(function(r) {
            if (!r || !r.ok) return;
            var pendingDevice = r.pendingDevice || null;
            if (r.state === "pending" && pendingDevice && !pairDialogShown) {
              showPairDialog(pendingDevice, respond);
            } else if (r.state !== "pending" && pairDialogShown) {
              removePairDialog();
            }
          }).catch(function() {
          });
        }
        useEffect(function() {
          refreshList();
          pollStatus();
          pairTimer.current = setInterval(pollStatus, 2e3);
          return function() {
            clearInterval(pairTimer.current);
            removePairDialog();
          };
        }, []);
        function revoke(deviceId) {
          setBusyId(deviceId);
          fetchJson("/api/remote-access/pair/revoke", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ deviceId })
          }).then(function() {
            refreshList();
          }).catch(function() {
          }).finally(function() {
            setBusyId(null);
          });
        }
        return h(
          "div",
          { style: S.root },
          h("div", { style: S.title }, "\u914D\u5BF9\u8BBE\u5907\u7BA1\u7406"),
          h("div", { style: S.sub }, "\u624B\u673A\u901A\u8FC7\u670D\u52A1\u5668\u8FDE\u63A5\u65F6\uFF0C\u5148\u626B\u7801\u6216\u7C98\u8D34\u8FDE\u63A5\u4FE1\u606F\uFF0C\u518D\u5728\u7535\u8111\u786E\u8BA4\uFF1B\u76F4\u8FDE\u53EF\u4F7F\u7528\u914D\u5BF9\u7801\u6216\u672C\u673A\u786E\u8BA4\u3002\u64A4\u9500\u8BBE\u5907\u540E\u7ACB\u5373\u65AD\u5F00\uFF0C\u9700\u91CD\u65B0\u914D\u5BF9\u3002"),
          h(
            "div",
            { style: S.card },
            devices === null ? h("div", { style: S.sub }, "\u52A0\u8F7D\u4E2D\u2026") : devices.length === 0 ? h("div", { style: S.sub }, "\u6682\u65E0\u5DF2\u914D\u5BF9\u8BBE\u5907\u3002") : devices.map(function(d) {
              return h(
                "div",
                { key: d.deviceId, style: Object.assign({}, S.row, { justifyContent: "space-between", marginBottom: 8 }) },
                h("div", { style: S.label }, d.name || d.deviceId),
                h("button", { style: S.btn, disabled: busyId === d.deviceId, onClick: function() {
                  revoke(d.deviceId);
                } }, busyId === d.deviceId ? "\u64A4\u9500\u4E2D\u2026" : "\u64A4\u9500")
              );
            })
          )
        );
      }
      function PairCodeCard() {
        var [pc, setPc] = useState(null);
        var [busy, setBusy] = useState(false);
        var [left, setLeft] = useState(0);
        var timer = useRef(null);
        function arm(r) {
          setPc(r);
          setLeft(Math.floor(r.expiresInSec || 600));
          if (timer.current) clearInterval(timer.current);
          timer.current = setInterval(function() {
            setLeft(function(prev) {
              if (prev <= 1) {
                if (timer.current) clearInterval(timer.current);
                timer.current = null;
                fetchJson("/api/remote-access/pair/code/current").then(function(nr) {
                  if (nr && nr.ok && nr.code) arm(nr);
                }).catch(function() {
                  setPc(null);
                });
                return 0;
              }
              return prev - 1;
            });
          }, 1e3);
        }
        function generate() {
          setBusy(true);
          fetchJson("/api/remote-access/pair/code/generate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}"
          }).then(function(r) {
            if (r && r.ok && r.code) arm(r);
          }).catch(function() {
          }).finally(function() {
            setBusy(false);
          });
        }
        useEffect(function() {
          fetchJson("/api/remote-access/pair/code/current").then(function(r) {
            if (r && r.ok && r.code) arm(r);
          }).catch(function() {
          });
          return function() {
            if (timer.current) clearInterval(timer.current);
          };
        }, []);
        var mm = String(Math.floor(left / 60)).padStart(2, "0");
        var ss = String(left % 60).padStart(2, "0");
        return h(
          "div",
          { style: S.card },
          h(
            "div",
            { style: S.row },
            h("div", { style: S.title }, "\u76F4\u8FDE\u914D\u5BF9\u7801"),
            pc ? h("span", { style: Object.assign({}, S.badge, { background: "rgba(52,199,89,.15)", color: "var(--dsw-alias-state-success-primary)" }) }, "\u6709\u6548 " + mm + ":" + ss) : null
          ),
          h("div", { style: S.sub }, "\u751F\u6210\u4E00\u4E2A 6 \u4F4D\u968F\u673A\u914D\u5BF9\u7801\uFF0C\u624B\u673A\u76F4\u8FDE\u672C\u7535\u8111\u540E\u8F93\u5165\u5373\u53EF\u914D\u5BF9\u3002\u670D\u52A1\u5668\u8FDE\u63A5\u8BF7\u4F7F\u7528\u201C\u6DFB\u52A0\u624B\u673A\u201D\u3002"),
          pc ? h("div", { style: Object.assign({}, S.mono, { fontSize: 30, fontWeight: 700, letterSpacing: "0.4em", color: "var(--dsw-alias-brand-primary)", marginTop: 8, marginBottom: 2 }) }, pc.code) : null,
          pc ? h("div", { style: S.sub }, "10 \u5206\u949F\u6709\u6548 \xB7 \u6700\u591A\u8BD5\u9519 5 \u6B21 \xB7 \u9A8C\u8BC1\u901A\u8FC7\u7ACB\u5373\u4F5C\u5E9F") : null,
          h(
            "div",
            { style: Object.assign({}, S.row, { marginTop: 10 }) },
            h("button", { style: S.btnPrimary, disabled: busy, onClick: generate }, busy ? "\u751F\u6210\u4E2D\u2026" : pc ? "\u91CD\u65B0\u751F\u6210" : "\u751F\u6210\u914D\u5BF9\u7801")
          )
        );
      }
      function TrustedHostsCard() {
        var [hosts, setHosts] = useState(null);
        var [input, setInput] = useState("");
        var [busy, setBusy] = useState(false);
        var [err, setErr] = useState(null);
        function refresh() {
          fetchJson("/api/remote-access/trusted-hosts").then(function(r) {
            if (r && r.ok) {
              setHosts(r.hosts || []);
              setErr(null);
            } else setErr(r && r.error || "\u52A0\u8F7D\u5931\u8D25");
          }).catch(function() {
            setErr("\u65E0\u6CD5\u8FDE\u63A5\u63D2\u4EF6\u540E\u7AEF\uFF08\u8BF7\u68C0\u67E5 DeepSeek Harness \u662F\u5426\u8FD0\u884C\u53CA\u63D2\u4EF6\u662F\u5426\u5DF2\u52A0\u8F7D\uFF09");
          });
        }
        useEffect(function() {
          refresh();
        }, []);
        function mutate(action, host) {
          setBusy(true);
          setErr(null);
          fetchJson("/api/remote-access/trusted-hosts", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ action, host })
          }).then(function(r) {
            if (r && r.ok) {
              setHosts(r.hosts || []);
              if (action === "add") setInput("");
            } else {
              setErr(r && r.error || "\u64CD\u4F5C\u5931\u8D25");
            }
          }).catch(function() {
            setErr("\u8BF7\u6C42\u5931\u8D25");
          }).finally(function() {
            setBusy(false);
          });
        }
        return h(
          "div",
          { style: S.card },
          h(
            "div",
            { style: S.row },
            h("div", { style: S.title }, "\u516C\u7F51\u57DF\u540D\u767D\u540D\u5355"),
            hosts !== null ? h("span", { style: Object.assign({}, S.badge, { background: "rgba(142,142,147,.15)", color: "var(--dsw-alias-label-secondary)" }) }, "\u5171 " + hosts.length + " \u6761") : null
          ),
          h("div", { style: S.sub }, "\u624B\u673A\u901A\u8FC7\u516C\u7F51\u96A7\u9053\u8FDE\u63A5\u65F6\uFF0CDeepSeek Harness \u6838\u5FC3\u6821\u9A8C\u8BF7\u6C42\u7684 Host \u57DF\u540D\uFF1B\u628A\u96A7\u9053\u7684\u516C\u7F51\u57DF\u540D\u52A0\u8FDB\u6765\u624B\u673A\u624D\u80FD\u8FDE\u4E0A\u3002\u586B\u300C\u57DF\u540D\u300D\u6216\u300C\u57DF\u540D:\u7AEF\u53E3\u300D\uFF08\u4E0D\u5E26 https://\uFF1B\u4E2D\u6587\u57DF\u540D\u8BF7\u7528 punycode\uFF09\u3002"),
          hosts === null ? h("div", { style: S.sub }, "\u52A0\u8F7D\u4E2D\u2026") : hosts.length === 0 ? h("div", { style: S.sub }, "\u6682\u65E0\u767D\u540D\u5355\u6761\u76EE\u3002") : hosts.map(function(x) {
            return h(
              "div",
              { key: x, style: Object.assign({}, S.row, { justifyContent: "space-between", marginBottom: 6 }) },
              h("span", { style: S.mono }, x),
              h("button", { style: S.btn, disabled: busy, onClick: function() {
                mutate("remove", x);
              } }, "\u5220\u9664")
            );
          }),
          h(
            "div",
            { style: Object.assign({}, S.row, { marginTop: 10 }) },
            h("input", { style: Object.assign({}, S.input, { width: 230 }), placeholder: "\u5982 tunnel.example.com \u6216 \u57DF\u540D:8080", value: input, onChange: function(e) {
              setInput(e.target.value);
            } }),
            h("button", { style: S.btnPrimary, disabled: busy || !input.trim(), onClick: function() {
              mutate("add", input.trim());
            } }, busy ? "\u5904\u7406\u4E2D\u2026" : "\u6DFB\u52A0")
          ),
          err ? h("div", { style: Object.assign({}, S.err, { marginTop: 6 }) }, err) : null,
          h("div", { style: S.sub, marginTop: 6 }, "\u767D\u540D\u5355\u5199\u5165 connection \u884C\u7684 trustedHosts\uFF08\u62FC\u56DE LAN \u5730\u5740\u4E0E --trusted-host\uFF09\uFF0C\u4FEE\u6539\u540E\u7ACB\u5373\u751F\u6548\uFF08DeepSeek Harness \u70ED\u76D1\u89C6 cordis.patch.yml\uFF09\uFF1B\u5982\u672A\u751F\u6548\u8BF7\u91CD\u542F DeepSeek Harness\u3002\u767D\u540D\u5355\u53EA\u89E3\u51B3 Host \u6821\u9A8C\uFF0C\u624B\u673A\u8BF7\u6C42\u4ECD\u9700\u914D\u5BF9\u62FF\u5230\u901A\u9053 token\u3002")
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
        var labels = { disabled: "\u672A\u542F\u7528", registering: "\u6B63\u5728\u6CE8\u518C\u7535\u8111", connecting: "\u8FDE\u63A5\u4E2D", connected: "\u5DF2\u8FDE\u63A5", reconnecting: "\u6B63\u5728\u91CD\u8FDE", error: "\u8FDE\u63A5\u5931\u8D25" };
        function refresh(initial) {
          fetchJson("/api/remote-access/relay/config").then(function(r) {
            if (!r || !r.ok) throw new Error("\u65E0\u6CD5\u8BFB\u53D6\u4E2D\u8F6C\u8BBE\u7F6E");
            if (initial) setConfig(r);
            setStatus((labels[r.state] || r.state) + (r.detail ? " \xB7 " + r.detail : ""));
          }).catch(function() {
            setStatus("\u65E0\u6CD5\u8BFB\u53D6\u8FDE\u63A5\u72B6\u6001");
          });
        }
        useEffect(function() {
          refresh(true);
          var timer = setInterval(function() {
            setNow(Date.now());
            refresh(false);
          }, 3e3);
          return function() {
            clearInterval(timer);
          };
        }, []);
        function change(key, value) {
          setConfig(function(c) {
            return Object.assign({}, c, { [key]: value });
          });
        }
        function save() {
          setBusy(true);
          setError("");
          setPairing(null);
          fetchJson("/api/remote-access/relay/config", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              enabled: config.enabled,
              serverUrl: config.serverUrl,
              computerName: config.computerName,
              caFile: config.caFile,
              allowInsecureLoopback: config.allowInsecureLoopback
            })
          }).then(function(r) {
            if (!r || !r.ok) {
              setError(r && r.error || "\u4FDD\u5B58\u5931\u8D25");
              return;
            }
            refresh(true);
          }).catch(function() {
            setError("\u4FDD\u5B58\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u7535\u8111\u7AEF\u8FDE\u63A5");
          }).finally(function() {
            setBusy(false);
          });
        }
        function addPhone() {
          setBusy(true);
          setError("");
          setPairing(null);
          fetchJson("/api/remote-access/relay/pairing", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" }).then(function(r) {
            if (!r || !r.ok) throw new Error(r && r.error || "\u65E0\u6CD5\u751F\u6210\u4E8C\u7EF4\u7801");
            setPairing(r);
            setNow(Date.now());
          }).catch(function(e) {
            setError(e.message || "\u65E0\u6CD5\u751F\u6210\u4E8C\u7EF4\u7801");
          }).finally(function() {
            setBusy(false);
          });
        }
        function field(label, key, placeholder) {
          return h(
            "label",
            { style: { display: "block", marginTop: 10 } },
            h("div", { style: S.sub }, label),
            h("input", {
              style: Object.assign({}, S.input, { width: "100%", boxSizing: "border-box" }),
              value: config[key] || "",
              placeholder,
              disabled: busy,
              onChange: function(e) {
                change(key, e.target.value);
              }
            })
          );
        }
        return h(
          "div",
          { style: S.card },
          h("div", { style: S.title }, "\u670D\u52A1\u5668"),
          h("div", { style: S.sub }, "\u586B\u5199\u670D\u52A1\u5668\u5730\u5740\u5E76\u542F\u7528\uFF0C\u7535\u8111\u8EAB\u4EFD\u81EA\u52A8\u6CE8\u518C\u3002\u624B\u673A\u626B\u63CF\u201C\u6DFB\u52A0\u624B\u673A\u201D\u751F\u6210\u7684\u4E8C\u7EF4\u7801\uFF0C\u518D\u5728\u7535\u8111\u786E\u8BA4\u3002\u6BCF\u53F0\u624B\u673A\u5206\u522B\u914D\u5BF9\uFF0C\u53EF\u540C\u65F6\u63A7\u5236\u8FD9\u53F0\u7535\u8111\u3002"),
          h("div", { style: S.sub, role: "status" }, status || "\u52A0\u8F7D\u4E2D\u2026"),
          config ? h(
            "div",
            {},
            h("label", { style: S.row }, h("input", {
              type: "checkbox",
              checked: config.enabled,
              disabled: busy,
              onChange: function(e) {
                change("enabled", e.target.checked);
              }
            }), "\u542F\u7528\u5728\u7EBF\u670D\u52A1"),
            field("\u670D\u52A1\u5668\u5730\u5740", "serverUrl", "https://relay.example.com:8443"),
            field("\u7535\u8111\u540D\u79F0", "computerName", "\u7535\u8111"),
            h("button", { style: S.btn, onClick: function() {
              setAdvanced(!advanced);
            } }, advanced ? "\u6536\u8D77\u9AD8\u7EA7\u8BBE\u7F6E" : "\u9AD8\u7EA7\u8BBE\u7F6E"),
            advanced ? h(
              "div",
              {},
              field("\u81EA\u5B9A\u4E49 CA \u8BC1\u4E66\u8DEF\u5F84\uFF08\u53EF\u9009\uFF09", "caFile", "\u4F7F\u7528\u516C\u5F00\u53D7\u4FE1\u8BC1\u4E66\u65F6\u7559\u7A7A")
            ) : null,
            h(
              "div",
              { style: Object.assign({}, S.row, { marginTop: 12 }) },
              h("button", { style: S.btnPrimary, disabled: busy, onClick: save }, busy ? "\u5904\u7406\u4E2D\u2026" : "\u4FDD\u5B58\u4E2D\u8F6C\u8BBE\u7F6E"),
              h("button", { style: S.btn, disabled: busy, onClick: addPhone }, "\u6DFB\u52A0\u624B\u673A")
            ),
            pairing ? h(
              "div",
              { style: { marginTop: 12 } },
              pairing.expiresAt > now ? h(
                "div",
                {},
                h("img", { src: pairing.image, alt: "\u624B\u673A\u914D\u5BF9\u4E8C\u7EF4\u7801", style: { width: 300, maxWidth: "100%", height: "auto" } }),
                h("div", { style: S.sub }, "\u7528\u624B\u673A App \u626B\u7801\uFF0C\u968F\u540E\u5728\u7535\u8111\u786E\u8BA4\u3002\u6BCF\u4E2A\u4E8C\u7EF4\u7801\u4EC5\u4F9B\u4E00\u53F0\u624B\u673A\u4F7F\u7528\uFF0C\u6709\u6548\u671F 2 \u5206\u949F\u3002"),
                h("div", { style: S.sub }, "\u65E0\u6CD5\u626B\u7801\u65F6\uFF0C\u590D\u5236\u4E0B\u9762\u7684\u8FDE\u63A5\u4FE1\u606F\uFF0C\u5728\u624B\u673A\u9996\u9875\u9009\u62E9\u201C\u7C98\u8D34\u8FDE\u63A5\u4FE1\u606F\u201D\u3002"),
                h("textarea", { readOnly: true, value: pairing.link, "aria-label": "\u4E34\u65F6\u8FDE\u63A5\u4FE1\u606F", style: Object.assign({}, S.input, { width: "100%", boxSizing: "border-box" }), onFocus: function(e) {
                  e.target.select();
                } })
              ) : h("div", { style: S.sub }, "\u4E8C\u7EF4\u7801\u5DF2\u8FC7\u671F\uFF0C\u8BF7\u91CD\u65B0\u70B9\u51FB\u201C\u6DFB\u52A0\u624B\u673A\u201D\u3002")
            ) : null
          ) : null,
          error ? h("div", { style: S.err, role: "alert" }, error) : null
        );
      }
      function TrustSection() {
        return h(
          "div",
          { style: S.root },
          h(
            "div",
            { style: S.row },
            h("div", { style: S.title }, "\u8FDC\u7A0B\u8EAB\u4EFD\u8BA4\u8BC1"),
            h("span", { style: Object.assign({}, S.badge, { background: "rgba(52,199,89,.15)", color: "var(--dsw-alias-state-success-primary)" }) }, "\u25CF \u5DF2\u542F\u7528")
          ),
          h("div", { style: S.sub }, "\u901A\u8FC7\u76F4\u8FDE\u6216\u670D\u52A1\u5668\u8FDE\u63A5\u3002\u670D\u52A1\u5668\u8FDE\u63A5\u9700\u626B\u7801\u6216\u7C98\u8D34\u4E34\u65F6\u8FDE\u63A5\u4FE1\u606F\u540E\u5728\u7535\u8111\u786E\u8BA4\uFF1B\u76F4\u8FDE\u53EF\u4F7F\u7528\u914D\u5BF9\u7801\u6216\u672C\u673A\u786E\u8BA4\u3002"),
          h("div", { style: S.ok }, "\u8BBE\u5907\u7BA1\u7406\u548C\u4E2D\u8F6C\u914D\u7F6E\u4EC5\u5141\u8BB8\u5728\u672C\u673A\u64CD\u4F5C\uFF1B\u624B\u673A\u914D\u5BF9\u540E\u83B7\u5F97\u72EC\u7ACB\u8BBF\u95EE\u6743\u9650\u3002"),
          h(RelayCard, {}),
          h(PairCodeCard, {}),
          h(TrustedHostsCard, {}),
          h("hr", { style: S.divider }),
          h(PairSection, {})
        );
      }
      function apply(ctx) {
        ctx.slots.inject("settings.section", function() {
          return ctx.slots.register(
            { name: "settings.section", id: "dsh-remote-access", order: 40, label: function() {
              return "\u8FDC\u7A0B\u63A7\u5236";
            } },
            TrustSection
          );
        });
      }
      exports.apply = apply;
      exports.inject = ["slots"];
      return module.exports;
    }
  });
})();
