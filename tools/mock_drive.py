#!/usr/bin/env python3
"""Tiny stand-in for the Drive v3 appDataFolder API, for emulator tests. Run, then set
cards.mockDrive=http://10.0.2.2:8099 in local.properties and rebuild."""
import json, re, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FILES = {}  # id -> (name, bytes)

class H(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", ctype="application/json"):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        m = re.match(r"/drive/v3/files/(\w+)", self.path)
        if m:
            f = FILES.get(m.group(1))
            return self._send(200, f[1], "application/octet-stream") if f else self._send(404)
        files = [{"id": i, "name": n} for i, (n, _) in FILES.items()]
        self._send(200, json.dumps({"files": files}).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        b = re.search(r"boundary=(.+)", self.headers["Content-Type"]).group(1).encode()
        parts = body.split(b"--" + b)[1:-1]
        meta = json.loads(parts[0].split(b"\r\n\r\n", 1)[1].rstrip(b"\r\n"))
        data = parts[1].split(b"\r\n\r\n", 1)[1][:-2]
        fid = uuid.uuid4().hex
        FILES[fid] = (meta["name"], data)
        print("upload", meta["name"], len(data), flush=True)
        self._send(200, json.dumps({"id": fid}).encode())

    def do_DELETE(self):
        fid = self.path.rsplit("/", 1)[1].split("?")[0]
        print("delete", FILES.get(fid, ("?",))[0], flush=True)
        self._send(204 if FILES.pop(fid, None) else 404)

    def log_message(self, *a): pass

ThreadingHTTPServer(("0.0.0.0", 8099), H).serve_forever()
