package com.visdat.mobile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Calendar;

public class KonfirmasiFoto extends AppCompatActivity {

    private ImageView photoConfirm;
    private Button btnRetake, btnAbsen;
    private Uri photoUri;
    private Bitmap photoBitmap;

    private RadioButton radioCekIn, radioCekOut, radioVisit, radioDinasLuar;
    private RadioButton currentlySelected = null;

    private double latitude = 0.0;
    private double longitude = 0.0;
    private long serverTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_konfirmasi_foto);

        initializeViews();
        setupRadioButtonListeners();
        setupClickListeners();
        loadPhotoAndLocation();
        fetchServerTimeAndSetupAbsensi();
    }

    private void initializeViews() {
        try {
            photoConfirm = findViewById(R.id.photo_confirm);
            btnRetake = findViewById(R.id.btn_retake);
            btnAbsen = findViewById(R.id.btn_absen);

            radioCekIn = findViewById(R.id.radioCekIn);
            radioCekOut = findViewById(R.id.radioCekOut);
            radioVisit = findViewById(R.id.radioVisit);
            radioDinasLuar = findViewById(R.id.radioDinasLuar);

        } catch (Exception e) {
            CustomToast.showToast(this, "Error initializing views: " + e.getMessage(), Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    private void setupRadioButtonListeners() {
        // Setup manual radio button behavior
        radioCekIn.setOnClickListener(v -> selectRadioButton(radioCekIn));
        radioCekOut.setOnClickListener(v -> selectRadioButton(radioCekOut));
        radioVisit.setOnClickListener(v -> selectRadioButton(radioVisit));
        radioDinasLuar.setOnClickListener(v -> selectRadioButton(radioDinasLuar));
    }

    private void selectRadioButton(RadioButton selected) {
        // Uncheck semua radio button dulu
        radioCekIn.setChecked(false);
        radioCekOut.setChecked(false);
        radioVisit.setChecked(false);
        radioDinasLuar.setChecked(false);

        // Check yang dipilih
        selected.setChecked(true);
        currentlySelected = selected;
    }

    private void fetchServerTimeAndSetupAbsensi() {
        new Thread(() -> {
            try {
                org.json.JSONObject serverTimeData = ApiClient.getServerTime(this);
                serverTimestamp = serverTimeData.getLong("timestamp");
                int hour = (int) (serverTimestamp / 1000 / 3600 % 24);

                runOnUiThread(() -> setupDefaultAbsensi(hour, true));
            } catch (Exception e) {
                Log.e("KonfirmasiFoto", "Failed to fetch server time, using local time", e);
                runOnUiThread(() -> {
                    Calendar calendar = Calendar.getInstance();
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    setupDefaultAbsensi(hour, false);
                });
            }
        }).start();
    }

    private void setupDefaultAbsensi(int hour, boolean fromServer) {
        int overtimeHour = getOvertimeStartHour();

        if (hour >= 6 && hour <= 13) {
            selectRadioButton(radioCekIn);
            CustomToast.showToast(this, "Default: Cek In (waktu pagi/siang)" + (fromServer ? " - Server" : " - Local"), Toast.LENGTH_SHORT);
        } else if (hour > 13 && hour <= overtimeHour) {
            selectRadioButton(radioCekOut);
            CustomToast.showToast(this, "Default: Cek Out (waktu siang/sore)" + (fromServer ? " - Server" : " - Local"), Toast.LENGTH_SHORT);
        } else if (hour > overtimeHour) {
            selectRadioButton(radioVisit);
            CustomToast.showToast(this, "Default: Lembur (waktu malam)" + (fromServer ? " - Server" : " - Local"), Toast.LENGTH_SHORT);
        } else {
            selectRadioButton(radioCekIn);
            CustomToast.showToast(this, "Pilih jenis absensi sesuai kebutuhan" + (fromServer ? " - Server" : " - Local"), Toast.LENGTH_SHORT);
        }
    }

    private int getOvertimeStartHour() {
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String overtimeStartTime = prefs.getString("overtime_start_time", "17:00:00");
        try {
            String[] parts = overtimeStartTime.split(":");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 17;
        }
    }

    private void setupClickListeners() {
        try {
            if (btnRetake != null) {
                btnRetake.setOnClickListener(v -> {
                    Intent relaunch = new Intent(KonfirmasiFoto.this, NavigationActivity.class);
                    relaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    relaunch.putExtra("relaunch_camera", true);
                    startActivity(relaunch);
                    finish();
                });
            }

            if (btnAbsen != null) {
                btnAbsen.setOnClickListener(v -> processAbsensi());
            }
        } catch (Exception e) {
            CustomToast.showToast(this, "Error setting up click listeners: " + e.getMessage(), Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    private void loadPhotoAndLocation() {
        try {
            String photoUriString = getIntent().getStringExtra("photo_uri");
            if (photoUriString != null) {
                photoUri = Uri.parse(photoUriString);
                InputStream inputStream = getContentResolver().openInputStream(photoUri);
                photoBitmap = BitmapFactory.decodeStream(inputStream);
                if (photoBitmap != null && photoConfirm != null) {
                    photoConfirm.setImageBitmap(photoBitmap);
                } else {
                    CustomToast.showToast(this, "Gagal memuat foto", Toast.LENGTH_SHORT);
                }
            } else {
                CustomToast.showToast(this, "Tidak ada foto untuk ditampilkan", Toast.LENGTH_SHORT);
            }

            latitude = getIntent().getDoubleExtra("latitude", 0.0);
            longitude = getIntent().getDoubleExtra("longitude", 0.0);

        } catch (FileNotFoundException e) {
            CustomToast.showToast(this, "File foto tidak ditemukan", Toast.LENGTH_SHORT);
        } catch (Exception e) {
            CustomToast.showToast(this, "Error loading photo: " + e.getMessage(), Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    private void processAbsensi() {
        String jenisAbsensi = "";

        if (currentlySelected == radioCekIn) {
            jenisAbsensi = "Cek In";
        } else if (currentlySelected == radioCekOut) {
            jenisAbsensi = "Cek Out";
        } else if (currentlySelected == radioDinasLuar) {
            jenisAbsensi = "Dinas Luar";
        } else if (currentlySelected == radioVisit) {
            jenisAbsensi = "Lembur";
        } else {
            CustomToast.showToast(this, "Pilih jenis absensi dulu", Toast.LENGTH_SHORT);
            return;
        }

        Intent intent = new Intent(KonfirmasiFoto.this, SummaryPage.class);
        if (photoUri != null) {
            intent.putExtra("photo_uri", photoUri.toString());
        }
        long timestamp = serverTimestamp > 0 ? serverTimestamp : System.currentTimeMillis();
        intent.putExtra("timestamp", timestamp);
        intent.putExtra("jenis_absensi", jenisAbsensi);
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        Intent relaunch = new Intent(KonfirmasiFoto.this, NavigationActivity.class);
        relaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        relaunch.putExtra("relaunch_camera", true);
        startActivity(relaunch);
        finish();
    }
}