// Decode test links independently of the production encoder; no live connection information.
export function decodePairingTestLink(link) {
  const fragment = new URL(link).hash.slice(1);
  if (!fragment.startsWith('2.')) return JSON.parse(Buffer.from(fragment, 'base64url'));
  const bytes = Buffer.from(fragment.slice(2), 'base64url');
  let offset = 72;
  const string = () => {
    const size = bytes.readUInt16BE(offset); offset += 2;
    const value = bytes.subarray(offset, offset + size).toString('utf8'); offset += size; return value;
  };
  const hex = (a, b) => bytes.subarray(a, b).toString('hex');
  const result = { v: 1, server: string(), hostId: hex(8, 24),
    computerId: [hex(24, 28), hex(28, 30), hex(30, 32), hex(32, 34), hex(34, 40)].join('-'),
    name: string(), ticket: hex(40, 72), expiresAt: Number(bytes.readBigUInt64BE(0)) };
  if (offset !== bytes.length) throw new Error('Trailing bytes');
  return result;
}
