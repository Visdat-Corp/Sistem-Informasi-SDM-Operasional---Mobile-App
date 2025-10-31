# Auto-Detect Override Request - Update Documentation

## 🎯 Overview
**Update terbaru:** Sistem sekarang **otomatis mendeteksi** keterlambatan check-in atau kepulangan cepat, dan langsung menampilkan dialog untuk request override.

## ✅ What Changed

### Backend (Laravel API)

#### 1. **EmployeeController.php - apiCheckIn()**
**Tambahan di response:**
```php
'is_late' => $isLate,                    // boolean
'late_minutes' => $lateMinutes,          // int
'normal_check_in_time' => '08:00:00',    // string (jam kerja normal)
```

**Logic deteksi terlambat:**
- Ambil `jam_masuk_normal` dari JamKerja
- Tambah `toleransi_keterlambatan`
- Jika waktu check-in > batas waktu → `is_late = true`
- Hitung selisih menit

#### 2. **EmployeeController.php - apiCheckOut()**
**Tambahan di response:**
```php
'is_early' => $isEarly,                  // boolean
'early_minutes' => $earlyMinutes,        // int
'normal_check_out_time' => '17:00:00',   // string (jam pulang normal)
```

**Logic deteksi pulang cepat:**
- Ambil `jam_keluar_normal` dari JamKerja
- Kurangi `toleransi_pulang_cepat`
- Jika waktu check-out < batas waktu → `is_early = true`
- Hitung selisih menit

---

### Mobile App (Android)

#### 1. **SummaryPage.java - onResponse()**
**Parse response dan detect:**
```java
boolean isLate = data.optBoolean("is_late", false);
boolean isEarly = data.optBoolean("is_early", false);
int attendanceId = data.optInt("attendance_id", 0);

if (isLate && attendanceId > 0) {
    showOverrideDialog(attendanceId, "late_check_in", lateMinutes, normalTime);
} else if (isEarly && attendanceId > 0) {
    showOverrideDialog(attendanceId, "early_check_out", earlyMinutes, normalTime);
}
```

#### 2. **New Methods Added:**

**a. showOverrideDialog()** - Alert pertama
- Tampilkan informasi keterlambatan/kepulangan cepat
- Tanya: "Apakah ingin ajukan override?"
- Button: "Ya, Ajukan Override" | "Tidak, Nanti Saja"

**b. showOverrideReasonDialog()** - Input alasan
- TextField untuk input alasan (min. 10 karakter)
- Placeholder sesuai jenis override
- Button: "Kirim" | "Batal"

**c. sendOverrideRequestNew()** - Kirim ke API
- POST ke `/v1/attendance/override-request`
- Parameter: `id_absensi`, `override_type`, `reason`
- Handle success/error response

---

## 🎬 New User Flow

### Scenario 1: Check-In Terlambat

1. **User check-in** pukul 08:30 (jam normal: 08:00, toleransi: 15 menit)
2. **Server detect:** Terlambat 30 menit
3. **Response API:**
   ```json
   {
     "success": true,
     "data": {
       "attendance_id": 123,
       "is_late": true,
       "late_minutes": 30,
       "normal_check_in_time": "08:00:00"
     }
   }
   ```
4. **Mobile app:** Toast "✅ Berhasil kirim ke server"
5. **Dialog 1 muncul otomatis:**
   ```
   ⚠️ Terlambat Check In
   
   Anda check in 30 menit lebih lambat dari jam 
   kerja normal (08:00:00).
   
   Apakah Anda ingin mengajukan override untuk 
   keterlambatan ini?
   
   [Ya, Ajukan Override] [Tidak, Nanti Saja]
   ```
6. **User klik "Ya"**
7. **Dialog 2 muncul:**
   ```
   Alasan Override
   
   Alasan (min. 10 karakter):
   ┌────────────────────────────────────┐
   │ Terlambat karena pergi ke lokasi  │
   │ project lalu balik ke kantor...    │
   └────────────────────────────────────┘
   
   [Kirim] [Batal]
   ```
