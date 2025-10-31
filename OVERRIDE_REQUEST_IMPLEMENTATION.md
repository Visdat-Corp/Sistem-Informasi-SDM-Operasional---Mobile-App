# Override Request Implementation - Mobile App

## 📱 Overview
Implementasi fitur override request untuk menangani **terlambat check-in** dan **pulang cepat** di aplikasi mobile Android.

## ✅ What Has Been Changed

### 1. **HistoryStore.java**
- ✅ Tambah field `id` (int) - untuk menyimpan ID absensi dari server
- ✅ Tambah field `overrideStatus` (String) - untuk status override (pending/approved/rejected)
- ✅ Update constructor dengan backward compatibility

### 2. **HistoryFragment.java**
- ✅ Parse `id` dan `override_status` dari API response
- ✅ Tambah button "Request Override" di setiap item history
- ✅ Tambah TextView untuk menampilkan status override (pending/approved/rejected)
- ✅ Implementasi `showOverrideRequestDialog()` - dialog dengan:
  - Dropdown pilihan jenis: "Terlambat Check In" atau "Pulang Cepat"
  - Input field untuk alasan (min. 10 karakter)
- ✅ Implementasi `sendOverrideRequest()` - kirim request ke API baru dengan parameter:
  - `id_absensi` (int)
  - `override_type` (string: "late_check_in" atau "early_check_out")
  - `reason` (string)

### 3. **item_history.xml**
- ✅ Tambah `Button btnRequestOverride` - untuk request override
- ✅ Tambah `TextView tvOverrideStatus` - untuk tampilkan status override
- ✅ Styling dengan warna orange untuk button, berbagai warna untuk status

### 4. **SummaryPage.java**
- ✅ Hide button override di summary page (tidak relevan lagi)
- ✅ Override request sekarang hanya bisa dari History page

---

## 🎯 User Flow

### Scenario 1: Request Override untuk Terlambat Check In

1. User buka **History** page (Navigation menu → History)
2. Lihat list absensi yang sudah pernah dilakukan
3. Pada item absensi yang ingin di-override, klik **"Request Override"**
4. Dialog muncul dengan:
   - Dropdown: Pilih "Terlambat Check In"
   - Input field: Isi alasan, contoh: *"Terlambat karena pergi ke lokasi project lalu balik ke kantor untuk absen"*
5. Klik **"Kirim Request"**
6. Toast muncul: *"✅ Permintaan override berhasil dikirim! Menunggu approval Manager SDM."*
7. Status berubah menjadi: **"⏳ Menunggu Approval Manager"** (warna orange)

### Scenario 2: Request Override untuk Pulang Cepat

1. User buka **History** page
2. Klik **"Request Override"** pada absensi yang ingin di-override
3. Dialog muncul, pilih **"Pulang Cepat"** dari dropdown
4. Isi alasan: *"Check out cepat karena ingin pergi ke lokasi project"*
5. Klik **"Kirim Request"**
6. Status: **"⏳ Menunggu Approval Manager"**

### Scenario 3: Melihat Status Override

Setelah Manager SDM approve/reject di web admin:

- **Approved**: Status berubah jadi **"✅ Override Disetujui"** (hijau)
- **Rejected**: Status berubah jadi **"❌ Override Ditolak"** (merah), button "Request Ulang Override" muncul
- **Pending**: Status **"⏳ Menunggu Approval Manager"** (orange), button request disembunyikan

---

## 🔧 Technical Details

### API Endpoint
```
POST /api/v1/attendance/override-request
```

### Request Body
```json
{
  "id_absensi": 123,
  "override_type": "late_check_in",
  "reason": "Terlambat karena pergi ke lokasi project lalu balik ke kantor"
}
```

### Response (Success)
```json
{
  "success": true,
  "message": "Permintaan override telah dikirim. Menunggu persetujuan Manager SDM.",
  "data": {
    "absensi_id": 123,
    "status": "pending",
    "override_type": "late_check_in"
  }
}
```

