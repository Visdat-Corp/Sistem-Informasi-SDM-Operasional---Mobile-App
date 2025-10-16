package com.example.ptvisdatteknikutama;

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
import android.view.View;
import android.widget.ImageView;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SummaryPage extends AppCompatActivity {
    // UI Components - hanya yang ada di XML
    private ImageView imageProfile, imageCheckmark;
    private TextView textTimestamp, textJamKehadiran, textWaktuTerlambat, textLokasi, textKoordinat, textJenisAbsensi;
    private TextView textJamKehadiranLabel, textWaktuTerlambatLabel, textLokasiLabel, textKoordinatLabel;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary_page);

        client = ApiClient.getClient(getApplicationContext());

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
            textJenisAbsensi = findViewById(R.id.textJenisAbsensi);

            // Label components
            textJamKehadiranLabel = findViewById(R.id.textJamKehadiranLabel);
            textWaktuTerlambatLabel = findViewById(R.id.textWaktuTerlambatLabel);
            textLokasiLabel = findViewById(R.id.textLokasiLabel);
            textKoordinatLabel = findViewById(R.id.textKoordinatLabel);

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

            // Auto-determine if not provided
            if (jenisAbsensi == null || jenisAbsensi.isEmpty()) {
                jenisAbsensi = determineAttendanceType();
                isAutoSelected = true;
            }

            // Load photo if available
            if (photoUriString != null) {
                photoUri = Uri.parse(photoUriString);
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
                }

                if (addressText != null && addressText.length() > 0) {
                    textLokasi.setText(addressText);
                    locationName = addressText;
                } else {
                    String fallback = "Lokasi: " + latitude + ", " + longitude;
                    textLokasi.setText(fallback);
                    locationName = fallback;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            locationName = "Error mendapatkan lokasi";
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
                    // Animation feedback
                    v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                    });

                    // Save data and navigate
                    saveAttendanceData();
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
        } catch (Exception e) {
            CustomToast.showToast(this, "Error setting up click listeners: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    private void saveAttendanceData() {
        try {
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

            // Determine endpoint based on jenisAbsensi
            String endpoint = "";
            if (jenisAbsensi.equals("Cek In")) {
                endpoint = "/v1/attendance/check-in";
            } else if (jenisAbsensi.equals("Cek Out")) {
                endpoint = "/v1/attendance/check-out";
            } else if (jenisAbsensi.equals("Lembur")) {
                endpoint = "/v1/attendance/overtime";
            }

            // Send to backend
            sendAttendanceToBackend(endpoint);

            CustomToast.showToast(this, jenisAbsensi + " tersimpan ke History dan dikirim ke server", Toast.LENGTH_SHORT);

            // Navigate to NavigationActivity with history tab selected
            Intent intent = new Intent(SummaryPage.this, NavigationActivity.class);
            intent.putExtra("from_history", true);
            // Pass the auth token and username for header display
            String authToken = ApiClient.getStoredAuthToken(this);
            if (authToken != null) {
                intent.putExtra("AUTH_TOKEN", authToken);
            }
            // Note: USERNAME is not available here, but NavigationActivity will fetch from API
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            CustomToast.showToast(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT);
            e.printStackTrace();
        }
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

    private void sendAttendanceToBackend(String endpoint) {
        Log.d("SummaryPage", "Sending attendance to backend: " + endpoint + ", jenis: " + jenisAbsensi);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("latitude", String.valueOf(latitude))
                .addFormDataPart("longitude", String.valueOf(longitude));

        Log.d("SummaryPage", "Request data - lat: " + latitude + ", lng: " + longitude + ", jenis: " + jenisAbsensi);

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
                runOnUiThread(() -> CustomToast.showToast(SummaryPage.this, "Gagal kirim ke server: " + e.getMessage(), Toast.LENGTH_SHORT));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String contentType = response.header("Content-Type", "");
                boolean looksHtml = contentType != null && contentType.contains("text/html");

                if (code >= 200 && code < 300 && !looksHtml) {
                    runOnUiThread(() -> CustomToast.showToast(SummaryPage.this, "Berhasil kirim ke server", Toast.LENGTH_SHORT));
                } else if (code == 302 || code == 401 || code == 403) {
                    runOnUiThread(() -> CustomToast.showToast(SummaryPage.this, "Gagal: sesi tidak valid/harus login ulang (" + code + ")", Toast.LENGTH_SHORT));
                } else {
                    String body = "";
                    try {
                        body = response.body() != null ? response.body().string() : "";
                    } catch (Exception ex) {
                        // ignore parsing error
                    }
                    final String finalBody = body;
                    runOnUiThread(() -> CustomToast.showToast(SummaryPage.this, "Gagal (" + code + "): " + finalBody, Toast.LENGTH_LONG));
                }
            }
        });
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
