package com.visdat.mobile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    // Global exception handler to prevent app crashes
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    EditText etEmail, etPassword;
    MaterialButton btnLogin;
    TextView tvTitle;
    ImageView mainLogo;
    LinearLayout logoContainer;
    CardView loginCard;

    SharedPreferences sharedPreferences;
    public static final String PREFS_NAME = "loginPrefs";
    public static final String KEY_AUTH_TOKEN = "authToken";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_NAME = "name";
    public static final String KEY_ID_KARYAWAN = "idKaryawan";
    public static final String KEY_REMEMBER_LOGIN = "rememberLogin";

    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up global exception handler to prevent app crashes
        setupGlobalExceptionHandler();

        setContentView(R.layout.activity_main);

        client = ApiClient.getClient(getApplicationContext());

        etEmail = findViewById(R.id.Username);
        etPassword = findViewById(R.id.Password);
        btnLogin = findViewById(R.id.btnLogin);
        mainLogo = findViewById(R.id.mainLogo);
        logoContainer = findViewById(R.id.logoContainer);
        loginCard = findViewById(R.id.loginCard);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Tambahkan animasi masuk untuk elemen-elemen UI
        setupAnimations();

        // Auto login jika remember aktif
        boolean rememberLogin = sharedPreferences.getBoolean(KEY_REMEMBER_LOGIN, false);
        // cbRemember.setChecked(rememberLogin); // Dihapus karena CheckBox dihapus

        if (rememberLogin) {
            String token = sharedPreferences.getString(KEY_AUTH_TOKEN, null);
            String username = sharedPreferences.getString(KEY_USERNAME, null);
            String name = sharedPreferences.getString(KEY_NAME, null);
            String idKaryawan = sharedPreferences.getString(KEY_ID_KARYAWAN, null);
            if (token != null && username != null && name != null && idKaryawan != null) {
                ApiClient.storeAuthToken(MainActivity.this, token);
                Intent intent = new Intent(MainActivity.this, activity_dashboard.class);
                intent.putExtra("USERNAME", username);
                intent.putExtra("NAME", name);
                intent.putExtra("ID_KARYAWAN", idKaryawan);
                intent.putExtra("AUTH_TOKEN", token);
                startActivity(intent);
                finish();
                return;
            }
        }

        // Tombol login
        btnLogin.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_scale));

            String emailOrUsername = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (emailOrUsername.isEmpty() || password.isEmpty()) {
                CustomToast.showToast(this, "Isi Email/Username & Password dulu", Toast.LENGTH_SHORT);
                return;
            }
            loginToBackend(emailOrUsername, password);
        });
    }

    /**
     * Set up global exception handler to prevent app crashes
     */
    private void setupGlobalExceptionHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Log the exception
            Log.e("MainActivity", "Uncaught exception in thread " + thread.getName(), throwable);

            try {
                // Show user-friendly error message
                runOnUiThread(() -> {
                    try {
                        CustomToast.showToast(MainActivity.this,
                                "Terjadi kesalahan aplikasi. Silakan restart aplikasi.",
                                Toast.LENGTH_LONG);

                        // Show dialog with error details
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Kesalahan Aplikasi")
                                .setMessage("Terjadi kesalahan yang tidak terduga. Aplikasi akan ditutup untuk keamanan.\n\n" +
                                        "Error: " + throwable.getMessage())
                                .setCancelable(false)
                                .setPositiveButton("Tutup Aplikasi", (dialog, which) -> {
                                    // Exit the app gracefully
                                    finishAffinity();
                                    System.exit(0);
                                })
                                .create()
                                .show();
                    } catch (Exception e) {
                        // If even the error handling fails, just exit
                        Log.e("MainActivity", "Error in exception handler", e);
                        finishAffinity();
                        System.exit(1);
                    }
                });
            } catch (Exception e) {
                // Last resort - just exit
                Log.e("MainActivity", "Critical error in global exception handler", e);
                finishAffinity();
                System.exit(1);
            }
        });
    }

    private void setupAnimations() {
        // Animasi untuk logo (scale in dengan bounce)
        mainLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in));

        // Animasi untuk logo container (fade in)
        logoContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_slow));

        // Animasi untuk login card (slide in dari bawah)
        loginCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom));
    }

    private void loginToBackend(String usernameValue, String password) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", usernameValue)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(ApiClient.API_BASE_URL + "/v1/employee/login")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        CustomToast.showToast(MainActivity.this,
                                "Login gagal: " + e.getMessage(),
                                Toast.LENGTH_SHORT)
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        boolean success = jsonResponse.getBoolean("success");

                        if (success) {
                            JSONObject dataObject = jsonResponse.getJSONObject("data");
                            String authToken = dataObject.getString("token");
                            JSONObject userObject = dataObject.getJSONObject("user");
                            String usernameFromServer = userObject.getString("username");
                            String nameFromServer = userObject.getString("name");
                            String idKaryawanFromServer = userObject.optString("id_karyawan",
                                    userObject.optString("id", "N/A"));

                            // Simpan token + data user
                            ApiClient.storeAuthToken(MainActivity.this, authToken);

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            // Selalu simpan login
                            editor.putString(KEY_AUTH_TOKEN, authToken);
                            editor.putString(KEY_USERNAME, usernameFromServer);
                            editor.putString(KEY_NAME, nameFromServer);
                            editor.putString(KEY_ID_KARYAWAN, idKaryawanFromServer);
                            editor.putBoolean(KEY_REMEMBER_LOGIN, true); // Selalu set true untuk auto-login berikutnya
                            // Jika tidak ingin mengingat login, baris di atas bisa diubah menjadi false
                            // atau dihapus, dan bagian auto-login di atas akan di-skip.
                            // Untuk saat ini, kita asumsikan login selalu diingat.
                            editor.apply();

                            runOnUiThread(() -> {
                                CustomToast.showToast(MainActivity.this, "Login berhasil!", Toast.LENGTH_SHORT);
                                Intent intent = new Intent(MainActivity.this, activity_dashboard.class);
                                intent.putExtra("USERNAME", usernameFromServer);
                                intent.putExtra("NAME", nameFromServer);
                                intent.putExtra("ID_KARYAWAN", idKaryawanFromServer);
                                intent.putExtra("AUTH_TOKEN", authToken);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String message = jsonResponse.optString("message", "Username / Password salah");
                            runOnUiThread(() ->
                                    CustomToast.showToast(MainActivity.this, message, Toast.LENGTH_SHORT)
                            );
                        }

                    } catch (JSONException e) {
                        runOnUiThread(() ->
                                CustomToast.showToast(MainActivity.this,
                                        "Gagal memproses data login: " + e.getMessage(),
                                        Toast.LENGTH_SHORT)
                        );
                    }
                } else {
                    final String finalErrorMessage = response.message();
                    runOnUiThread(() ->
                            CustomToast.showToast(MainActivity.this,
                                    "Login gagal: " + finalErrorMessage + " (Kode: " + response.code() + ")",
                                    Toast.LENGTH_LONG)
                    );
                }
            }
        });
    }
}
