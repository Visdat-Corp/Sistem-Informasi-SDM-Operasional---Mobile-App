package com.example.ptvisdatteknikutama;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private org.osmdroid.views.overlay.Marker currentMarker;
    private LocationManager locationManager;

    private Uri photoUri;

    // UI Components
    private ImageButton btnCamera;
    private CardView locationCard;
    private FloatingActionButton fabMyLocation;
    private TextView tvName, tvId;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<String[]> requestLocationPermissionsLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Intent> enableGpsLauncher;

    private boolean isReturningFromGpsSettings = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Launcher untuk mengambil foto
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (this.photoUri != null) {
                            handleImageCaptureSuccess();
                        } else {
                            Log.e("HomeFragment", "photoUri is null after image capture.");
                            CustomToast.showToast(getContext(), "Gagal mendapatkan URI foto setelah pengambilan.", Toast.LENGTH_SHORT);
                        }
                    } else {
                        Log.d("HomeFragment", "Image capture cancelled or failed. Result code: " + result.getResultCode());
                        CustomToast.showToast(getContext(), "Pengambilan foto dibatalkan atau gagal.", Toast.LENGTH_SHORT);
                    }
                });

        // Launcher untuk permission lokasi
        requestLocationPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    if (fineLocationGranted) {
                        Log.d("HomeFragment", "Fine location permission granted via launcher.");
                        if (mapView != null) {
                            startLocationUpdates();
                        }
                    } else {
                        Log.w("HomeFragment", "Fine location permission denied via launcher.");
                        CustomToast.showToast(getContext(), "Izin lokasi ditolak. Fitur peta mungkin tidak berfungsi.", Toast.LENGTH_LONG);
                    }
                });

        // Launcher untuk permission kamera
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d("HomeFragment", "Camera permission granted");
                        dispatchTakePictureIntent();
                    } else {
                        Log.w("HomeFragment", "Camera permission denied");
                        CustomToast.showToast(getContext(), "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_LONG);
                    }
                });

        // Launcher untuk GPS settings
        enableGpsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isReturningFromGpsSettings = true;
                    checkGpsStatus();
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(requireActivity().getPackageName());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);

        initializeViews(view);
        setupMap(view, savedInstanceState);
        setupClickListeners();
        
        // Cek GPS terlebih dahulu sebelum cek permission
        checkGpsStatus();
        
        checkLocationPermission();

        // Set Username
        String username = requireActivity().getIntent().getStringExtra("USERNAME");
        if (tvName != null && username != null) {
            tvName.setText(username);
        }

        // Set ID Karyawan
        if (tvId != null) {
            String userId = ApiClient.getStoredUserId(getContext());
            String idKaryawan = requireActivity().getIntent().getStringExtra("ID_KARYAWAN");

            if (userId != null) {
                tvId.setText("ID: " + userId);
            } else if (idKaryawan != null) {
                tvId.setText("ID: " + idKaryawan);
            }
        }
    }

    private void initializeViews(View view) {
        btnCamera = view.findViewById(R.id.btnCamera);
        mapView = view.findViewById(R.id.map);
        locationCard = view.findViewById(R.id.locationCard);
        fabMyLocation = view.findViewById(R.id.fab_my_location);
        tvName = view.findViewById(R.id.tvName);
        tvId = view.findViewById(R.id.tvId);
    }

    private void setupMap(View view, Bundle savedInstanceState) {
        if (mapView != null) {
            try {
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
                mapView.getController().setZoom(16.0);
                GeoPoint startPoint = new GeoPoint(-5.1477, 119.4327); // Makassar
                mapView.getController().setCenter(startPoint);
                mapView.setMultiTouchControls(false);
                mapView.setBuiltInZoomControls(false);
                mapView.setHorizontalMapRepetitionEnabled(false);
                mapView.setVerticalMapRepetitionEnabled(false);
                mapView.setOnTouchListener((v, event) -> true);
                mapView.setMinZoomLevel(16.0);
                mapView.setMaxZoomLevel(18.0);
                mapView.invalidate();

                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates();
                }

            } catch (Exception e) {
                Log.e("HomeFragment", "Error setting up map", e);
                CustomToast.showToast(getContext(), "Gagal memuat peta: " + e.getMessage(), Toast.LENGTH_SHORT);
            }
        } else {
            Log.e("HomeFragment", "MapView is null in setupMap.");
        }
    }

    private void setupClickListeners() {
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                Animation buttonAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_click);
                v.startAnimation(buttonAnim);
                checkCameraPermissionAndTakePicture();
            });
        }
        if (fabMyLocation != null) {
            fabMyLocation.setOnClickListener(v -> {
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    mapView.getController().animateTo(myLocationOverlay.getMyLocation());
                    mapView.getController().setZoom(17.0);
                } else {
                    CustomToast.showToast(getContext(), "Lokasi saat ini belum tersedia.", Toast.LENGTH_SHORT);
                    checkLocationPermission();
                }
            });
        }
    }

    private void showLocationCardWithAnimation() {
        if (locationCard != null && locationCard.getVisibility() != View.VISIBLE) {
            locationCard.setVisibility(View.VISIBLE);
            Animation slideInAnim = AnimationUtils.loadAnimation(getContext(), R.anim.card_slide_in);
            locationCard.startAnimation(slideInAnim);
        }
    }

    private void checkCameraPermissionAndTakePicture() {
        // Cek GPS terlebih dahulu sebelum mengambil foto
        if (!isGpsEnabled()) {
            showGpsAlertDialog();
            return;
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("HomeFragment", "Camera permission not granted, requesting...");
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            Log.d("HomeFragment", "Camera permission already granted");
            dispatchTakePictureIntent();
        }
    }

    /**
     * Mengecek apakah GPS/Location Services aktif
     */
    private boolean isGpsEnabled() {
        if (locationManager == null) {
            locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        }
        
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            Log.e("HomeFragment", "Error checking GPS provider", e);
        }
        
        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            Log.e("HomeFragment", "Error checking Network provider", e);
        }
        
        return gpsEnabled || networkEnabled;
    }

    /**
     * Mengecek status GPS saat fragment dibuka atau kembali dari settings
     */
    private void checkGpsStatus() {
        if (!isGpsEnabled()) {
            if (isReturningFromGpsSettings) {
                // Jika kembali dari settings dan GPS masih mati, kembali ke dashboard
                isReturningFromGpsSettings = false;
                CustomToast.showToast(getContext(), "GPS tidak aktif. Kembali ke dashboard.", Toast.LENGTH_SHORT);
                navigateBackToDashboard();
            } else {
                // Pertama kali cek, tampilkan dialog
                showGpsAlertDialog();
            }
        } else {
            isReturningFromGpsSettings = false;
        }
    }

    /**
     * Menampilkan dialog untuk meminta pengguna menyalakan GPS
     */
    private void showGpsAlertDialog() {
        if (getContext() == null) return;
        
        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_gps_alert, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Ya, Aktifkan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Buka settings lokasi
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        enableGpsLauncher.launch(intent);
                    }
                })
                .setNegativeButton("Tidak", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        CustomToast.showToast(getContext(), "Absensi memerlukan GPS aktif. Kembali ke dashboard.", Toast.LENGTH_SHORT);
                        navigateBackToDashboard();
                    }
                });
        
        AlertDialog alert = builder.create();
        alert.show();
        
        // Styling tombol (opsional, untuk Material Design)
        if (alert.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.visdat_blue)
            );
        }
        if (alert.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            );
        }
    }

    /**
     * Kembali ke dashboard
     */
    private void navigateBackToDashboard() {
        if (getActivity() != null) {
            // Jika dalam fragment, cukup close fragment atau navigate
            // Karena HomeFragment ada di dalam Dashboard, kita bisa kembali ke tab default
            getActivity().onBackPressed();
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            if (mapView != null && (myLocationOverlay == null || !myLocationOverlay.isMyLocationEnabled()) ) {
                startLocationUpdates();
            }
        }
    }

    private void startLocationUpdates() {
        if (mapView == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (myLocationOverlay == null) {
            GpsMyLocationProvider provider = new GpsMyLocationProvider(requireContext());
            provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
            provider.addLocationSource(LocationManager.GPS_PROVIDER);
            myLocationOverlay = new MyLocationNewOverlay(provider, mapView);
        }

        if (!mapView.getOverlays().contains(myLocationOverlay)) {
            mapView.getOverlays().add(myLocationOverlay);
        }

        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();

        myLocationOverlay.runOnFirstFix(() -> {
            if (getActivity() != null && mapView != null && myLocationOverlay.getMyLocation() != null) {
                getActivity().runOnUiThread(() -> {
                    mapView.getController().animateTo(myLocationOverlay.getMyLocation());
                    mapView.getController().setZoom(17.0);
                });
            }
        });
        mapView.invalidate();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            throw new IOException("Cannot get external files directory.");
        }
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        this.photoUri = FileProvider.getUriForFile(requireContext(),
                "com.example.ptvisdatteknikutama.fileprovider",
                image);
        return image;
    }

    private void dispatchTakePictureIntent() {
        Log.d("HomeFragment", "dispatchTakePictureIntent called");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            Log.d("HomeFragment", "Camera app found");
            try {
                createImageFile();
                if (this.photoUri != null) {
                    Log.d("HomeFragment", "Photo URI created: " + this.photoUri.toString());
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, this.photoUri);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureLauncher.launch(takePictureIntent);
                } else {
                    Log.e("HomeFragment", "Photo URI is null");
                    CustomToast.showToast(getContext(), "Gagal menyiapkan file foto.", Toast.LENGTH_SHORT);
                }
            } catch (IOException ex) {
                Log.e("HomeFragment", "Error creating image file", ex);
                CustomToast.showToast(getContext(), "Gagal buat file foto: " + ex.getMessage(), Toast.LENGTH_SHORT);
            }
        } else {
            Log.e("HomeFragment", "No camera app found");
            CustomToast.showToast(getContext(), "Tidak ada aplikasi kamera ditemukan.", Toast.LENGTH_SHORT);
        }
    }

    private void handleImageCaptureSuccess() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(lastLocation == null) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastLocation != null) {
                navigateToKonfirmasiFoto(this.photoUri, lastLocation);
            } else {
                navigateToKonfirmasiFoto(this.photoUri, null);
                CustomToast.showToast(getContext(), "Gagal mendapatkan lokasi saat ini. Foto tanpa data lokasi.", Toast.LENGTH_LONG);
            }
        } else {
            navigateToKonfirmasiFoto(this.photoUri, null);
            CustomToast.showToast(getContext(), "Izin lokasi tidak diberikan, foto tanpa data lokasi.", Toast.LENGTH_LONG);
        }
    }

    private void navigateToKonfirmasiFoto(Uri capturedPhotoUri, @Nullable Location location) {
        Intent intent = new Intent(getActivity(), KonfirmasiFoto.class);
        intent.putExtra("photo_uri", capturedPhotoUri.toString());
        if (location != null) {
            intent.putExtra("latitude", location.getLatitude());
            intent.putExtra("longitude", location.getLongitude());
        } else {
            intent.putExtra("latitude", 0.0);
            intent.putExtra("longitude", 0.0);
        }
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Cek GPS status saat resume (kembali dari activity lain atau screen on)
        if (!isReturningFromGpsSettings) {
            // Hanya cek jika bukan dari settings, untuk menghindari double check
            checkGpsStatus();
        }
        
        if (mapView != null) {
            mapView.onResume();
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (mapView != null && (myLocationOverlay == null || !myLocationOverlay.isMyLocationEnabled())) {
                startLocationUpdates();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDetach();
        }
        mapView = null;
        myLocationOverlay = null;
        currentMarker = null;
    }
}