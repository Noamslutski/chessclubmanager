package com.ptchess.club.data.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;

/**
 * Thin availability gate for Firebase. Everything Firebase-related is optional:
 * if no {@code google-services.json} was added, no default {@link FirebaseApp}
 * initializes and {@link #isAvailable} returns false, so the app keeps working
 * fully offline/local. Once Firebase is configured, cloud features light up.
 */
public final class FirebaseServices {

    private FirebaseServices() { }

    public static boolean isAvailable(Context context) {
        try {
            return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
}
