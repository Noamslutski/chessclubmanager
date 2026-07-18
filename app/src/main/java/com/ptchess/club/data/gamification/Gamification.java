package com.ptchess.club.data.gamification;

import com.ptchess.club.data.model.Activity;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure gamification engine: turns an append-only {@link Activity} log into XP,
 * level, streaks, badges and an admin leaderboard. No Android or Firebase types,
 * so it is fully unit-tested. XP values live here so scoring stays in one place.
 */
public final class Gamification {

    public static final int XP_PUZZLE = 10;
    public static final int XP_ASSIGNMENT = 25;
    public static final int XP_TOURNAMENT = 40;
    public static final int XP_PODIUM = 60;

    /** XP required to advance one level (level = 1 + xp / XP_PER_LEVEL). */
    public static final int XP_PER_LEVEL = 100;

    private Gamification() { }

    public static int pointsFor(String type) {
        if (type == null) return 0;
        switch (type) {
            case Activity.PUZZLE: return XP_PUZZLE;
            case Activity.ASSIGNMENT: return XP_ASSIGNMENT;
            case Activity.TOURNAMENT: return XP_TOURNAMENT;
            case Activity.PODIUM: return XP_PODIUM;
            default: return 0;
        }
    }

    public static int level(int xp) {
        return 1 + Math.max(0, xp) / XP_PER_LEVEL;
    }

    // ----------------------------------------------------------------- Stats

    public static final class Stats {
        public int xp;
        public int level;
        public int xpIntoLevel;   // progress within the current level
        public int xpForLevel = XP_PER_LEVEL;
        public int puzzles;
        public int assignments;
        public int tournaments;
        public int podiums;
        public int currentStreak;
        public int longestStreak;
        public List<Badge> badges = new ArrayList<>();
    }

    public static final class Badge {
        public final String id;      // stable key; UI maps it to a title + emoji
        public final int current;
        public final int target;
        public final boolean earned;

        public Badge(String id, int current, int target) {
            this.id = id;
            this.current = current;
            this.target = target;
            this.earned = current >= target;
        }
    }

    /** Summary for one member's activities, as of {@code today}. */
    public static Stats summarize(List<Activity> activities, LocalDate today) {
        Stats s = new Stats();
        Set<Long> activeDays = new HashSet<>();
        if (activities != null) {
            for (Activity a : activities) {
                if (a == null || a.type == null) continue;
                s.xp += pointsFor(a.type);
                switch (a.type) {
                    case Activity.PUZZLE: s.puzzles++; break;
                    case Activity.ASSIGNMENT: s.assignments++; break;
                    case Activity.TOURNAMENT: s.tournaments++; break;
                    case Activity.PODIUM: s.podiums++; break;
                    default: break;
                }
                Long day = epochDay(a.dateIso);
                if (day != null) activeDays.add(day);
            }
        }
        s.level = level(s.xp);
        s.xpIntoLevel = s.xp % XP_PER_LEVEL;
        s.currentStreak = currentStreak(activeDays, today);
        s.longestStreak = longestStreak(activeDays);
        s.badges = badges(s);
        return s;
    }

    private static List<Badge> badges(Stats s) {
        List<Badge> out = new ArrayList<>();
        out.add(new Badge("puzzle_1", s.puzzles, 1));
        out.add(new Badge("puzzle_10", s.puzzles, 10));
        out.add(new Badge("puzzle_50", s.puzzles, 50));
        out.add(new Badge("puzzle_100", s.puzzles, 100));
        out.add(new Badge("assign_1", s.assignments, 1));
        out.add(new Badge("assign_10", s.assignments, 10));
        out.add(new Badge("assign_25", s.assignments, 25));
        out.add(new Badge("tourney_1", s.tournaments, 1));
        out.add(new Badge("tourney_5", s.tournaments, 5));
        out.add(new Badge("podium_1", s.podiums, 1));
        out.add(new Badge("streak_3", s.longestStreak, 3));
        out.add(new Badge("streak_7", s.longestStreak, 7));
        out.add(new Badge("streak_30", s.longestStreak, 30));
        return out;
    }

    // ----------------------------------------------------------- Leaderboard

    public static final class Ranked {
        public String email;
        public String name;
        public int xp;
        public int level;
        public int puzzles;
        public int assignments;
        public int tournaments;
    }

    /**
     * Aggregates the club's activity log into a per-member XP ranking (highest first).
     * {@code nameByEmail} supplies display names; unknown emails fall back to the email.
     */
    public static List<Ranked> leaderboard(List<Activity> all, Map<String, String> nameByEmail) {
        Map<String, Ranked> byEmail = new LinkedHashMap<>();
        if (all != null) {
            for (Activity a : all) {
                if (a == null || a.email == null || a.email.isEmpty() || a.type == null) continue;
                String key = a.email.trim().toLowerCase(Locale.ROOT);
                Ranked r = byEmail.get(key);
                if (r == null) {
                    r = new Ranked();
                    r.email = key;
                    r.name = nameByEmail != null && nameByEmail.containsKey(key)
                            ? nameByEmail.get(key) : key;
                    byEmail.put(key, r);
                }
                r.xp += pointsFor(a.type);
                switch (a.type) {
                    case Activity.PUZZLE: r.puzzles++; break;
                    case Activity.ASSIGNMENT: r.assignments++; break;
                    case Activity.TOURNAMENT: r.tournaments++; break;
                    default: break;
                }
            }
        }
        List<Ranked> out = new ArrayList<>(byEmail.values());
        for (Ranked r : out) r.level = level(r.xp);
        out.sort((x, y) -> Integer.compare(y.xp, x.xp));
        return out;
    }

    /** Number of distinct members active (any activity) since {@code sinceInclusive}. */
    public static int activeSince(List<Activity> all, LocalDate sinceInclusive) {
        Set<String> emails = new HashSet<>();
        if (all != null) {
            long cutoff = sinceInclusive.toEpochDay();
            for (Activity a : all) {
                if (a == null || a.email == null) continue;
                Long day = epochDay(a.dateIso);
                if (day != null && day >= cutoff) emails.add(a.email.trim().toLowerCase(Locale.ROOT));
            }
        }
        return emails.size();
    }

    // ---------------------------------------------------------------- streaks

    private static int currentStreak(Set<Long> days, LocalDate today) {
        if (days == null || days.isEmpty() || today == null) return 0;
        long anchor = today.toEpochDay();
        // Allow "today not done yet": start from today if present, else yesterday.
        long cursor;
        if (days.contains(anchor)) cursor = anchor;
        else if (days.contains(anchor - 1)) cursor = anchor - 1;
        else return 0;
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor--;
        }
        return streak;
    }

    private static int longestStreak(Set<Long> days) {
        if (days == null || days.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(days);
        Collections.sort(sorted);
        int best = 1;
        int run = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) == sorted.get(i - 1) + 1) {
                run++;
            } else {
                run = 1;
            }
            if (run > best) best = run;
        }
        return best;
    }

    private static Long epochDay(String dateIso) {
        if (dateIso == null || dateIso.length() < 10) return null;
        try {
            return LocalDate.parse(dateIso.substring(0, 10)).toEpochDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** All badge ids in display order (used by the UI to render locked + earned). */
    public static List<String> allBadgeIds() {
        return Arrays.asList("puzzle_1", "puzzle_10", "puzzle_50", "puzzle_100",
                "assign_1", "assign_10", "assign_25",
                "tourney_1", "tourney_5", "podium_1",
                "streak_3", "streak_7", "streak_30");
    }
}
