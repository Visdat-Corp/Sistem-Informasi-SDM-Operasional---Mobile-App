package com.visdat.mobile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import okhttp3.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SummaryPage extends AppCompatActivity {
    // UI Components - hanya yang ada di XML
    private ImageView imageProfile, imageCheckmark;
    private TextView textTimestamp, textJamKehadiran, textWaktuTerlambat, textLokasi, textKoordinat, textJenisAbsensi;
    private TextView textJamKehadiranLabel, textWaktuTerlambatLabel, textLokasiLabel, textKoordinatLabel;
    private android.widget.Button btnRequestOverride, btnRetryLocation;

    // Data variables
    private Uri photoUri;
    private long timestamp;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private String jenisAbsensi = "";
    private boolean isAutoSelected = false;
    private String locationName = "";

    // Work hours settings
    private String jamMasukNormal = "08:00:00";
    private String jamKeluarNormal = "17:00:00";
    private int toleransiKeterlambatan = 15; // minutes
    private int toleransiPulangCepat = 30; // minutes

    private OkHttpClient client;
    private android.content.SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary_page);

        client = ApiClient.getClient(getApplicationContext());
        sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        // Initialize notification channel untuk notifikasi modern
        NotificationHelper.createNotificationChannel(this);

        initializeViews();
        loadData();
        fetchWorkHoursSettings();
        updateLocationUI();
        setupClickListeners();
        animateEntry();
    }

    private void initializeViews() {
        try {
            // Main components - hanya yang ada di XML
            imageProfile = findViewById(R.id.imageProfile);
            imageCheckmark = findViewById(R.id.imageCheckmark);

            // Text components
            textTimestamp = findViewById(R.id.textTimestamp);
            textJamKehadiran = findViewById(R.id.textJamKehadiran);
            textWaktuTerlambat = findViewById(R.id.textWaktuTerlambat);
            textLokasi = findViewById(R.id.textLokasi);
            textKoordinat = findViewById(R.id.textKoordinat);
            btnRequestOverride = findViewById(R.id.btnRequestOverride);
            btnRetryLocation = findViewById(R.id.btnRetryLocation);
            textJenisAbsensi = findViewById(R.id.textJenisAbsensi);

            // Label components
            textJamKehadiranLabel = findViewById(R.id.textJamKehadiranLabel);
            textWaktuTerlambatLabel = findViewById(R.id.textWaktuTerlambatLabel);
            textLokasiLabel = findViewById(R.id.textLokasiLabel);
            textKoordinatLabel = findViewById(R.id.textKoordinatLabel);

            Log.d("SummaryPage", "All views initialized successfully");

        } catch (Exception e) {
            CustomToast.showToast(this, "Error initializing views: " + e.getMessage(), Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    private void loadData() {
        try {
            // Get data from intent
            Intent intent = getIntent();
            String photoUriString = intent.getStringExtra("photo_uri");
            long localTimestamp = intent.getLongExtra("timestamp", System.currentTimeMillis());
            jenisAbsensi = intent.getStringExtra("jenis_absensi");
            isAutoSelected = intent.getBooleanExtra("is_auto_selected", false);
            latitude = intent.getDoubleExtra("latitude", 0.0);
            longitude = intent.getDoubleExtra("longitude", 0.0);

            // Jika data tidak ada di intent, coba restore dari SharedPreferences
            if (photoUriString == null || photoUriString.isEmpty()) {
                Log.d("SummaryPage", "No data in intent, attempting to restore from SharedPreferences");
                restorePendingAttendanceData();
            } else {
                // Data baru dari intent, simpan ke SharedPreferences untuk backup
                savePendingAttendanceData(photoUriString, localTimestamp, jenisAbsensi, isAutoSelected, latitude, longitude);
            }

            // Auto-determine if not provided
            if (jenisAbsensi == null || jenisAbsensi.isEmpty()) {
                jenisAbsensi = determineAttendanceType();
                isAutoSelected = true;
            }

            // Load photo if available
            if (photoUriString != null) {
                photoUri = Uri.parse(photoUriString);
                loadPhoto();
            } else if (photoUri != null) {
                // Photo URI sudah di-set dari restore
                loadPhoto();
            }

            // Fetch server time for timestamp
            fetchServerTimeAndSetTimestamp(localTimestamp);

            // Update UI berdasarkan jenis absensi
            updateJenisAbsensiDisplay();
            adjustUIByAbsensiType();

            // Set lokasi
            if (textLokasi != null) {
                textLokasi.setText("Mendeteksi lokasi...");
            }
            if (textKoordinat != null) {
                textKoordinat.setText("Latitude: -\nLongitude: -");
            }
        } catch (Exception e) {
            CustomToast.showToast(this, "Error loading data: " + e.getMessage(), Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    /**
     * Simpan data absensi yang pending ke SharedPreferences
     * Untuk di-restore nanti jika user kembali dari notification
     */
    private void savePendingAttendanceData(String photoUriString, long timestamp, String jenisAbsensi, 
                                          boolean isAutoSelected, double latitude, double longitude) {
        try {
            android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("pending_photo_uri", photoUriString);
            editor.putLong("pending_timestamp", timestamp);
            editor.putString("pending_jenis_absensi", jenisAbsensi);
            editor.putBoolean("pending_is_auto_selected", isAutoSelected);
            editor.putString("pending_latitude", String.valueOf(latitude));
            editor.putString("pending_longitude", String.valueOf(longitude));
            editor.putLong("pending_saved_at", System.currentTimeMillis());
            editor.apply();
            
            Log.d("SummaryPage", "Pending attendance data saved to SharedPreferences");
        } catch (Exception e) {
            Log.e("SummaryPage", "Error saving pending attendance data: " + e.getMessage(), e);
        }
    }

    /**
     * Restore data absensi dari SharedPreferences
     * Digunakan ketika user kembali dari notification "Coba Lagi"
     */
    private void restorePendingAttendanceData() {
        try {
            String photoUriString = sharedPreferences.getString("pending_photo_uri", null);
            long savedTimestamp = sharedPreferences.getLong("pending_timestamp", System.currentTimeMillis());
            String savedJenisAbsensi = sharedPreferences.getString("pending_jenis_absensi", null);
            boolean savedIsAutoSelected = sharedPreferences.getBoolean("pending_is_auto_selected", false);
            String savedLatitude = sharedPreferences.getString("pending_latitude", "0.0");
            String savedLongitude = sharedPreferences.getString("pending_longitude", "0.0");
            long savedAt = sharedPreferences.getLong("pending_saved_at", 0);
            
            // Cek apakah data masih valid (tidak lebih dari 1 jam)
            long currentTime = System.currentTimeMillis();
            long oneHourInMillis = 60 * 60 * 1000;
            
            if (savedAt > 0 && (currentTime - savedAt) < oneHourInMillis && photoUriString != null) {
                // Data masih valid, restore
                photoUri = Uri.parse(photoUriString);
                timestamp = savedTimestamp;
                jenisAbsensi = savedJenisAbsensi;
                isAutoSelected = savedIsAutoSelected;
                latitude = Double.parseDouble(savedLatitude);
                longitude = Double.parseDouble(savedLongitude);
                
                Log.d("SummaryPage", "Pending attendance data restored from SharedPreferences");
                CustomToast.showToast(this, "✅ Data absensi dipulihkan", Toast.LENGTH_SHORT);
            } else {
                Log.d("SummaryPage", "No valid pending attendance data found or data expired");
            }
        } catch (Exception e) {
            Log.e("SummaryPage", "Error restoring pending attendance data: " + e.getMessage(), e);
        }
    }

    /**
     * Hapus data pending setelah berhasil submit
     */
    private void clearPendingAttendanceData() {
        try {
            android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("pending_photo_uri");
            editor.remove("pending_timestamp");
            editor.remove("pending_jenis_absensi");
            editor.remove("pending_is_auto_selected");
            editor.remove("pending_latitude");
            editor.remove("pending_longitude");
            editor.remove("pending_saved_at");
            editor.apply();
            
            Log.d("SummaryPage", "Pending attendance data cleared");
        } catch (Exception e) {
            Log.e("SummaryPage", "Error clearing pending attendance data: " + e.getMessage(), e);
        }
    }

    private void fetchWorkHoursSettings() {
        new Thread(() -> {
            try {
                // Fetch jam kerja data directly from database via API
                org.json.JSONObject jamKerjaData = ApiClient.getJamKerja(this);
                if (jamKerjaData != null) {
                    jamMasukNormal = jamKerjaData.optString("jam_masuk_normal", "08:00:00");
                    jamKeluarNormal = jamKerjaData.optString("jam_keluar_normal", "17:00:00");
                    toleransiKeterlambatan = jamKerjaData.optInt("toleransi_keterlambatan", 15);
                    toleransiPulangCepat = jamKerjaData.optInt("toleransi_pulang_cepat", 30);

                    Log.d("SummaryPage", "Fetched jam kerja from database: masuk=" + jamMasukNormal + ", keluar=" + jamKeluarNormal + ", toleransi=" + toleransiKeterlambatan + ", toleransi pulang cepat=" + toleransiPulangCepat);

                    runOnUiThread(() -> {
                        // Re-adjust UI with fetched work hours
                        adjustUIByAbsensiType();
                    });
                } else {
                    Log.w("SummaryPage", "Jam kerja data is null, using defaults");
                }
            } catch (Exception e) {
                Log.e("SummaryPage", "Failed to fetch jam kerja settings, using defaults", e);
            }
        }).start();
    }

    private String determineAttendanceType() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);

        int currentTimeInMinutes = currentHour * 60 + currentMinute;

        // Waktu absensi otomatis
        int checkinStart = 6 * 60;   // 6:00 AM
        int checkinEnd = 13 * 60;    // 1:00 PM
        int checkoutStart = 13 * 60; // 1:00 PM
        int checkoutEnd = 17 * 60;   // 5:00 PM
        int overtimeStart = 17 * 60; // 5:00 PM

        if (currentTimeInMinutes >= checkinStart && currentTimeInMinutes < checkinEnd) {
            return "Cek In";
        } else if (currentTimeInMinutes >= checkoutStart && currentTimeInMinutes < checkoutEnd) {
            return "Cek Out";
        } else if (currentTimeInMinutes >= overtimeStart) {
            return "Lembur";
        } else {
            return "Cek In"; // Default
        }
    }

    private void updateJenisAbsensiDisplay() {
        if (textJenisAbsensi == null) return;

        String displayText = "Jenis Absensi: " + jenisAbsensi;
        if (isAutoSelected) {
            displayText += " (Auto)";
        }

        textJenisAbsensi.setText(displayText);

        // Set warna berdasarkan jenis absensi
        switch (jenisAbsensi) {
            case "Cek In":
                textJenisAbsensi.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                break;
            case "Cek Out":
                textJenisAbsensi.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                break;
            case "Lembur":
                textJenisAbsensi.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                break;
            default:
                textJenisAbsensi.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                break;
        }
    }

    private void adjustUIByAbsensiType() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTimeStr = timeFormat.format(new Date(timestamp));
        long lastCheckInTime = HistoryStore.getLastCheckInTimestamp(this);

        Calendar timestampCal = Calendar.getInstance();
        timestampCal.setTimeInMillis(timestamp);

        // Tampilkan tombol override hanya untuk Dinas Luar
        if (btnRequestOverride != null) {
            // Hide override button - override should be requested from History page
            btnRequestOverride.setVisibility(android.view.View.GONE);
        }

        switch (jenisAbsensi) {
            case "Cek In":
                setupCheckInUI(currentTimeStr, timestampCal);
                break;
            case "Cek Out":
                setupCheckOutUI(currentTimeStr, timestampCal, lastCheckInTime);
                break;
            case "Lembur":
                setupLemburUI(currentTimeStr, timestampCal);
                break;
            default:
                setupDefaultUI(currentTimeStr);
                break;
        }
    }

    private void setupCheckInUI(String currentTimeStr, Calendar timestampCal) {
        // Update labels untuk Cek In
        if (textJamKehadiranLabel != null) {
            textJamKehadiranLabel.setText("Jam Masuk");
        }
        if (textJamKehadiran != null) {
            textJamKehadiran.setText(currentTimeStr + " WIT");
        }
        if (textWaktuTerlambatLabel != null) {
            textWaktuTerlambatLabel.setText("Status Kehadiran");
        }

        // Hitung status kehadiran menggunakan jam masuk standar dari database
        if (textWaktuTerlambat != null) {
            try {
                // Parse jam masuk normal dari database
                String[] parts = jamMasukNormal.split(":");
                int jamMasukHour = Integer.parseInt(parts[0]);
                int jamMasukMinute = Integer.parseInt(parts[1]);

                Calendar jamMasuk = Calendar.getInstance();
                jamMasuk.setTimeInMillis(timestamp);
                jamMasuk.set(Calendar.HOUR_OF_DAY, jamMasukHour);
                jamMasuk.set(Calendar.MINUTE, jamMasukMinute);
                jamMasuk.set(Calendar.SECOND, 0);

                // Tambahkan toleransi keterlambatan
                jamMasuk.add(Calendar.MINUTE, toleransiKeterlambatan);

                if (timestampCal.after(jamMasuk)) {
                    long diff = timestamp - jamMasuk.getTimeInMillis();
                    long hours = diff / (1000 * 60 * 60);
                    long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
                    long seconds = (diff % (1000 * 60)) / 1000;
                    textWaktuTerlambat.setText(String.format("TERLAMBAT: %02d:%02d:%02d", hours, minutes, seconds));
                    textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                } else {
                    long diff = jamMasuk.getTimeInMillis() - timestamp;
                    long hours = diff / (1000 * 60 * 60);
                    long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
                    if (hours > 0 || minutes > 0) {
                        textWaktuTerlambat.setText(String.format("Lebih awal: %02d:%02d", hours, minutes));
                        textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                    } else {
                        textWaktuTerlambat.setText("TEPAT WAKTU!");
                        textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                    }
                }
            } catch (Exception e) {
                Log.e("SummaryPage", "Error calculating late time", e);
                textWaktuTerlambat.setText("Error menghitung status");
                textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            }
        }
    }

    private void setupCheckOutUI(String currentTimeStr, Calendar timestampCal, long lastCheckInTime) {
        // Update labels untuk Cek Out
        if (textJamKehadiranLabel != null) {
            textJamKehadiranLabel.setText("Jam Pulang");
        }
        if (textJamKehadiran != null) {
            textJamKehadiran.setText(currentTimeStr + " WIT");
        }
        if (textWaktuTerlambatLabel != null) {
            textWaktuTerlambatLabel.setText("Durasi Kerja");
        }

        // Hitung durasi kerja
        if (textWaktuTerlambat != null) {
            if (lastCheckInTime > 0) {
                long diff = timestamp - lastCheckInTime;
                long hours = diff / (1000 * 60 * 60);
                long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
                textWaktuTerlambat.setText(String.format("%02d jam %02d menit", hours, minutes));
            } else {
                // Estimasi berdasarkan jam standar
                Calendar jamMasuk = Calendar.getInstance();
                jamMasuk.setTimeInMillis(timestamp);
                jamMasuk.set(Calendar.HOUR_OF_DAY, 8);
                jamMasuk.set(Calendar.MINUTE, 0);
                jamMasuk.set(Calendar.SECOND, 0);

                long diff = timestamp - jamMasuk.getTimeInMillis();
                long hours = Math.max(0, diff / (1000 * 60 * 60));
                long minutes = Math.max(0, (diff % (1000 * 60 * 60)) / (1000 * 60));
                textWaktuTerlambat.setText(String.format("%02d jam %02d menit (Est.)", hours, minutes));
            }

            // Info pulang lebih awal
            try {
                String[] parts = jamKeluarNormal.split(":");
                int jamKeluarHour = Integer.parseInt(parts[0]);
                int jamKeluarMinute = Integer.parseInt(parts[1]);

                Calendar jamPulangStandar = Calendar.getInstance();
                jamPulangStandar.setTimeInMillis(timestamp);
                jamPulangStandar.set(Calendar.HOUR_OF_DAY, jamKeluarHour);
                jamPulangStandar.set(Calendar.MINUTE, jamKeluarMinute);
                jamPulangStandar.set(Calendar.SECOND, 0);

                // Kurangi toleransi pulang cepat
                jamPulangStandar.add(Calendar.MINUTE, -toleransiPulangCepat);

                if (timestampCal.before(jamPulangStandar)) {
                    String currentText = textWaktuTerlambat.getText().toString();
                    long earlyDiff = jamPulangStandar.getTimeInMillis() - timestamp;
                    long earlyHours = earlyDiff / (1000 * 60 * 60);
                    long earlyMinutes = (earlyDiff % (1000 * 60 * 60)) / (1000 * 60);
                    textWaktuTerlambat.setText(currentText + String.format("\nPulang %02d:%02d lebih awal", earlyHours, earlyMinutes));
                }
            } catch (Exception e) {
                Log.e("SummaryPage", "Error calculating early departure", e);
            }

            textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        }
    }

    private void setupLemburUI(String currentTimeStr, Calendar timestampCal) {
        // Update labels untuk Lembur
        if (textJamKehadiranLabel != null) {
            textJamKehadiranLabel.setText("Jam Mulai Lembur");
        }
        if (textJamKehadiran != null) {
            textJamKehadiran.setText(currentTimeStr + " WIT");
        }
        if (textWaktuTerlambatLabel != null) {
            textWaktuTerlambatLabel.setText("Info Lembur");
        }

        // Setup lembur info
        if (textWaktuTerlambat != null) {
            // Default 3 jam lembur
            long estimasiMs = 3 * 60 * 60 * 1000; // 3 jam
            long endTime = timestamp + estimasiMs;
            SimpleDateFormat estimasiFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            // Hitung sudah lembur berapa lama dari jam overtime start
            Calendar jamNormal = Calendar.getInstance();
            jamNormal.setTimeInMillis(timestamp);
            int overtimeHour = getOvertimeStartHour();
            jamNormal.set(Calendar.HOUR_OF_DAY, overtimeHour);
            jamNormal.set(Calendar.MINUTE, 0);
            jamNormal.set(Calendar.SECOND, 0);

            if (timestampCal.after(jamNormal)) {
                long overtimeDiff = timestamp - jamNormal.getTimeInMillis();
                long overtimeHours = overtimeDiff / (1000 * 60 * 60);
                long overtimeMinutes = (overtimeDiff % (1000 * 60 * 60)) / (1000 * 60);
                textWaktuTerlambat.setText(String.format("LEMBUR AKTIF: %02d:%02d\nTarget 3 jam (Est. selesai: %s)",
                        overtimeHours, overtimeMinutes, estimasiFormat.format(new Date(endTime))));
            } else {
                textWaktuTerlambat.setText("Target 3 jam lembur\n(Est. selesai: " + estimasiFormat.format(new Date(endTime)) + ")");
            }

            textWaktuTerlambat.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
        }
    }

    private int getOvertimeStartHour() {
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String overtimeStartTime = prefs.getString("overtime_start_time", "17:00:00");
        try {
            String[] parts = overtimeStartTime.split(":");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            // Default to 17
            return 17;
        }
    }

    private void setupDefaultUI(String currentTimeStr) {
        if (textJamKehadiranLabel != null) {
            textJamKehadiranLabel.setText("Jam Absensi");
        }
        if (textJamKehadiran != null) {
            textJamKehadiran.setText(currentTimeStr + " WIT");
        }
        if (textWaktuTerlambatLabel != null) {
            textWaktuTerlambatLabel.setText("Status");
        }
        if (textWaktuTerlambat != null) {
            textWaktuTerlambat.setText("Absensi tercatat");
        }
    }

    private void loadPhoto() {
        try {
            if (photoUri != null && imageProfile != null) {
                InputStream inputStream = getContentResolver().openInputStream(photoUri);
                Bitmap photoBitmap = BitmapFactory.decodeStream(inputStream);
                if (photoBitmap != null) {
                    imageProfile.setImageBitmap(photoBitmap);
                }
            }
        } catch (FileNotFoundException e) {
            CustomToast.showToast(this, "Foto tidak ditemukan", Toast.LENGTH_SHORT);
        } catch (Exception e) {
            CustomToast.showToast(this, "Error loading photo: " + e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void fetchServerTimeAndSetTimestamp(long localTimestamp) {
        new Thread(() -> {
            try {
                org.json.JSONObject serverTimeData = ApiClient.getServerTime(this);
                timestamp = serverTimeData.getLong("timestamp");

                runOnUiThread(() -> {
                    // Set timestamp display
                    if (textTimestamp != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault());
                        textTimestamp.setText(dateFormat.format(new Date(timestamp)));
                    }
                    // Re-adjust UI with server time
                    adjustUIByAbsensiType();
                });
            } catch (Exception e) {
                Log.e("SummaryPage", "Failed to fetch server time, using local time", e);
                timestamp = localTimestamp;
                runOnUiThread(() -> {
                    // Set timestamp display with local time
                    if (textTimestamp != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault());
                        textTimestamp.setText(dateFormat.format(new Date(timestamp)));
                    }
                    // Adjust UI with local time
                    adjustUIByAbsensiType();
                });
            }
        }).start();
    }

    private void updateLocationUI() {
        if (latitude == 0.0 && longitude == 0.0) {
            if (textKoordinat != null) {
                textKoordinat.setText("Latitude: Tidak tersedia\nLongitude: Tidak tersedia");
            }
            if (textLokasi != null) {
                textLokasi.setText("Lokasi tidak tersedia");
            }
            locationName = "Lokasi tidak tersedia";
            // Tampilkan tombol retry jika lokasi tidak tersedia
            if (btnRetryLocation != null) {
                btnRetryLocation.setVisibility(android.view.View.VISIBLE);
            }
            return;
        }

        try {
            if (textKoordinat != null) {
                textKoordinat.setText(String.format(Locale.getDefault(),
                        "Latitude: %.6f\nLongitude: %.6f", latitude, longitude));
            }

            if (textLokasi != null) {
                String addressText = null;
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> results = geocoder.getFromLocation(latitude, longitude, 1);
                    if (results != null && !results.isEmpty()) {
                        Address a = results.get(0);
                        StringBuilder sb = new StringBuilder();
                        if (a.getThoroughfare() != null) sb.append(a.getThoroughfare()).append(" ");
                        if (a.getSubLocality() != null) sb.append(a.getSubLocality()).append("\n");
                        if (a.getLocality() != null) sb.append(a.getLocality()).append("\n");
                        if (a.getSubAdminArea() != null) sb.append(a.getSubAdminArea()).append("\n");
                        if (a.getAdminArea() != null) sb.append(a.getAdminArea());
                        addressText = sb.toString().trim();
                    }
                } catch (Exception ignored) {
                    addressText = "Tidak dapat menemukan alamat (Offline/Error)";
                    // Tampilkan tombol retry jika gagal mendapatkan alamat
                    if (btnRetryLocation != null) {
                        btnRetryLocation.setVisibility(android.view.View.VISIBLE);
                    }
                }

                if (addressText != null && addressText.length() > 0) {
                    textLokasi.setText(addressText);
                    locationName = addressText;
                    // Sembunyikan tombol retry jika berhasil
                    if (!addressText.contains("Offline/Error") && btnRetryLocation != null) {
                        btnRetryLocation.setVisibility(android.view.View.GONE);
                    }
                } else {
                    String fallback = "Lokasi: " + latitude + ", " + longitude;
                    textLokasi.setText(fallback);
                    locationName = fallback;
                    // Tampilkan tombol retry jika fallback digunakan
                    if (btnRetryLocation != null) {
                        btnRetryLocation.setVisibility(android.view.View.VISIBLE);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            locationName = "Error mendapatkan lokasi";
            // Tampilkan tombol retry jika error
            if (btnRetryLocation != null) {
                btnRetryLocation.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    private void animateEntry() {
        // Animate checkmark button entrance
        if (imageCheckmark != null) {
            imageCheckmark.setScaleX(0f);
            imageCheckmark.setScaleY(0f);
            imageCheckmark.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(500)
                    .start();
        }
    }

    private void setupClickListeners() {
        try {
            // Checkmark button
            if (imageCheckmark != null) {
                imageCheckmark.setOnClickListener(v -> {
                    try {
                        // Animation feedback
                        v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                        });

                        // Save data with location validation
                        saveAttendanceData();
                    } catch (Exception e) {
                        Log.e("SummaryPage", "Error in checkmark click handler", e);

                        // Tampilkan dialog error
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                        builder.setTitle("Terjadi Kesalahan")
                                .setMessage("Terjadi kesalahan: " + e.getMessage())
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi", (dialog, which) -> saveAttendanceData())
                                .setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss());
                        builder.create().show();
                    }
                });
            }

            // Profile image click
            if (imageProfile != null) {
                imageProfile.setOnClickListener(v -> {
                    if (photoUri != null) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(photoUri, "image/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            CustomToast.showToast(this, "Tidak dapat membuka foto", Toast.LENGTH_SHORT);
                        }
                    }
                });
            }

            // Request override button
            if (btnRequestOverride != null) {
                btnRequestOverride.setOnClickListener(v -> {
                    // Show dialog for reason input
                    android.widget.EditText input = new android.widget.EditText(SummaryPage.this);
                    input.setHint("Alasan permintaan override (mis. langsung ke lokasi klien)");

                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                    builder.setTitle("Minta Override Absensi")
                            .setView(input)
                            .setCancelable(true)
                            .setPositiveButton("Kirim", (d, w) -> {
                                String reason = input.getText() != null ? input.getText().toString().trim() : "";
                                if (reason.isEmpty()) {
                                    CustomToast.showToast(SummaryPage.this, "Isi alasan override terlebih dahulu", android.widget.Toast.LENGTH_SHORT);
                                } else {
                                    sendOverrideRequest(reason);
                                }
                            })
                            .setNegativeButton("Batal", (d, w) -> d.dismiss());
                    builder.create().show();
                });
            }

            // Retry location button
            if (btnRetryLocation != null) {
                btnRetryLocation.setOnClickListener(v -> {
                    // Animation feedback
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                    });

                    CustomToast.showToast(SummaryPage.this, "Melacak ulang lokasi...", Toast.LENGTH_SHORT);
                    retryGetLocation();
                });
            }
        } catch (Exception e) {
            CustomToast.showToast(this, "Error setting up click listeners: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    private void saveAttendanceData() {
        // Cek apakah user sudah absen hari ini untuk jenis yang sama
        checkTodayAttendanceStatus(() -> {
            // Validate location first (skip for Dinas Luar), then proceed
            validateLocationThenProceed(() -> performSaveAndSend());
        });
    }

    /**
     * Cek status absensi hari ini untuk mencegah double absen
     */
    private void checkTodayAttendanceStatus(Runnable onValidationSuccess) {
        new Thread(() -> {
            try {
                // Ambil tanggal hari ini
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String todayDate = sdf.format(new Date(timestamp));

                // Request ke server untuk cek status absensi hari ini
                Request request = new Request.Builder()
                        .url(ApiClient.API_BASE_URL + "/v1/attendance/check-today?date=" + todayDate)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);

                    if (jsonResponse.getBoolean("success")) {
                        org.json.JSONObject data = jsonResponse.getJSONObject("data");
                        boolean hasCheckedIn = data.optBoolean("has_checked_in", false);
                        boolean hasCheckedOut = data.optBoolean("has_checked_out", false);
                        boolean hasLembur = data.optBoolean("has_lembur", false);
                        boolean hasDinasLuar = data.optBoolean("has_dinas_luar", false);

                        // Validasi berdasarkan jenis absensi
                        runOnUiThread(() -> {
                            boolean shouldBlock = false;
                            String blockMessage = "";

                            if (jenisAbsensi.equals("Cek In") && hasCheckedIn) {
                                shouldBlock = true;
                                blockMessage = "Anda sudah melakukan Cek In hari ini.\nSilakan pilih jenis absensi lain atau tunggu hingga besok.";
                            } else if (jenisAbsensi.equals("Cek Out") && hasCheckedOut) {
                                shouldBlock = true;
                                blockMessage = "Anda sudah melakukan Cek Out hari ini.\nTerima kasih telah bekerja hari ini!";
                            } else if (jenisAbsensi.equals("Lembur") && hasLembur) {
                                shouldBlock = true;
                                blockMessage = "Anda sudah melakukan absensi Lembur hari ini.\nAbsensi lembur hanya bisa dilakukan sekali per hari.";
                            } else if (jenisAbsensi.equals("Dinas Luar") && hasDinasLuar) {
                                shouldBlock = true;
                                blockMessage = "Anda sudah melakukan absensi Dinas Luar hari ini.";
                            } else if (jenisAbsensi.equals("Cek Out") && !hasCheckedIn) {
                                shouldBlock = true;
                                blockMessage = "Anda belum melakukan Cek In hari ini.\nSilakan lakukan Cek In terlebih dahulu sebelum Cek Out.";
                            }

                            if (shouldBlock) {
                                // Tampilkan notifikasi warning
                                NotificationHelper.showAttendanceWarningNotification(
                                        SummaryPage.this,
                                        "⚠️ Tidak Dapat Melakukan Absensi",
                                        blockMessage
                                );

                                // Tampilkan dialog warning
                                ModernErrorDialog.showWarning(
                                        SummaryPage.this,
                                        "Tidak Dapat Melakukan " + jenisAbsensi,
                                        blockMessage,
                                        () -> {
                                            // Kembali ke dashboard
                                            Intent intent = new Intent(SummaryPage.this, activity_dashboard.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            finish();
                                        }
                                );
                            } else {
                                // Validasi sukses, lanjutkan proses
                                onValidationSuccess.run();
                            }
                        });
                    } else {
                        // Jika response tidak success, asumsikan belum ada absensi (safe to proceed)
                        runOnUiThread(() -> onValidationSuccess.run());
                    }
                } else {
                    // Jika request gagal, asumsikan belum ada absensi (safe to proceed)
                    runOnUiThread(() -> onValidationSuccess.run());
                }

            } catch (Exception e) {
                Log.e("SummaryPage", "Error checking today attendance status", e);
                // Jika error, tetap lanjutkan proses (safe to proceed)
                runOnUiThread(() -> onValidationSuccess.run());
            }
        }).start();
    }

    private void performSaveAndSend() {
        try {
            // Validasi jenis absensi tidak kosong
            if (jenisAbsensi == null || jenisAbsensi.isEmpty()) {
                Log.e("SummaryPage", "Jenis absensi kosong!");

                runOnUiThread(() -> {
                    NotificationHelper.showAttendanceWarningNotification(
                            SummaryPage.this,
                            "Jenis Absensi Tidak Valid",
                            "Silakan pilih jenis absensi terlebih dahulu"
                    );

                    ModernErrorDialog.showWarning(
                            SummaryPage.this,
                            "Jenis Absensi Tidak Valid",
                            "Silakan kembali dan pilih jenis absensi (Cek In/Cek Out/Lembur/Dinas Luar) terlebih dahulu.",
                            () -> {
                                finish();
                            }
                    );
                });
                return;
            }

            // Simpan timestamp Cek In terakhir jika jenis adalah Cek In
            if (jenisAbsensi.equals("Cek In")) {
                HistoryStore.saveLastCheckInTimestamp(this, timestamp);
            }

            // Prepare data for history
            String statusText = jenisAbsensi + " berhasil";
            if (isAutoSelected) {
                statusText += " (Auto-select)";
            }

            String jamHadir = textJamKehadiran != null ? textJamKehadiran.getText().toString() : "-";
            String terlambat = textWaktuTerlambat != null ? textWaktuTerlambat.getText().toString() : "00:00:00";

            String jamPulang = "-";
            String cepatPulang = "00:00:00";

            if (jenisAbsensi.equals("Cek Out")) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                jamPulang = timeFormat.format(new Date(timestamp)) + " WIB";

                // Hitung cepat pulang
                try {
                    String[] parts = jamKeluarNormal.split(":");
                    int jamKeluarHour = Integer.parseInt(parts[0]);
                    int jamKeluarMinute = Integer.parseInt(parts[1]);

                    Calendar currentTime = Calendar.getInstance();
                    currentTime.setTimeInMillis(timestamp);
                    Calendar jamPulangStandar = Calendar.getInstance();
                    jamPulangStandar.setTimeInMillis(timestamp);
                    jamPulangStandar.set(Calendar.HOUR_OF_DAY, jamKeluarHour);
                    jamPulangStandar.set(Calendar.MINUTE, jamKeluarMinute);
                    jamPulangStandar.set(Calendar.SECOND, 0);

                    // Kurangi toleransi pulang cepat
                    jamPulangStandar.add(Calendar.MINUTE, -toleransiPulangCepat);

                    if (currentTime.before(jamPulangStandar)) {
                        long diff = jamPulangStandar.getTimeInMillis() - timestamp;
                        long hours = diff / (1000 * 60 * 60);
                        long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
                        cepatPulang = String.format("%02d:%02d:00", hours, minutes);
                    } else {
                        cepatPulang = "00:00:00 (Tepat Waktu/Lembur)";
                    }
                } catch (Exception e) {
                    Log.e("SummaryPage", "Error calculating cepat pulang", e);
                    cepatPulang = "00:00:00 (Error)";
                }
            } else if (jenisAbsensi.equals("Lembur")) {
                long estimasiMs = 3 * 60 * 60 * 1000; // 3 jam
                long endTime = timestamp + estimasiMs;
                SimpleDateFormat estimasiFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                jamPulang = estimasiFormat.format(new Date(endTime)) + " WIB (Est.)";
                cepatPulang = "N/A (Lembur)";
            } else if (jenisAbsensi.equals("Dinas Luar")) {
                // Untuk dinas luar, anggap pulang otomatis pada jam keluar standar
                jamPulang = jamKeluarNormal + " WIB (Auto Dinas Luar)";
                cepatPulang = "N/A (Dinas Luar)";
            }

            // Save to history
            HistoryStore.addEntry(
                    this,
                    statusText,
                    timestamp,
                    jamHadir,
                    terlambat,
                    jamPulang,
                    cepatPulang
            );

            // Determine endpoint and attendance_type based on jenisAbsensi
            String endpoint = "";
            String attendanceType = "normal"; // default

            if (jenisAbsensi.equals("Cek In")) {
                endpoint = "/v1/attendance/check-in";
                attendanceType = "normal";
            } else if (jenisAbsensi.equals("Cek Out")) {
                endpoint = "/v1/attendance/check-out";
                // Check-out doesn't need attendance_type (backend will read from existing record)
            } else if (jenisAbsensi.equals("Lembur")) {
                endpoint = "/v1/attendance/check-in";
                attendanceType = "lembur";
            } else if (jenisAbsensi.equals("Dinas Luar")) {
                endpoint = "/v1/attendance/check-in";
                attendanceType = "dinas_luar";
            }

            // Send to backend
            sendAttendanceToBackend(endpoint, attendanceType);

            CustomToast.showToast(this, jenisAbsensi + " tersimpan ke History dan dikirim ke server", Toast.LENGTH_SHORT);

            // Clear pending data setelah berhasil submit
            clearPendingAttendanceData();

            // Buka History setelah absensi berhasil
            Intent intent = new Intent(SummaryPage.this, NavigationActivity.class);
            intent.putExtra("from_history", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e("SummaryPage", "Error in performSaveAndSend", e);
            CustomToast.showToast(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT);
            e.printStackTrace();
        }
    }

    private void validateLocationThenProceed(Runnable proceed) {
        try {
            if ("Dinas Luar".equals(jenisAbsensi)) {
                proceed.run();
                return;
            }

            new Thread(() -> {
                double refLat = 0.0;
                double refLng = 0.0;
                double radiusM = 0.0;
                try {
                    org.json.JSONObject settings = ApiClient.getSettings(SummaryPage.this);
                    if (settings != null) {
                        // Try multiple keys to be robust
                        refLat = settings.optDouble("office_latitude", settings.optDouble("latitude", 0.0));
                        refLng = settings.optDouble("office_longitude", settings.optDouble("longitude", 0.0));
                        radiusM = settings.optDouble("attendance_radius_m", settings.optDouble("radius_m", settings.optDouble("radius", 0.0)));

                        if (refLat == 0.0 && refLng == 0.0 && settings.has("attendance")) {
                            org.json.JSONObject att = settings.optJSONObject("attendance");
                            if (att != null) {
                                refLat = att.optDouble("office_latitude", att.optDouble("latitude", 0.0));
                                refLng = att.optDouble("office_longitude", att.optDouble("longitude", 0.0));
                                radiusM = att.optDouble("attendance_radius_m", att.optDouble("radius_m", att.optDouble("radius", 0.0)));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("SummaryPage", "Failed to fetch settings for location reference", e);
                }

                final double fRefLat = refLat;
                final double fRefLng = refLng;
                final double fRadiusM = radiusM;

                runOnUiThread(() -> {
                    if (fRefLat == 0.0 && fRefLng == 0.0) {
                        // No reference available; proceed
                        proceed.run();
                        return;
                    }

                    double distance = distanceMeters(latitude, longitude, fRefLat, fRefLng);
                    boolean within = fRadiusM <= 0.0 || distance <= fRadiusM;

                    if (within) {
                        proceed.run();
                    } else {
                        // Dialog untuk lokasi di luar area
                        String msg = String.format(java.util.Locale.getDefault(),
                                "Lokasi di luar area absensi.\n\nJarak Anda: %.1f meter\nRadius maksimal: %.0f meter\n\nSilakan kembali ke area kantor untuk melakukan absensi.",
                                distance, fRadiusM);
                        
                        // Tampilkan toast
                        CustomToast.showToast(SummaryPage.this, 
                            "⚠️ Anda di luar area kerja!", 
                            Toast.LENGTH_LONG);
                        
                        // Tampilkan notifikasi persistent
                        NotificationHelper.showAttendanceWarningNotification(
                            SummaryPage.this,
                            "⚠️ Di Luar Area Absensi",
                            String.format(java.util.Locale.getDefault(),
                                "Jarak: %.1f m dari kantor (max: %.0f m). Silakan kembali ke area kerja.",
                                distance, fRadiusM)
                        );
                        
                        // Tampilkan dialog dengan info lebih detail
                        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                        b.setTitle("⚠️ Di Luar Area Absensi")
                                .setMessage(msg)
                                .setCancelable(false)
                                .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                        b.create().show();
                    }
                });
            }).start();
        } catch (Exception e) {
            proceed.run();
        }
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private byte[] compressImageFromUri(Uri uri, int maxWidth, int maxHeight, int maxBytes) throws IOException {
        // 1) Decode bounds to get original size
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream is1 = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(is1, null, options);
        if (is1 != null) is1.close();

        // 2) Downsample to fit within max dimensions
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
        options.inJustDecodeBounds = false;
        InputStream is2 = getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
        if (is2 != null) is2.close();

        if (bitmap == null) {
            throw new IOException("Failed to decode image");
        }

        // 3) Additional scale if still larger than target dimensions
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float ratio = Math.min((float) maxWidth / w, (float) maxHeight / h);
        if (ratio < 1f) {
            int nw = Math.max(1, Math.round(w * ratio));
            int nh = Math.max(1, Math.round(h * ratio));
            bitmap = Bitmap.createScaledBitmap(bitmap, nw, nh, true);
        }

        // 4) Add watermark to the bitmap
        bitmap = drawWatermarkOnBitmap(bitmap);

        // 5) Compress to temp file first
        File tempFile = File.createTempFile("compressed", ".jpg", getCacheDir());
        FileOutputStream fos = new FileOutputStream(tempFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        fos.close();

        // 6) Add EXIF metadata
        addExifMetadata(tempFile.getAbsolutePath());

        // 7) Read back to byte array
        java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        fis.close();
        tempFile.delete(); // Clean up

        byte[] data = baos.toByteArray();

        // 8) If still over maxBytes, compress further (but without EXIF to avoid corruption)
        if (data.length > maxBytes) {
            // Re-compress the bitmap with lower quality
            int quality = 85;
            while (data.length > maxBytes && quality > 50) {
                quality -= 5;
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                data = baos.toByteArray();
            }
        }

        return data;
    }

    private void addExifMetadata(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);

            // Set GPS coordinates
            if (latitude != 0.0 || longitude != 0.0) {
                exif.setLatLong(latitude, longitude);
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude >= 0 ? "N" : "S");
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude >= 0 ? "E" : "W");
            }

            // Set timestamp
            SimpleDateFormat exifFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
            String dateTime = exifFormat.format(new Date(timestamp));
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime);

            // Set location name in user comment
            if (locationName != null && !locationName.isEmpty()) {
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, locationName);
            }

            // Set software tag
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "PT Visdat Teknik Utama Absensi App");

            exif.saveAttributes();
        } catch (IOException e) {
            Log.e("SummaryPage", "Failed to add EXIF metadata", e);
        }
    }

    private Bitmap drawWatermarkOnBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        // Make bitmap mutable
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        // Prepare text
        String timeStr = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
        String coordStr = String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude);
        String locStr = (locationName != null && !locationName.isEmpty()) ? locationName : "Lokasi tidak tersedia";

        String watermarkText = "Lokasi: " + locStr + "\nKoordinat: " + coordStr + "\nWaktu: " + timeStr;

        // Paint for background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(128); // Semi-transparent

        // Paint for text
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setAntiAlias(true);

        // Measure text bounds
        Rect textBounds = new Rect();
        String[] lines = watermarkText.split("\n");
        float maxWidth = 0;
        float totalHeight = 0;
        for (String line : lines) {
            textPaint.getTextBounds(line, 0, line.length(), textBounds);
            maxWidth = Math.max(maxWidth, textBounds.width());
            totalHeight += textBounds.height() + 10; // Add some padding
        }

        // Position at bottom left with padding
        float x = 20f;
        float y = canvas.getHeight() - totalHeight - 20f;

        // Draw background rectangle
        canvas.drawRect(x - 10, y - 40, x + maxWidth + 10, y + totalHeight, bgPaint);

        // Draw text lines
        float lineY = y;
        for (String line : lines) {
            canvas.drawText(line, x, lineY, textPaint);
            lineY += textPaint.getTextSize() + 5;
        }

        return mutableBitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void sendAttendanceToBackend(String endpoint, String attendanceType) {
        Log.d("SummaryPage", "Sending attendance to backend: " + endpoint + ", jenis: " + jenisAbsensi + ", attendance_type: " + attendanceType);

    MultipartBody.Builder builder = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("latitude", String.valueOf(latitude))
        .addFormDataPart("longitude", String.valueOf(longitude))
        .addFormDataPart("jenis_absensi", jenisAbsensi)
        .addFormDataPart("timestamp", String.valueOf(timestamp));

        // Add attendance_type parameter for check-in requests
        if (endpoint.contains("check-in") && attendanceType != null && !attendanceType.isEmpty()) {
            builder.addFormDataPart("attendance_type", attendanceType);
            Log.d("SummaryPage", "Adding attendance_type parameter: " + attendanceType);
        }

        Log.d("SummaryPage", "Request data - lat: " + latitude + ", lng: " + longitude + ", jenis: " + jenisAbsensi + ", type: " + attendanceType);

        // Add photo if available
        if (photoUri != null) {
            try {
                // Compress to <= ~900KB and max 1280x1280 to satisfy backend 2MB limit (Laravel max:2048 KB)
                byte[] compressed = compressImageFromUri(photoUri, 1280, 1280, 900 * 1024);
                RequestBody photoBody = RequestBody.create(compressed, MediaType.parse("image/jpeg"));
                builder.addFormDataPart("foto", "photo.jpg", photoBody);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        RequestBody requestBody = builder.build();

        // Build request with proper headers using ApiClient
        Request.Builder requestBuilder = new Request.Builder()
                .url(ApiClient.API_BASE_URL + endpoint)
                .post(requestBody);

        // Add Authorization header manually if token exists
        String authToken = ApiClient.getStoredAuthToken(this);
        if (authToken != null && !authToken.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
            Log.d("SummaryPage", "Adding Authorization header with token: " + authToken.substring(0, Math.min(20, authToken.length())) + "...");
        } else {
            Log.w("SummaryPage", "No auth token found for attendance request! Token: " + authToken);

            // Try to get token from SharedPreferences directly as fallback
            try {
                android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                String fallbackToken = prefs.getString(MainActivity.KEY_AUTH_TOKEN, null);
                if (fallbackToken != null && !fallbackToken.isEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer " + fallbackToken);
                    Log.d("SummaryPage", "Using fallback token from SharedPreferences: " + fallbackToken.substring(0, Math.min(20, fallbackToken.length())) + "...");
                } else {
                    Log.e("SummaryPage", "No token found anywhere - user needs to login again!");
                    runOnUiThread(() -> {
                        CustomToast.showToast(SummaryPage.this, "Sesi login telah berakhir. Silakan login ulang.", Toast.LENGTH_LONG);
                        // Redirect to login
                        Intent loginIntent = new Intent(SummaryPage.this, MainActivity.class);
                        loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(loginIntent);
                        finish();
                    });
                    return;
                }
            } catch (Exception e) {
                Log.e("SummaryPage", "Error getting fallback token: " + e.getMessage());
                runOnUiThread(() -> {
                    CustomToast.showToast(SummaryPage.this, "Error autentikasi. Silakan login ulang.", Toast.LENGTH_LONG);
                    Intent loginIntent = new Intent(SummaryPage.this, MainActivity.class);
                    loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(loginIntent);
                    finish();
                });
                return;
            }
        }

        Request request = requestBuilder.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SummaryPage", "Network failure during attendance request: " + e.getMessage(), e);

                runOnUiThread(() -> {
                    try {
                        // User-friendly error message
                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("failed to connect")) {
                            errorMessage = "Gagal terhubung ke server.\nPeriksa koneksi internet Anda.";
                        } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                            errorMessage = "Koneksi timeout.\nServer tidak merespons, coba lagi nanti.";
                        } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unable to resolve host")) {
                            errorMessage = "Tidak dapat menemukan server.\nPeriksa koneksi internet Anda.";
                        } else {
                            errorMessage = e.getMessage() != null ? e.getMessage() : "Koneksi bermasalah";
                        }

                        // Show simple dialog
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                        builder.setTitle("❌ Gagal Mengirim Absensi")
                                .setMessage(errorMessage + "\n\nData absensi belum terkirim.")
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi", (dialog, which) -> {
                                    CustomToast.showToast(SummaryPage.this, "Mencoba kirim ulang...", Toast.LENGTH_SHORT);
                                    sendAttendanceToBackend(endpoint, attendanceType);
                                })
                                .setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss());
                        builder.create().show();

                    } catch (Exception ex) {
                        Log.e("SummaryPage", "Critical error in failure handler: " + ex.getMessage(), ex);
                        CustomToast.showToast(SummaryPage.this,
                                "Terjadi kesalahan saat memproses kesalahan jaringan.",
                                Toast.LENGTH_LONG);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                int code = response.code();
                String contentType = response.header("Content-Type", "");
                boolean looksHtml = contentType != null && contentType.contains("text/html");

                if (code >= 200 && code < 300 && !looksHtml) {
                    // Parse response to check if late or early
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(body);
                        
                        if (jsonResponse.optBoolean("success", false)) {
                            org.json.JSONObject data = jsonResponse.optJSONObject("data");
                            
                            if (data != null) {
                                int attendanceId = data.optInt("attendance_id", 0);
                                boolean isLate = data.optBoolean("is_late", false);
                                boolean isEarly = data.optBoolean("is_early", false);
                                int lateMinutes = data.optInt("late_minutes", 0);
                                int earlyMinutes = data.optInt("early_minutes", 0);
                                String normalCheckInTime = data.optString("normal_check_in_time", null);
                                String normalCheckOutTime = data.optString("normal_check_out_time", null);
                                
                                runOnUiThread(() -> {
                                    CustomToast.showToast(SummaryPage.this, "✅ Berhasil kirim ke server", Toast.LENGTH_SHORT);
                                    
                                    // Show override dialog if late or early
                                    if (isLate && attendanceId > 0) {
                                        showOverrideDialog(attendanceId, "late_check_in", lateMinutes, normalCheckInTime);
                                    } else if (isEarly && attendanceId > 0) {
                                        showOverrideDialog(attendanceId, "early_check_out", earlyMinutes, normalCheckOutTime);
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.e("SummaryPage", "Error parsing success response: " + e.getMessage(), e);
                    }
                    
                    // Fallback if parsing fails
                    runOnUiThread(() -> {
                        CustomToast.showToast(SummaryPage.this, "✅ Berhasil kirim ke server", Toast.LENGTH_SHORT);
                    });
                } else if (code == 302 || code == 401 || code == 403) {
                    runOnUiThread(() -> {
                        // Tampilkan toast
                        CustomToast.showToast(SummaryPage.this, 
                            "🔒 Sesi login berakhir!", 
                            Toast.LENGTH_LONG);
                        
                        // Dialog error autentikasi
                        new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this)
                                .setTitle("🔒 Sesi Berakhir")
                                .setMessage("Sesi login Anda telah berakhir. Silakan login ulang.")
                                .setCancelable(false)
                                .setPositiveButton("Login", (dialog, which) -> {
                                    Intent loginIntent = new Intent(SummaryPage.this, MainActivity.class);
                                    loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(loginIntent);
                                    finish();
                                })
                                .show();
                    });
                } else {
                    // Handle error responses (4xx, 5xx)
                    String body = "";
                    try {
                        if (response.body() != null) {
                            body = response.body().string();
                        }
                    } catch (Exception ex) {
                        Log.e("SummaryPage", "Error reading response body: " + ex.getMessage(), ex);
                    }

                    final String finalBody = body;
                    runOnUiThread(() -> {
                        try {
                            // Parse error message dari response JSON
                            String errorMsg = "Server merespons kode " + code;
                            String detailedError = "";

                            try {
                                if (finalBody != null && !finalBody.isEmpty()) {
                                    org.json.JSONObject jsonError = new org.json.JSONObject(finalBody);
                                    if (jsonError.has("message")) {
                                        errorMsg = jsonError.getString("message");
                                    }

                                    // Cek jika ada errors array untuk validasi
                                    if (jsonError.has("errors") && !jsonError.isNull("errors")) {
                                        org.json.JSONObject errors = jsonError.optJSONObject("errors");
                                        if (errors != null && errors.length() > 0) {
                                            StringBuilder sb = new StringBuilder();
                                            org.json.JSONArray names = errors.names();
                                            if (names != null) {
                                                for (int i = 0; i < names.length(); i++) {
                                                    String field = names.getString(i);
                                                    org.json.JSONArray fieldErrors = errors.getJSONArray(field);
                                                    if (fieldErrors != null && fieldErrors.length() > 0) {
                                                        sb.append(fieldErrors.getString(0));
                                                        if (i < names.length() - 1) {
                                                            sb.append("\n");
                                                        }
                                                    }
                                                }
                                            }
                                            if (sb.length() > 0) {
                                                detailedError = sb.toString();
                                            }
                                        }
                                    }
                                }
                            } catch (org.json.JSONException jsonEx) {
                                Log.e("SummaryPage", "Failed to parse error JSON: " + jsonEx.getMessage(), jsonEx);
                                // Jika gagal parse JSON, gunakan body sebagai error message jika tidak terlalu panjang
                                if (finalBody != null && !finalBody.isEmpty() && finalBody.length() < 200) {
                                    errorMsg = finalBody;
                                }
                            }

                            // Gabungkan pesan error utama dengan detail jika ada
                            String fullErrorMessage = errorMsg;
                            if (!detailedError.isEmpty()) {
                                fullErrorMessage = errorMsg + "\n\n" + detailedError;
                            }

                            // Log error untuk debugging
                            Log.e("SummaryPage", "Attendance error - Code: " + code + ", Message: " + fullErrorMessage);

                            // Tentukan apakah error bisa di-retry atau tidak
                            final boolean canRetry = (code >= 500 && code < 600) || code == 408 || code == 429;
                            final String finalErrorMessage = fullErrorMessage;

                            // Tampilkan toast terlebih dahulu
                            if (code == 422) {
                                // Validation error (seperti lokasi di luar area)
                                CustomToast.showToast(SummaryPage.this, 
                                    "⚠️ " + errorMsg.substring(0, Math.min(50, errorMsg.length())) + "...", 
                                    Toast.LENGTH_LONG);
                                
                                // Tampilkan notification untuk error lokasi
                                if (errorMsg.toLowerCase().contains("lokasi") || errorMsg.toLowerCase().contains("area")) {
                                    NotificationHelper.showAttendanceWarningNotification(
                                        SummaryPage.this,
                                        "⚠️ Di Luar Area Absensi",
                                        errorMsg
                                    );
                                }
                            } else if (canRetry) {
                                // Server error
                                CustomToast.showToast(SummaryPage.this, 
                                    "❌ Server bermasalah!", 
                                    Toast.LENGTH_LONG);
                            } else {
                                // Client error lainnya
                                CustomToast.showToast(SummaryPage.this, 
                                    "❌ Absensi gagal!", 
                                    Toast.LENGTH_LONG);
                            }

                            // Tampilkan dialog error
                            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                            builder.setTitle(canRetry ? "❌ Server Bermasalah" : "⚠️ Absensi Gagal")
                                    .setMessage(finalErrorMessage)
                                    .setCancelable(false);

                            // Tombol Coba Lagi hanya untuk server errors
                            if (canRetry) {
                                builder.setPositiveButton("Coba Lagi", (dialog, which) -> {
                                    sendAttendanceToBackend(endpoint, attendanceType);
                                });
                            }
                            
                            builder.setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss());
                            builder.create().show();

                        } catch (Exception ex) {
                            // Catch-all untuk memastikan app tidak crash
                            Log.e("SummaryPage", "Critical error in error handler: " + ex.getMessage(), ex);
                            CustomToast.showToast(SummaryPage.this,
                                "Terjadi kesalahan saat memproses respons server. Silakan coba lagi.",
                                Toast.LENGTH_LONG);
                            // User tetap di SummaryPage untuk bisa retry
                        }
                    });
                }
            }
        });
    }

    /**
     * Send override request to backend for manager approval.
     */
    private void sendOverrideRequest(String reason) {
        try {
            FormBody.Builder form = new FormBody.Builder()
                    .add("jenis_absensi", jenisAbsensi)
                    .add("timestamp", String.valueOf(timestamp))
                    .add("latitude", String.valueOf(latitude))
                    .add("longitude", String.valueOf(longitude))
                    .add("reason", reason);

            Request request = new Request.Builder()
                    .url(ApiClient.API_BASE_URL + "/v1/attendance/override-request")
                    .post(form.build())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("SummaryPage", "Failed to send override request: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        String msg = e.getMessage() != null ? e.getMessage() : "Gagal mengirim permintaan override.";
                        
                        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                        b.setTitle("Gagal kirim override")
                                .setMessage(msg)
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequest(reason))
                                .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                        b.create().show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (code >= 200 && code < 300) {
                            CustomToast.showToast(SummaryPage.this, "Permintaan override dikirim. Menunggu approval Manajer SDM.", android.widget.Toast.LENGTH_LONG);
                        } else {
                            String serverMsg = "Server merespons: " + code + "\n" + body;
                            
                            androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                            b.setTitle("Gagal kirim override")
                                    .setMessage(serverMsg)
                                    .setCancelable(false)
                                    .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequest(reason))
                                    .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                            b.create().show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            CustomToast.showToast(this, "Error membangun permintaan override: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
        }
    }

    /**
     * Retry getting current location
     */
    private void retryGetLocation() {
        new Thread(() -> {
            try {
                // Cek permission
                if (androidx.core.app.ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> CustomToast.showToast(this, "Izin lokasi tidak diberikan", Toast.LENGTH_SHORT));
                    return;
                }

                // Dapatkan LocationManager
                android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
                if (locationManager == null) {
                    runOnUiThread(() -> CustomToast.showToast(this, "LocationManager tidak tersedia", Toast.LENGTH_SHORT));
                    return;
                }

                // Update UI: sedang melacak
                runOnUiThread(() -> {
                    if (textLokasi != null) {
                        textLokasi.setText("Melacak lokasi...");
                    }
                    if (btnRetryLocation != null) {
                        btnRetryLocation.setEnabled(false);
                        btnRetryLocation.setText("⏳ Melacak...");
                    }
                });

                // Coba dapatkan lokasi terbaru dari GPS
                android.location.Location location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);

                // Jika GPS tidak ada, coba dari NETWORK
                if (location == null) {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }

                // Jika masih null, request location update
                if (location == null) {
                    final android.location.Location[] newLocation = {null};
                    final Object lock = new Object();

                    android.location.LocationListener listener = new android.location.LocationListener() {
                        @Override
                        public void onLocationChanged(android.location.Location loc) {
                            synchronized (lock) {
                                newLocation[0] = loc;
                                lock.notify();
                            }
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}
                    };

                    // Request location update
                    locationManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            0,
                            0,
                            listener);

                    // Wait dengan timeout 10 detik
                    synchronized (lock) {
                        try {
                            lock.wait(10000); // 10 seconds timeout
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    locationManager.removeUpdates(listener);
                    location = newLocation[0];
                }

                final android.location.Location finalLocation = location;

                if (finalLocation != null) {
                    latitude = finalLocation.getLatitude();
                    longitude = finalLocation.getLongitude();

                    runOnUiThread(() -> {
                        CustomToast.showToast(this, "Lokasi berhasil ditemukan!", Toast.LENGTH_SHORT);
                        updateLocationUI();
                        if (btnRetryLocation != null) {
                            btnRetryLocation.setEnabled(true);
                            btnRetryLocation.setText("🔄 Lacak Ulang Lokasi");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        CustomToast.showToast(this, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_LONG);
                        if (textLokasi != null) {
                            textLokasi.setText("Lokasi tidak ditemukan");
                        }
                        if (btnRetryLocation != null) {
                            btnRetryLocation.setEnabled(true);
                            btnRetryLocation.setText("🔄 Lacak Ulang Lokasi");
                            btnRetryLocation.setVisibility(android.view.View.VISIBLE);
                        }
                    });
                }

            } catch (SecurityException e) {
                runOnUiThread(() -> {
                    CustomToast.showToast(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT);
                    if (btnRetryLocation != null) {
                        btnRetryLocation.setEnabled(true);
                        btnRetryLocation.setText("🔄 Lacak Ulang Lokasi");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    CustomToast.showToast(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT);
                    if (btnRetryLocation != null) {
                        btnRetryLocation.setEnabled(true);
                        btnRetryLocation.setText("🔄 Lacak Ulang Lokasi");
                    }
                });
            }
        }).start();
    }

    /**
     * Show override dialog when late check-in or early check-out detected
     */
    private void showOverrideDialog(int attendanceId, String overrideType, int minutes, String normalTime) {
        String title, message, placeholder;
        
        if ("late_check_in".equals(overrideType)) {
            title = "⚠️ Terlambat Check In";
            message = String.format(
                "Anda check in %d menit lebih lambat dari jam kerja normal (%s).\n\n" +
                "Apakah Anda ingin mengajukan override untuk keterlambatan ini?",
                minutes, normalTime != null ? normalTime : "waktu normal"
            );
            placeholder = "Contoh: Terlambat karena pergi ke lokasi project lalu balik ke kantor untuk absen";
        } else {
            title = "⚠️ Pulang Lebih Cepat";
            message = String.format(
                "Anda check out %d menit lebih cepat dari jam pulang normal (%s).\n\n" +
                "Apakah Anda ingin mengajukan override untuk kepulangan cepat ini?",
                minutes, normalTime != null ? normalTime : "waktu normal"
            );
            placeholder = "Contoh: Check out cepat karena ingin pergi ke lokasi project";
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Ya, Ajukan Override", (dialog, which) -> {
                    // Show input dialog for reason
                    showOverrideReasonDialog(attendanceId, overrideType, placeholder);
                })
                .setNegativeButton("Tidak, Nanti Saja", (dialog, which) -> {
                    dialog.dismiss();
                    CustomToast.showToast(this, 
                        "Anda dapat mengajukan override dari menu History", 
                        Toast.LENGTH_LONG);
                });
        
        builder.create().show();
    }

    /**
     * Show dialog to input override reason
     */
    private void showOverrideReasonDialog(int attendanceId, String overrideType, String placeholder) {
        // Create custom dialog layout
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 40, 40, 40);

        // Label
        TextView label = new TextView(this);
        label.setText("Alasan (min. 10 karakter):");
        label.setTextSize(14);
        label.setPadding(0, 0, 0, 8);
        dialogLayout.addView(label);

        // Input field
        android.widget.EditText inputReason = new android.widget.EditText(this);
        inputReason.setHint(placeholder);
        inputReason.setMinLines(3);
        inputReason.setMaxLines(5);
        inputReason.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        dialogLayout.addView(inputReason);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Alasan Override")
                .setView(dialogLayout)
                .setCancelable(true)
                .setPositiveButton("Kirim", (dialog, which) -> {
                    String reason = inputReason.getText() != null ? inputReason.getText().toString().trim() : "";
                    if (reason.length() < 10) {
                        CustomToast.showToast(this, "Alasan minimal 10 karakter!", Toast.LENGTH_SHORT);
                        // Show dialog again
                        showOverrideReasonDialog(attendanceId, overrideType, placeholder);
                    } else {
                        sendOverrideRequestNew(attendanceId, overrideType, reason);
                    }
                })
                .setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    /**
     * Send override request to API (new system)
     */
    private void sendOverrideRequestNew(int idAbsensi, String overrideType, String reason) {
        try {
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("id_absensi", String.valueOf(idAbsensi))
                    .add("override_type", overrideType)
                    .add("reason", reason);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(ApiClient.API_BASE_URL + "/v1/attendance/override-request")
                    .post(formBuilder.build());

            // Add Authorization header
            String authToken = ApiClient.getStoredAuthToken(this);
            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + authToken);
            }

            Request request = requestBuilder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("SummaryPage", "Failed to send override request: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                        builder.setTitle("Gagal Kirim Override")
                                .setMessage("Terjadi kesalahan jaringan: " + e.getMessage())
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequestNew(idAbsensi, overrideType, reason))
                                .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                        builder.create().show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        int code = response.code();
                        String body = response.body() != null ? response.body().string() : "";

                        runOnUiThread(() -> {
                            if (code >= 200 && code < 300) {
                                CustomToast.showToast(SummaryPage.this, 
                                    "✅ Permintaan override berhasil dikirim!\nMenunggu approval Manager SDM.", 
                                    Toast.LENGTH_LONG);
                            } else {
                                String errorMsg = "Server Error (" + code + ")";
                                try {
                                    org.json.JSONObject jsonError = new org.json.JSONObject(body);
                                    errorMsg = jsonError.optString("message", errorMsg);
                                } catch (org.json.JSONException e) {
                                    errorMsg += "\n" + body;
                                }

                                final String finalErrorMsg = errorMsg;
                                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(SummaryPage.this);
                                builder.setTitle("Gagal Kirim Override")
                                        .setMessage(finalErrorMsg)
                                        .setCancelable(false)
                                        .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequestNew(idAbsensi, overrideType, reason))
                                        .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                                builder.create().show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e("SummaryPage", "Error processing override response: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            CustomToast.showToast(SummaryPage.this, 
                                "Error memproses response: " + e.getMessage(), 
                                Toast.LENGTH_SHORT);
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e("SummaryPage", "Error building override request: " + e.getMessage(), e);
            CustomToast.showToast(this, 
                "Error membangun request: " + e.getMessage(), 
                Toast.LENGTH_LONG);
        }
    }

    @Override
    @SuppressWarnings("GestureBackNavigation")
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, activity_dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
