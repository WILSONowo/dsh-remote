/** Settings copy is registered with the host locale service. */
export const en = {
  nav: 'Remote control', intro: 'Connect your phone to this computer.',
  server: 'Server relay', serverHint: 'Connect through your own relay server.',
  enable: 'Enable server relay', address: 'Server address', computerName: 'Computer name', computer: 'Computer',
  advanced: 'Advanced', ca: 'CA certificate path', caHint: 'Optional for a private certificate authority.',
  save: 'Save', saving: 'Saving…', saved: 'Saved', addPhone: 'Add phone',
  disabled: 'Off', registering: 'Registering', connecting: 'Connecting', connected: 'Connected', reconnecting: 'Reconnecting', error: 'Connection failed',
  loading: 'Loading…', retry: 'Retry', close: 'Close', cancel: 'Cancel',
  qrTitle: 'Add a phone', qrHint: 'Scan with DSH Remote, then approve the request on this computer.',
  qrAlt: 'Phone pairing QR code', qrExpiry: 'One phone · Expires in {time}', qrExpired: 'Connection information expired. Generate a new code.',
  regenerate: 'Generate again', copyLink: 'Copy connection info', copied: 'Copied', copyFailed: 'Copy failed. Select and copy the connection info below.',
  connectionInfo: 'Connection info', pasteHint: 'On your phone, choose “Paste connection info”.',
  direct: 'Direct connection', directHint: 'Enter this code on a phone connected directly to this computer.',
  code: 'Pairing code', codeRules: 'Single use · 10 minutes · 5 attempts', expires: 'Expires in {time}', generate: 'Generate code', generating: 'Generating…',
  devices: 'Paired devices', devicesHint: 'Each phone has its own access. Revoking it disconnects that phone.',
  noDevices: 'No paired devices', revoke: 'Revoke', revoking: 'Revoking…',
  approveTitle: 'Pair “{name}”?', approveHint: 'Allow this phone to control this computer?', allow: 'Allow', deny: 'Deny', responding: 'Responding…',
  hosts: 'Trusted domains', hostsHint: 'For direct connections through a public tunnel. Server relay does not need this.',
  hostFormat: 'Domain or domain:port, without https://. Use Punycode for international domains.',
  domain: 'Domain', noHosts: 'No trusted domains', add: 'Add', remove: 'Remove', working: 'Working…',
  loadFailed: 'Could not load settings. Check the computer connection and try again.',
  requestFailed: 'Request failed. Try again.', saveFailed: 'Could not save. Check the server address, certificate and file permissions.',
  invalidDomain: 'Enter a valid domain or domain:port, without a URL scheme.',
  connectFirst: 'Save your settings and wait for the relay to connect.',
  legacyConfig: 'Set the server address again, then pair your phone with a new QR code.',
  certificateFailed: 'Check the server address, network and TLS certificate.',
  caFailed: 'Could not read the CA certificate. Check its path and permissions.',
  registrationDisabled: 'Computer registration is disabled. Contact the server administrator.',
  serverUpdate: 'Update the relay server to support automatic registration.',
  rateLimited: 'Too many registration requests. Try again later.',
  unavailable: 'The server is unavailable or has reached its computer limit.',
  identityMismatch: 'The server returned a different computer identity. Check the server configuration.',
  duplicate: 'This computer could not connect. Check its registration and close any duplicate plugin instance.',
};

export const zh: Record<keyof typeof en, string> = {
  nav: '远程控制', intro: '用手机连接并控制这台电脑。',
  server: '服务器中转', serverHint: '通过自建服务器连接。', enable: '启用服务器中转',
  address: '服务器地址', computerName: '电脑名称', computer: '电脑',
  advanced: '高级设置', ca: 'CA 证书路径', caHint: '使用私有证书颁发机构时填写。',
  save: '保存', saving: '保存中…', saved: '已保存', addPhone: '添加手机',
  disabled: '未启用', registering: '注册中', connecting: '连接中', connected: '已连接', reconnecting: '正在重连', error: '连接失败',
  loading: '加载中…', retry: '重试', close: '关闭', cancel: '取消',
  qrTitle: '添加手机', qrHint: '用 DSH Remote 扫码，再在这台电脑上确认。',
  qrAlt: '手机配对二维码', qrExpiry: '仅供一台手机使用 · {time} 后过期', qrExpired: '连接信息已过期，请重新生成。',
  regenerate: '重新生成', copyLink: '复制连接信息', copied: '已复制', copyFailed: '复制失败，请选中下方连接信息手动复制。',
  connectionInfo: '连接信息', pasteHint: '在手机上选择“粘贴连接信息”。',
  direct: '直连', directHint: '手机直连这台电脑后，输入下方配对码。',
  code: '配对码', codeRules: '一次有效 · 10 分钟 · 最多尝试 5 次', expires: '{time} 后过期', generate: '生成配对码', generating: '生成中…',
  devices: '已配对设备', devicesHint: '每台手机独立授权，撤销后立即断开该手机。',
  noDevices: '暂无已配对设备', revoke: '撤销', revoking: '撤销中…',
  approveTitle: '允许“{name}”配对？', approveHint: '允许这台手机远程控制本电脑？', allow: '允许', deny: '拒绝', responding: '处理中…',
  hosts: '可信域名', hostsHint: '仅用于通过公网隧道直连，服务器中转无需设置。',
  hostFormat: '填写域名或域名:端口，不带 https://；中文域名请转为 Punycode。',
  domain: '域名', noHosts: '暂无可信域名', add: '添加', remove: '删除', working: '处理中…',
  loadFailed: '加载失败，请检查电脑连接后重试。', requestFailed: '请求失败，请重试。',
  saveFailed: '保存失败，请检查服务器地址、证书和文件权限。', invalidDomain: '请填写有效的域名或域名:端口，不带协议前缀。',
  connectFirst: '请保存设置并等待服务器连接成功。', legacyConfig: '请重新填写服务器地址，再让手机扫描新二维码配对。',
  certificateFailed: '请检查服务器地址、网络和 TLS 证书。', caFailed: '无法读取 CA 证书，请检查路径和权限。',
  registrationDisabled: '服务器未开放电脑注册，请联系维护者。', serverUpdate: '请更新服务器后端以支持自动注册。',
  rateLimited: '注册请求过于频繁，请稍后重试。', unavailable: '服务器暂不可用或已达到电脑数量上限。',
  identityMismatch: '服务器返回的电脑身份不一致，请检查服务器配置。', duplicate: '电脑连接被拒绝，请检查登记状态或是否重复运行插件。',
};

/** Map server diagnostics to actionable, localized copy instead of leaking raw errors. */
export function detailKey(detail: unknown): keyof typeof en {
  const value = String(detail || '');
  if (/旧版|手动方案|配置已失效/.test(value)) return 'legacyConfig';
  if (/无法读取 CA/.test(value)) return 'caFailed';
  if (/未启用自动注册/.test(value)) return 'registrationDisabled';
  if (/版本不支持/.test(value)) return 'serverUpdate';
  if (/过于频繁/.test(value)) return 'rateLimited';
  if (/容量上限|暂不可用/.test(value)) return 'unavailable';
  if (/身份不一致/.test(value)) return 'identityMismatch';
  if (/连接被拒绝/.test(value)) return 'duplicate';
  return 'certificateFailed';
}
