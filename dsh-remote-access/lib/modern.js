import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { WebSocketServer, WebSocket } from 'ws';
import { DEVICE_ID, reply } from './security.js';
// Compatibility carrier for DSH 0.2.1-rc.5. All business calls go through
// Typert validation and invocation scopes, never directly to host services.
const RPCS = [
    'session.list', 'session.search', 'session.create', 'session.history',
    'session.models', 'session.selectModel', 'session.prompt', 'session.cancel',
    'session.rename', 'commands/execute', 'commands/list', 'skill.list',
    'workspace.list', 'workspace.create', 'workspace.archiveSession',
    'workspace.unarchiveSession', 'workspace.insertSessionBefore',
    'agentPreset.list', 'host.listDirectory',
];
export function assistantBaseline(attempt) {
    let text = '', reasoning = '';
    for (const record of attempt?.stream || []) {
        if (record.type === 'text-chunks')
            text += record.texts.join('');
        else if (record.type === 'reasoning-chunks')
            reasoning += record.texts.join('');
        else if (record.type === 'chunk' && record.chunk?.type === 'text-delta')
            text += record.chunk.text || '';
        else if (record.type === 'chunk' && record.chunk?.type === 'reasoning-delta')
            reasoning += record.chunk.text || '';
    }
    return { text, reasoning, startedAfterSeq: attempt?.startedAfterSeq ?? -1 };
}
const errorOf = (e) => ({ code: e?.code || 'remote/error', message: e?.message || 'Host request failed' });
async function readEnvelope(req) {
    if (!String(req.headers['content-type']).toLowerCase().startsWith('application/json'))
        throw new Error('JSON required');
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
        size += chunk.length;
        if (size > 16 * 1024 * 1024)
            throw new Error('Request exceeds 16 MiB');
        chunks.push(chunk);
    }
    const value = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!value || typeof value.rpcId !== 'string' || value.rpcId.length > 128)
        throw new Error('Invalid RPC envelope');
    return value;
}
export function createModernAdapter(ctx, register) {
    // The desktop launcher passes its active runtime directory as argv[2], including for ASAR installations.
    const runtimeDir = process.argv[2];
    let hostVersion;
    if (runtimeDir && isAbsolute(runtimeDir)) {
        try {
            const manifest = JSON.parse(readFileSync(join(runtimeDir, 'desktop-runtime.json'), 'utf8'));
            const version = manifest.release?.version;
            if (typeof version === 'string' && /^\d+\.\d+\.\d+(?:-[\w.-]+)?(?:\+[\w.-]+)?$/.test(version))
                hostVersion = version;
        }
        catch { /* The existing client displays an unknown version when runtime metadata is unavailable. */ }
    }
    const gateway = ctx.typertGateway;
    const streams = new Set();
    const wanted = new Map();
    const pending = new Map();
    const lifetime = new AbortController();
    const peers = new Set();
    const peer = () => {
        const p = ctx.connection.createPeer({ principal: { kind: 'owner' }, interactions: 'answer' });
        peers.add(p);
        return p;
    };
    const release = async (p) => { peers.delete(p); await p.dispose(); };
    const invoke = (p, signal, endpoint, args) => {
        const [namespace, method] = endpoint.split('/');
        return gateway.invoke({ namespace, method, args, peer: p, signal });
    };
    const source = (p, signal, endpoint, args) => gateway.wireStream.open(endpoint, { args }, undefined, p, signal);
    async function first(p, signal, endpoint, args) {
        const control = new AbortController();
        const iterable = await source(p, AbortSignal.any([signal, control.signal]), endpoint, args);
        const it = iterable[Symbol.asyncIterator]();
        try {
            const result = await it.next();
            if (result.done)
                throw new Error('Missing Host stream baseline');
            return result.value;
        }
        finally {
            control.abort();
            await it.return?.();
        }
    }
    async function translate(method, payload, rpcId, p, signal) {
        const request = payload && typeof payload === 'object' && !Array.isArray(payload) ? payload : {};
        switch (method) {
            case 'session.list': return invoke(p, signal, 'session/list', { _request: request });
            case 'session.prompt': return invoke(p, signal, 'session/prompt', { request: { ...request, requestId: rpcId } });
            case 'session.history': {
                const address = { kind: 'session', sessionId: request.sessionId };
                const snapshot = await first(p, signal, 'session/follow', { request: { address, maxMessages: request.maxMessages ?? 15 } });
                const page = request.beforeSeq === undefined ? snapshot : await invoke(p, signal, 'session/page', {
                    request: { address, throughSeq: snapshot.cursor, beforeSeq: request.beforeSeq, maxMessages: request.maxMessages ?? 15 },
                });
                return { events: page.records.filter((r) => r.type === 'event').map((r) => ({ event: r.event })), hasMore: page.hasMore, projections: snapshot.projections };
            }
            case 'session.models': {
                const catalog = await invoke(p, signal, 'session/modelCatalog', {});
                const projections = request.sessionId === undefined ? undefined
                    : await invoke(p, signal, 'session/projections', { request: { sessionId: request.sessionId } });
                const current = projections?.values?.modelSelection?.next ?? projections?.values?.modelSelection?.lastUsed ?? catalog.default;
                return { current, routable: catalog.routableProviders.includes(current.provider), groups: catalog.groups, failures: catalog.failures };
            }
            case 'workspace.list': return (await first(p, signal, 'workspace/follow', {})).value;
            case 'workspace.insertSessionBefore': {
                // The old mobile endpoint also restores archived sessions. Modern DSH
                // separates ordering from the archive set; validate the move first.
                const result = await invoke(p, signal, 'workspace/insertSessionBefore', { request });
                await invoke(p, signal, 'workspace/unarchiveSession', { request: { sessionId: request.sessionId } });
                return result;
            }
            case 'agentPreset.list': return invoke(p, signal, 'agentPresets/list', {});
            case 'host.listDirectory': return invoke(p, signal, 'directoryPicker/list', { path: request.path });
            case 'skill.list': return invoke(p, signal, 'skills/list', { request });
            case 'commands/execute': return invoke(p, signal, method, { agentId: request.agentId, line: request.line, ...(request.submittedAttachments ? { submittedAttachments: request.submittedAttachments } : {}) });
            case 'commands/list': return invoke(p, signal, method, { agentId: request.agentId });
            default: return invoke(p, signal, method.replace('.', '/'), { request });
        }
    }
    function follow(s, sessionId) {
        if (s.followers.has(sessionId) || s.control.signal.aborted)
            return;
        // Only sessions opened by this device are followed; cap tabs per device.
        if (s.followers.size >= 8) {
            const [old, control] = s.followers.entries().next().value;
            control.abort();
            s.followers.delete(old);
        }
        const control = new AbortController();
        s.followers.set(sessionId, control);
        const signal = AbortSignal.any([s.control.signal, control.signal]);
        void (async () => {
            for await (const f of await source(s.peer, signal, 'session/follow', { request: { address: { kind: 'session', sessionId }, maxMessages: 1, assistantStream: true } })) {
                if (signal.aborted)
                    break;
                if (f.type === 'snapshot') {
                    s.send({ type: 'session/subscribed', sessionId, lastSeq: f.cursor });
                    s.send({ type: 'session/event', sessionId, event: {
                            type: 'assistant/reset', seq: -1, time: Date.now(), data: assistantBaseline(f.assistantStream?.activeAttempt),
                        } });
                    for (const [key, value] of Object.entries(f.projections?.values || {}))
                        s.send({ type: 'session/projection', sessionId, key, value, seq: f.cursor });
                }
                else if (f.type === 'event')
                    s.send({ type: 'session/event', sessionId, event: f.event });
                else if (f.type === 'assistant-stream' && f.frame.type === 'chunk')
                    s.send({
                        type: 'session/event', sessionId,
                        event: { type: 'assistant/chunk', seq: -1, time: f.frame.time, data: { chunk: f.frame.chunk } },
                    });
            }
        })().catch(e => { if (!signal.aborted)
            s.send({ type: 'stream/error', sessionId, error: errorOf(e) }); })
            .finally(() => { if (s.followers.get(sessionId) === control)
            s.followers.delete(sessionId); });
    }
    function want(device, sessionId) {
        if (typeof sessionId !== 'string' || !sessionId || sessionId.length > 256)
            return;
        let sessions = wanted.get(device);
        if (!sessions)
            wanted.set(device, sessions = new Map());
        sessions.delete(sessionId);
        sessions.set(sessionId, Date.now());
        if (sessions.size > 8)
            sessions.delete(sessions.keys().next().value);
        for (const s of streams)
            if (s.device === device)
                follow(s, sessionId);
    }
    function fallback(req, res) {
        // Native browser RPCs keep their normal Host authentication and envelope.
        const routes = [...ctx.webServer.prefixes.values()].filter((r) => req.url?.startsWith(r.path)).sort((a, b) => b.path.length - a.path.length);
        return routes[0] ? routes[0].handler(req, res) : reply(res, 401, { error: 'device_token_required' });
    }
    for (const method of RPCS)
        register({ kind: 'exact', path: '/api/' + method, handler: async (req, res) => {
                if (!req[DEVICE_ID])
                    return fallback(req, res);
                if (req.method !== 'POST')
                    return reply(res, 405, { error: 'method_not_allowed' });
                const p = peer(), control = new AbortController();
                const signal = AbortSignal.any([control.signal, p.signal, lifetime.signal]);
                const closed = () => control.abort();
                res.once('close', closed);
                let rpcId = 'invalid-request';
                try {
                    const body = await readEnvelope(req);
                    rpcId = body.rpcId;
                    if (body.type !== 'client-request' || body.method !== method)
                        throw new Error('RPC method mismatch');
                    if (['session.history', 'session.prompt'].includes(method))
                        want(req[DEVICE_ID], body.payload?.sessionId);
                    const value = await translate(method, body.payload, rpcId, p, signal);
                    if (method === 'session.create')
                        want(req[DEVICE_ID], value.sessionId);
                    reply(res, 200, { type: 'server-response', rpcId, result: { ok: true, value: value ?? null } });
                }
                catch (e) {
                    if (!res.destroyed)
                        reply(res, 200, { type: 'server-response', rpcId, result: { ok: false, error: errorOf(e) } });
                }
                finally {
                    res.removeListener('close', closed);
                    control.abort();
                    await release(p);
                }
            } });
    register({ kind: 'exact', path: '/api/host.describe', handler: async (req, res) => {
            if (req.method !== 'POST')
                return reply(res, 405, { error: 'method_not_allowed' });
            try {
                const body = await readEnvelope(req);
                reply(res, 200, { type: 'server-response', rpcId: body.rpcId, result: { ok: true, value: { protocol: 'dsh-remote-v3', hostProtocol: 'typert', pairingRequired: true, version: hostVersion } } });
            }
            catch {
                reply(res, 400, { error: 'invalid_request' });
            }
        } });
    function attach(device, send, closed) {
        const s = { device, peer: peer(), control: new AbortController(), send, followers: new Map() };
        streams.add(s);
        closed(() => {
            s.control.abort();
            streams.delete(s);
            for (const [id, value] of pending)
                if (value.stream === s)
                    pending.delete(id);
            void release(s.peer);
        });
        for (const id of wanted.get(device)?.keys() || [])
            follow(s, id);
        void (async () => {
            let clientId = '';
            for await (const e of await source(s.peer, s.control.signal, '$events', {})) {
                if (s.control.signal.aborted)
                    break;
                if (e.type === 'ready')
                    clientId = e.clientId;
                else if (e.type === 'waterfall') {
                    if (pending.size >= 256)
                        throw new Error('Too many pending interactions');
                    const rpcId = randomUUID();
                    const frame = e.event === 'approval/request'
                        ? { type: 'approval/requested', sessionId: e.agentId, approvalId: e.eventId, ...e.request }
                        : { type: 'question/requested', sessionId: e.agentId, questionRpcId: rpcId, ...e.request };
                    pending.set(rpcId, { stream: s, clientId, eventId: e.eventId, frame });
                    send(frame, rpcId);
                }
                else if (e.type === 'cancel') {
                    for (const [id, value] of pending)
                        if (value.stream === s && value.eventId === e.eventId) {
                            send({ ...value.frame, type: value.frame.type.replace('requested', 'resolved'), outcome: 'cancelled' }, id);
                            pending.delete(id);
                        }
                }
                else if (e.type === 'emit') {
                    const [first, second] = e.args;
                    if (e.event === 'api-session/added')
                        send({ ...first, type: 'host/session-added' });
                    if (e.event === 'api-session/removed')
                        send({ type: 'host/session-removed', sessionId: first });
                    if (e.event === 'api-session/status')
                        send({ type: 'host/session-status', sessionId: first, status: second ? 'running' : 'idle' });
                    if (e.event === 'api-session/error')
                        send({ type: 'stream/error', sessionId: first, error: second });
                }
            }
        })().catch(e => { if (!s.control.signal.aborted)
            send({ type: 'stream/error', error: errorOf(e) }); });
        void (async () => {
            for await (const f of await source(s.peer, s.control.signal, 'workspace/follow', {})) {
                if (s.control.signal.aborted)
                    break;
                send({ type: 'host/workspace-changed' });
            }
        })().catch(e => { if (!s.control.signal.aborted)
            send({ type: 'stream/error', error: errorOf(e) }); });
    }
    const wire = (payload, rpcId = randomUUID()) => JSON.stringify({ type: 'server-request', rpcId, method: 'events.mux', payload });
    const ws = new WebSocketServer({ noServer: true, maxPayload: 16384 });
    for (const path of ['/api/events.mux', '/api/events.host']) {
        register({ kind: 'exact', path, handler: (req, res) => {
                if (!req[DEVICE_ID])
                    return reply(res, 401, { error: 'device_token_required' });
                if (req.method !== 'GET')
                    return reply(res, 405, { error: 'method_not_allowed' });
                res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no' });
                res.write(': connected\n\n');
                const heartbeat = setInterval(() => res.write(': ping\n\n'), 20000);
                heartbeat.unref();
                res.once('close', () => clearInterval(heartbeat));
                if (path.endsWith('events.host'))
                    return; // mux carries Host events too; avoid duplicate interactions.
                attach(req[DEVICE_ID], (payload, rpcId) => {
                    if (res.writableLength > 1024 * 1024) {
                        res.destroy();
                        return;
                    }
                    res.write('data: ' + wire(payload, rpcId) + '\n\n');
                }, close => res.once('close', close));
            } });
        register({ kind: 'upgrade', path, handler: (req, socket, head) => {
                if (!req[DEVICE_ID]) {
                    socket.destroy();
                    return;
                }
                ws.handleUpgrade(req, socket, head, client => {
                    let alive = true;
                    client.on('pong', () => { alive = true; });
                    client.on('error', () => client.terminate());
                    const heartbeat = setInterval(() => { if (!alive)
                        return client.terminate(); alive = false; client.ping(); }, 20000);
                    heartbeat.unref();
                    client.once('close', () => clearInterval(heartbeat));
                    if (path.endsWith('events.host'))
                        return;
                    attach(req[DEVICE_ID], (payload, rpcId) => {
                        if (client.bufferedAmount > 1024 * 1024) {
                            client.terminate();
                            return;
                        }
                        if (client.readyState === WebSocket.OPEN)
                            client.send(wire(payload, rpcId));
                    }, close => client.once('close', close));
                });
            } });
    }
    register({ kind: 'exact', path: '/api/respond', handler: async (req, res) => {
            if (req.method !== 'POST')
                return reply(res, 405, { error: 'method_not_allowed' });
            try {
                const body = await readEnvelope(req), item = pending.get(body.rpcId);
                if (body.type !== 'client-response' || !item || item.stream.device !== req[DEVICE_ID])
                    return reply(res, 403, { error: 'interaction_not_owned' });
                const answer = body.result?.value;
                if (body.result?.ok !== true || answer?.sessionId !== item.frame.sessionId)
                    return reply(res, 400, { error: 'invalid_response' });
                const approval = item.frame.type === 'approval/requested';
                if (approval && !['allowed-once', 'rejected'].includes(answer.outcome))
                    return reply(res, 400, { error: 'invalid_outcome' });
                const result = await gateway.dispatchRpc('$events/result', { args: { clientId: item.clientId, eventId: item.eventId,
                        outcome: { kind: 'result', value: approval ? answer.outcome : answer.answer } } }, item.stream.control.signal, item.stream.peer);
                if (!result.ok)
                    return reply(res, 409, result);
                pending.delete(body.rpcId);
                item.stream.send({ ...item.frame, type: item.frame.type.replace('requested', 'resolved'), outcome: approval ? answer.outcome : 'answered' }, body.rpcId);
                reply(res, 200, { ok: true });
            }
            catch {
                reply(res, 400, { error: 'invalid_response' });
            }
        } });
    return { dispose: async () => {
            lifetime.abort();
            for (const s of streams)
                s.control.abort();
            for (const client of ws.clients)
                client.terminate();
            ws.close();
            await Promise.all([...peers].map(release));
            streams.clear();
            wanted.clear();
            pending.clear();
        } };
}
