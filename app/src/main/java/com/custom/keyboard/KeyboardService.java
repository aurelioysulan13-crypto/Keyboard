
package com.custom.keyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen keyboard. Built entirely in code (no deprecated KeyboardView),
 * with letters, two symbol pages, emoji, suggestions and gesture cursor control.
 */
public class KeyboardService extends InputMethodService {

    // ------------------------------------------------------------------ layers
    private static final int LAYER_LETTERS = 0;
    private static final int LAYER_SYMBOLS1 = 1;
    private static final int LAYER_SYMBOLS2 = 2;
    private static final int LAYER_EMOJI = 3;

    // ---------------------------------------------------------------- key types
    private static final int T_CHAR = 0;
    private static final int T_SHIFT = 1;
    private static final int T_DELETE = 2;
    private static final int T_ENTER = 3;
    private static final int T_SPACE = 4;
    private static final int T_TO_SYMBOLS1 = 5;
    private static final int T_TO_SYMBOLS2 = 6;
    private static final int T_TO_LETTERS = 7;
    private static final int T_TO_EMOJI = 8;
    private static final int T_GLOBE = 9;
    private static final int T_SPACER = 10;

    private static final String WORD_BREAKS = ".,!?;:)";

    private static final int[] EMOJI = {
            0x1F600, 0x1F601, 0x1F602, 0x1F923, 0x1F603, 0x1F604, 0x1F605, 0x1F606,
            0x1F609, 0x1F60A, 0x1F60B, 0x1F60E, 0x1F60D, 0x1F618, 0x1F617, 0x1F619,
            0x1F61A, 0x1F642, 0x1F917, 0x1F914, 0x1F610, 0x1F611, 0x1F636, 0x1F644,
            0x1F60F, 0x1F623, 0x1F625, 0x1F62E, 0x1F910, 0x1F62F, 0x1F62A, 0x1F62B,
            0x1F634, 0x1F60C, 0x1F61B, 0x1F61C, 0x1F61D, 0x1F612, 0x1F613, 0x1F614,
            0x1F615, 0x1F643, 0x1F911, 0x1F632, 0x1F641, 0x1F616, 0x1F61E, 0x1F61F,
            0x1F624, 0x1F622, 0x1F62D, 0x1F626, 0x1F627, 0x1F628, 0x1F629, 0x1F62C,
            0x1F630, 0x1F631, 0x1F633, 0x1F635, 0x1F621, 0x1F620, 0x1F44D, 0x1F44E,
            0x1F44C, 0x270C, 0x1F91E, 0x1F44F, 0x1F64C, 0x1F64F, 0x1F4AA, 0x1F44B,
            0x1F91D, 0x261D, 0x2764, 0x1F495, 0x1F496, 0x1F49B, 0x1F499, 0x1F49A,
            0x1F49C, 0x1F494, 0x1F525, 0x2728, 0x1F31F, 0x1F4AF, 0x1F389, 0x1F38A,
            0x1F381, 0x1F3B5, 0x2705, 0x274C, 0x2757, 0x2753, 0x1F440, 0x1F4A9,
            0x1F480, 0x1F47B, 0x1F680, 0x1F308, 0x2600, 0x1F319, 0x2B50, 0x1F355,
            0x1F354, 0x2615, 0x1F37A, 0x1F382
    };

    private static final class Colors {
        int bg, key, keyPressed, fn, fnPressed, text, hint, accent, divider;
    }

    private static final class Key {
        final int type;
        final String label;
        final String text;
        final float weight;
        final String alt;
        final boolean letter;

        Key(int type, String label, String text, float weight, String alt, boolean letter) {
            this.type = type;
            this.label = label;
            this.text = text;
            this.weight = weight;
            this.alt = alt;
            this.letter = letter;
        }
    }

    // ------------------------------------------------------------------- state
    private SharedPreferences prefs;
    private Suggester suggester;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ArrayList<KeyView> letterViews = new ArrayList<>();
    private final TextView[] suggestionViews = new TextView[3];
    private KeyView shiftView;
    private LinearLayout keysContainer;
    private Colors colors = new Colors();
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density = 2f;
    private int keyHeightPx = 150;

    private int layer = LAYER_LETTERS;
    private boolean shifted = false;
    private boolean capsLock = false;
    private long lastShiftTap = 0;
    private long lastSpaceTime = 0;
    private long lastOwnEdit = 0;
    private boolean spaceAfterSuggestion = false;
    private boolean rebuildPending = false;

