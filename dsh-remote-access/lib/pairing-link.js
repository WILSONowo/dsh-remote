/** Compact v2 transport; preserves every v1 field and the full 256-bit pairing ticket. */
export function encodePairingLink(payload) {
    const server = Buffer.from(payload.server, 'utf8'), name = Buffer.from(payload.name, 'utf8');
    if (payload.v !== 1 || !/^[a-f0-9]{32}$/.test(payload.hostId) ||
        !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(payload.computerId) ||
        !/^[a-f0-9]{64}$/.test(payload.ticket) || !Number.isSafeInteger(payload.expiresAt) || payload.expiresAt <= 0 ||
        !server.length || server.length > 2048 || !payload.name.trim() || payload.name.length > 64 || /[\x00-\x1f\x7f]/.test(payload.name) || name.length > 256) {
        throw new Error('invalid_pairing_payload');
    }
    const bytes = Buffer.alloc(76 + server.length + name.length);
    bytes.writeBigUInt64BE(BigInt(payload.expiresAt), 0);
    Buffer.from(payload.hostId, 'hex').copy(bytes, 8);
    Buffer.from(payload.computerId.replaceAll('-', ''), 'hex').copy(bytes, 24);
    Buffer.from(payload.ticket, 'hex').copy(bytes, 40);
    bytes.writeUInt16BE(server.length, 72);
    server.copy(bytes, 74);
    bytes.writeUInt16BE(name.length, 74 + server.length);
    name.copy(bytes, 76 + server.length);
    return 'dshremote://pair#2.' + bytes.toString('base64url');
}
