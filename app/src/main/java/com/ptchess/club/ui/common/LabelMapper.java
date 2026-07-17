package com.ptchess.club.ui.common;

import android.content.Context;

import com.ptchess.club.R;

/** Maps stored enum-style keys (category / side) to localized display strings. */
public final class LabelMapper {

    private LabelMapper() { }

    public static String category(Context ctx, String key) {
        if (key == null) return "";
        switch (key) {
            case "opening": return ctx.getString(R.string.cat_opening);
            case "attack": return ctx.getString(R.string.cat_attack);
            case "defense": return ctx.getString(R.string.cat_defense);
            case "endgame": return ctx.getString(R.string.cat_endgame);
            case "psychology": return ctx.getString(R.string.cat_psychology);
            case "strategy": return ctx.getString(R.string.cat_strategy);
            case "tactics": return ctx.getString(R.string.cat_tactics);
            default: return key;
        }
    }

    public static String side(Context ctx, String key) {
        if (key == null) return "";
        switch (key) {
            case "WHITE": return ctx.getString(R.string.side_white);
            case "BLACK": return ctx.getString(R.string.side_black);
            default: return ctx.getString(R.string.side_both);
        }
    }

    public static final String[] CATEGORY_KEYS = {
            "opening", "tactics", "attack", "defense", "endgame", "strategy", "psychology"};

    public static final String[] SIDE_KEYS = {"WHITE", "BLACK", "BOTH"};
}