### Button Visibility Logic
Button "Request Override" akan muncul HANYA jika:
- ✅ Item memiliki `id` (dari server, bukan lokal)
- ✅ Check in sudah dilakukan (`jamHadir` bukan "-")
- ✅ Belum ada override request yang pending/approved

Button akan **DISEMBUNYIKAN** jika:
- ❌ Status override = "pending" (menunggu approval)
- ❌ Status override = "approved" (sudah disetujui)

Button "Request Ulang Override" akan muncul jika:
- ⚠️ Status override = "rejected" (ditolak, boleh request ulang)

---

## 🎨 UI/UX

### Status Colors:
- **Pending** (⏳): Orange light (`android.R.color.holo_orange_light`)
- **Approved** (✅): Green light (`android.R.color.holo_green_light`)
- **Rejected** (❌): Red light (`android.R.color.holo_red_light`)

### Button Style:
- **Request Override**: Orange dark (`android.R.color.holo_orange_dark`)
- Text: White, bold

### Dialog:
- Title: "Request Override Absensi"
- Dropdown: Material spinner
- Input: Multi-line EditText (3-5 lines)
- Buttons: "Kirim Request" (positive) | "Batal" (negative)

---

## ⚠️ Important Notes

### Validation Rules:
- ✅ Alasan minimal **10 karakter**
- ✅ ID absensi harus valid dan milik user yang login
- ✅ Override type hanya boleh: `late_check_in` atau `early_check_out`
- ✅ Tidak bisa request override jika sudah ada yang pending

### Error Handling:
- Network error → Dialog dengan tombol "Coba Lagi"
- Validation error → Toast singkat
- Server error → Dialog dengan detail error message

### Refresh Data:
- Setelah berhasil kirim override → Auto refresh history list
- Pull to refresh → Clear cache dan fetch ulang dari server

---

## 🧪 Testing Checklist

- [ ] Open History page, pastikan list muncul dari server
- [ ] Klik "Request Override" pada item yang valid
- [ ] Dialog muncul dengan dropdown dan input field
- [ ] Pilih "Terlambat Check In", isi alasan < 10 char → Button disabled
- [ ] Isi alasan >= 10 char → Button enabled
- [ ] Klik "Kirim Request" → Toast success muncul
- [ ] Status berubah jadi "⏳ Menunggu Approval Manager"
- [ ] Button "Request Override" hilang untuk item tersebut
- [ ] Manager approve di web → Refresh history → Status jadi "✅ Override Disetujui"
- [ ] Manager reject di web → Refresh history → Status jadi "❌ Override Ditolak"
- [ ] Button "Request Ulang Override" muncul untuk yang rejected
- [ ] Test dengan "Pulang Cepat" override type
- [ ] Test error handling: no network, server error, validation error

---

## 📚 Related Files

**Modified Files:**
- `app/src/main/java/com/visdat/mobile/HistoryStore.java`
- `app/src/main/java/com/visdat/mobile/HistoryFragment.java`
- `app/src/main/java/com/visdat/mobile/SummaryPage.java`
- `app/src/main/res/layout/item_history.xml`

**Backend API:**
- `app/Http/Controllers/EmployeeController.php` → `apiRequestOverride()`
- `routes/api.php` → `/v1/attendance/override-request`

**Documentation:**
- `/OVERRIDE_REQUEST_UPDATE.md` (Backend API documentation)

---

## 🚀 Deployment Notes

### Build Commands:
```bash
cd Sistem-Informasi-SDM-Operasional---Mobile-App
./gradlew clean
./gradlew assembleRelease
```

### APK Location:
```
app/build/outputs/apk/release/app-release.apk
```

### Version Update:
Update `build.gradle.kts`:
```kotlin
versionCode = 3
versionName = "1.2.0"
```

---

**Last Updated:** October 31, 2025  
**Version:** 1.2.0  
**Feature:** Override Request for Late Check-In and Early Check-Out
