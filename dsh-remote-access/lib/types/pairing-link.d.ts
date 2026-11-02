export type PairingPayload = {
    v: number;
    server: string;
    hostId: string;
    computerId: string;
    name: string;
    ticket: string;
    expiresAt: number;
};
/** Compact v2 transport; preserves every v1 field and the full 256-bit pairing ticket. */
export declare function encodePairingLink(payload: PairingPayload): string;
