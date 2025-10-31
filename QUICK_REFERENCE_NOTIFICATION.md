# 🚀 Quick Reference - Sistem Notifikasi

## 📋 Import yang Diperlukan

```java
import com.visdat.mobile.NotificationHelper;
import com.visdat.mobile.ModernErrorDialog;
import com.visdat.mobile.CustomToast;
```

## ⚡ Quick Start

### 1. Initialize Notification Channel
```java
// Di onCreate() activity
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.your_layout);
    
    // WAJIB: Initialize notification channel
    NotificationHelper.createNotificationChannel(this);
}
```

### 2. Show Error Notification
```java
NotificationHelper.showAttendanceFailureNotification(
    context,
    "Connection timeout",      // Error message
    "Cek In"                   // Jenis absensi
);
```

### 3. Show Error Dialog
```java
ModernErrorDialog.showAttendanceError(
    context,
    "Cek In",                  // Jenis absensi
    "Connection timeout",      // Error message
    () -> {
        // Retry action
        retryAbsensi();
    }
);
```

### 4. Show Success Notification
```java
NotificationHelper.showAttendanceSuccessNotification(
    context,
    "Cek Out"                  // Jenis absensi
);
```

## 🎯 Common Use Cases

### Use Case 1: Network Error
```java
try {
    sendToServer();
} catch (IOException e) {
    // Notifikasi
    NotificationHelper.showAttendanceFailureNotification(
        this, e.getMessage(), jenisAbsensi
    );
    
    // Dialog dengan retry
    ModernErrorDialog.showAttendanceError(
        this, jenisAbsensi, e.getMessage(),
        () -> sendToServer() // Retry
    );
}
```

### Use Case 2: HTTP Error Response
```java
if (response.code() >= 400) {
    String errorMsg = parseErrorMessage(response);
    
    NotificationHelper.showAttendanceFailureNotification(
        this, errorMsg, jenisAbsensi
    );
    
    ModernErrorDialog.showAttendanceError(
        this, jenisAbsensi, errorMsg,
        this::retryRequest
    );
}
```

### Use Case 3: Authentication Error
```java
if (response.code() == 401) {
    NotificationHelper.showAttendanceFailureNotification(
        this, "Sesi berakhir", jenisAbsensi
    );
    
    ModernErrorDialog.showAttendanceError(
        this, jenisAbsensi, 
        "Silakan login ulang",
        () -> redirectToLogin()
    );
}
```

### Use Case 4: Validation Error
```java
if (response.code() == 422) {
    String validationMsg = getValidationMessage(response);
    
    NotificationHelper.showAttendanceWarningNotification(
        this, "Validasi Gagal", validationMsg
    );
    
    ModernErrorDialog.showWarning(
        this, "Validasi Gagal", validationMsg,
        null // No action needed
    );
}
```

### Use Case 5: Success with Confirmation
```java
if (response.isSuccessful()) {
    NotificationHelper.showAttendanceSuccessNotification(
        this, jenisAbsensi
    );
    
    // Optional: Dialog sukses
    ModernErrorDialog.showSuccess(
        this,
        "Absensi Berhasil",
        "Data Anda telah tersimpan"
    );
}
```

## 🔄 Retry Pattern

### Pattern 1: Simple Retry
```java
ModernErrorDialog.showAttendanceError(
    this, jenisAbsensi, errorMsg,
    () -> {
        // Show loading
        showLoading();
        
        // Retry
        sendAbsensi();
    }
);
```

### Pattern 2: Retry with Counter
```java
private int retryCount = 0;
private static final int MAX_RETRY = 3;

private void handleError(String error) {
    if (retryCount < MAX_RETRY) {
        ModernErrorDialog.showAttendanceError(
            this, jenisAbsensi, 
            error + "\n\nPercobaan ke-" + (retryCount + 1),
            () -> {
                retryCount++;
                sendAbsensi();
            }
        );
    } else {
        // Max retry reached
        ModernErrorDialog.showWarning(
            this,
            "Gagal Setelah " + MAX_RETRY + " Kali",
            "Hubungi IT Support atau coba lagi nanti",
            null
        );
    }
}
```

