package com.ptchess.club.data.model;

/**
 * One entry in the append-only activity log that powers gamification. Kept a plain
 * POJO (no Android/Firebase deps) so the {@code Gamification} engine over it stays
 * unit-testable. {@code type} is one of PUZZLE / ASSIGNMENT / TOURNAMENT / PODIUM.
 */
public class Activity {
    public static final String PUZZLE = "PUZZLE";
    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String TOURNAMENT = "TOURNAMENT";
    public static final String PODIUM = "PODIUM";

    public String email;    // who earned it (stable cross-device key)
    public String type;
    public String dateIso;  // yyyy-MM-dd (activity day, for streaks)
    public long tsMs;

    public Activity() { }

    public Activity(String email, String type, String dateIso, long tsMs) {
        this.email = email;
        this.type = type;
        this.dateIso = dateIso;
        this.tsMs = tsMs;
    }
}
