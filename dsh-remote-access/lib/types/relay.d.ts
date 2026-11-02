import http from 'node:http';
type Config = {
    enabled: boolean;
    serverUrl: string;
    hostId: string;
    registrationToken: string;
    caFile: string;
    allowInsecureLoopback: boolean;
};
export declare function validateRelayConfig(input: any, previous?: Config): Config;
/** A private gateway dispatches only /api routes and always marks requests as remote. */
export declare function createGateway(webServer: any): {
    gateway: http.Server<typeof http.IncomingMessage, typeof http.ServerResponse>;
    close(): Promise<void>;
};
export declare function createRelayManager(home: string, webServer: any, register: (route: any) => void): {
    start: () => Promise<void>;
    stop: () => Promise<void>;
    status: () => {
        state: string;
        detail: string;
    };
};
export {};
