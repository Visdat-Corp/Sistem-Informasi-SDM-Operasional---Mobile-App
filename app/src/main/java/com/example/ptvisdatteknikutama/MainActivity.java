package com.example.ptvisdatteknikutama;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvTitle;

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
        setContentView(R.layout.activity_main);

        client = ApiClient.getClient(getApplicationContext());

        etEmail = findViewById(R.id.Username);
        etPassword = findViewById(R.id.Password);
        btnLogin = findViewById(R.id.btnLogin);
        tvTitle = findViewById(R.id.tvTitle);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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
                intent.putExtra("show_welcome_dialog", true);
                startActivity(intent);
                finish();
                return;
            }
        }

        // Animasi judul
        tvTitle.startAnimation(AnimationUtils.loadAnimation(this, R.anim.title_anim));

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
                                intent.putExtra("show_welcome_dialog", true);
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
