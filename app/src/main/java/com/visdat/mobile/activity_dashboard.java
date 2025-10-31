package com.visdat.mobile;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class activity_dashboard extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int REQUEST_LOCATION_PERMISSION = 102;
    private static final int REQUEST_PICK_IMAGE = 201;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 103;

    private Uri photoUri;
    private String currentPhotoPath;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private ImageView imgProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this);
        
        // Request notification permission untuk Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
        
        // Get SharedPreferences once
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        
        // Load and setup profile image
        imgProfile = findViewById(R.id.imgProfile);
        if (imgProfile != null) {
            // Load saved profile uri if exists
            String savedProfileUri = prefs.getString("profile_image_uri", null);
            if (savedProfileUri != null) {
                try {
                    imgProfile.setImageURI(Uri.parse(savedProfileUri));
                } catch (Exception ignored) { }
            }

            imgProfile.setOnClickListener(v -> {
                Intent pick = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pick.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Pilih foto profil"), REQUEST_PICK_IMAGE);
                } catch (Exception e) {
                    CustomToast.showToast(this, "Tidak bisa membuka galeri", Toast.LENGTH_SHORT);
                }
            });
        }

        // Ambil data dari Intent atau SharedPreferences
        
        String username = getIntent().getStringExtra("USERNAME");
        String name = getIntent().getStringExtra("NAME");
        String idKaryawan = getIntent().getStringExtra("ID_KARYAWAN");
        
        // Jika tidak ada di Intent, ambil dari SharedPreferences
        if (name == null || name.isEmpty()) {
            name = prefs.getString(MainActivity.KEY_NAME, null);
        }
        if (username == null || username.isEmpty()) {
            username = prefs.getString(MainActivity.KEY_USERNAME, null);
        }
        if (idKaryawan == null || idKaryawan.isEmpty()) {
            idKaryawan = prefs.getString(MainActivity.KEY_ID_KARYAWAN, null);
        }

        // Set nama karyawan
        TextView tvName = findViewById(R.id.tvName);
        if (tvName != null) {
            if (name != null && !name.isEmpty()) {
                tvName.setText(name);
            } else {
                tvName.setText("User"); // Default jika tidak ada nama
            }
        }

        // Set ID Karyawan
        TextView tvId = findViewById(R.id.tvId);
        if (tvId != null) {
            String userId = ApiClient.getStoredUserId(this);
            if (userId != null) {
                tvId.setText("ID: " + userId);
            } else if (idKaryawan != null) {
                tvId.setText("ID: " + idKaryawan);
            }
        }

        // ✅ Tombol Absensi - Langsung buka kamera
        LinearLayout btnAbsensi = findViewById(R.id.btnAbsensi);
        if (btnAbsensi != null) {
            btnAbsensi.setOnClickListener(v -> {
                Intent i = new Intent(activity_dashboard.this, NavigationActivity.class);
                startActivity(i);
            });
        }

        // Tombol Pengeluaran Kendaraan
        LinearLayout btnPengeluaranKendaraan = findViewById(R.id.btnPengeluaranKendaraan);
        if (btnPengeluaranKendaraan != null) {
            btnPengeluaranKendaraan.setOnClickListener(v -> {
                CustomToast.showToast(this, "Fitur Pengeluaran Kendaraan akan segera hadir!", Toast.LENGTH_SHORT);
            });
        }

        // Tombol Saldo Base Camp
        LinearLayout btnSaldoBaseCamp = findViewById(R.id.btnSaldoBaseCamp);
        if (btnSaldoBaseCamp != null) {
            btnSaldoBaseCamp.setOnClickListener(v ->
                    CustomToast.showToast(this, "Fitur Saldo Base Camp akan segera hadir!", Toast.LENGTH_SHORT)
            );
        }

        // Tombol Operasional / Peralatan
        LinearLayout btnOperasionalPeralatan = findViewById(R.id.btnOperasionalPeralatan);
        if (btnOperasionalPeralatan != null) {
            btnOperasionalPeralatan.setOnClickListener(v ->
                    CustomToast.showToast(this, "Fitur Operasional / Peralatan akan segera hadir!", Toast.LENGTH_SHORT)
            );
        }

        // Tombol Logout dengan animasi + konfirmasi
        CardView btnLogoutDashboard = findViewById(R.id.btnLogoutDashboard);
        if (btnLogoutDashboard != null) {
            btnLogoutDashboard.setOnClickListener(v -> {
                Animation animScale = AnimationUtils.loadAnimation(this, R.anim.scale);
                btnLogoutDashboard.startAnimation(animScale);

                new AlertDialog.Builder(activity_dashboard.this)
                        .setTitle("Konfirmasi Logout")
                        .setMessage("Apakah Anda yakin ingin logout?")
                        .setPositiveButton("Ya", (dialog, which) -> {
                            ApiClient.clearAllAuth(activity_dashboard.this);

                            // Clear SharedPreferences
                            getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                                    .edit().clear().apply();

                            Intent intent = new Intent(activity_dashboard.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }
    }

    // Check permissions dan langsung buka kamera
    private void checkPermissionsAndStartCamera() {
        boolean cameraPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean locationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!cameraPermission || !locationPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_CAMERA_PERMISSION);
        } else {
            getLocationAndOpenCamera();
        }
    }

    // Get GPS location dan buka kamera
    private void getLocationAndOpenCamera() {
        try {
            android.location.LocationManager locationManager =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                android.location.Location location = locationManager.getLastKnownLocation(
                        android.location.LocationManager.GPS_PROVIDER);

                if (location == null) {
                    location = locationManager.getLastKnownLocation(
                            android.location.LocationManager.NETWORK_PROVIDER);
                }

                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                }
            }

            openCamera();

        } catch (Exception e) {
            CustomToast.showToast(this, "Error mendapatkan lokasi: " + e.getMessage(),
                    Toast.LENGTH_LONG);
            e.printStackTrace();
        }
    }

    // Buka kamera
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                CustomToast.showToast(this, "Error membuat file foto", Toast.LENGTH_SHORT);
                ex.printStackTrace();
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        "com.visdat.mobile.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            CustomToast.showToast(this, "Kamera tidak tersedia", Toast.LENGTH_SHORT);
        }
    }

    // Create image file
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "ABSENSI_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Langsung ke KonfirmasiFoto dengan data foto dan lokasi
            Intent intent = new Intent(this, KonfirmasiFoto.class);
            intent.putExtra("photo_uri", photoUri.toString());
            intent.putExtra("latitude", latitude);
            intent.putExtra("longitude", longitude);
            startActivity(intent);
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selected = data.getData();
            if (selected != null && imgProfile != null) {
                imgProfile.setImageURI(selected);
                // persist uri
                SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putString("profile_image_uri", selected.toString()).apply();
                CustomToast.showToast(this, "Foto profil diperbarui", Toast.LENGTH_SHORT);
            }
        } else if (resultCode == RESULT_CANCELED) {
            CustomToast.showToast(this, "Pengambilan foto dibatalkan", Toast.LENGTH_SHORT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                getLocationAndOpenCamera();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Izin Diperlukan")
                        .setMessage("Aplikasi memerlukan izin kamera dan lokasi untuk melakukan absensi.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CustomToast.showToast(this, "✅ Notifikasi diaktifkan. Anda akan menerima pemberitahuan status absensi.", Toast.LENGTH_LONG);
            } else {
                CustomToast.showToast(this, "⚠️ Notifikasi dinonaktifkan. Anda tidak akan menerima pemberitahuan, tapi masih bisa menggunakan Toast.", Toast.LENGTH_LONG);
            }
        }
    }
}