export declare class RequestError extends Error {
    status: number;
    code: string;
    constructor(status: number, code: string);
}
/** Keep HTTP status even if an older host returns HTML or an empty error body. */
export declare function request(route: string, body?: unknown, signal?: AbortSignal): Promise<any>;
export declare function accessErrorKey(error: unknown, saving?: boolean): "accessRestart" | "accessDenied" | "awakeUnsupported" | "accessSaveFailed" | "accessLoadFailed";
