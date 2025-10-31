# Dokumentasi Fitur Deteksi GPS untuk Absensi

## Deskripsi
Fitur ini menambahkan validasi GPS/Location Services sebelum pengguna dapat melakukan absensi. Jika GPS tidak aktif, pengguna akan diminta untuk mengaktifkannya. Jika pengguna menolak, aplikasi akan kembali ke dashboard.

## Perubahan yang Dilakukan

### 1. HomeFragment.java
**Lokasi**: `app/src/main/java/com/example/ptvisdatteknikutama/HomeFragment.java`

#### Import Baru:
- `android.content.DialogInterface`
- `android.provider.Settings`
- `androidx.appcompat.app.AlertDialog`

#### Variabel Baru:
- `ActivityResultLauncher<Intent> enableGpsLauncher` - Launcher untuk membuka settings GPS
- `boolean isReturningFromGpsSettings` - Flag untuk tracking kembali dari settings

#### Method Baru:

1. **`isGpsEnabled()`**
   - Mengecek apakah GPS atau Network Provider aktif
   - Return: `true` jika salah satu provider aktif, `false` jika tidak

2. **`checkGpsStatus()`**
   - Mengecek status GPS saat fragment dibuka atau kembali dari settings
   - Jika GPS tidak aktif dan kembali dari settings → kembali ke dashboard
   - Jika GPS tidak aktif pertama kali → tampilkan dialog

3. **`showGpsAlertDialog()`**
   - Menampilkan custom dialog dengan layout `dialog_gps_alert.xml`
   - Opsi "Ya, Aktifkan" → buka Settings GPS
   - Opsi "Tidak" → kembali ke dashboard

4. **`navigateBackToDashboard()`**
   - Memanggil `onBackPressed()` untuk kembali ke dashboard

#### Modifikasi Method Existing:

1. **`onCreate()`**
   - Menambahkan `enableGpsLauncher` initialization

2. **`onViewCreated()`**
   - Menambahkan `checkGpsStatus()` sebelum `checkLocationPermission()`

3. **`checkCameraPermissionAndTakePicture()`**
   - Menambahkan cek GPS sebelum membuka kamera
   - Jika GPS tidak aktif → tampilkan dialog

4. **`onResume()`**
   - Menambahkan `checkGpsStatus()` saat fragment di-resume
   - Mencegah double check dengan flag `isReturningFromGpsSettings`

### 2. File Drawable Baru

#### ic_gps_off.xml
**Lokasi**: `app/src/main/res/drawable/ic_gps_off.xml`
- Icon GPS merah untuk indikasi GPS tidak aktif

#### ic_gps_on.xml
**Lokasi**: `app/src/main/res/drawable/ic_gps_on.xml`
- Icon GPS hijau untuk indikasi GPS aktif (untuk penggunaan future)

### 3. Layout Dialog Baru

#### dialog_gps_alert.xml
**Lokasi**: `app/src/main/res/layout/dialog_gps_alert.xml`
- Custom layout untuk dialog GPS alert
- Berisi:
  - ImageView dengan icon `ic_gps_off`
  - TextView untuk title "GPS Tidak Aktif"
  - TextView untuk pesan informasi

## Alur Kerja (Flow)

```
1. User masuk ke HomeFragment
   ↓
2. checkGpsStatus() dipanggil
   ↓
3. GPS aktif?
   ├─ YA → Lanjut normal
   └─ TIDAK → Tampilkan dialog
       ↓
       User pilih:
       ├─ "Ya, Aktifkan"
       │   ↓
       │   Buka Settings GPS
       │   ↓
       │   User kembali (onResume)
       │   ↓
       │   checkGpsStatus() dipanggil lagi
       │   ↓
       │   GPS aktif?
       │   ├─ YA → Lanjut normal
       │   └─ TIDAK → Kembali ke dashboard
       │
       └─ "Tidak"
           ↓
           Kembali ke dashboard
```

