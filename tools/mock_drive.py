#!/usr/bin/env python3
"""Tiny stand-in for the Drive v3 API (folders, parents, appDataFolder), for emulator tests. Run, then set
cards.mockDrive=http://10.0.2.2:8099 in local.properties and rebuild. GET /_dump prints the whole tree."""
import json, re, uuid
from urllib.parse import urlparse, parse_qs
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FILES = {}  # id -> {"name", "data", "parent", "folder"}
FOLDER = "application/vnd.google-apps.folder"

class H(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", ctype="application/json"):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path == "/_dump":
            return self._send(200, json.dumps({i: {"name": f["name"], "parent": f["parent"], "size": len(f["data"])} for i, f in FILES.items()}, indent=1).encode())
        m = re.match(r"/drive/v3/files/(\w+)", u.path)
        if m:
            f = FILES.get(m.group(1))
            return self._send(200, f["data"], "application/octet-stream") if f else self._send(404)
        if q.get("spaces") == ["appDataFolder"]:
            rows = [(i, f) for i, f in FILES.items() if f["parent"] == "appDataFolder"]
        else:
            query = q.get("q", [""])[0]
            if f"mimeType='{FOLDER}'" in query:
                name = re.search(r"name='([^']*)'", query).group(1)
                rows = [(i, f) for i, f in FILES.items() if f["folder"] and f["name"] == name and f["parent"] == "root"]
            else:
                parent = re.search(r"'(\w+)' in parents", query).group(1)
                rows = [(i, f) for i, f in FILES.items() if f["parent"] == parent and not f["folder"]]
        self._send(200, json.dumps({"files": [{"id": i, "name": f["name"]} for i, f in rows]}).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        ctype = self.headers["Content-Type"]
        fid = uuid.uuid4().hex
        if ctype.startswith("application/json"):
            meta = json.loads(body)
            FILES[fid] = {"name": meta["name"], "data": b"", "parent": meta["parents"][0], "folder": meta.get("mimeType") == FOLDER}
            print("create folder", meta["name"], "in", meta["parents"][0], flush=True)
            return self._send(200, json.dumps({"id": fid}).encode())
        b = re.search(r"boundary=(.+)", ctype).group(1).encode()
        parts = body.split(b"--" + b)[1:-1]
        meta = json.loads(parts[0].split(b"\r\n\r\n", 1)[1].rstrip(b"\r\n"))
        data = parts[1].split(b"\r\n\r\n", 1)[1][:-2]
        FILES[fid] = {"name": meta["name"], "data": data, "parent": meta["parents"][0], "folder": False}
        print("upload", meta["name"], len(data), "to", meta["parents"][0], flush=True)
        self._send(200, json.dumps({"id": fid}).encode())

    def do_DELETE(self):
        fid = self.path.rsplit("/", 1)[1].split("?")[0]
        print("delete", FILES.get(fid, {"name": "?"})["name"], flush=True)
        self._send(204 if FILES.pop(fid, None) else 404)

    def log_message(self, *a): pass

ThreadingHTTPServer(("0.0.0.0", 8099), H).serve_forever()
