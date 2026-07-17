package com.ptchess.club.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.ptchess.club.R;
import com.ptchess.club.security.SessionManager;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.BaseActivity;

/** Hosts the login / register / reset-password fragments. */
public class AuthActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        if (savedInstanceState == null) {
            show(new LoginFragment(), false);
        }
    }

    public void showLogin() { show(new LoginFragment(), true); }
    public void showRegister() { show(new RegisterFragment(), true); }
    public void showReset() { show(new ResetPasswordFragment(), true); }

    private void show(Fragment fragment, boolean addToBackStack) {
        var tx = getSupportFragmentManager().beginTransaction()
                .replace(R.id.authContainer, fragment);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }

    /** Called by LoginFragment on a successful sign-in. */
    public void onAuthSuccess(long userId) {
        new SessionManager(this).setUserId(userId);
        startActivity(new Intent(this, MainActivity.class));
        finishAffinity();
    }
}
