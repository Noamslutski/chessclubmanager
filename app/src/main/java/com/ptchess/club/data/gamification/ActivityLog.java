package com.ptchess.club.data.gamification;

import android.content.Context;

import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.FirebaseContent;
import com.ptchess.club.data.model.Activity;
import com.ptchess.club.data.model.User;
import com.ptchess.club.util.Async;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reads/writes the gamification activity log, transparently choosing Firestore
 * (when Firebase is configured) or the local SQLite store. Results are delivered
 * on the main thread. Logging is fire-and-forget.
 */
public final class ActivityLog {

    public interface Cb { void ready(List<Activity> activities); }
    public interface NamesCb { void ready(Map<String, String> nameByEmail); }

    private ActivityLog() { }

    public static String today() {
        return LocalDate.now().toString(); // yyyy-MM-dd
    }

    /** Records one earned activity for {@code user} (today). */
    public static void log(Context ctx, User user, String type) {
        log(ctx, user, type, today());
    }

    public static void log(Context ctx, User user, String type, String dateIso) {
        if (user == null) return;
        if (FirebaseContent.enabled(ctx)) {
            FirebaseContent.logActivity(user.clubId, user.email, type, dateIso);
        } else {
            ClubRepository repo = ClubRepository.getInstance(ctx);
            Async.io(() -> repo.logActivity(user.clubId, user.id, user.email, type, dateIso));
        }
    }

    /** A member's own activities (for the progress dashboard). */
    public static void forUser(Context ctx, User user, Cb cb) {
        if (FirebaseContent.enabled(ctx)) {
            FirebaseContent.fetchActivityForEmail(user.clubId, user.email,
                    new FirebaseContent.ActivityCb() {
                        @Override public void ok(List<Activity> items) { cb.ready(items); }
                        @Override public void fail() { localForEmail(ctx, user.email, cb); }
                    });
        } else {
            localForEmail(ctx, user.email, cb);
        }
    }

    /** The whole club's activities (for the admin leaderboard/analytics). */
    public static void forClub(Context ctx, long clubId, Cb cb) {
        if (FirebaseContent.enabled(ctx)) {
            FirebaseContent.fetchActivityForClub(clubId, new FirebaseContent.ActivityCb() {
                @Override public void ok(List<Activity> items) { cb.ready(items); }
                @Override public void fail() { localForClub(ctx, clubId, cb); }
            });
        } else {
            localForClub(ctx, clubId, cb);
        }
    }

    /** email→name map for leaderboard display. */
    public static void clubNames(Context ctx, long clubId, NamesCb cb) {
        if (FirebaseContent.enabled(ctx)) {
            FirebaseContent.fetchClubEmailNames(clubId, new FirebaseContent.EmailNamesCb() {
                @Override public void ok(Map<String, String> names) { cb.ready(names); }
                @Override public void fail() { localNames(ctx, clubId, cb); }
            });
        } else {
            localNames(ctx, clubId, cb);
        }
    }

    private static void localForEmail(Context ctx, String email, Cb cb) {
        ClubRepository repo = ClubRepository.getInstance(ctx);
        Async.io(() -> {
            List<Activity> list = repo.getActivityForEmail(email);
            Async.main(() -> cb.ready(list));
        });
    }

    private static void localForClub(Context ctx, long clubId, Cb cb) {
        ClubRepository repo = ClubRepository.getInstance(ctx);
        Async.io(() -> {
            List<Activity> list = repo.getActivityForClub(clubId);
            Async.main(() -> cb.ready(list));
        });
    }

    private static void localNames(Context ctx, long clubId, NamesCb cb) {
        ClubRepository repo = ClubRepository.getInstance(ctx);
        Async.io(() -> {
            Map<String, String> names = repo.getClubEmailNames(clubId);
            Async.main(() -> cb.ready(names));
        });
    }
}