8. **User isi alasan dan klik "Kirim"**
9. **Toast:** "✅ Permintaan override berhasil dikirim! Menunggu approval Manager SDM."

### Scenario 2: Check-Out Lebih Cepat

1. **User check-out** pukul 16:30 (jam normal: 17:00, toleransi: 15 menit)
2. **Server detect:** Pulang 30 menit lebih cepat
3. **Response API:**
   ```json
   {
     "success": true,
     "data": {
       "attendance_id": 123,
       "is_early": true,
       "early_minutes": 30,
       "normal_check_out_time": "17:00:00"
     }
   }
   ```
4. **Dialog muncul:** "⚠️ Pulang Lebih Cepat"
5. **User klik "Ya, Ajukan Override"**
6. **Input alasan:** "Check out cepat karena ingin pergi ke lokasi project"
7. **Kirim request**

### Scenario 3: User Menolak Override

1. **Dialog muncul** (late/early detected)
2. **User klik "Tidak, Nanti Saja"**
3. **Toast:** "Anda dapat mengajukan override dari menu History"
4. **User bisa request override kapan saja dari History page**

---

## 📊 Response Examples

### Check-In Success (Normal - Tidak Terlambat)
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "attendance_id": 123,
    "check_in_time": "07:55:00",
    "date": "2025-10-31",
    "location": "Kantor Pusat",
    "coordinates": "-6.200000,106.816666",
    "photo_url": "http://domain.com/storage/attendance_photos/photo.jpg",
    "is_late": false,
    "late_minutes": 0,
    "normal_check_in_time": "08:00:00"
  }
}
```

### Check-In Success (Terlambat)
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "attendance_id": 124,
    "check_in_time": "08:30:00",
    "date": "2025-10-31",
    "location": "Kantor Pusat",
    "coordinates": "-6.200000,106.816666",
    "photo_url": "http://domain.com/storage/attendance_photos/photo.jpg",
    "is_late": true,
    "late_minutes": 30,
    "normal_check_in_time": "08:00:00"
  }
}
```

### Check-Out Success (Pulang Cepat)
```json
{
  "success": true,
  "message": "Check-out successful",
  "data": {
    "attendance_id": 124,
    "check_out_time": "16:30:00",
    "date": "2025-10-31",
    "location": "Kantor Pusat",
    "coordinates": "-6.200000,106.816666",
    "work_duration": {
      "hours": 8,
      "minutes": 0,
      "total_minutes": 480
    },
    "photo_url": "http://domain.com/storage/attendance_photos/photo.jpg",
    "is_early": true,
    "early_minutes": 30,
    "normal_check_out_time": "17:00:00"
  }
}
```

---

## 🎨 UI/UX Details

### Dialog 1: Confirmation Alert

**Title Colors:**
- ⚠️ Orange untuk warning (late/early)

**Message Format:**
```
Anda [action] [X] menit [comparison] dari jam 
[normal_time].

Apakah Anda ingin mengajukan override untuk 
[problem] ini?
```

**Buttons:**
- **Positive:** "Ya, Ajukan Override" (orange)
- **Negative:** "Tidak, Nanti Saja" (gray)

### Dialog 2: Reason Input

**Title:** "Alasan Override"

**Input Field:**
- Multi-line (3-5 lines)
- Hint/Placeholder berbeda untuk late vs early
- Auto-focus ketika dialog muncul

**Validation:**
- Min. 10 characters
- Jika < 10 → Toast error + dialog muncul lagi

**Buttons:**
- **Positive:** "Kirim" (enabled only if length >= 10)
- **Negative:** "Batal"

---

## 🔧 Technical Implementation

