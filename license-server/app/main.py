
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pathlib import Path
from secrets import token_hex
import sqlite3, hashlib, re

DB = Path(os.getenv("KASIRALVA_DB", str(Path(__file__).resolve().parent.parent / "licenses.db")))
app = FastAPI(title="KasirAlva License Server", version="1.1")
ADMIN_TOKEN = os.getenv("KASIRALVA_ADMIN_TOKEN", "")

def db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    con.execute("""CREATE TABLE IF NOT EXISTS licenses(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        license_key TEXT UNIQUE NOT NULL,
        status TEXT NOT NULL DEFAULT 'ACTIVE',
        device_hash TEXT DEFAULT '',
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        activated_at TEXT DEFAULT ''
    )""")
    con.commit()
    return con

def make_key():
    return "ALVA-" + "-".join(token_hex(2).upper() for _ in range(2)) + "-" + token_hex(4).upper()

class GenerateRequest(BaseModel):
    quantity: int = 1

class ActivateRequest(BaseModel):
    license_key: str
    device_hash: str

def require_admin(token: str):
    if not ADMIN_TOKEN or token != ADMIN_TOKEN:
        raise HTTPException(401, "admin authentication required")

@app.get("/health")
def health():
    return {"ok": True, "service": "KasirAlva License Server"}

@app.post("/admin/generate")
def generate(req: GenerateRequest, admin_token: str = ""):
    require_admin(admin_token)
    if req.quantity < 1 or req.quantity > 1000:
        raise HTTPException(400, "quantity harus 1..1000")
    con = db()
    keys = []
    for _ in range(req.quantity):
        while True:
            key = make_key()
            try:
                con.execute("INSERT INTO licenses(license_key) VALUES(?)", (key,))
                keys.append(key)
                break
            except sqlite3.IntegrityError:
                continue
    con.commit()
    con.close()
    return {"count": len(keys), "licenses": keys}

@app.post("/activate")
def activate(req: ActivateRequest):
    key = req.license_key.strip().upper().replace(" ", "")
    device = req.device_hash.strip().upper()
    if not re.fullmatch(r"ALVA-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{8}", key):
        raise HTTPException(400, "Format lisensi tidak valid")
    if not re.fullmatch(r"[A-F0-9]{8}", device):
        raise HTTPException(400, "device_hash tidak valid")

    con = db()
    row = con.execute(
        "SELECT * FROM licenses WHERE license_key=?", (key,)
    ).fetchone()

    if row is None:
        con.close()
        raise HTTPException(404, "Lisensi tidak ditemukan")
    if row["status"] != "ACTIVE":
        con.close()
        raise HTTPException(409, "Lisensi tidak aktif")

    if row["device_hash"] and row["device_hash"] != device:
        con.close()
        raise HTTPException(409, "Lisensi sudah terikat ke perangkat lain")

    con.execute(
        """UPDATE licenses SET device_hash=?, activated_at=CURRENT_TIMESTAMP
           WHERE license_key=?""",
        (device, key)
    )
    con.commit()
    con.close()
    return {"ok": True, "license_key": key, "device_hash": device}

@app.post("/admin/revoke")
def revoke(req: ActivateRequest, admin_token: str = ""):
    require_admin(admin_token)
    key = req.license_key.strip().upper().replace(" ", "")
    con = db()
    cur = con.execute(
        "UPDATE licenses SET status='REVOKED' WHERE license_key=?", (key,)
    )
    con.commit()
    con.close()
    if cur.rowcount == 0:
        raise HTTPException(404, "Lisensi tidak ditemukan")
    return {"ok": True, "license_key": key, "status": "REVOKED"}


@app.get("/admin/licenses")
def list_licenses(admin_token: str = ""):
    require_admin(admin_token)
    con = db()
    rows = con.execute(
        "SELECT license_key,status,device_hash,created_at,activated_at "
        "FROM licenses ORDER BY id DESC"
    ).fetchall()
    con.close()
    return {"licenses": [dict(row) for row in rows]}