### Pattern 3: Delayed Retry
```java
ModernErrorDialog.showAttendanceError(
    this, jenisAbsensi, errorMsg,
    () -> {
        // Retry after delay
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> sendAbsensi(),
            2000 // 2 seconds delay
        );
    }
);
```

## 📱 Notification Types

### Error (Red)
```java
NotificationHelper.showAttendanceFailureNotification(
    context, errorMsg, jenisAbsensi
);
```

### Success (Green)
```java
NotificationHelper.showAttendanceSuccessNotification(
    context, jenisAbsensi
);
```

### Warning (Yellow)
```java
NotificationHelper.showAttendanceWarningNotification(
    context, title, message
);
```

## 🎨 Dialog Types

### Error Dialog
```java
ModernErrorDialog.showAttendanceError(
    context, jenisAbsensi, errorMsg, retryAction
);
```

### Success Dialog
```java
ModernErrorDialog.showSuccess(
    context, title, message
);
```

### Warning Dialog
```java
ModernErrorDialog.showWarning(
    context, title, message, action
);
```

## 🛠️ Utility Methods

### Cancel Notifications
```java
// Cancel all
NotificationHelper.cancelAllNotifications(context);

// Cancel specific
NotificationHelper.cancelNotification(context, notificationId);
```

### Parse Error Message
```java
private String parseErrorMessage(Response response) {
    try {
        String body = response.body().string();
        JSONObject json = new JSONObject(body);
        return json.optString("message", "Unknown error");
    } catch (Exception e) {
        return "Error: " + response.code();
    }
}
```

## ⚠️ Common Mistakes

### ❌ SALAH
```java
// Tidak initialize channel
NotificationHelper.showAttendanceFailureNotification(...);
// ERROR: Channel not created!
```

### ✅ BENAR
```java
// Initialize dulu
NotificationHelper.createNotificationChannel(this);

// Baru tampilkan notifikasi
NotificationHelper.showAttendanceFailureNotification(...);
```

---

### ❌ SALAH
```java
// Retry tanpa loading indicator
ModernErrorDialog.showAttendanceError(
    this, jenisAbsensi, error,
    () -> sendToServer() // User tidak tahu sedang retry
);
```

### ✅ BENAR
```java
ModernErrorDialog.showAttendanceError(
    this, jenisAbsensi, error,
    () -> {
        CustomToast.showToast(this, "Mencoba lagi...", Toast.LENGTH_SHORT);
        sendToServer();
    }
);
```

---

### ❌ SALAH
```java
// Error message tidak jelas
NotificationHelper.showAttendanceFailureNotification(
    this, "Error", jenisAbsensi
);
```

### ✅ BENAR
```java
NotificationHelper.showAttendanceFailureNotification(
    this, 
    "Connection timeout - Tidak dapat terhubung ke server", 
    jenisAbsensi
);
```

## 🎯 Best Practices

### 1. Always Initialize
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    NotificationHelper.createNotificationChannel(this);
}
```

### 2. Provide Clear Messages
```java
// Good
"Connection timeout - Periksa koneksi internet Anda"

// Bad
"Error 500"
```

### 3. Always Offer Retry
```java
ModernErrorDialog.showAttendanceError(
    context, jenisAbsensi, error,
    () -> retry() // Always provide retry option
);
```

### 4. Cancel When Done
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    NotificationHelper.cancelAllNotifications(this);
}
```

### 5. Handle Permissions
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) 
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
            REQUEST_CODE
        );
    }
}
```

## 📊 Error Code Mapping

```java
switch (statusCode) {
    case 400: return "Permintaan tidak valid";
    case 401: return "Sesi berakhir - Login ulang";
    case 403: return "Akses ditolak";
    case 404: return "Endpoint tidak ditemukan";
    case 422: return "Data tidak valid";
    case 500: return "Server error";
    case 502: return "Gateway error";
    case 503: return "Service unavailable";
    default:  return "Error " + statusCode;
}
```

## 🔗 Links

- [Full Documentation](NOTIFICATION_FEATURE.md)
- [Examples](NotificationExampleUsage.java)
- [Main README](Readme.md)

---

**Quick Ref v1.0** | Last Updated: 2025-10-30
