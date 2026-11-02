import { Duplex } from 'node:stream';
import WebSocket from 'ws';
export declare const MAX_FRAME = 131072;
/** Version 1: bounded, credit-controlled TCP streams over one authenticated WebSocket. */
export declare class Tunnel {
    readonly ws: WebSocket;
    private accept?;
    readonly channels: Map<string, Channel>;
    private heartbeat;
    private alive;
    constructor(ws: WebSocket, accept?: (channel: Duplex) => void);
    open(): Duplex;
    send(message: unknown): void;
    destroy(): void;
}
declare class Channel extends Duplex {
    private tunnel;
    readonly id: string;
    private available;
    private allowance;
    private withheld;
    private waiting?;
    constructor(tunnel: Tunnel, id: string);
    receive(data: Buffer): void;
    private grant;
    _read(): void;
    credit(bytes: number): void;
    _write(data: Buffer, _: string, done: (e?: Error | null) => void): void;
    private flush;
    _final(done: (e?: Error | null) => void): void;
    _destroy(error: Error | null, done: (e?: Error | null) => void): void;
}
export {};
