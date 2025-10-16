package com.example.ptvisdatteknikutama;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class activity_dashboard extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        String username = getIntent().getStringExtra("USERNAME");
        String idKaryawan = getIntent().getStringExtra("ID_KARYAWAN");

        TextView tvName = findViewById(R.id.tvName);
        if (tvName != null && username != null) {
            tvName.setText(username);
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

        // Welcome dialog (opsional)
        boolean showWelcomeDialog = getIntent().getBooleanExtra("show_welcome_dialog", false);
        if (showWelcomeDialog) {
            final String finalIdKaryawan = idKaryawan != null ? idKaryawan : "N/A";
            final String finalUsername = username != null ? username : "Pengguna";
            findViewById(android.R.id.content).postDelayed(() -> {
                WelcomeDialog.show(activity_dashboard.this, finalIdKaryawan, finalUsername);
            }, 500);
        }

        // Tombol Absensi
        LinearLayout btnAbsensi = findViewById(R.id.btnAbsensi);
        if (btnAbsensi != null) {
            btnAbsensi.setOnClickListener(v -> {
                try {
                    // TODO: Ganti HomeFragment dengan activity absensi yang benar (misal NavigationActivity)
                    Intent intent = new Intent(activity_dashboard.this, NavigationActivity.class);
                    intent.putExtra("USERNAME", username);
                    intent.putExtra("AUTH_TOKEN", getIntent().getStringExtra("AUTH_TOKEN"));
                    startActivity(intent);
                } catch (Exception e) {
                    CustomToast.showToast(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG);
                    e.printStackTrace();
                }
            });
        }

        // ✅ Tombol Pengeluaran Kendaraan → tampilkan pesan toast
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
        ImageView btnLogoutDashboard = findViewById(R.id.btnLogoutDashboard);
        if (btnLogoutDashboard != null) {
            btnLogoutDashboard.setOnClickListener(v -> {
                Animation animScale = AnimationUtils.loadAnimation(this, R.anim.scale);
                btnLogoutDashboard.startAnimation(animScale);

                new AlertDialog.Builder(activity_dashboard.this)
                        .setTitle("Konfirmasi Logout")
                        .setMessage("Apakah Anda yakin ingin logout?")
                        .setPositiveButton("Ya", (dialog, which) -> {
                            ApiClient.clearAllAuth(activity_dashboard.this);

                            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                            prefs.edit().clear().apply();

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
}
