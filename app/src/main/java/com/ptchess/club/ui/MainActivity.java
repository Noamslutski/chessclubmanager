package com.ptchess.club.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.PushTokens;
import com.ptchess.club.data.model.User;
import com.ptchess.club.security.SessionManager;
import com.ptchess.club.ui.auth.AuthActivity;
import com.ptchess.club.ui.child.LibraryFragment;
import com.ptchess.club.ui.child.PuzzlesFragment;
import com.ptchess.club.ui.common.BaseActivity;
import com.ptchess.club.ui.common.HomeFragment;
import com.ptchess.club.ui.common.MoreFragment;
import com.ptchess.club.ui.common.WatchFragment;

/** Root screen after login: toolbar + bottom navigation hosting the sections. */
public class MainActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private User currentUser;
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SessionManager session = new SessionManager(this);
        currentUser = ClubRepository.getInstance(this).getUserById(session.getUserId());
        if (currentUser == null || !currentUser.isActive()) {
            session.clear();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Keep the Up affordance in sync with the sub-screen back stack.
        getSupportFragmentManager().addOnBackStackChangedListener(() ->
                showUp(getSupportFragmentManager().getBackStackEntryCount() > 0));

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return top(new HomeFragment(), R.string.app_name);
            if (id == R.id.nav_watch) return top(new WatchFragment(), R.string.nav_watch);
            if (id == R.id.nav_puzzles) return top(new PuzzlesFragment(), R.string.nav_puzzles);
            if (id == R.id.nav_library) return top(new LibraryFragment(), R.string.nav_library);
            if (id == R.id.nav_more) return top(new MoreFragment(), R.string.nav_more);
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        // Push notifications: register this device's token and (on Android 13+)
        // ask for permission. Both no-op when Firebase isn't configured.
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
        requestNotificationsIfNeeded();
        PushTokens.register(this);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /** Reloads the user (role/status may have changed) — used after admin actions. */
    public void refreshUser() {
        currentUser = ClubRepository.getInstance(this).getUserById(currentUser.id);
    }

    private boolean top(Fragment fragment, int titleRes) {
        getSupportFragmentManager().popBackStack(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainContainer, fragment)
                .commit();
        setTitle(getString(titleRes));
        showUp(false);
        return true;
    }

    /** Opens a sub-screen from the More menu with an Up affordance. */
    public void openSection(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainContainer, fragment)
                .addToBackStack(null)
                .commit();
        setTitle(title);
        showUp(true);
    }

    private void showUp(boolean show) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(show);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    public void logout() {
        PushTokens.removeCurrentToken(this); // stop pushes to this device for this user
        new SessionManager(this).clear();
        com.ptchess.club.data.firebase.FirebaseAuthService.signOut();
        Intent i = new Intent(this, AuthActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
