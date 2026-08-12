
# KasirAlva License Server

Server kecil untuk:
- generate kode lisensi
- aktivasi satu kali ke device hash
- revoke lisensi
- pengecekan kesehatan server

## Jalankan lokal

```bash
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Dokumentasi API tersedia di `/docs`.

## Catatan keamanan
Endpoint `/admin/generate` dan `/admin/revoke` masih merupakan fondasi MVP.
Untuk server produksi, endpoint admin wajib diberi autentikasi kuat, HTTPS,
rate limiting, logging, dan secret management. Jangan mengekspos endpoint admin
tanpa perlindungan.


## V13 Admin Dashboard
Open `admin.html` through the same web server/reverse proxy to manage licenses.

Features:
- Admin token login
- Generate 1–1000 licenses
- View license status and device binding
- Revoke a license
- Refresh license inventory

For production, serve this page over HTTPS and protect it with an additional
authentication layer. Never publish the admin token in the Android APK.
