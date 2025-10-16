package com.example.ptvisdatteknikutama;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.tabs.TabLayout;

import org.json.JSONObject;

import java.io.IOException;

public class NavigationActivity extends AppCompatActivity {

    private TextView tvId, tvUserName, tvUserStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initializeViews();
        setupTabLayout();

        // Load fragment awal
        if (savedInstanceState == null) {
            boolean fromHistory = getIntent().getBooleanExtra("from_history", false);
            if (fromHistory) {
                loadFragment(new HistoryFragment());
            } else {
                loadFragment(new HomeFragment());
            }
        }
    }

    private void initializeViews() {
        tvId = findViewById(R.id.tvId);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserStatus = findViewById(R.id.tvUserStatus);

        // Set nama dari intent
        String name = getIntent().getStringExtra("NAME");
        if (name != null && tvUserName != null) {
            tvUserName.setText(name);
        }
        if (tvUserStatus != null) {
            tvUserStatus.setText("Online");
        }

        // Tombol kembali ke Dashboard
        ImageView btnLogout = findViewById(R.id.btnLogoutDashboard);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, activity_dashboard.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Ambil data user dari API
        fetchUserProfile();

        // Ambil pengaturan dari API
        fetchSettings();
    }

    private void fetchUserProfile() {
        String authToken = getIntent().getStringExtra("AUTH_TOKEN");
        if (authToken == null) return;

        ApiClient.getClient(this).newCall(new okhttp3.Request.Builder()
                        .url(ApiClient.API_BASE_URL + "/v1/employee/profile")
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build())
                .enqueue(new okhttp3.Callback() {
                    @Override
                    public void onFailure(okhttp3.Call call, IOException e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                        if (response.isSuccessful()) {
                            try {
                                String responseBody = response.body().string();
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.getBoolean("success")) {
                                    JSONObject data = jsonResponse.getJSONObject("data");
                                    String name = data.getString("name");
                                    String id = data.getString("id");
                                    runOnUiThread(() -> {
                                        if (tvUserName != null) {
                                            tvUserName.setText(name);
                                        }
                                        if (tvId != null) {
                                            tvId.setText("ID: " + id);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void setupTabLayout() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout != null) {
            tabLayout.addTab(tabLayout.newTab().setText("Home"));
            tabLayout.addTab(tabLayout.newTab().setText("History"));

            boolean fromHistory = getIntent().getBooleanExtra("from_history", false);
            if (fromHistory) {
                tabLayout.getTabAt(1).select();
            } else {
                tabLayout.getTabAt(0).select();
            }

            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int position = tab.getPosition();
                    if (position == 0) {
                        loadFragment(new HomeFragment());
                    } else if (position == 1) {
                        loadFragment(new HistoryFragment());
                    }
                }

                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void fetchSettings() {
        new Thread(() -> {
            try {
                JSONObject settings = ApiClient.getSettings(this);
                JSONObject workHours = settings.getJSONObject("work_hours");
                String overtimeStartTime = workHours.getString("overtime_start_time");

                SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putString("overtime_start_time", overtimeStartTime).apply();

                android.util.Log.d("NavigationActivity", "Settings fetched, overtime start: " + overtimeStartTime);
            } catch (IOException | org.json.JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showToast(this, "Gagal mengambil pengaturan", Toast.LENGTH_SHORT));
            }
        }).start();
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_enter, R.anim.fragment_exit);
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    public String getTvId() {
        return tvId != null ? tvId.getText().toString() : "";
    }
}
