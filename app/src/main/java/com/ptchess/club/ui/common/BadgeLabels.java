package com.ptchess.club.ui.common;

import android.content.Context;

import com.ptchess.club.R;

/** Maps gamification badge ids to a display emoji + localized title. */
public final class BadgeLabels {

    private BadgeLabels() { }

    public static String emoji(String id) {
        if (id == null) return "⭐";
        if (id.startsWith("puzzle")) return "🧩";
        if (id.startsWith("assign")) return "✅";
        if (id.startsWith("tourney")) return "♟";
        if (id.startsWith("podium")) return "🏆";
        if (id.startsWith("streak")) return "🔥";
        return "⭐";
    }

    /** e.g. "10 puzzles", "1 podium", "7 day streak" — composed for both locales. */
    public static String title(Context ctx, String id, int target) {
        String word;
        if (id == null) {
            word = "";
        } else if (id.startsWith("puzzle")) {
            word = ctx.getString(R.string.gam_cat_puzzles);
        } else if (id.startsWith("assign")) {
            word = ctx.getString(R.string.gam_cat_assignments);
        } else if (id.startsWith("tourney")) {
            word = ctx.getString(R.string.gam_cat_tournaments);
        } else if (id.startsWith("podium")) {
            word = ctx.getString(R.string.gam_cat_podium);
        } else {
            word = ctx.getString(R.string.gam_cat_streak);
        }
        return target + " " + word;
    }
}
