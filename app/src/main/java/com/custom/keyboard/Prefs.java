package com.custom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared settings used by both the keyboard and the settings screen. */
final class Prefs {
    private Prefs() {}

    static final String FILE = "keyboard_prefs";

    static final String K_THEME = "theme";            // 0 = system, 1 = light, 2 = dark
    static final String K_ACCENT = "accent";          // index into ACCENTS
    static final String K_HEIGHT = "key_height_dp";
    static final String K_HAPTIC = "haptic";
    static final String K_SOUND = "sound";
    static final String K_SUGGEST = "suggestions";
    static final String K_AUTOCORRECT = "autocorrect";
    static final String K_AUTOCAP = "autocap";
    static final String K_DBLSPACE = "dblspace";
    static final String K_BLUR = "frosted_glass";
    static final String K_LEARN = "learn";
    static final String K_LEARNED = "learned_words";
    static final String K_CLEAR_TOKEN = "clear_learned_token";

    static final int[] ACCENTS = {
            0xFF3478F7, // iOS blue
            0xFF2FB36D, // green
            0xFF9B6BFF, // purple
            0xFFFF8A3D, // orange
            0xFFE9507A  // pink
    };

    static final int MIN_HEIGHT = 44;
    static final int MAX_HEIGHT = 68;
    static final int DEFAULT_HEIGHT = 50;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** All on/off settings default to ON, except key sounds. */
    static boolean on(SharedPreferences p, String key) {
        return p.getBoolean(key, !K_SOUND.equals(key));
    }

    static int theme(SharedPreferences p) {
        int t = p.getInt(K_THEME, 0);
        return (t < 0 || t > 2) ? 0 : t;
    }

    static int accent(SharedPreferences p) {
        int a = p.getInt(K_ACCENT, 0);
        return (a < 0 || a >= ACCENTS.length) ? 0 : a;
    }

    static int height(SharedPreferences p) {
        int h = p.getInt(K_HEIGHT, DEFAULT_HEIGHT);
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
    }
}
