# KasirAlva Basic — Android (V14)

Offline-first POS (WebView + Room) with Firebase Firestore license activation.

## Fitur utama
- Kasir / POS, produk, laporan, stock opname
- Persistensi native via Room (produk, transaksi, pembelian, opname, settings)
- Backup / restore file JSON
- Lisensi online via **Firestore** (tanpa Cloud Functions)

## Build
1. Buka folder ini di **Android Studio**
2. Biarkan Gradle sync (butuh internet pertama kali)
3. Pastikan `app/google-services.json` sudah ada
4. Run di emulator / HP

## Firestore setup (sekali saja)
1. Buat collection `licenses`
2. Tambah dokumen, ID = kode lisensi, contoh: `ALVA-TEST-0001-12345678`
3. Field:
   - `status` (string) = `ACTIVE`
   - `deviceHash` (string) = `""` (kosong)
   - `createdAt` (timestamp)
   - `activatedAt` (timestamp, boleh kosong)
4. Tempel **Security Rules** yang sudah disiapkan (APK hanya boleh get + aktivasi pertama)

## Format lisensi
`ALVA-XXXX-XXXX-XXXXXXXX`

## Catatan
- Data kasir tetap offline (Room + localStorage fallback)
- Lisensi butuh internet hanya saat aktivasi pertama
- Setelah aktif, app bisa dipakai offline
