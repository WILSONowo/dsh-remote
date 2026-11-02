import http from 'node:http';
type Config = {
    enabled: boolean;
    serverUrl: string;
    hostId: string;
    registrationToken: string;
    registrationMode: 'automatic';
    caFile: string;
    allowInsecureLoopback: boolean;
};
export declare function validateRelayConfig(input: any, previous?: Config, persisted?: boolean): Config;
/** A private gateway dispatches only /api routes and always marks requests as remote. */
export declare function createGateway(webServer: any): {
    gateway: http.Server<typeof http.IncomingMessage, typeof http.ServerResponse>;
    close(): Promise<void>;
};
export declare function createRelayManager(home: string, webServer: any, register: (route: any) => void, generatePairTicket?: () => {
    ticket: string;
    expiresAt: number;
}): {
    start: () => Promise<void>;
    stop: () => Promise<void>;
    status: () => {
        state: string;
        detail: string;
    };
};
export {};
