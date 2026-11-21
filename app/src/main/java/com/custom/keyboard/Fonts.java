package com.custom.keyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * SF Pro Display typeface provider.
 *
 * Font files are bundled in app/src/main/assets/fonts/.
 * This applies only inside this app and its keyboard IME, not to Android
 * or other apps on the device.
 */
final class Fonts {

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface bold;
    private static Typeface emoji;

    private Fonts() {}

    private static Typeface load(Context context, String file, Typeface fallback) {
        try {
            return Typeface.createFromAsset(context.getAssets(), "fonts/" + file);
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    static synchronized Typeface ui(Context context) {
        if (regular == null) {
            regular = load(context, "SFPRODISPLAYREGULAR.OTF", Typeface.create("sans-serif", Typeface.NORMAL));
        }
        return regular;
    }

    static synchronized Typeface medium(Context context) {
        if (medium == null) {
            medium = load(context, "SFPRODISPLAYMEDIUM.OTF", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return medium;
    }

    static synchronized Typeface bold(Context context) {
        if (bold == null) {
            bold = load(context, "SFPRODISPLAYBOLD.OTF", Typeface.create("sans-serif", Typeface.BOLD));
        }
        return bold;
    }

    /**
     * Keep Android's system emoji fallback so emoji remain colorful and complete.
     */
    static synchronized Typeface emoji(Context context) {
        if (emoji == null) {
            emoji = Typeface.DEFAULT;
        }
        return emoji;
    }

    /**
     * Applies SF Pro Display recursively to the app's visible text widgets.
     * Call this after building the Activity's view hierarchy.
     */
    static void applyToTree(Context context, View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface current = textView.getTypeface();
            boolean wasBold = current != null && current.isBold();
            textView.setTypeface(wasBold ? bold(context) : ui(context));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToTree(context, group.getChildAt(i));
            }
        }
    }
}
