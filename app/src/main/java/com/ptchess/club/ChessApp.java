package com.ptchess.club;

import android.app.Application;
import android.content.Context;

import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.LocaleHelper;

/**
 * Application entry point. Applies the persisted UI language (Hebrew by default)
 * and warms up the local repository / seeds demo data on first launch.
 */
public class ChessApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Warm the DB off the main thread so first-run seeding (which hashes the
        // demo passwords with PBKDF2) never blocks the UI.
        Async.io(() -> ClubRepository.getInstance(getApplicationContext()).getAnyAdmin());
    }
}
