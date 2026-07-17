package com.ptchess.club.data.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Wraps Firebase Authentication (email/password) with simple callbacks. All
 * calls are no-ops-with-error when Firebase isn't configured, so callers can
 * fall back to the local login path.
 *
 * <p>Firebase owns credentials and password-reset emails; the app keeps a local
 * mirror of the user profile (role/club/status) for its offline-first data.</p>
 */
public final class FirebaseAuthService {

    public interface AuthCb {
        void onSuccess(String uid);
        void onError(String message);
    }

    private FirebaseAuthService() { }

    public static boolean enabled(Context context) {
        return FirebaseServices.isAvailable(context);
    }

    public static String currentUid() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u != null ? u.getUid() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void signOut() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Throwable ignored) { }
    }

    public static void signIn(String email, String password, AuthCb cb) {
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener(r -> cb.onSuccess(uid(r.getUser())))
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        } catch (Throwable t) {
            cb.onError(t.getMessage());
        }
    }

    public static void register(String email, String password, AuthCb cb) {
        try {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener(r -> cb.onSuccess(uid(r.getUser())))
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        } catch (Throwable t) {
            cb.onError(t.getMessage());
        }
    }

    public static void updatePassword(String newPassword, AuthCb cb) {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) {
                cb.onError("no session");
                return;
            }
            u.updatePassword(newPassword)
                    .addOnSuccessListener(x -> cb.onSuccess(u.getUid()))
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        } catch (Throwable t) {
            cb.onError(t.getMessage());
        }
    }

    /** Sends a real password-reset email. Always calls back (no enumeration hint). */
    public static void sendReset(String email, Runnable onDone) {
        try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                    .addOnCompleteListener(t -> onDone.run());
        } catch (Throwable t) {
            onDone.run();
        }
    }

    /**
     * Verifies another account's credentials (used for parent→child linking)
     * on a secondary Firebase app so the current session is untouched.
     */
    public static void verifyCredentials(Context ctx, String email, String password, AuthCb cb) {
        try {
            FirebaseApp secondary = secondaryApp(ctx);
            FirebaseAuth auth = FirebaseAuth.getInstance(secondary);
            auth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener(r -> {
                        String id = uid(r.getUser());
                        auth.signOut();
                        cb.onSuccess(id);
                    })
                    .addOnFailureListener(e -> {
                        auth.signOut();
                        cb.onError(e.getMessage());
                    });
        } catch (Throwable t) {
            cb.onError(t.getMessage());
        }
    }

    private static FirebaseApp secondaryApp(Context ctx) {
        try {
            return FirebaseApp.getInstance("verify");
        } catch (IllegalStateException notYet) {
            FirebaseApp def = FirebaseApp.getInstance();
            return FirebaseApp.initializeApp(ctx.getApplicationContext(), def.getOptions(), "verify");
        }
    }

    private static String uid(FirebaseUser user) {
        return user != null ? user.getUid() : null;
    }
}
