"""Loopback-only emulator fixture for session sync and notification actions.

Start with an unused port: python session_sync_fixture.py --port PORT
Use adb reverse for that port, then pair to http://127.0.0.1:PORT with 123456.
GET /fixture/state reports synthetic requests and stream counts. POST to
/fixture/archive, /fixture/question or /fixture/revoke simulates a computer action.
Restart the fixture for a fresh, empty computer.
"""
import argparse
import json
import queue
import threading
import time
from collections import Counter
from http.server import ThreadingHTTPServer
from urllib.parse import urlsplit

from local_ui_server import handler_for


class Fixture(handler_for(0)):
    counts = Counter()
    streams = set()
    lock = threading.RLock()
    rows = []
    archived = []
    replies = []
    histories = {}
    revoked = False

    def emit(self, frame, rpc="fixture-event"):
        wire = dict(type="server-request", rpcId=rpc, method="events.mux", payload=frame)
        for stream in self.streams:
            stream.put(wire)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/fixture/state":
            with self.lock:
                return self.reply(dict(counts=self.counts, streams=len(self.streams), sessions=self.rows,
                                       archived=self.archived, replies=self.replies, revoked=self.revoked))
        with self.lock:
            self.counts[path] += 1
        if path.endswith("/pair/check") and self.revoked:
            return self.reply(dict(ok=True, paired=False))
        if path.startswith("/api/events."):
            if self.headers.get("Upgrade"):
                return self.reply({}, 400)
            if path.endswith(".mux"):
                return self.mux()
        return super().do_GET()

    def mux(self):
        mailbox = queue.Queue()
        with self.lock:
            self.streams.add(mailbox)
            self.counts["mux.open"] += 1
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            while True:
                try:
                    data = "data: " + json.dumps(mailbox.get(timeout=0.3))
                except queue.Empty:
                    data = ": heartbeat"
                self.wfile.write((data + "\n\n").encode())
                self.wfile.flush()
        except OSError:
            pass
        finally:
            with self.lock:
                self.streams.discard(mailbox)

    def do_POST(self):
        path = urlsplit(self.path).path
        with self.lock:
            if path == "/fixture/archive" and self.rows:
                self.archived[:] = [self.rows[0]["sessionId"]]
                self.emit(dict(type="host/workspace-changed"))
                return self.reply(dict(ok=True))
            if path == "/fixture/question" and self.rows:
                self.emit(dict(type="question/requested", sessionId=self.rows[0]["sessionId"],
                               questions=[dict(id="q", question="Fixture reply?")]), "fixture-question")
                return self.reply(dict(ok=True))
            if path == "/fixture/revoke":
                type(self).revoked = True
                return self.reply(dict(ok=True))
            self.counts[path] += 1
            if path not in {"/api/" + method for method in ("session.list", "session.create", "session.prompt",
                                                            "session.history", "workspace.list", "respond")}:
                return super().do_POST()
            message = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if path.endswith("/respond"):
                self.replies.append(message)
                return self.reply(dict(ok=True))
            if path.endswith("session.create"):
                sid = f"fixture-{len(self.rows) + 1}"
                self.rows.append(dict(sessionId=sid, updatedAt=int(time.time()*1000),
                                      projections=dict(values=dict(title=f"Sync task {len(self.rows) + 1}"))))
                self.histories[sid] = []
                self.emit(dict(type="host/session-added", sessionId=sid))
                value = dict(sessionId=sid)
            elif path.endswith("session.prompt"):
                history = self.histories.get(message["payload"]["sessionId"])
                if history is None:
                    return self.reply({}, 404)
                entry = dict(type="user/message", seq=len(history) + 1, time=int(time.time()*1000),
                             data=dict(content=message["payload"]["content"]))
                history.append(dict(event=entry))
                value = {}
            elif path.endswith("session.history"):
                value = dict(events=self.histories.get(message["payload"]["sessionId"], []), hasMore=False)
            elif path.endswith("workspace.list"):
                value = dict(items=[], archivedSessionIds=self.archived)
            else:
                value = dict(items=self.rows)
            return self.reply(dict(type="server-response", rpcId=message["rpcId"], result=dict(ok=True, value=value)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", required=True, type=int)
    args = parser.parse_args()
    ThreadingHTTPServer(("127.0.0.1", args.port), Fixture).serve_forever()
