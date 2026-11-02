export class RequestError extends Error {
    status;
    code;
    constructor(status, code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
/** Keep HTTP status even if an older host returns HTML or an empty error body. */
export async function request(route, body, signal) {
    const response = await fetch('/api/remote-access/' + route, {
        method: body === undefined ? 'GET' : 'POST', signal,
        ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    });
    let value;
    try {
        value = await response.json();
    }
    catch {
        throw new RequestError(response.status, 'invalid_response');
    }
    if (!response.ok || !value?.ok)
        throw new RequestError(response.status, value?.error || 'request_failed');
    if (route === 'access/settings' && (typeof value.allowConnections !== 'boolean' || typeof value.keepAwake !== 'boolean'
        || typeof value.wake?.supported !== 'boolean' || typeof value.wake?.state !== 'string')) {
        throw new RequestError(response.status, 'invalid_response');
    }
    return value;
}
export function accessErrorKey(error, saving = false) {
    if (error instanceof RequestError) {
        if ([404, 405].includes(error.status) || (error.status === 200 && error.code === 'invalid_response'))
            return 'accessRestart';
        if ([401, 403].includes(error.status))
            return 'accessDenied';
        if (error.code === 'wake_unsupported')
            return 'awakeUnsupported';
    }
    return saving ? 'accessSaveFailed' : 'accessLoadFailed';
}
