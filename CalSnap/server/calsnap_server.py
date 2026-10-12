#!/usr/bin/env python3
"""CalSnap server: riceve le foto dei pasti dall'app e le mostra a un ospite via link segreto.
Solo stdlib. Le foto (e i metadati) vengono cancellate dopo 7 giorni."""
import hmac, html, json, math, os, re, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

DIR = os.environ.get("CALSNAP_DIR", "/var/lib/calsnap")
UPLOAD_KEY = os.environ.get("CALSNAP_UPLOAD_KEY", "")
GUEST_TOKEN = os.environ.get("CALSNAP_GUEST_TOKEN", "")
PORT = int(os.environ.get("CALSNAP_PORT", "8080"))
MAX_AGE = 7 * 86400
MAX_BYTES = 1_000_000  # le foto arrivano già compresse (~30 KB)
ID = r"[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}"
DAYS = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"]


def cleanup():
    cutoff = time.time() - MAX_AGE
    for f in os.listdir(DIR):
        p = os.path.join(DIR, f)
        if os.path.getmtime(p) < cutoff:
            os.remove(p)


def cleanup_loop():
    while True:
        cleanup()
        time.sleep(3600)


def check(given, secret):
    return bool(secret) and hmac.compare_digest(given.encode(), secret.encode())


def page(token):
    meals = []
    for f in os.listdir(DIR):
        if f.endswith(".json"):
            p = os.path.join(DIR, f)
            with open(p) as fh:
                meals.append((os.path.getmtime(p), f[:-5], json.load(fh)))
    days = {}
    for mtime, mid, meta in sorted(meals, reverse=True):
        t = time.localtime(mtime)
        days.setdefault(f"{DAYS[t.tm_wday]} {t.tm_mday:02}/{t.tm_mon:02}", []).append((t, mid, meta))
    out = []
    for day, items in days.items():
        out.append(f"<h2>{day} <small>{sum(m['kcal'] for _, _, m in items)} kcal</small></h2>")
        for t, mid, m in items:
            img = (f'<img src="/g/{html.escape(token)}/{mid}.jpg" loading="lazy">'
                   if os.path.exists(os.path.join(DIR, mid + ".jpg")) else "")
            out.append(f'<div class="meal">{img}<p><b>{html.escape(m["name"])}</b><br>'
                       f'{t.tm_hour:02}:{t.tm_min:02} · {m["kcal"]} kcal</p></div>')
    return f"""<!doctype html><html lang="it"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Cosa ho mangiato</title>
<style>body{{font-family:system-ui;max-width:600px;margin:auto;padding:16px;color-scheme:light dark}}
small{{color:gray;font-weight:normal}}.meal{{display:flex;gap:12px;align-items:center;margin:8px 0}}
img{{width:96px;height:96px;object-fit:cover;border-radius:12px}}</style>
<h1>Ultimi 7 giorni</h1>{''.join(out) or '<p>Nessun pasto.</p>'}</html>""".encode()


def num(s, default):
    try:
        v = float(s)
    except (TypeError, ValueError):
        return default
    return v if math.isfinite(v) else default


class Handler(BaseHTTPRequestHandler):
    def authorized(self):
        """Ritorna (query, id) se la richiesta è valida, altrimenti risponde con l'errore."""
        url = urlparse(self.path)
        if url.path != "/upload" or not check(self.headers.get("X-Key", ""), UPLOAD_KEY):
            self.send_error(403)
            return None
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        mid = q.get("id", "").upper()
        if not re.fullmatch(ID, mid):
            self.send_error(400)
            return None
        return q, mid

    def do_POST(self):
        req = self.authorized()
        if not req:
            return
        q, mid = req
        size = int(self.headers.get("Content-Length") or 0)
        if not 0 <= size <= MAX_BYTES:
            return self.send_error(400)
        body = self.rfile.read(size)
        # mtime = ora del pasto, così pagina e cancellazione usano la data giusta anche se l'upload è in ritardo
        ts = min(num(q.get("ts"), time.time()), time.time())
        paths = [os.path.join(DIR, mid + ".json")]
        jpg = os.path.join(DIR, mid + ".jpg")
        if body:
            paths.append(jpg)
            with open(jpg, "wb") as f:
                f.write(body)
        elif os.path.exists(jpg):
            os.remove(jpg)  # pasto modificato e foto rimossa
        with open(paths[0], "w") as f:
            json.dump({"name": q.get("name", "")[:100], "kcal": int(num(q.get("kcal"), 0))}, f)
        for p in paths:
            os.utime(p, (ts, ts))
        self.send_response(201)
        self.end_headers()

    def do_DELETE(self):
        req = self.authorized()
        if not req:
            return
        for ext in (".json", ".jpg"):
            try:
                os.remove(os.path.join(DIR, req[1] + ext))
            except FileNotFoundError:
                pass
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        parts = urlparse(self.path).path.split("/")  # ["", "g", token, file?]
        if len(parts) < 3 or parts[1] != "g" or not check(parts[2], GUEST_TOKEN):
            return self.send_error(404)
        if len(parts) == 4 and parts[3]:
            path = os.path.join(DIR, parts[3])
            if not re.fullmatch(ID + r"\.jpg", parts[3]) or not os.path.exists(path):
                return self.send_error(404)
            with open(path, "rb") as f:
                body, ctype, cache = f.read(), "image/jpeg", "private, max-age=86400"
        else:
            body, ctype, cache = page(parts[2]), "text/html; charset=utf-8", "no-cache"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", cache)
        self.end_headers()
        self.wfile.write(body)


def serve(port=PORT):
    os.makedirs(DIR, exist_ok=True)
    threading.Thread(target=cleanup_loop, daemon=True).start()
    return ThreadingHTTPServer(("0.0.0.0", port), Handler)


if __name__ == "__main__":
    if not UPLOAD_KEY or not GUEST_TOKEN:
        sys.exit("Imposta CALSNAP_UPLOAD_KEY e CALSNAP_GUEST_TOKEN")
    serve().serve_forever()
