import os, tempfile, threading, time, urllib.error, urllib.request

os.environ.update(CALSNAP_DIR=tempfile.mkdtemp(), CALSNAP_UPLOAD_KEY="up", CALSNAP_GUEST_TOKEN="guest")
import calsnap_server as s

srv = s.serve(port=0)
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = f"http://127.0.0.1:{srv.server_address[1]}"
MID = "0E984725-C51C-4BF4-9960-E1C80E27ABA0"


def req(path, data=None, key=None, method=None):
    r = urllib.request.Request(base + path, data=data, method=method or ("POST" if data is not None else "GET"))
    if key:
        r.add_header("X-Key", key)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, b""


up = f"/upload?id={MID}&name=Pasta%20%2B%20%3Cb%3E&kcal=650"
assert req(up, b"JPEG", key="wrong")[0] == 403
assert req(f"/upload?id=../../etc&kcal=1", b"x", key="up")[0] == 400
assert req(up, b"JPEG", key="up")[0] == 201
assert req("/upload?id=11111111-2222-3333-4444-555555555555&name=Mela&kcal=x", b"", key="up")[0] == 201

code, body = req("/g/guest/")
assert code == 200 and b"Pasta + &lt;b&gt;" in body and b"650 kcal" in body and b"Mela" in body, body
assert req(f"/g/guest/{MID}.jpg") == (200, b"JPEG")
# ri-upload senza foto (foto rimossa nell'app) → la foto sparisce dal server
FOTO = "33333333-2222-3333-4444-555555555555"
assert req(f"/upload?id={FOTO}&name=F&kcal=1", b"JPEG", key="up")[0] == 201
assert req(f"/upload?id={FOTO}&name=F&kcal=1", b"", key="up")[0] == 201
assert req(f"/g/guest/{FOTO}.jpg")[0] == 404
assert req(f"/upload?id={FOTO}", key="up", method="DELETE")[0] == 204
assert req("/g/nope/")[0] == 404
# upload in ritardo: la data del pasto viene da ts
LATE = "22222222-2222-3333-4444-555555555555"
assert req(f"/upload?id={LATE}&name=Ieri&kcal=100&ts={time.time() - 86400}", b"", key="up")[0] == 201
assert os.path.getmtime(os.path.join(s.DIR, LATE + ".json")) < time.time() - 86000
assert req(f"/upload?id={LATE}&name=x&kcal=nan&ts=nan", b"", key="up")[0] == 201
assert req(f"/upload?id={LATE}", key="wrong", method="DELETE")[0] == 403
assert req(f"/upload?id={LATE}", key="up", method="DELETE")[0] == 204
assert req(f"/upload?id={LATE}", key="up", method="DELETE")[0] == 204  # idempotente
assert not os.path.exists(os.path.join(s.DIR, LATE + ".json"))
assert req("/g/guest/..%2Fx.jpg")[0] == 404

old = time.time() - 8 * 86400
for f in os.listdir(s.DIR):
    if f.startswith(MID):
        os.utime(os.path.join(s.DIR, f), (old, old))
s.cleanup()
assert sorted(os.listdir(s.DIR)) == ["11111111-2222-3333-4444-555555555555.json"]
print("ok")
