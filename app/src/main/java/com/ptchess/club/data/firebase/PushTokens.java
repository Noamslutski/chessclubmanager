package com.ptchess.club.data.firebase;

import android.content.Context;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores this device's FCM registration token on the signed-in user's profile
 * ({@code users/{uid}.fcmTokens}) so Cloud Functions can target them. A user may
 * write their own profile, so no extra rules are needed. All calls are guarded and
 * no-op when Firebase isn't configured or nobody is signed in.
 */
public final class PushTokens {

    private PushTokens() { }

    /** Fetches the current token and saves it (call after login / on app start). */
    public static void register(Context ctx) {
        if (!FirebaseServices.isAvailable(ctx) || FirebaseAuthService.currentUid() == null) return;
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> save(ctx, token));
        } catch (Throwable ignored) { }
    }

    public static void save(Context ctx, String token) {
        String uid = FirebaseAuthService.currentUid();
        if (uid == null || token == null || token.isEmpty()) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("fcmTokens", FieldValue.arrayUnion(token));
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(m, SetOptions.merge());
        } catch (Throwable ignored) { }
    }

    /** Best-effort removal of this device's token, so a shared device stops
     *  receiving the previous user's pushes after logout. */
    public static void removeCurrentToken(Context ctx) {
        if (!FirebaseServices.isAvailable(ctx)) return;
        final String uid = FirebaseAuthService.currentUid();
        if (uid == null) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                if (token == null || token.isEmpty()) return;
                Map<String, Object> m = new HashMap<>();
                m.put("fcmTokens", FieldValue.arrayRemove(token));
                FirebaseFirestore.getInstance().collection("users").document(uid)
                        .set(m, SetOptions.merge());
            });
        } catch (Throwable ignored) { }
    }
}
