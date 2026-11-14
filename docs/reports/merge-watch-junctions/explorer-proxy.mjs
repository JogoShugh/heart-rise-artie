// Serves HAL Explorer at /explorer/ and passes every other request to one target, so the explorer reads the
// target from its own origin (no CORS). Usage: node explorer-proxy.mjs PORT TARGET_ORIGIN EXPLORER_DIR
import http from "node:http";
import fs from "node:fs";
import path from "node:path";

const [port, target, dir] = process.argv.slice(2);
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".ico": "image/x-icon",
  ".svg": "image/svg+xml", ".woff2": "font/woff2", ".woff": "font/woff", ".ttf": "font/ttf", ".txt": "text/plain" };

http.createServer((req, res) => {
  if (req.url === "/explorer") { res.writeHead(302, { Location: "/explorer/" }); return res.end(); }
  if (req.url.startsWith("/explorer/")) {
    const rel = decodeURIComponent(req.url.slice("/explorer/".length).split(/[?#]/)[0]) || "index.html";
    const file = path.join(dir, path.normalize(rel));
    if (!file.startsWith(path.resolve(dir)) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404); return res.end();
    }
    res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream" });
    return fs.createReadStream(file).pipe(res);
  }
  const url = new URL(req.url, target);
  const headers = { ...req.headers, host: url.host };
  // A browser page can't set headers: AUTH, when given, stands in for the credential it would send.
  if (process.env.AUTH && !headers.authorization) headers.authorization = process.env.AUTH;
  const out = http.request(url, { method: req.method, headers }, (answer) => {
    res.writeHead(answer.statusCode, answer.headers);
    answer.pipe(res);
  });
  out.on("error", (e) => { res.writeHead(502); res.end(String(e)); });
  req.pipe(out);
}).listen(Number(port), () => console.log(`explorer on http://localhost:${port}/explorer/ -> ${target}`));
