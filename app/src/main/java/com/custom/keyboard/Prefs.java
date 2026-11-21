
package com.custom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared settings used by the keyboard and the settings screen.
 */
final class Prefs {

    private Prefs() {}

    static final String FILE = "keyboard_prefs";

    // Appearance
    static final String K_THEME = "theme";       // 0 = system, 1 = light, 2 = dark
    static final String K_ACCENT = "accent";     // Index into ACCENTS
    static final String K_HEIGHT = "key_height_dp";
    static final String K_BLUR = "frosted_glass";
    static final String K_ROUNDED = "rounded_corners";

    // Keyboard interaction
    static final String K_HAPTIC = "haptic";
    static final String K_SOUND = "sound";

    // Text assistance
    static final String K_SUGGEST = "suggestions";
    static final String K_AUTOCORRECT = "autocorrect";
    static final String K_AUTOCAP = "autocap";
    static final String K_DBLSPACE = "dblspace";
    static final String K_LEARN = "learn";
    static final String K_LEARNED = "learned_words";
    static final String K_CLEAR_TOKEN = "clear_learned_token";

    // Emoji
    static final String K_EMOJI_RECENT = "emoji_recent";

    // Clipboard
    static final String K_CLIP = "clipboard_enabled";
    static final String K_CLIP_CLEAR_TOKEN = "clip_clear_token";

    static final int[] ACCENTS = {
            0xFF3478F7, // Blue
            0xFF2FB36D, // Green
            0xFF9B6BFF, // Purple
            0xFFFF8A3D, // Orange
            0xFFE9507A  // Pink
    };

    static final int MIN_HEIGHT = 44;
    static final int MAX_HEIGHT = 68;
    static final int DEFAULT_HEIGHT = 50;

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * All on/off settings default to ON, except key sounds.
     */
    static boolean on(SharedPreferences preferences, String key) {
        if (preferences == null || key == null) {
            return false;
        }

        return preferences.getBoolean(key, !K_SOUND.equals(key));
    }

    static int theme(SharedPreferences preferences) {
        if (preferences == null) return 0;

        int value = preferences.getInt(K_THEME, 0);
        return (value < 0 || value > 2) ? 0 : value;
    }

    static int accent(SharedPreferences preferences) {
        if (preferences == null) return 0;

        int value = preferences.getInt(K_ACCENT, 0);
        return (value < 0 || value >= ACCENTS.length) ? 0 : value;
    }

    static int height(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_HEIGHT;

        int value = preferences.getInt(K_HEIGHT, DEFAULT_HEIGHT);
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, value));
    }
}