    private boolean isPasswordField = false;
    private boolean isEmailField = false;
    private boolean isUriField = false;
    private boolean numberMode = false;
    private boolean noSuggest = false;
    private boolean smart = false;
    private boolean rawKeyMode = false;
    private int imeAction = EditorInfo.IME_ACTION_NONE;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (Prefs.K_CLEAR_TOKEN.equals(key)) {
                        if (suggester != null) suggester.clearLearned();
                    } else if (Prefs.K_THEME.equals(key) || Prefs.K_ACCENT.equals(key)
                            || Prefs.K_HEIGHT.equals(key)) {
                        rebuildPending = true;
                    }
                }
            };

    // --------------------------------------------------------------- lifecycle
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Prefs.get(this);
        density = getResources().getDisplayMetrics().density;
        suggester = new Suggester(this);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        return buildKeyboard();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // never take over the whole screen in landscape
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (keysContainer != null) {
            setInputView(buildKeyboard());
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        classifyField(info);
        if (rebuildPending || keysContainer == null) {
            rebuildPending = false;
            setInputView(buildKeyboard());
        }
        if (!restarting) {
            capsLock = false;
            shifted = false;
            layer = numberMode ? LAYER_SYMBOLS1 : LAYER_LETTERS;
        }
        spaceAfterSuggestion = false;
        renderKeys();
        updateAutoShift();
        updateSuggestions();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
                                  int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (SystemClock.uptimeMillis() - lastOwnEdit < 150) {
            return; // our own edit, already handled
        }
        updateSuggestions();
        if (newSelStart == newSelEnd && Math.abs(newSelStart - oldSelStart) > 1) {
            updateAutoShift();
        }
    }

    // ------------------------------------------------------- field classification
    private void classifyField(EditorInfo info) {
        isPasswordField = false;
        isEmailField = false;
        isUriField = false;
        numberMode = false;
        noSuggest = false;
        smart = false;
        rawKeyMode = false;
        imeAction = EditorInfo.IME_ACTION_NONE;
        if (info == null) {
            return;
        }
        int type = info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int var = type & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                isPasswordField = true;
            }
            if (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
                isEmailField = true;
            }
            if (var == InputType.TYPE_TEXT_VARIATION_URI) {
                isUriField = true;
            }
            if ((type & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                noSuggest = true;
            }
            smart = !isPasswordField && !isEmailField && !isUriField && !noSuggest;
        } else if (cls == InputType.TYPE_CLASS_NUMBER
                || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            numberMode = true;
        } else if (cls == InputType.TYPE_NULL) {
            rawKeyMode = true;
        }
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            imeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (imeAction == EditorInfo.IME_ACTION_UNSPECIFIED) {
                imeAction = EditorInfo.IME_ACTION_NONE;
            }
        }
    }

    // ------------------------------------------------------------ building views
    private int dp(float v) {
        return Math.round(v * density);
    }

    private Colors loadColors() {
        Colors c = new Colors();
        int mode = Prefs.theme(prefs);
        boolean dark;
        if (mode == 0) {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        } else {
            dark = mode == 2;
        }
        if (dark) {
            c.bg = 0xFF1B1C20;
            c.key = 0xFF3A3C42;
            c.keyPressed = 0xFF55585F;
            c.fn = 0xFF2A2C31;
            c.fnPressed = 0xFF44474D;
            c.text = 0xFFECEDEF;
            c.hint = 0xFF9A9DA5;
            c.divider = 0xFF3A3C42;
        } else {
            c.bg = 0xFFD9DCE2;
            c.key = 0xFFFFFFFF;
            c.keyPressed = 0xFFC3C7CF;
            c.fn = 0xFFC4C9D2;
            c.fnPressed = 0xFFA8AEBA;
            c.text = 0xFF1B1C20;
            c.hint = 0xFF6B6F78;
            c.divider = 0xFFBFC3CB;
        }
        c.accent = Prefs.ACCENTS[Prefs.accent(prefs)];
        return c;
    }

    private View buildKeyboard() {
        density = getResources().getDisplayMetrics().density;
        colors = loadColors();
        int hDp = Prefs.height(prefs);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hDp = Math.round(hDp * 0.72f);
        }
        keyHeightPx = dp(hDp);
        hintPaint.setTextSize(dp(10));
        hintPaint.setTextAlign(Paint.Align.RIGHT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.bg);
        root.setPadding(dp(2), dp(2), dp(2), dp(6));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildSuggestionBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setMotionEventSplittingEnabled(true);
        root.addView(keysContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        renderKeys();
        return root;
    }

    private TextView barButton(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(colors.hint);
        return tv;
    }

    private View buildSuggestionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView gear = barButton("\u2699");
        gear.setContentDescription("Keyboard settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        bar.addView(gear, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tv.setTextColor(colors.text);
            tv.setPadding(dp(6), 0, dp(6), 0);
            if (i == 1) {
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            }
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String s = ((TextView) v).getText().toString();
                    if (!s.isEmpty()) {
                        feedback(v, T_CHAR);
                        pickSuggestion(s);
                    }
                }
            });
            suggestionViews[i] = tv;
            bar.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            if (i < 2) {
                View d = new View(this);
                d.setBackgroundColor(colors.divider);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(20));
                lp.gravity = Gravity.CENTER_VERTICAL;
                bar.addView(d, lp);
            }
        }

        TextView hide = barButton("\u25BE");
        hide.setContentDescription("Hide keyboard");
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestHideSelf(0);
            }
        });
        bar.addView(hide, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        return bar;
    }

    private void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        requestHideSelf(0);
        startActivity(i);
    }

    // ------------------------------------------------------------------ layouts
    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static Key fn(int type, String label, float weight) {
        return new Key(type, label, null, weight, null, false);
    }

    private static Key ch(String s, float weight) {
        return new Key(T_CHAR, s, s, weight, null, false);
    }

    private static Key[] chars(String chars, String alts, boolean letters) {
        Key[] out = new Key[chars.length()];
        for (int i = 0; i < chars.length(); i++) {
            String s = String.valueOf(chars.charAt(i));
            String a = (alts == null) ? null : String.valueOf(alts.charAt(i));
            out[i] = new Key(T_CHAR, s, s, 1f, a, letters);
        }
        return out;
    }

    private static Key[] row(Object... parts) {
        ArrayList<Key> list = new ArrayList<>();
        for (Object p : parts) {
            if (p instanceof Key) {
                list.add((Key) p);
            } else if (p instanceof Key[]) {
                for (Key k : (Key[]) p) {
                    list.add(k);
                }
            }
        }
        return list.toArray(new Key[0]);
    }

    private String enterLabel() {
        switch (imeAction) {
            case EditorInfo.IME_ACTION_GO:
                return "Go";
            case EditorInfo.IME_ACTION_SEARCH:
                return cp(0x1F50D);
            case EditorInfo.IME_ACTION_SEND:
                return "\u27A4";
            case EditorInfo.IME_ACTION_NEXT:
                return "\u2192";
            case EditorInfo.IME_ACTION_DONE:
                return "\u2713";
            default:
                return "\u21B5";
        }
    }

    private Key[] bottomRow(boolean fromLetters) {
        String comma = isEmailField ? "@" : (isUriField ? "/" : ",");
        return new Key[]{
                fromLetters ? fn(T_TO_SYMBOLS1, "?123", 1.3f) : fn(T_TO_LETTERS, "ABC", 1.3f),
                fn(T_GLOBE, cp(0x1F310), 1f),
                ch(comma, 1f),
                new Key(T_SPACE, "English", " ", 3.4f, null, false),
                ch(".", 1f),
                fn(T_TO_EMOJI, cp(0x1F642), 1f),
                fn(T_ENTER, enterLabel(), 1.3f)
        };
    }

    private Key[][] lettersLayout() {
        return new Key[][]{
                chars("qwertyuiop", "1234567890", true),
                row(fn(T_SPACER, "", 0.5f), chars("asdfghjkl", null, true), fn(T_SPACER, "", 0.5f)),
                row(fn(T_SHIFT, "\u21E7", 1.5f), chars("zxcvbnm", null, true), fn(T_DELETE, "\u232B", 1.5f)),
                bottomRow(true)
        };
    }

    private Key[][] symbols1Layout() {
        return new Key[][]{
                chars("1234567890", null, false),
                chars("@#$_&-+()/", null, false),
                row(fn(T_TO_SYMBOLS2, "=\\<", 1.5f), chars("*\"':;!?", null, false),
                        fn(T_DELETE, "\u232B", 1.5f)),
                bottomRow(false)
        };
    }

    private Key[][] symbols2Layout() {
        return new Key[][]{
                chars("~`|\u2022\u221A\u03C0\u00F7\u00D7<>", null, false),
                chars("\u00A3\u00A2\u20AC\u00A5^\u00B0={}\\", null, false),
                row(fn(T_TO_SYMBOLS1, "?123", 1.5f), chars("%\u00A9\u00AE\u2122[]\u00B6", null, false),
                        fn(T_DELETE, "\u232B", 1.5f)),
                bottomRow(false)
        };
    }

    private Key[] emojiBottomRow() {
        return new Key[]{
                fn(T_TO_LETTERS, "ABC", 1.5f),
                fn(T_GLOBE, cp(0x1F310), 1f),
                new Key(T_SPACE, "English", " ", 4.5f, null, false),
                fn(T_DELETE, "\u232B", 1.5f),
                fn(T_ENTER, enterLabel(), 1.5f)
        };
    }

    // ---------------------------------------------------------------- rendering
    private void renderKeys() {
        if (keysContainer == null) {
            return;
        }
        keysContainer.removeAllViews();
        letterViews.clear();
        shiftView = null;
        if (layer == LAYER_EMOJI) {
            renderEmoji();
        } else {
            Key[][] rows;
            switch (layer) {
                case LAYER_SYMBOLS1:
                    rows = symbols1Layout();
                    break;
                case LAYER_SYMBOLS2:
                    rows = symbols2Layout();
                    break;
                default:
                    rows = lettersLayout();
                    break;
            }
            for (Key[] r : rows) {
                addKeyRow(r);
            }
        }
        updateShiftVisuals();
    }

    private void addKeyRow(Key[] keys) {
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        rowView.setMotionEventSplittingEnabled(true);
        for (Key k : keys) {
            if (k.type == T_SPACER) {
                rowView.addView(new View(this), new LinearLayout.LayoutParams(0, keyHeightPx, k.weight));
                continue;
            }
            KeyView kv = new KeyView(k);
            rowView.addView(kv, new LinearLayout.LayoutParams(0, keyHeightPx, k.weight));
            if (k.letter) {
                letterViews.add(kv);
            }
            if (k.type == T_SHIFT) {
                shiftView = kv;
            }
        }
        keysContainer.addView(rowView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx));
    }

    private void renderEmoji() {
        int cols = 8;
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < EMOJI.length; i += cols) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < cols; j++) {
                TextView tv = new TextView(this);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(colors.text);
                if (i + j < EMOJI.length) {
                    int code = EMOJI[i + j];
                    final String e = (code < 0x10000) ? cp(code) + "\uFE0F" : cp(code);
                    tv.setText(e);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    tv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            feedback(v, T_CHAR);
                            commitPlain(e);
                        }
                    });
                }
                r.addView(tv, new LinearLayout.LayoutParams(0, keyHeightPx, 1f));
            }
            list.addView(r, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx));
        }
        sv.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        keysContainer.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx * 3));
        addKeyRow(emojiBottomRow());
    }

    private void updateShiftVisuals() {
        boolean up = shifted || capsLock;
        for (KeyView kv : letterViews) {
            kv.setText(up ? kv.key.label.toUpperCase(Locale.ROOT) : kv.key.label);
        }
        if (shiftView != null) {
            shiftView.setText(capsLock ? "\u21EA" : "\u21E7");
            shiftView.setActive(up);
        }
    }

    private static int darken(int c) {
        return Color.rgb((int) (Color.red(c) * 0.8f), (int) (Color.green(c) * 0.8f),
                (int) (Color.blue(c) * 0.8f));
    }

    private Drawable roundRect(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(7));
        return g;
    }

    private Drawable keyBackground(int normal, int pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                new InsetDrawable(roundRect(pressed), dp(2), dp(3), dp(2), dp(3)));
        s.addState(new int[]{},
                new InsetDrawable(roundRect(normal), dp(2), dp(3), dp(2), dp(3)));
        return s;
    }

    // ------------------------------------------------------------------ key view
    private final class KeyView extends TextView {
        final Key key;
        private boolean active = false;
        private boolean cancelled = false;
        private boolean longPressFired = false;
        private boolean swiped = false;
        private float lastStepX = 0f;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (key.alt != null) {
                    longPressFired = true;
                    typeChar(key.alt, false);
                } else if (key.type == T_GLOBE) {
                    longPressFired = true;
                    showKeyboardPicker();
                }
            }
        };

        private final Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                handleDelete();
                handler.postDelayed(this, 45);
            }
        };

        KeyView(Key k) {
            super(KeyboardService.this);
            key = k;
            setGravity(Gravity.CENTER);
            setIncludeFontPadding(false);
            setSoundEffectsEnabled(false);
            setText(displayLabel());

            float size;
            if (k.type == T_CHAR) {
                size = k.letter ? 22 : 20;
            } else if (k.type == T_SPACE) {
                size = 13;
            } else if (k.type == T_TO_SYMBOLS1 || k.type == T_TO_SYMBOLS2
                    || k.type == T_TO_LETTERS || "Go".equals(k.label)) {
                size = 15;
            } else {
                size = 21;
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            setTextColor(k.type == T_ENTER ? Color.WHITE
                    : (k.type == T_SPACE ? colors.hint : colors.text));
            applyBackground();
            setContentDescription(describe(k));
        }

        private String displayLabel() {
            return (key.letter && (shifted || capsLock))
                    ? key.label.toUpperCase(Locale.ROOT) : key.label;
        }

        private String describe(Key k) {
            switch (k.type) {
                case T_SHIFT: return "Shift";
                case T_DELETE: return "Delete";
                case T_ENTER: return "Enter";
                case T_SPACE: return "Space";
                case T_GLOBE: return "Switch keyboard";
                case T_TO_EMOJI: return "Emoji";
                case T_TO_SYMBOLS1: return "Numbers and symbols";
                case T_TO_SYMBOLS2: return "More symbols";
                case T_TO_LETTERS: return "Letters";
                default: return k.label;
            }
        }

        void setActive(boolean on) {
            active = on;
            setTextColor(on ? Color.WHITE : colors.text);
            applyBackground();
        }

        private void applyBackground() {
            int bg;
            int bgPressed;
            if (key.type == T_ENTER || (key.type == T_SHIFT && active)) {
                bg = colors.accent;
                bgPressed = darken(colors.accent);
            } else if (key.type == T_CHAR || key.type == T_SPACE) {
                bg = colors.key;
                bgPressed = colors.keyPressed;
            } else {
                bg = colors.fn;
                bgPressed = colors.fnPressed;
            }
            setBackground(keyBackground(bg, bgPressed));
        }

        private boolean outside(MotionEvent e) {
            float s = dp(28);
            return e.getX() < -s || e.getX() > getWidth() + s
                    || e.getY() < -s || e.getY() > getHeight() + s;
        }

        private void cancelPending() {
            handler.removeCallbacks(longPressRunnable);
            handler.removeCallbacks(repeatRunnable);
        }

        @Override
        protected void onDetachedFromWindow() {
            cancelPending();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (key.alt != null && layer == LAYER_LETTERS) {
                hintPaint.setColor(colors.hint);
                canvas.drawText(key.alt, getWidth() - dp(8), dp(15), hintPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(true);
                    cancelled = false;
                    longPressFired = false;
                    swiped = false;
                    lastStepX = e.getX();
                    feedback(this, key.type);
                    if (key.type == T_DELETE) {
                        handleDelete();
                        handler.postDelayed(repeatRunnable, 380);
                    } else if (key.alt != null || key.type == T_GLOBE) {
                        handler.postDelayed(longPressRunnable, 380);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (key.type == T_SPACE) {
                        float step = dp(14);
                        float x = e.getX();
                        while (x - lastStepX >= step) {
                            moveCursor(1);
                            lastStepX += step;
                            swiped = true;
                        }
                        while (lastStepX - x >= step) {
                            moveCursor(-1);
                            lastStepX -= step;
                            swiped = true;
                        }
                    } else if (!cancelled && outside(e)) {
                        cancelled = true;
                        cancelPending();
                        setPressed(false);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    cancelPending();
                    if (key.type == T_DELETE) {
                        afterEdit();
                    } else if (!cancelled && !longPressFired && !swiped) {
                        onKeyTapped(key);
                    } else if (swiped) {
                        afterEdit();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    setPressed(false);
                    cancelPending();
                    return true;

                default:
                    return true;
            }
        }
    }

    // ------------------------------------------------------------ key handling
    private void feedback(View v, int keyType) {
        if (Prefs.on(prefs, Prefs.K_HAPTIC)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (Prefs.on(prefs, Prefs.K_SOUND)) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int fx = AudioManager.FX_KEYPRESS_STANDARD;
                if (keyType == T_SPACE) fx = AudioManager.FX_KEYPRESS_SPACEBAR;
                else if (keyType == T_DELETE) fx = AudioManager.FX_KEYPRESS_DELETE;
                else if (keyType == T_ENTER) fx = AudioManager.FX_KEYPRESS_RETURN;
                am.playSoundEffect(fx);
            }
        }
    }

    private void onKeyTapped(Key k) {
        switch (k.type) {
            case T_CHAR:
                typeChar(k.letter && (shifted || capsLock) ? k.text.toUpperCase(Locale.ROOT) : k.text,
                        k.letter);
                break;
            case T_SPACE:
                handleSpace();
                break;
            case T_ENTER:
                handleEnter();
                break;
            case T_SHIFT:
                handleShift();
                break;
            case T_TO_SYMBOLS1:
                setLayer(LAYER_SYMBOLS1);
                break;
            case T_TO_SYMBOLS2:
                setLayer(LAYER_SYMBOLS2);
                break;
            case T_TO_LETTERS:
                setLayer(LAYER_LETTERS);
                break;
            case T_TO_EMOJI:
                setLayer(LAYER_EMOJI);
                break;
            case T_GLOBE:
                switchKeyboard();
                break;
            default:
                break;
        }
    }

    private void setLayer(int newLayer) {
        layer = newLayer;
        renderKeys();
        updateAutoShift();
        updateSuggestions();
    }

    private void handleShift() {
        long now = SystemClock.uptimeMillis();
        if (capsLock) {
            capsLock = false;
            shifted = false;
        } else if (shifted && now - lastShiftTap < 350) {
            capsLock = true;
            shifted = false;
        } else {
            shifted = !shifted;
        }
        lastShiftTap = now;
        updateShiftVisuals();
        updateSuggestions();
    }

    private void commit(InputConnection ic, String s) {
        if (rawKeyMode && s.length() == 1) {
            sendKeyChar(s.charAt(0));
        } else {
            ic.commitText(s, 1);
        }
    }

    private void typeChar(String s, boolean isLetter) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) {
            return;
        }
        char c = s.charAt(0);
        boolean punctuation = s.length() == 1 && WORD_BREAKS.indexOf(c) >= 0;
        if (spaceAfterSuggestion && punctuation) {
            CharSequence b = ic.getTextBeforeCursor(1, 0);
            if (b != null && b.length() == 1 && b.charAt(0) == ' ') {
                ic.deleteSurroundingText(1, 0);
            }
        }
        if (punctuation) {
            finishWord(ic);
        }
        spaceAfterSuggestion = false;
        commit(ic, s);
        if (isLetter && shifted && !capsLock) {
            shifted = false;
            updateShiftVisuals();
        }
        afterEdit();
    }

    private void commitPlain(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        spaceAfterSuggestion = false;
        ic.commitText(s, 1);
        afterEdit();
    }

    private void handleDelete() {
        spaceAfterSuggestion = false;
        lastOwnEdit = SystemClock.uptimeMillis();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void handleSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (Prefs.on(prefs, Prefs.K_DBLSPACE) && smart && now - lastSpaceTime < 450) {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() == 2 && before.charAt(1) == ' '
                    && Character.isLetterOrDigit(before.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                lastSpaceTime = 0;
                spaceAfterSuggestion = false;
                afterEdit();
                return;
            }
        }
        finishWord(ic);
        commit(ic, " ");
        spaceAfterSuggestion = false;
        lastSpaceTime = now;
        if ((layer == LAYER_SYMBOLS1 || layer == LAYER_SYMBOLS2) && !numberMode) {
            layer = LAYER_LETTERS;
            renderKeys();
        }
        afterEdit();
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        finishWord(ic);
        spaceAfterSuggestion = false;
        lastOwnEdit = SystemClock.uptimeMillis();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null && ei.actionLabel != null
                && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            ic.performEditorAction(ei.actionId);
        } else if (imeAction != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(imeAction);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        afterEdit();
    }

    private void moveCursor(int dir) {
        lastOwnEdit = SystemClock.uptimeMillis();
        sendDownUpKeyEvents(dir > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
    }

    private void switchKeyboard() {
        boolean ok = false;
        try {
            ok = switchToNextInputMethod(false);
        } catch (Throwable ignored) {
            // fall through to the picker
        }
        if (!ok) {
            showKeyboardPicker();
        }
    }

    private void showKeyboardPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    // -------------------------------------------------- words, shift, suggestions
    private static boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'' || c == '\u2019';
    }

    private String currentWord(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(32, 0);
        if (before == null) {
            return "";
        }
        int end = before.length();
        int i = end;
        while (i > 0 && isWordChar(before.charAt(i - 1))) {
            i--;
        }
        while (i < end && (before.charAt(i) == '\'' || before.charAt(i) == '\u2019')) {
            i++;
        }
        return before.subSequence(i, end).toString();
    }

    private String previousWord(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null) {
            return "";
        }
        int i = before.length();
        int spaces = 0;
        while (i > 0 && before.charAt(i - 1) == ' ') {
            i--;
            spaces++;
        }
        if (spaces == 0) {
            return "";
        }
        int end = i;
        while (i > 0 && isWordChar(before.charAt(i - 1))) {
            i--;
        }
        return before.subSequence(i, end).toString().replace('\u2019', '\'').toLowerCase(Locale.ROOT);
    }

    private boolean cursorInsideWord(InputConnection ic) {
        CharSequence after = ic.getTextAfterCursor(1, 0);
        return after != null && after.length() > 0 && Character.isLetter(after.charAt(0));
    }

    /** Called when a word is finished (space / punctuation / enter). */
    private void finishWord(InputConnection ic) {
        if (!smart || cursorInsideWord(ic)) {
            return;
        }
        String w = currentWord(ic);
        if (w.isEmpty()) {
            return;
        }
        if (Prefs.on(prefs, Prefs.K_AUTOCORRECT)) {
            String fix = suggester.correction(w);
            if (fix != null) {
                ic.deleteSurroundingText(w.length(), 0);
                ic.commitText(fix, 1);
                w = fix;
            }
        }
        if (Prefs.on(prefs, Prefs.K_LEARN)) {
            suggester.learn(w);
        }
    }

    private void pickSuggestion(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (Prefs.on(prefs, Prefs.K_AUTOCORRECT)) {
            String fix = suggester.correction(s);
            if (fix != null) {
                s = fix;
            }
        }
        String w = currentWord(ic);
        if (!w.isEmpty()) {
            ic.deleteSurroundingText(w.length(), 0);
        }
        ic.commitText(s + " ", 1);
        if (Prefs.on(prefs, Prefs.K_LEARN)) {
            suggester.learn(s);
        }
        spaceAfterSuggestion = true;
        lastSpaceTime = 0;
        afterEdit();
    }

    private void afterEdit() {
        lastOwnEdit = SystemClock.uptimeMillis();
        updateAutoShift();
        updateSuggestions();
    }

    private void updateAutoShift() {
        if (capsLock || layer != LAYER_LETTERS || !smart || !Prefs.on(prefs, Prefs.K_AUTOCAP)) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null) {
            return;
        }
        boolean want = ic.getCursorCapsMode(ei.inputType) != 0;
        if (want != shifted) {
            shifted = want;
            updateShiftVisuals();
        }
    }

    private void updateSuggestions() {
        if (suggestionViews[0] == null) {
            return;
        }
        List<String> list = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && layer == LAYER_LETTERS && smart
                && Prefs.on(prefs, Prefs.K_SUGGEST) && !cursorInsideWord(ic)) {
            String w = currentWord(ic);
            if (w.isEmpty()) {
                list = suggester.nextWords(previousWord(ic));
                if (shifted || capsLock) {
                    ArrayList<String> up = new ArrayList<>();
                    for (String s : list) {
                        up.add(Character.toUpperCase(s.charAt(0)) + s.substring(1));
                    }
                    list = up;
                }
            } else {
                list = suggester.suggest(w, 3);
            }
        }
        for (int i = 0; i < suggestionViews.length; i++) {
            suggestionViews[i].setText(list != null && i < list.size() ? list.get(i) : "");
        }
    }
}
