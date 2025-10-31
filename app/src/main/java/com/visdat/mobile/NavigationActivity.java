package com.visdat.mobile;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NavigationActivity extends AppCompatActivity {
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<String[]> requestLocationPermissionsLauncher;
    private Uri photoUri;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setupActivityResultLaunchers();

        // Jika diminta menampilkan History, tampilkan HistoryFragment penuh tanpa XML
        boolean fromHistory = getIntent() != null && getIntent().getBooleanExtra("from_history", false);
        if (fromHistory) {
            showHistoryFragment();
        } else if (savedInstanceState == null) {
            // Default: buka kamera
            launchCameraFlow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("from_history", false)) {
            showHistoryFragment();
            return;
        }
        if (intent != null && intent.getBooleanExtra("relaunch_camera", false)) {
            launchCameraFlow();
        }
    }

    private void showHistoryFragment() {
        // Buat container secara programatik
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int containerId = android.view.View.generateViewId();
        container.setId(containerId);
        setContentView(container);

        // Muat HistoryFragment ke container
        getSupportFragmentManager()
                .beginTransaction()
                .replace(containerId, new HistoryFragment())
                .commitNowAllowingStateLoss();
    }

    private void setupActivityResultLaunchers() {
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        double lat = 0.0;
                        double lng = 0.0;
                        try {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                Location last = locationManager != null ? locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
                                if (last == null && locationManager != null) {
                                    last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                                }
                                if (last != null) {
                                    lat = last.getLatitude();
                                    lng = last.getLongitude();
                                }
                            }
                        } catch (Exception ignored) { }

                        if (photoUri != null) {
                            Intent i = new Intent(NavigationActivity.this, KonfirmasiFoto.class);
                            i.putExtra("photo_uri", photoUri.toString());
                            i.putExtra("latitude", lat);
                            i.putExtra("longitude", lng);
                            startActivity(i);
                        } else {
                            CustomToast.showToast(this, "Gagal mendapatkan URI foto.", Toast.LENGTH_SHORT);
                        }
                    } else {
                        CustomToast.showToast(this, "Pengambilan foto dibatalkan.", Toast.LENGTH_SHORT);
                        // Tidak ada layout yang di-set pada activity ini ketika hanya membuka kamera.
                        // Jika kamera dibatalkan, segera akhiri activity agar langsung kembali ke dashboard.
                        finish();
                    }
                });

        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        dispatchTakePictureIntent();
                    } else {
                        CustomToast.showToast(this, "Izin kamera diperlukan.", Toast.LENGTH_LONG);
                        finish();
                    }
                });

        requestLocationPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean camGranted = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.CAMERA, false));
                    boolean locFine = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                    boolean locCoarse = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));

                    if (camGranted) {
                        // proceed to camera regardless of location result (lokasi opsional)
                        dispatchTakePictureIntent();
                    } else {
                        CustomToast.showToast(this, "Izin kamera diperlukan.", Toast.LENGTH_LONG);
                        finish();
                    }
                });
    }

    private void launchCameraFlow() {
        boolean needCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean needLocFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean needLocCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (needCamera || needLocFine || needLocCoarse) {
            requestLocationPermissionsLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            dispatchTakePictureIntent();
        }
    }

    private java.io.File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        java.io.File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) throw new IOException("Cannot get external files directory.");
        java.io.File image = java.io.File.createTempFile(imageFileName, ".jpg", storageDir);
        photoUri = FileProvider.getUriForFile(this, "com.visdat.mobile.fileprovider", image);
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                createImageFile();
                if (photoUri != null) {
                    // Minta mode quick capture (tidak semua vendor menghormati flag ini)
                    // Gunakan string extra agar kompatibel di lebih banyak SDK/OEM
                    takePictureIntent.putExtra("android.intent.extra.quickCapture", true);

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureLauncher.launch(takePictureIntent);
                } else {
                    CustomToast.showToast(this, "Gagal menyiapkan file foto.", Toast.LENGTH_SHORT);
                    finish();
                }
            } catch (IOException e) {
                CustomToast.showToast(this, "Gagal buat file foto: " + e.getMessage(), Toast.LENGTH_SHORT);
                finish();
            }
        } else {
            CustomToast.showToast(this, "Tidak ada aplikasi kamera.", Toast.LENGTH_SHORT);
            finish();
        }
    }
}
