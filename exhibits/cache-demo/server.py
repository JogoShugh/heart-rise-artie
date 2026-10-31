#!/usr/bin/env python3
"""
server.py

A tiny, dependency-free static file server that serves fixtures/ with the
cache headers the three-level scheme depends on:

  /resources/*.json        -> Cache-Control: no-cache  (always revalidated
                               via ETag / If-None-Match; 304 if unchanged)
  /affordance-sets/*.json  -> Cache-Control: public, max-age=31536000,
                               immutable  (content-addressed filename ->
                               never needs revalidation)
  /schemas/*.json           -> same as affordance-sets

Every request is logged with an incrementing counter, and a per-path hit
count is tracked. GET /__stats returns that count as JSON, so a client can
assert -- not just eyeball -- that a given URL was only ever actually
requested once even though several "resources" reference it.
"""

import hashlib
import http.server
import json
import os
import socketserver
import sys
from collections import defaultdict

FIXTURES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080

request_counts = defaultdict(int)
request_log = []


def etag_for(path: str) -> str:
    with open(path, "rb") as f:
        return '"' + hashlib.sha256(f.read()).hexdigest()[:16] + '"'


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "ArtieFixtureServer/1.0"

    def log_message(self, fmt, *args):
        # silence default stderr logging; we do our own structured logging below
        pass

    def _record(self, status: int):
        n = sum(request_counts.values()) + 1
        request_counts[self.path] += 1
        request_log.append({"n": n, "path": self.path, "status": status})
        print(f"REQ #{n:<3} {self.command:<4} {self.path:<45} -> {status}")

    def do_GET(self):
        if self.path == "/__stats":
            body = json.dumps(
                {"counts": dict(request_counts), "log": request_log}, indent=2
            ).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            self._record(200)
            return

        rel_path = self.path.lstrip("/")
        full_path = os.path.normpath(os.path.join(FIXTURES_DIR, rel_path))

        # basic path traversal guard
        if not full_path.startswith(FIXTURES_DIR) or not os.path.isfile(full_path):
            self.send_response(404)
            self.end_headers()
            self._record(404)
            return

        tag = etag_for(full_path)
        client_tag = self.headers.get("If-None-Match")

        if client_tag == tag:
            self.send_response(304)
            self.send_header("ETag", tag)
            self._set_cache_control(rel_path)
            self.end_headers()
            self._record(304)
            return

        with open(full_path, "rb") as f:
            body = f.read()

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("ETag", tag)
        self._set_cache_control(rel_path)
        self.end_headers()
        self.wfile.write(body)
        self._record(200)

    def _set_cache_control(self, rel_path: str):
        if rel_path.startswith("affordance-sets/") or rel_path.startswith("schemas/"):
            # content-addressed: filename IS the fingerprint, so the client
            # never needs to ask again within this window
            self.send_header("Cache-Control", "public, max-age=31536000, immutable")
        else:
            # resource state: always revalidate, but cheaply (304, no body)
            self.send_header("Cache-Control", "no-cache")


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    if not os.path.isdir(FIXTURES_DIR):
        print(f"No fixtures/ directory found at {FIXTURES_DIR}.")
        print("Run `gradle generateFixtures` in kotlin-client/ first.")
        sys.exit(1)

    with ThreadingHTTPServer(("0.0.0.0", PORT), Handler) as httpd:
        print(f"Serving {FIXTURES_DIR} on http://localhost:{PORT}")
        print("GET /__stats for the request-count summary at any time.")
        httpd.serve_forever()
