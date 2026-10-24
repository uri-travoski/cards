#!/usr/bin/env python3
"""Stand-in S3 (path-style) that verifies AWS Signature V4, for emulator tests of the app's S3 storage.
   python3 mock_s3.py          serve on :8098 (bucket 'test', key AKIATEST / secret SECRETTEST, region auto)
   python3 mock_s3.py selftest check the verifier against AWS's published example"""
import hashlib, hmac, re, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEY, SECRET, REGION, BUCKET = "AKIATEST", "SECRETTEST", "auto", "test"
OBJECTS = {}

def h(k, m): return hmac.new(k, m.encode(), hashlib.sha256).digest()

def signature(secret, method, path, rawquery, headers, signed, payload_hash, amzdate, scope):
    q = sorted((urllib.parse.quote(k, safe="-_.~"), urllib.parse.quote(v, safe="-_.~"))
               for k, v in urllib.parse.parse_qsl(rawquery, keep_blank_values=True))
    canon = "\n".join([method, path, "&".join(f"{k}={v}" for k, v in q),
                       "".join(f"{n}:{headers[n].strip()}\n" for n in signed), ";".join(signed), payload_hash])
    day, region = scope.split("/")[:2]
    k = h(h(h(h(("AWS4" + secret).encode(), day), region), "s3"), "aws4_request")
    sts = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(canon.encode()).hexdigest()])
    return hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()

if sys.argv[1:] == ["selftest"]:
    got = signature("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "GET", "/test.txt", "",
        {"host": "examplebucket.s3.amazonaws.com", "range": "bytes=0-9",
         "x-amz-content-sha256": hashlib.sha256(b"").hexdigest(), "x-amz-date": "20130524T000000Z"},
        ["host", "range", "x-amz-content-sha256", "x-amz-date"], hashlib.sha256(b"").hexdigest(),
        "20130524T000000Z", "20130524/us-east-1/s3/aws4_request")
    want = "f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"
    print("selftest", "OK" if got == want else f"FAIL {got}"); sys.exit(got != want)

class H(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", ctype="application/xml"):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def _auth(self, body):
        a = self.headers.get("Authorization", "")
        m = re.match(r"AWS4-HMAC-SHA256 Credential=([^/]+)/([^,]+), SignedHeaders=([^,]+), Signature=(\w+)", a)
        if not m or m.group(1) != KEY: print("bad credential:", a, flush=True); return False
        path, _, rq = self.path.partition("?")
        hdrs = {k.lower(): v for k, v in self.headers.items()}
        ph = hdrs.get("x-amz-content-sha256", "")
        if ph != hashlib.sha256(body).hexdigest(): print("payload hash mismatch", flush=True); return False
        want = signature(SECRET, self.command, path, rq, hdrs, m.group(3).split(";"), ph, hdrs["x-amz-date"], m.group(2))
        good = hmac.compare_digest(want, m.group(4))
        if not good: print("signature mismatch; host=%r signed=%s" % (hdrs.get("host"), m.group(3)), flush=True)
        return good

    def _handle(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if not self._auth(body):
            print("403", self.command, self.path, flush=True)
            return self._send(403, b"<Error><Code>SignatureDoesNotMatch</Code><Message>bad signature</Message></Error>")
        path, _, rq = self.path.partition("?")
        parts = urllib.parse.unquote(path).lstrip("/").split("/", 1)
        if parts[0] != BUCKET: return self._send(404, b"<Error><Message>no such bucket</Message></Error>")
        key = parts[1] if len(parts) > 1 else ""
        if self.command == "GET" and not key:
            pre = dict(urllib.parse.parse_qsl(rq)).get("prefix", "")
            print("list ->", len([k for k in OBJECTS if k.startswith(pre)]), "objects", flush=True)
            keys = "".join(f"<Contents><Key>{k}</Key></Contents>" for k in sorted(OBJECTS) if k.startswith(pre))
            return self._send(200, f"<ListBucketResult><IsTruncated>false</IsTruncated>{keys}</ListBucketResult>".encode())
        if self.command == "GET":
            return self._send(200, OBJECTS[key], "application/octet-stream") if key in OBJECTS else self._send(404, b"<Error><Message>nope</Message></Error>")
        if self.command == "PUT": OBJECTS[key] = body; print("put", key, len(body), flush=True); return self._send(200)
        if self.command == "DELETE": OBJECTS.pop(key, None); print("delete", key, flush=True); return self._send(204)
    do_GET = do_PUT = do_DELETE = _handle
    def log_message(self, *a): pass

ThreadingHTTPServer(("0.0.0.0", 8098), H).serve_forever()