## Kapan GPS Dicek?

1. **onViewCreated()** - Saat fragment pertama kali dibuat
2. **onResume()** - Saat fragment kembali ke foreground
3. **checkCameraPermissionAndTakePicture()** - Sebelum mengambil foto untuk absensi

## Testing

### Test Case 1: GPS Sudah Aktif
1. Pastikan GPS device aktif
2. Buka aplikasi dan masuk ke halaman absensi (HomeFragment)
3. **Expected**: Tidak ada dialog, dapat langsung ambil foto

### Test Case 2: GPS Tidak Aktif - User Aktifkan
1. Matikan GPS device
2. Buka aplikasi dan masuk ke halaman absensi
3. **Expected**: Muncul dialog GPS alert
4. Pilih "Ya, Aktifkan"
5. **Expected**: Terbuka Settings GPS
6. Aktifkan GPS di settings
7. Kembali ke aplikasi (back button)
8. **Expected**: Dialog tidak muncul lagi, dapat langsung ambil foto

### Test Case 3: GPS Tidak Aktif - User Menolak
1. Matikan GPS device
2. Buka aplikasi dan masuk ke halaman absensi
3. **Expected**: Muncul dialog GPS alert
4. Pilih "Tidak"
5. **Expected**: Tampil toast "Absensi memerlukan GPS aktif. Kembali ke dashboard."
6. **Expected**: Kembali ke halaman dashboard

### Test Case 4: GPS Tidak Aktif - User ke Settings tapi Tidak Aktifkan
1. Matikan GPS device
2. Buka aplikasi dan masuk ke halaman absensi
3. **Expected**: Muncul dialog GPS alert
4. Pilih "Ya, Aktifkan"
5. **Expected**: Terbuka Settings GPS
6. JANGAN aktifkan GPS
7. Kembali ke aplikasi (back button)
8. **Expected**: Tampil toast "GPS tidak aktif. Kembali ke dashboard."
9. **Expected**: Kembali ke halaman dashboard

### Test Case 5: Ambil Foto dengan GPS Mati
1. Masuk ke HomeFragment dengan GPS aktif (tidak ada dialog)
2. Matikan GPS
3. Tekan tombol camera
4. **Expected**: Muncul dialog GPS alert

## Catatan Penting

1. **Permission**: Fitur ini tidak memerlukan permission tambahan. Hanya mengecek status GPS yang sudah tersedia.

2. **User Experience**: Dialog dibuat tidak dapat di-dismiss dengan back button (`setCancelable(false)`) untuk memastikan user membuat keputusan.

3. **Compatibility**: Menggunakan LocationManager yang kompatibel dengan semua versi Android.

4. **Network Provider**: Selain GPS, juga mengecek Network Provider sebagai fallback location.

## Integrasi dengan Fitur Lain

Fitur ini terintegrasi dengan:
- **Camera Permission** - GPS dicek sebelum permission kamera
- **Location Permission** - Bekerja bersama dengan location permission checking
- **Map Display** - GPS status mempengaruhi tampilan peta

## Troubleshooting

### Dialog Muncul Terus
- Pastikan GPS benar-benar aktif di device
- Cek logcat untuk error di method `isGpsEnabled()`

### Tidak Kembali ke Dashboard
- Pastikan `navigateBackToDashboard()` dipanggil dengan benar
- Cek apakah fragment ada dalam navigation stack

### Dialog Tidak Muncul
- Pastikan `checkGpsStatus()` dipanggil
- Cek kondisi `isReturningFromGpsSettings`
- Pastikan `getContext()` tidak null

## Future Improvements

1. Tambahkan visual indicator di UI untuk status GPS (icon di toolbar)
2. Periodic check GPS status selama fragment aktif
3. Tambahkan option "Jangan tanya lagi" untuk advanced users
4. Custom animation untuk dialog
5. Haptic feedback saat dialog muncul
