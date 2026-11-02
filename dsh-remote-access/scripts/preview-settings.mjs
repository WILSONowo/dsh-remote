import http from 'node:http';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
// Actual host controls, locale and theme with synthetic API data. Loopback only.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sdk = process.env.DSH_TEST_RUNTIME;
if (!sdk) throw new Error('Set DSH_TEST_RUNTIME to the isolated @deepseek-ai SDK directory');
const dist = join(sdk, 'dsh-web-frontend/dist');
const files = readdirSync(join(dist, 'assets'));
let bundle = readFileSync(join(dist, 'assets', files.find(x => /^index-.*\.js$/.test(x))), 'utf8');
const registry = /function (\w+)\(\)\{return\{react:[^}]+"@deepseek-ai\/dsh-client-ui-primitives"[^}]+\}\}/.exec(bundle);
const boot = bundle.lastIndexOf('const Ls=globalThis.dshDesktopBoot');
if (!registry || boot < 0) throw new Error('Unsupported SDK fixture bundle; use 0.2.1-rc.5');
// Keep host libraries, omit application startup and all real host connections.
bundle = bundle.slice(0, boot) + `\nexport const modules=${registry[1]}();`;
const themeSource = readFileSync(join(sdk, 'dsh-client-ui-theme/lib/client.js'), 'utf8');
const themeCss = [...themeSource.matchAll(/var \w+_css_default = ("(?:[^"\\]|\\.)*");/g)].map(x => JSON.parse(x[1])).join('\n');
const QRCode = createRequire(join(root, 'package.json'))('qrcode');
const qr = await QRCode.toDataURL('dshremote://synthetic-preview-only');
const bootScript = `
import { modules } from '/assets/fixture-runtime.js';
const loaded = {};
window.__ModuleLoader__ = {load(module) { loaded[module.id] = module.factory(id => modules[id] || loaded[id]); }};
async function script(src) { await new Promise((resolve,reject) => { const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=reject;document.head.append(s); }); }
await script('/locale.js'); await script('/plugin.js');
const React=modules.react, h=React.createElement, disposers=[];
const locale=new loaded['@deepseek-ai/dsh-client-locale'].LocaleRuntime({emit() {},effect(fn) {disposers.push(fn());}},undefined,{languages:['en'],preference:'en'});
let registration, Settings;
loaded['dsh-remote-access'].apply({locale,effect(fn) {disposers.push(fn());},slots:{inject(name,fn){disposers.push(fn());},register(meta,component){registration=meta;Settings=component;return()=>{};}}});
const nativeFetch=window.fetch.bind(window);
let config={ok:true,enabled:false,serverUrl:'https://relay.example.test',computerName:'Studio computer',caFile:'',state:'disabled',detail:''};
let devices=[],hosts=[],pending=null,failNext=false;
window.fetch=async(url,options={})=>{
 if(!String(url).startsWith('/api/remote-access/'))return nativeFetch(url,options);
 const route=String(url).split('/api/remote-access/')[1], b=options.body?JSON.parse(options.body):null;
 let value={ok:true};
 if(failNext){failNext=false;return new Response(JSON.stringify({ok:false,error:'synthetic failure'}),{status:503});}
 if(route==='relay/config'){if(b)config={...config,...b,state:b.enabled?'connected':'disabled'};value=config;}
 else if(route==='relay/pairing')value={ok:true,link:'dshremote://synthetic-preview-only',image:${JSON.stringify(qr)},expiresAt:Date.now()+120000};
 else if(route.startsWith('pair/code/'))value={ok:true,code:route.endsWith('generate')?'987654':'123456',expiresInSec:600};
 else if(route==='pair/list')value={ok:true,devices};
 else if(route==='pair/status')value={ok:true,state:pending?'pending':'none',pendingDevice:pending};
 else if(route==='pair/respond'){if(b.outcome==='approve')devices=[...devices,pending];pending=null;}
 else if(route==='pair/revoke')devices=devices.filter(d=>d.deviceId!==b.deviceId);
 else if(route==='trusted-hosts'){if(b){if(b.host.includes('://'))value={ok:false,error:'invalid_host'};else hosts=b.action==='add'?[...hosts,b.host]:hosts.filter(x=>x!==b.host);}if(value.ok)value={ok:true,hosts};}
 return new Response(JSON.stringify(value),{status:value.ok?200:400,headers:{'Content-Type':'application/json'}});
};
function Preview(){React.useSyncExternalStore(fn=>locale.subscribe(fn),()=>locale.getSnapshot());
 return h(React.Fragment,{},h('div',{className:'preview-controls'},
 h('button',{onClick:()=>locale.setLocale('en')},'English'),h('button',{onClick:()=>locale.setLocale('zh')},'中文'),
 h('button',{onClick:()=>document.body.toggleAttribute('data-ds-dark-theme')},'Theme'),
 h('button',{onClick:()=>{pending={deviceId:'synthetic-phone',name:'Sample phone'};}},'Pair request'),
 h('button',{onClick:()=>{config={...config,state:'error',detail:'无法读取 CA 证书'};}},'Connection error'),
 h('button',{onClick:()=>{failNext=true;}},'Fail next request')),
 h('div',{className:'preview-shell'},h('nav',{},h('h2',{},locale.getLocale().active==='zh'?'设置':'Settings'),
 h('p',{},locale.getLocale().active==='zh'?'通用':'General'),h('p',{className:'selected'},registration.label())),
 h('main',{},h(Settings,{t:locale.bind(registration.locale)}))));}
modules['react-dom/client'].createRoot(document.getElementById('root')).render(h(Preview));
`;
const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Remote settings · synthetic preview</title>
${files.filter(x=>x.endsWith('.css')).map(x=>`<link rel="stylesheet" href="/assets/${x}">`).join('')}
<style>${themeCss}
body{font:14px/22px var(--dsw-font-family),sans-serif;background:var(--dsw-alias-bg-layer-1);color:var(--dsw-alias-label-primary);margin:0}.preview-controls{padding:16px;display:flex;gap:10px;flex-wrap:wrap}.preview-controls button{font:inherit;color:inherit;background:transparent;border:1px solid var(--dsw-alias-border-l2);border-radius:8px;padding:4px 10px;cursor:pointer}.preview-shell{display:flex;width:800px;max-width:calc(100vw - 32px);margin:8px auto 24px;border-radius:var(--dsw-radius-panel);background:var(--dsw-alias-bg-layer-2);min-height:740px}nav{width:188px;flex:none;padding:24px 12px;box-sizing:border-box}nav h2{font-size:16px;font-weight:500;margin:0 12px 28px}nav p{padding:9px 12px;border-radius:var(--dsw-radius-md)}nav .selected{background:var(--dsw-specific-sidebar-nav-item-active)}main{flex:1;min-width:0;padding:54px 24px 24px}@media(max-width:640px){nav{display:none}main{padding:24px 20px}.preview-shell{width:auto}}
</style></head><body data-ds-dark-theme><div id="root"></div><script type="module" src="/fixture.js"></script></body></html>`;
const server=http.createServer((req,res)=>{
 let content,type='text/javascript'; const path=new URL(req.url,'http://localhost').pathname;
 if(path==='/'){content=html;type='text/html';}
 else if(path==='/fixture.js')content=bootScript;
 else if(path==='/assets/fixture-runtime.js')content=bundle;
 else if(path==='/locale.js')content=readFileSync(join(sdk,'dsh-client-locale/lib/client.js'));
 else if(path==='/plugin.js')content=readFileSync(join(root,'lib/client.js'));
 else if(path.startsWith('/assets/')&&!path.includes('..')){
  try{content=readFileSync(join(dist,path.slice(1)));type=path.endsWith('.css')?'text/css':path.endsWith('.woff2')?'font/woff2':'text/javascript';}catch{}
 }
 if(content===undefined){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'Content-Type':type+'; charset=utf-8','Cache-Control':'no-store'});res.end(content);
});
server.listen(0,'127.0.0.1',()=>console.log('Synthetic preview: http://127.0.0.1:'+server.address().port));
