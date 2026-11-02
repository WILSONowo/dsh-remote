/** Local layout only; interactive controls come from the DSH component library. */
export const settingsCss = `
.dsh-remote { color:var(--dsw-alias-label-primary); font-family:inherit; font-size:14px; line-height:22px; min-width:0; }
.dsh-remote h2,.dsh-remote h3,.dsh-remote p { margin:0; }
.dsh-remote h2 { font-size:28px; font-weight:600; line-height:36px; }
.dsh-remote h3 { font-size:16px; font-weight:500; line-height:24px; }
.dsh-remote .remote-intro { padding:0 0 12px; }
.dsh-remote .remote-section { padding:24px 0 16px; }
.dsh-remote .remote-head,.dsh-remote .remote-row { display:flex; align-items:center; justify-content:space-between; gap:24px; }
.dsh-remote .remote-head { margin-bottom:16px; }
.dsh-remote .remote-head:last-child { margin-bottom:0; }
.dsh-remote .remote-muted { color:var(--dsw-alias-label-secondary); font-size:13px; line-height:19px; margin-top:4px; }
.dsh-remote .remote-form { display:grid; gap:16px; }
.dsh-remote .remote-field { display:grid; grid-template-columns:minmax(110px,1fr) minmax(0,2fr); align-items:center; gap:20px; }
.dsh-remote .remote-input { box-sizing:border-box; width:100%; min-width:0; border:1px solid var(--dsw-alias-border-l2); border-radius:var(--dsw-radius-md); background:var(--dsw-alias-bg-layer-1); color:var(--dsw-alias-label-primary); padding:8px 12px; font:inherit; font-size:13px; line-height:20px; }
.dsh-remote .remote-input:hover { border-color:var(--dsw-alias-border-l1); }
.dsh-remote .remote-input:focus-visible,.dsh-remote summary:focus-visible { outline:2px solid var(--dsw-alias-brand-primary); outline-offset:2px; }
.dsh-remote .remote-input:disabled { opacity:.5; }
.dsh-remote .remote-input::placeholder { color:var(--dsw-alias-label-tertiary); }
.dsh-remote .remote-actions { display:flex; flex-shrink:0; flex-wrap:wrap; align-items:center; justify-content:flex-end; gap:8px; }
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
.dsh-remote .remote-qr { display:block; width:224px; max-width:100%; height:auto; image-rendering:pixelated; margin:12px auto; border-radius:var(--dsw-radius-md); }
.dsh-remote .remote-qr-meta { text-align:center; margin:8px 0 16px; }
.dsh-remote .remote-link { resize:vertical; min-height:64px; max-height:160px; font:12px/18px ui-monospace,monospace; overflow-wrap:anywhere; }
.dsh-remote .remote-modal-copy { overflow-wrap:anywhere; }
.dsh-remote .remote-card { border:1px solid var(--dsw-alias-border-l2); border-radius:20px; padding:4px 20px; }
.dsh-remote .remote-card.remote-form { padding:20px; }
.dsh-remote .remote-device-row { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:18px 0; }
.dsh-remote .remote-device-row + .remote-device-row { border-top:1px solid var(--dsw-alias-border-l2); }
.dsh-remote .remote-device-info { display:flex; align-items:center; gap:14px; min-width:0; }
.dsh-remote .remote-device-info strong { font-size:14px; font-weight:500; }
.dsh-remote .remote-device-info .remote-muted { margin-top:2px; }
.dsh-remote .remote-icon { width:20px; height:20px; flex-shrink:0; }
.dsh-remote .remote-computer { display:flex; align-items:center; gap:8px; margin:-8px 0 12px; font-size:12px; color:var(--dsw-alias-label-secondary); overflow-wrap:anywhere; }
.dsh-remote .remote-computer .remote-icon { width:14px; height:14px; }
.dsh-remote .remote-head button,.dsh-remote .remote-device-row button { border-radius:999px; }
.dsh-remote button.remote-pill { background:var(--dsw-alias-interactive-bg-hover); border-radius:999px; }
.dsh-remote .remote-choices { display:grid; gap:12px; }
.dsh-remote button.remote-choice { display:flex; flex-direction:column; align-items:flex-start; height:auto; white-space:normal; gap:8px; padding:20px; border-radius:16px; text-align:left; }
.dsh-remote .remote-choice .remote-muted { text-align:left; }
.dsh-remote .remote-methods { justify-content:flex-start; margin:20px 0; }
.dsh-remote .remote-addresses { list-style:none; padding:0; margin:12px 0; overflow-wrap:anywhere; }
.dsh-remote .remote-addresses li { padding:8px 12px; margin-top:8px; border-radius:10px; background:var(--dsw-alias-bg-layer-1); }
.dsh-remote .remote-hosts-modal .remote-section { padding:0; }
.dsh-remote .remote-hosts-modal .remote-head { display:none; }
.dsh-remote .remote-empty { padding:18px 0; }
.dsh-remote .remote-add-body { max-height:calc(100dvh - 280px); overflow:auto; padding-right:4px; }
.dsh-remote .remote-choice-title,.dsh-remote .remote-discovery-computer { display:flex; align-items:center; gap:10px; }
.dsh-remote .remote-discovery-computer { margin:24px 0 16px; flex-wrap:wrap; }
.dsh-remote .remote-address-label { display:block; margin:16px 0 6px; }
.dsh-remote button.remote-choice { padding:14px 16px; gap:4px; }
.dsh-remote .remote-add-footer { width:100%; min-width:0; }
.dsh-remote .remote-footer-hint { text-align:left; margin:0 0 12px; }
.dsh-remote .remote-footer-row { display:flex; align-items:center; gap:8px; width:100%; }
.dsh-remote .remote-footer-copy { margin-right:auto; }
.dsh-remote .remote-footer-actions { margin-left:auto; flex-wrap:nowrap; }
.dsh-remote .remote-address-picker { position:relative; margin-bottom:16px; }
.dsh-remote .remote-address-control { display:flex; align-items:center; border:1px solid var(--dsw-alias-border-l2); border-radius:10px; background:var(--dsw-alias-bg-layer-1); min-height:40px; }
.dsh-remote .remote-address-control:focus-within { border-color:var(--dsw-alias-label-secondary); box-shadow:0 0 0 2px var(--dsw-alias-interactive-bg-hover); }
.dsh-remote .remote-address-control input { color:inherit; background:transparent; border:0; outline:none; min-width:0; width:100%; font:inherit; font-size:13px; padding:10px 12px; }
.dsh-remote .remote-address-toggle { display:grid; place-items:center; flex-shrink:0; width:36px; height:32px; margin-right:4px; border:0; border-radius:7px; color:var(--dsw-alias-label-secondary); background:transparent; cursor:pointer; }
.dsh-remote .remote-address-toggle:hover { background:var(--dsw-alias-interactive-bg-hover); }
.dsh-remote .remote-address-toggle:disabled { opacity:.4; cursor:default; }
.dsh-remote .remote-address-menu { position:absolute; z-index:10; top:calc(100% + 6px); left:0; right:0; padding:5px; border:1px solid var(--dsw-alias-border-l2); border-radius:10px; background:var(--dsw-alias-bg-layer-2); box-shadow:0 8px 24px #0004; max-height:150px; overflow:auto; }
.dsh-remote .remote-address-menu button { display:flex; align-items:center; justify-content:space-between; gap:8px; width:100%; padding:9px 10px; border:0; border-radius:6px; text-align:left; background:transparent; color:var(--dsw-alias-label-primary); font:inherit; font-size:13px; cursor:pointer; }
.dsh-remote .remote-address-menu button span:first-child { overflow-wrap:anywhere; min-width:0; }
.dsh-remote .remote-address-menu button[data-active=true],.dsh-remote .remote-address-menu button:hover { background:var(--dsw-alias-interactive-bg-hover); }
@media(max-height:650px) { .dsh-remote .remote-qr { width:180px; } }
@media(max-width:640px) {
 .dsh-remote .remote-field { grid-template-columns:1fr; gap:6px; }
 .dsh-remote .remote-head,.dsh-remote .remote-row { gap:12px; }
 .dsh-remote .remote-section { padding:16px 0; }
 .dsh-remote .remote-card { padding:4px 14px; }
 .dsh-remote .remote-device-row { flex-wrap:wrap; }
 .dsh-remote .remote-device-row>.remote-actions { margin-left:auto; }
}
`;
