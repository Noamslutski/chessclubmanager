package com.ptchess.club.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.User;
import com.ptchess.club.security.SessionManager;
import com.ptchess.club.ui.auth.AuthActivity;
import com.ptchess.club.ui.common.BaseActivity;
import com.ptchess.club.util.Async;

/** Brief branded splash that routes to the app or the login flow. */
public class SplashActivity extends BaseActivity {

    private static final long SPLASH_MS = 850;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::route, SPLASH_MS);
    }

    private void route() {
        SessionManager session = new SessionManager(this);
        long userId = session.getUserId();
        // DB lookup runs off the main thread; navigation happens back on it.
        Async.io(() -> {
            boolean active = false;
            if (userId > 0) {
                User user = ClubRepository.getInstance(getApplicationContext()).getUserById(userId);
                if (user != null && user.isActive()) {
                    active = true;
                } else {
                    session.clear();
                }
            }
            boolean toMain = active;
            Async.main(() -> {
                if (isFinishing()) return;
                startActivity(new Intent(this, toMain ? MainActivity.class : AuthActivity.class));
                finish();
            });
        });
    }
}
