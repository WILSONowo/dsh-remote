import type { IncomingMessage, ServerResponse } from 'node:http';
export declare const RELAY_REQUEST: unique symbol;
export declare const DEVICE_ID: unique symbol;
export declare const VALID_ID: RegExp;
export type Request = IncomingMessage & {
    [RELAY_REQUEST]?: boolean;
    [DEVICE_ID]?: string;
};
type Route = {
    kind: string;
    path: string;
    handler: (req: Request, res: ServerResponse) => unknown;
};
export declare const reply: (res: ServerResponse, status: number, body: unknown) => void;
export declare function bodyJson(req: IncomingMessage, limit?: number): Promise<any>;
export declare function isLocalAdmin(req: Request): boolean;
export declare function createSecurity(home: string, register: (route: Route) => void): {
    authorize: (req: Request) => boolean;
    track: (req: Request, target: {
        once: Function;
        destroy: () => void;
    }) => void;
    generatePairTicket: () => {
        ticket: string;
        expiresAt: number;
    };
    dispose: () => void;
};
export {};