### Backend Changes
```php
// File: app/Http/Controllers/EmployeeController.php

// After determine status (line ~367)
$isLate = false;
$lateMinutes = 0;
if ($jamKerja && $jamKerja->jam_masuk_normal && !$isDinasLuar && !$isLembur) {
    $jamMasukNormal = Carbon::parse($jamKerja->jam_masuk_normal);
    $toleransi = $jamKerja->toleransi_keterlambatan ?? 0;
    $batasWaktu = $jamMasukNormal->copy()->addMinutes($toleransi);
    
    if ($currentTime->gt($batasWaktu)) {
        $isLate = true;
        $lateMinutes = $currentTime->diffInMinutes($jamMasukNormal);
    }
}

$responseData = [
    // ... existing fields
    'is_late' => $isLate,
    'late_minutes' => $lateMinutes,
    'normal_check_in_time' => $jamKerja && $jamKerja->jam_masuk_normal 
        ? $jamKerja->jam_masuk_normal 
        : null,
];
```

### Mobile App Changes
```java
// File: SummaryPage.java

// Parse response (in onResponse callback)
org.json.JSONObject data = jsonResponse.optJSONObject("data");
if (data != null) {
    int attendanceId = data.optInt("attendance_id", 0);
    boolean isLate = data.optBoolean("is_late", false);
    int lateMinutes = data.optInt("late_minutes", 0);
    String normalTime = data.optString("normal_check_in_time", null);
    
    if (isLate && attendanceId > 0) {
        showOverrideDialog(attendanceId, "late_check_in", 
            lateMinutes, normalTime);
    }
}
```

---

## ⚠️ Edge Cases Handled

### 1. No JamKerja Configuration
- `is_late` = false
- `is_early` = false
- No override dialog shown

### 2. Dinas Luar / Lembur
- Skip late detection
- Dinas luar tidak perlu override
- Lembur tidak dianggap terlambat

### 3. User Rejects Override
- Toast shown: "Anda dapat mengajukan override dari menu History"
- Data tetap tersimpan
- Bisa request override kapan saja dari History

### 4. Network Error During Override Request
- Dialog dengan tombol "Coba Lagi"
- Bisa retry tanpa kehilangan data alasan

### 5. Validation Error
- Alasan < 10 karakter → Toast + dialog muncul lagi
- Reason di-preserve (user tidak perlu ketik ulang)

---

## 🧪 Testing Checklist

- [ ] Check-in normal (< toleransi) → No dialog
- [ ] Check-in terlambat → Dialog muncul otomatis
- [ ] Klik "Ya, Ajukan Override" → Input dialog muncul
- [ ] Isi alasan < 10 char → Toast error
- [ ] Isi alasan >= 10 char → Request terkirim
- [ ] Klik "Tidak, Nanti Saja" → Toast info, tidak ada request
- [ ] Check-out normal → No dialog
- [ ] Check-out terlambat → Dialog muncul
- [ ] Network error saat kirim → Dialog retry
- [ ] Server error → Dialog dengan pesan error
- [ ] Success → Toast + auto refresh history
- [ ] Dinas luar → No override dialog
- [ ] Lembur → No override dialog

---

## 📁 Modified Files

### Backend:
- `app/Http/Controllers/EmployeeController.php`
  - Method: `apiCheckIn()` (lines ~367-410)
  - Method: `apiCheckOut()` (lines ~568-610)

### Mobile App:
- `SummaryPage.java`
  - Import: `android.widget.LinearLayout`
  - Method: `onResponse()` - parse & detect (lines ~1383-1418)
  - Method: `showOverrideDialog()` - NEW (lines ~1783-1817)
  - Method: `showOverrideReasonDialog()` - NEW (lines ~1819-1857)
  - Method: `sendOverrideRequestNew()` - NEW (lines ~1859-1929)

---

## 🚀 Deployment

### Backend:
No migration needed. Just deploy updated controller.

### Mobile App:
```bash
cd Sistem-Informasi-SDM-Operasional---Mobile-App
./gradlew clean
./gradlew assembleRelease
```

### Version:
- Backend: No version change
- Mobile: Update to **v1.3.0**

---

**Last Updated:** October 31, 2025  
**Feature:** Auto-Detect Late Check-In & Early Check-Out with Override Request Dialog  
**Status:** ✅ Implemented and Ready for Testing
