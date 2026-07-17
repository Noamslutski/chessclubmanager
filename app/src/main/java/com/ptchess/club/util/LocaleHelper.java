package com.ptchess.club.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * Handles the app's UI language. Hebrew is the default; the user can switch to
 * English at any time from Settings. The choice is persisted and re-applied on
 * every Activity/Application {@code attachBaseContext}.
 */
public final class LocaleHelper {

    public static final String LANG_HEBREW = "he";
    public static final String LANG_ENGLISH = "en";

    private static final String PREFS = "ptchess_locale";
    private static final String KEY_LANG = "app_language";

    private LocaleHelper() { }

    public static Context onAttach(Context context) {
        return applyLocale(context, getLanguage(context));
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, LANG_HEBREW);
    }

    /** Persists the choice and returns a context configured for it. */
    public static Context setLanguage(Context context, String language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANG, language).apply();
        return applyLocale(context, language);
    }

    public static boolean isHebrew(Context context) {
        return LANG_HEBREW.equals(getLanguage(context));
    }

    private static Context applyLocale(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale); // RTL for Hebrew, LTR for English

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createConfigurationContext(config);
        } else {
            context.getResources().updateConfiguration(
                    config, context.getResources().getDisplayMetrics());
            return context;
        }
    }
}
