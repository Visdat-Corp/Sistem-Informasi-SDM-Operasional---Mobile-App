# 📱 Sistem Informasi SDM Operasional - Mobile App

Aplikasi mobile Android untuk sistem absensi karyawan PT Visdat Teknik Utama dengan fitur GPS tracking, kamera, dan notifikasi modern.

## ✨ Fitur Utama

### 🎯 Fitur Absensi
- ✅ **Check In/Check Out** - Absensi masuk dan pulang dengan foto
- 🕐 **Lembur** - Pencatatan waktu lembur karyawan
- 🚗 **Dinas Luar** - Absensi untuk tugas di luar kantor
- 📍 **GPS Tracking** - Validasi lokasi absensi
- 📷 **Foto Selfie** - Verifikasi identitas dengan foto
- ⏱️ **Real-time Sync** - Sinkronisasi dengan server secara real-time

### 🔔 Sistem Notifikasi Modern (NEW!)
- 📢 **Push Notifications** - Notifikasi Android native
- ❌ **Error Notifications** - Pemberitahuan ketika absensi gagal
- ✅ **Success Notifications** - Konfirmasi absensi berhasil
- ⚠️ **Warning Notifications** - Peringatan lokasi/GPS
- 🔄 **Retry Action** - Tombol coba lagi langsung dari notifikasi
- 🔙 **Back Navigation** - Kembali ke konfirmasi foto saat error (NEW!)
- 🎨 **Modern UI** - Dialog error dengan desain modern
- 💡 **Smart Solutions** - Saran solusi untuk mengatasi error

### 📊 Fitur Lainnya
- 📋 **Riwayat Absensi** - Lihat history absensi lengkap
- 🔐 **Login Secure** - Autentikasi dengan token JWT
- 🌐 **Offline Support** - Data tersimpan lokal saat offline
- 🔄 **Auto Sync** - Sinkronisasi otomatis saat online kembali

## 🚀 Teknologi

- **Language**: Java
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **Architecture**: MVC
- **Networking**: OkHttp3
- **Image Processing**: Android Camera2 API
- **Location**: GPS/FusedLocationProvider
- **Notifications**: Android Notification API + Custom Dialogs

## 📦 Dependencies

```gradle
dependencies {
    // Core Android
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Networking
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Image
    implementation 'androidx.exifinterface:exifinterface:1.3.7'
    
    // Location
    implementation 'com.google.android.gms:play-services-location:21.1.0'
}
```

## 🔧 Setup & Installation

### 1. Clone Repository
```bash
git clone https://github.com/Visdat-Corp/Sistem-Informasi-SDM-Operasional.git
cd Sistem-Informasi-SDM-Operasional/Sistem-Informasi-SDM-Operasional---Mobile-App
```

### 2. Open in Android Studio
- Buka Android Studio
- File → Open → Pilih folder project
- Tunggu Gradle sync selesai

### 3. Konfigurasi API Endpoint
Edit file `ApiClient.java`:
```java
public static final String API_BASE_URL = "https://your-api-server.com/api";
```

### 4. Build & Run
- Hubungkan device Android atau jalankan emulator
- Klik tombol Run (Shift+F10)

## 📱 Permissions Required

```xml
<!-- Internet & Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Camera -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Location/GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Storage -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## 🎨 Fitur Notifikasi Detail

Untuk dokumentasi lengkap tentang sistem notifikasi, lihat [NOTIFICATION_FEATURE.md](NOTIFICATION_FEATURE.md)

### Quick Usage
```java
// Initialize di onCreate
NotificationHelper.createNotificationChannel(this);

// Tampilkan notifikasi error
NotificationHelper.showAttendanceFailureNotification(
    context, errorMessage, jenisAbsensi
);

// Tampilkan dialog error dengan retry
ModernErrorDialog.showAttendanceError(
    context, jenisAbsensi, errorMessage,
    () -> { /* retry action */ }
);

