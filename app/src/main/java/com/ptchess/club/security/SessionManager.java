package com.ptchess.club.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Stores the currently signed-in user id in {@link EncryptedSharedPreferences}
 * (AES-256, key held in the Android Keystore) so a rooted-device dump does not
 * reveal the active session. Falls back to standard prefs only if the Keystore
 * is unavailable on very old / broken devices.
 */
public final class SessionManager {

    private static final String FILE = "ptchess_secure_session";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = build(context.getApplicationContext());
    }

    private static SharedPreferences build(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx,
                    FILE,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            return ctx.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public void setUserId(long userId) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public boolean isLoggedIn() {
        return getUserId() > 0;
    }

    public void clear() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }
}
