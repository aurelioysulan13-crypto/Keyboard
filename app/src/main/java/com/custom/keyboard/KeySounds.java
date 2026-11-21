
package com.custom.keyboard;

import android.content.Context;
import android.media.AudioManager;

/**
 * Uses Android's native keyboard sound effects.
 *
 * No custom sound files are bundled. The phone's own system sound-effect
 * configuration determines whether these sounds are audible.
 */
final class KeySounds {

    static final int KEY = 0;
    static final int DELETE = 1;
    static final int MODIFIER = 2;

    private final AudioManager audioManager;

    KeySounds(Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    void play(int kind) {
        if (audioManager == null) return;

        try {
            switch (kind) {
                case DELETE:
                    audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                    break;

                case MODIFIER:
                    audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
                    break;

                case KEY:
                default:
                    audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
                    break;
            }
        } catch (RuntimeException ignored) {
            // Some Android devices or system configurations may disable
            // keyboard sound effects.
        }
    }

    void release() {
        // AudioManager is a system service and does not need to be released.
    }
}
