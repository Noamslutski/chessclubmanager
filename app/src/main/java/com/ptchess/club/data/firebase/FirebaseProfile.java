package com.ptchess.club.data.firebase;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public interface StringsCb {
        void onResult(List<String> values);
    }

    private FirebaseProfile() { }

    /**
     * Records a verified parent→child link on the parent's own profile so the
     * relationship follows the parent across devices (the {@code childEmails}
     * array scopes their cloud reads). A parent may only write their own doc.
     */
    public static void addChildEmail(String parentUid, String childEmail) {
        if (parentUid == null || childEmail == null || childEmail.isEmpty()) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("childEmails", FieldValue.arrayUnion(childEmail.trim().toLowerCase()));
            FirebaseFirestore.getInstance().collection("users").document(parentUid)
                    .set(m, SetOptions.merge());
        } catch (Throwable ignored) { }
    }

    /** Reads the child emails a parent has linked (empty on any failure). */
    public static void fetchChildEmails(String parentUid, StringsCb cb) {
        if (parentUid == null) {
            cb.onResult(new ArrayList<>());
            return;
        }
        try {
            FirebaseFirestore.getInstance().collection("users").document(parentUid).get()
                    .addOnSuccessListener(d -> {
                        List<String> out = new ArrayList<>();
                        if (d != null && d.exists()) {
                            Object o = d.get("childEmails");
                            if (o instanceof List) {
                                for (Object e : (List<?>) o) if (e != null) out.add(e.toString());
                            }
                        }
                        cb.onResult(out);
                    })
                    .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
        } catch (Throwable t) {
            cb.onResult(new ArrayList<>());
        }
    }

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
