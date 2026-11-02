"""Synthetic DSH UI fixture. Loopback only; never use as a deployed server.

Run explicitly: python tests/tools/local_ui_server.py --ports 18741 18742
Pairing code: 123456. Both computers deliberately reuse the same session IDs.
Model and permission selections only update synthetic in-memory session state.
No real computer files, commands, accounts or credentials are accessed.
"""
import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


def handler_for(index, trace=False):
    name = f"示例电脑 {'AB'[index % 2]}"
    token = f"{index + 1:064x}"
    computer_id = f"00000000-0000-4000-8000-{index + 1:012d}"
    timestamp = 1793577600000
    selections = {}
    permissions = {}
    model_groups = [{"id": "fixture", "name": "示例提供商", "models": [
        {"id": "fixture-reasoning", "name": "示例模型 A", "reasoning": {"efforts": [
            {"id": "low", "name": "低"}, {"id": "medium", "name": "中"}, {"id": "high", "name": "高"}]}},
        {"id": "fixture-simple", "name": "示例模型 B"},
    ]}]

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def reply(self, value, status=200):
            data = json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(data)
            self.close_connection = True

        def do_GET(self):
            path = urlsplit(self.path).path
            if trace:
                print(self.command, path, flush=True)
            if path == "/api/remote-access/device/info":
                return self.reply({"ok": True, "name": name, "model": name,
                                   "platform": "fixture", "computerId": computer_id})
            if path == "/api/remote-access/pair/check":
                return self.reply({"paired": self.headers.get("Authorization") == f"Bearer {token}"})
            if path == "/api/remote-access/pair/status":
                return self.reply({"ok": True, "state": "approved"})
            if path.startswith("/api/events."):
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.end_headers()
                try:
                    while True:
                        self.wfile.write(b": fixture\n\n")
                        self.wfile.flush()
                        time.sleep(1)
                except (ConnectionError, OSError):
                    return
            self.reply({"ok": False, "error": "fixture_route_missing"}, 404)

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or b"{}")
            path = urlsplit(self.path).path
            if trace:
                print(self.command, path, flush=True)
            if path == "/api/remote-access/pair/code/verify":
                if body.get("code") != "123456":
                    return self.reply({"ok": False, "error": "code_mismatch", "attemptsLeft": 4})
                return self.reply({"ok": True, "token": token})
            if path == "/api/remote-access/pair/request":
                return self.reply({"ok": True, "state": "approved"})
            if path == "/api/remote-access/pair/claim":
                return self.reply({"ok": True, "token": token})
            method = path.removeprefix("/api/")
            payload = body.get("payload") or {}
            session_id = payload.get("sessionId") or payload.get("agentId") or "fixture-1"
            selection = selections.get(session_id, {"provider": "fixture", "model": "fixture-reasoning", "reasoningEffort": "low"})
            values = {
                "host.describe": {"version": "0.2.1-rc.5"},
                "session.list": {"items": [
                    {"sessionId": f"fixture-{n}", "updatedAt": timestamp - n * 1000,
                     "projections": {"values": {"title": f"{name} · 示例会话 {n}"}}}
                    for n in range(1, 14)]},
                "workspace.list": {"items": [], "archivedSessionIds": []},
                "agentPreset.list": {"items": [{"id": "cordis", "name": "Cordis"}]},
                "session.history": {"events": [
                    {"event": {"seq": 1, "time": timestamp, "type": "session/title",
                               "data": {"title": f"{name} · 示例会话"}}},
                    {"event": {"seq": 2, "time": timestamp + 1000, "type": "user/message",
                               "data": {"content": [{"type": "text", "text": "请整理这次界面测试的检查项目。"}]}}},
                    {"event": {"seq": 3, "time": timestamp + 2000, "type": "assistant/message",
                               "data": {"content": [{"type": "text", "text": f"这是 **{name}** 的合成会话。\n\n- 返回连接页并切换电脑\n- 查看设置和会话列表\n- 检查横竖屏、多行输入与大字号\n\n此服务不执行实际任务。"}]}}},
                    {"event": {"seq": 4, "time": timestamp + 3000, "type": "permission/preset",
                               "data": {"preset": permissions.get(session_id, "read-only")}}},
                ], "hasMore": False,
                                    "projections": {"values": {"title": f"{name} · 示例会话"}}},
                "session.models": {"current": selection, "groups": model_groups},
                "skill.list": {"items": []},
                "command.list": {"items": []},
            }
            if method == "session.selectModel" and payload.get("provider") == "fixture":
                model, effort = payload.get("model"), payload.get("reasoningEffort")
                if (model == "fixture-simple" and effort is None) or (model == "fixture-reasoning" and effort in (None, "low", "medium", "high")):
                    selections[session_id] = {"provider": "fixture", "model": model, "reasoningEffort": effort}
                    values[method] = {}
            if method == "commands/execute":
                line = payload.get("line", "")
                preset = line.removeprefix("/permission ")
                if line.startswith("/permission ") and preset in ("read-only", "workspace-write", "danger-full-access"):
                    permissions[session_id] = preset
                    values[method] = {"result": {"kind": "text", "text": "示例权限已更新"}}
            value = values.get(method)
            result = {"ok": True, "value": value} if value is not None else {
                "ok": False, "error": {"code": "FIXTURE_ONLY", "message": "本地界面测试不执行任务"}}
            self.reply({"type": "server-response", "rpcId": body.get("rpcId", "fixture"), "result": result})

    return Handler


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ports", type=int, nargs=2, required=True)
    parser.add_argument("--trace", action="store_true", help="Print method and path only")
    args = parser.parse_args()
    servers = [ThreadingHTTPServer(("127.0.0.1", port), handler_for(i, args.trace)) for i, port in enumerate(args.ports)]
    try:
        for server in servers:
            threading.Thread(target=server.serve_forever, daemon=True).start()
        print("Two loopback UI fixtures ready; pairing code 123456.", flush=True)
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        for server in servers:
            server.shutdown()
            server.server_close()
