package com.visdat.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.app.ActivityOptionsCompat;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIME = 2000; // 2 detik

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: konten benar-benar center tanpa terdorong status/navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);

        // Sembunyikan system bars sementara di splash agar layout benar-benar center
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), findViewById(android.R.id.content));
        insetsController.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        ImageView logo = findViewById(R.id.logoView);
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.splash_animation);
        logo.startAnimation(anim);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            // Shared element transition to MainActivity
            androidx.core.util.Pair<android.view.View, String> pair = androidx.core.util.Pair.create(logo, "app_logo");
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, pair);

            // Kembalikan system bars di activity berikutnya
            startActivity(intent, options.toBundle());
            finish();
        }, SPLASH_TIME);
    }
}