// Tampilkan notifikasi sukses
NotificationHelper.showAttendanceSuccessNotification(
    context, jenisAbsensi
);
```

Lihat `NotificationExampleUsage.java` untuk contoh lengkap.

## 📂 Struktur Project

```
app/
├── src/main/
│   ├── java/com/visdat/mobile/
│   │   ├── MainActivity.java          # Login screen
│   │   ├── activity_dashboard.java    # Dashboard utama
│   │   ├── NavigationActivity.java    # Navigation dengan kamera
│   │   ├── KonfirmasiFoto.java        # Konfirmasi foto absensi
│   │   ├── SummaryPage.java           # Summary & kirim absensi
│   │   ├── HistoryFragment.java       # Riwayat absensi
│   │   ├── ApiClient.java             # HTTP client
│   │   ├── NotificationHelper.java    # 🆕 Notification manager
│   │   ├── ModernErrorDialog.java     # 🆕 Custom error dialog
│   │   ├── CustomToast.java           # Custom toast
│   │   └── ...
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── activity_summary_page.xml
│   │   │   ├── notification_attendance_failure.xml  # 🆕
│   │   │   └── ...
│   │   ├── drawable/
│   │   ├── values/
│   │   └── xml/
│   └── AndroidManifest.xml
└── ...
```

## 🔐 API Endpoints

### Authentication
```
POST /api/v1/employee/login
Body: { username, password }
Response: { token, user }
```

### Attendance
```
POST /api/v1/attendance/check-in
POST /api/v1/attendance/check-out
GET  /api/v1/attendance/history
POST /api/v1/attendance/override-request
```

### Settings
```
GET /api/v1/settings
GET /api/v1/server-time
GET /api/v1/jam-kerja
```

## 🧪 Testing

### Test Error Notification
1. Matikan internet
2. Coba kirim absensi
3. Verifikasi notifikasi + dialog muncul
4. Klik "Coba Lagi"
5. Verifikasi retry berjalan

### Test Success Notification
1. Login dengan koneksi normal
2. Kirim absensi
3. Verifikasi notifikasi sukses muncul

## 🐛 Troubleshooting

### Notifikasi tidak muncul
- Pastikan permission POST_NOTIFICATIONS diberikan (Android 13+)
- Check notification channel sudah dibuat
- Verifikasi di Settings → Apps → Notifications

### GPS tidak akurat
- Aktifkan High Accuracy mode di Settings
- Pastikan GPS permission diberikan
- Tunggu beberapa detik untuk stabilisasi

### Absensi gagal terkirim
- Check koneksi internet
- Verifikasi token masih valid
- Check server logs untuk error detail
- Gunakan tombol **"Coba Lagi"** dari dialog error
- Gunakan tombol **"Kembali"** untuk kembali ke halaman konfirmasi foto
- Ubah jenis absensi atau ambil foto ulang jika perlu

## 📝 Changelog

### Version 2.1.0 (2025-10-31)
- 🔙 **NEW**: Navigasi kembali ke halaman konfirmasi saat error
- 🎯 **NEW**: 3 opsi error handling: Retry, Kembali, Tutup
- 💾 **NEW**: Data foto & lokasi persistent saat kembali
- 🔧 Improved error dialog dengan lebih banyak opsi
- 🎨 Better UX untuk error recovery
- 📖 Dokumentasi lengkap error handling flow

### Version 2.0.0 (2025-10-30)
- ✨ **NEW**: Sistem notifikasi modern
- ✨ **NEW**: Dialog error custom dengan design modern
- ✨ **NEW**: Retry functionality dari notifikasi
- ✨ **NEW**: Smart error handling dengan solusi
- 🔧 Improved error messages
- 🔧 Better user feedback
- 🐛 Bug fixes untuk edge cases

### Version 1.0.0
- 🎉 Initial release
- ✅ Basic attendance features
- 📷 Camera integration
- 📍 GPS tracking
- 📊 History view

## 👥 Team

**PT Visdat Teknik Utama**
- Mobile Development Team
- Backend Team
- UI/UX Team

## 📄 License

Proprietary - PT Visdat Teknik Utama © 2025

## 📞 Support

Untuk bantuan teknis atau pertanyaan:
- **Email**: support@visdat.co.id
- **Phone**: +62 xxx xxxx xxxx
- **Internal**: IT Support Extension 123

---

**Made with ❤️ by PT Visdat Teknik Utama Development Team**
