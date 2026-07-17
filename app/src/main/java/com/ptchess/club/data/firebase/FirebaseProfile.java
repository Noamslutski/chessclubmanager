package com.ptchess.club.data.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads/writes the user profile document at {@code users/{uid}} in Firestore.
 * This is the cross-device source of truth for a member's role, club and
 * status; a Cloud Function turns it into Auth custom claims for the rules.
 */
public final class FirebaseProfile {

    public interface Cb {
        void onResult(User user); // null when no profile exists
    }

    private FirebaseProfile() { }

    public static void write(String uid, User u) {
        if (uid == null || u == null) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("fullName", u.fullName);
            m.put("email", u.email);
            m.put("phone", u.phone);
            m.put("role", u.role != null ? u.role.name() : Role.CHILD.name());
            m.put("status", u.status);
            m.put("clubId", u.clubId);
            m.put("rating", u.rating);
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(m, SetOptions.merge());
        } catch (Throwable ignored) { }
    }

    /** Queues a registration request so the Cloud Function emails the club admin. */
    public static void queueRegistration(long clubId, String fullName, String email) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("clubId", clubId);
            m.put("fullName", fullName);
            m.put("email", email);
            m.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("registrations").add(m);
        } catch (Throwable ignored) { }
    }

    public static void fetch(String uid, Cb cb) {
        if (uid == null) {
            cb.onResult(null);
            return;
        }
        try {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(d -> {
                        if (d == null || !d.exists()) {
                            cb.onResult(null);
                            return;
                        }
                        User u = new User();
                        u.fullName = d.getString("fullName");
                        u.email = d.getString("email");
                        u.phone = d.getString("phone");
                        u.role = Role.fromName(d.getString("role"));
                        u.status = d.getString("status");
                        Long club = d.getLong("clubId");
                        u.clubId = club != null ? club : 0;
                        Long rating = d.getLong("rating");
                        u.rating = rating != null ? rating.intValue() : 0;
                        cb.onResult(u);
                    })
                    .addOnFailureListener(e -> cb.onResult(null));
        } catch (Throwable t) {
            cb.onResult(null);
        }
    }
}
