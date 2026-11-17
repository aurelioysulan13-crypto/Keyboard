package com.custom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen keyboard, styled after the iOS keyboard: frosted translucent panel,
 * uniform glass keys, thin-line icons, pop-up key callouts and a QuickType-style bar.
 * Built entirely in code (no deprecated KeyboardView).
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
    private static final int T_MIC = 11;

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
        boolean dark;
        int key, keyPressed, keyShadow, text, divider, accent, calloutFill, calloutText, calloutShadow;
    }

    private static final class Key {
        final int type;
        final String label;
        final String text;
        final float weight;
        final String alt;
        final boolean letter;
        final int icon;

        Key(int type, String label, String text, float weight, String alt, boolean letter, int icon) {
            this.type = type;
            this.label = label;
            this.text = text;
            this.weight = weight;
            this.alt = alt;
            this.letter = letter;
            this.icon = icon;
        }
    }

    // ------------------------------------------------------------------- state
    private SharedPreferences prefs;
    private Suggester suggester;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView[] suggestionViews = new TextView[3];
    private KeyView shiftView;
    private LinearLayout keysContainer;
    private LinearLayout accessoryRow;
    private Colors colors = new Colors();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density = 2f;
    private int keyHeightPx = 150;
    private int accessoryHeightPx = 110;
    private int insetH = 8;
    private int insetV = 14;
    private boolean multiIme = true;
    private boolean windowFormatSet = false;

    // key callout (pop-up) state
    private PopupWindow popup;
    private CalloutView calloutView;
    private KeyView calloutOwner;
    private boolean calibrated = false;
    private boolean calibrating = false;
    private int corrX = 0;
    private int corrY = 0;
    private int curPx = 0;
    private int curPy = 0;
    private float expectScreenX = 0f;
    private float expectScreenY = 0f;

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
                            || Prefs.K_HEIGHT.equals(key) || Prefs.K_BLUR.equals(key)) {
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
        dismissCalloutNow();
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
        dismissCalloutNow();
        if (keysContainer != null) {
            setInputView(buildKeyboard());
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        dismissCalloutNow();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        classifyField(info);
        multiIme = offerSwitch();
        if (rebuildPending || keysContainer == null) {
            rebuildPending = false;
            setInputView(buildKeyboard());
        } else {
            applyWindowStyle();
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

    /** True when the phone has other keyboards, so a globe key is useful (like iOS). */
    private boolean offerSwitch() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return shouldOfferSwitchingToNextInputMethod();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            return imm == null || imm.getEnabledInputMethodList().size() > 1;
        } catch (Throwable t) {
            return true;
        }
    }

    // ------------------------------------------------------- colors and window
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
        c.dark = dark;
        if (dark) {
            c.key = 0x2EFFFFFF;          // glass keys: white at ~18 %
            c.keyPressed = 0x55FFFFFF;
            c.keyShadow = 0;             // no drop edge on dark glass
            c.text = 0xFFFFFFFF;
            c.divider = 0x33FFFFFF;
            c.calloutFill = 0xFF6A6C72;
            c.calloutText = 0xFFFFFFFF;
            c.calloutShadow = 0x55000000;
        } else {
            c.key = 0xF5FFFFFF;
            c.keyPressed = 0xFFC9CCD3;
            c.keyShadow = 0x40000000;    // thin darker edge under white keys
            c.text = 0xFF000000;
            c.divider = 0x26000000;
            c.calloutFill = 0xFFFFFFFF;
            c.calloutText = 0xFF000000;
            c.calloutShadow = 0x40000000;
        }
        c.accent = Prefs.ACCENTS[Prefs.accent(prefs)];
        return c;
    }

    private boolean blurSupported() {
        if (Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            return wm != null && wm.isCrossWindowBlurEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private int panelColor(boolean frosted, boolean blurOk) {
        if (!frosted) {
            return colors.dark ? 0xFF1E2027 : 0xFFE3E4E6;           // solid
        }
        if (blurOk) {
            return colors.dark ? 0xA61E2027 : 0xB8E3E4E6;           // glass over blur
        }
        return colors.dark ? 0xE01E2027 : 0xEBE3E4E6;               // soft translucent, no blur
    }

    private Drawable panelDrawable(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(20);
        g.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        return g;
    }

    /** Makes the keyboard window itself the frosted glass panel. Returns false if it could not. */
    private boolean applyWindowStyle() {
        try {
            if (getWindow() == null) {
                return false;
            }
            Window w = getWindow().getWindow();
            if (w == null) {
                return false;
            }
            boolean frosted = Prefs.on(prefs, Prefs.K_BLUR);
            boolean blurOk = frosted && blurSupported();
            if (!windowFormatSet) {
                w.setFormat(PixelFormat.TRANSLUCENT);
                windowFormatSet = true;
            }
            w.setBackgroundDrawable(panelDrawable(panelColor(frosted, blurOk)));
            if (Build.VERSION.SDK_INT >= 31) {
                w.setBackgroundBlurRadius(blurOk ? dp(28) : 0);
            }
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 28) {
                w.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                View decor = w.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (colors.dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ building views
    private View buildKeyboard() {
        density = getResources().getDisplayMetrics().density;
        colors = loadColors();
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int hDp = Prefs.height(prefs);
        if (landscape) {
            hDp = Math.round(hDp * 0.72f);
        }
        keyHeightPx = dp(hDp);
        accessoryHeightPx = dp(landscape ? 30 : 40);
        insetH = Math.max(1, Math.round(dp(5.6f) / 2f));
        insetV = Math.max(1, Math.round(keyHeightPx * 0.105f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(3), dp(2), dp(3), 0);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!applyWindowStyle()) {
            // Could not style the window: fall back to painting the panel ourselves.
            root.setBackground(panelDrawable(panelColor(false, false)));
        }

        root.addView(buildSuggestionBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(landscape ? 36 : 44)));

        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setMotionEventSplittingEnabled(true);
        root.addView(keysContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        accessoryRow = new LinearLayout(this);
        accessoryRow.setOrientation(LinearLayout.HORIZONTAL);
        accessoryRow.setMotionEventSplittingEnabled(true);
        accessoryRow.setPadding(dp(10), 0, dp(10), 0);
        root.addView(accessoryRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, accessoryHeightPx));

        renderKeys();
        return root;
    }

    private View buildSuggestionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tv.setTextColor(colors.text);
            tv.setPadding(dp(8), 0, dp(8), 0);
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(22));
                lp.gravity = Gravity.CENTER_VERTICAL;
                bar.addView(d, lp);
            }
        }
        return bar;
    }

    // ------------------------------------------------------------------ layouts
    private static Key fn(int type, String label, float weight) {
        return new Key(type, label, null, weight, null, false, Icons.NONE);
    }

    private static Key ic(int type, int icon, float weight) {
        return new Key(type, "", null, weight, null, false, icon);
    }

    private static Key ch(String s, float weight) {
        return new Key(T_CHAR, s, s, weight, null, false, Icons.NONE);
    }

    private static Key space(float weight, String label) {
        return new Key(T_SPACE, label, " ", weight, null, false, Icons.NONE);
    }

    private static Key[] chars(String chars, String alts, boolean letters, float weight) {
        Key[] out = new Key[chars.length()];
        for (int i = 0; i < chars.length(); i++) {
            String s = String.valueOf(chars.charAt(i));
            String a = (alts == null) ? null : String.valueOf(alts.charAt(i));
            String label = letters ? s.toUpperCase(Locale.ROOT) : s;
            out[i] = new Key(T_CHAR, label, s, weight, a, letters, Icons.NONE);
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

    private boolean primaryAction() {
        return imeAction == EditorInfo.IME_ACTION_GO || imeAction == EditorInfo.IME_ACTION_SEARCH
                || imeAction == EditorInfo.IME_ACTION_SEND || imeAction == EditorInfo.IME_ACTION_DONE;
    }

    private Key returnKey(float weight) {
        switch (imeAction) {
            case EditorInfo.IME_ACTION_GO:
                return new Key(T_ENTER, "Go", null, weight, null, false, Icons.NONE);
            case EditorInfo.IME_ACTION_SEARCH:
                return ic(T_ENTER, Icons.SEARCH, weight);
            case EditorInfo.IME_ACTION_SEND:
                return new Key(T_ENTER, "Send", null, weight, null, false, Icons.NONE);
            case EditorInfo.IME_ACTION_DONE:
                return new Key(T_ENTER, "Done", null, weight, null, false, Icons.NONE);
            case EditorInfo.IME_ACTION_NEXT:
                return new Key(T_ENTER, "Next", null, weight, null, false, Icons.NONE);
            default:
                return ic(T_ENTER, Icons.RETURN, weight);
        }
    }

    private Key[] bottomRow(boolean fromLetters) {
        int toggleType = fromLetters ? T_TO_SYMBOLS1 : T_TO_LETTERS;
        String toggleLabel = fromLetters ? "123" : "ABC";
        ArrayList<Key> k = new ArrayList<>();
        if (isEmailField) {
            k.add(fn(toggleType, toggleLabel, 1.2f));
            if (multiIme) k.add(ic(T_TO_EMOJI, Icons.EMOJI, 1.0f));
            k.add(space(multiIme ? 3.6f : 4.6f, ""));
            k.add(ch("@", 1.0f));
            k.add(ch(".", 1.0f));
            k.add(returnKey(2.2f));
        } else if (isUriField) {
            k.add(fn(toggleType, toggleLabel, 1.2f));
            if (multiIme) k.add(ic(T_TO_EMOJI, Icons.EMOJI, 1.0f));
            k.add(space(multiIme ? 3.2f : 4.2f, ""));
            k.add(ch(".", 1.0f));
            k.add(ch("/", 1.0f));
            k.add(new Key(T_CHAR, ".com", ".com", 1.3f, null, false, Icons.NONE));
            k.add(returnKey(1.3f));
        } else if (multiIme) {
            k.add(fn(toggleType, toggleLabel, 1.2f));
            k.add(ic(T_TO_EMOJI, Icons.EMOJI, 1.2f));
            k.add(space(5.2f, "EN"));
            k.add(returnKey(2.4f));
        } else {
            k.add(fn(toggleType, toggleLabel, 2.4f));
            k.add(space(5.2f, "EN"));
            k.add(returnKey(2.4f));
        }
        return k.toArray(new Key[0]);
    }

    private Key[][] lettersLayout() {
        return new Key[][]{
                chars("qwertyuiop", "1234567890", true, 1f),
                row(fn(T_SPACER, "", 0.5f), chars("asdfghjkl", null, true, 1f), fn(T_SPACER, "", 0.5f)),
                row(ic(T_SHIFT, Icons.SHIFT, 1.5f), chars("zxcvbnm", null, true, 1f),
                        ic(T_DELETE, Icons.DELETE, 1.5f)),
                bottomRow(true)
        };
    }

    private Key[][] symbols1Layout() {
        return new Key[][]{
                chars("1234567890", null, false, 1f),
                chars("-/:;()$&@\"", null, false, 1f),
                row(fn(T_TO_SYMBOLS2, "#+=", 1.5f), chars(".,?!'", null, false, 1.4f),
                        ic(T_DELETE, Icons.DELETE, 1.5f)),
                bottomRow(false)
        };
    }

    private Key[][] symbols2Layout() {
        return new Key[][]{
                chars("[]{}#%^*+=", null, false, 1f),
                chars("_\\|~<>\u20AC\u00A3\u00A5\u2022", null, false, 1f),
                row(fn(T_TO_SYMBOLS1, "123", 1.5f), chars(".,?!'", null, false, 1.4f),
                        ic(T_DELETE, Icons.DELETE, 1.5f)),
                bottomRow(false)
        };
    }

    private Key[] emojiBottomRow() {
        return new Key[]{
                fn(T_TO_LETTERS, "ABC", 2.0f),
                space(4.8f, ""),
                ic(T_DELETE, Icons.DELETE, 1.6f),
                returnKey(1.6f)
        };
    }

    // ---------------------------------------------------------------- rendering
    private void renderKeys() {
        if (keysContainer == null) {
            return;
        }
        dismissCalloutNow();
        keysContainer.removeAllViews();
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
        renderAccessory();
        updateShiftVisuals();
    }

    private void renderAccessory() {
        if (accessoryRow == null) {
            return;
        }
        accessoryRow.removeAllViews();
        if (layer == LAYER_EMOJI) {
            accessoryRow.setVisibility(View.GONE);
            return;
        }
        accessoryRow.setVisibility(View.VISIBLE);
        Key left = multiIme ? ic(T_GLOBE, Icons.GLOBE, 1f) : ic(T_TO_EMOJI, Icons.EMOJI, 1f);
        Key right = ic(T_MIC, Icons.MIC, 1f);
        accessoryRow.addView(new KeyView(left, true), new LinearLayout.LayoutParams(
                dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
        accessoryRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        accessoryRow.addView(new KeyView(right, true), new LinearLayout.LayoutParams(
                dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
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
            KeyView kv = new KeyView(k, false);
            rowView.addView(kv, new LinearLayout.LayoutParams(0, keyHeightPx, k.weight));
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
                    final String e = (code < 0x10000)
                            ? new String(Character.toChars(code)) + "\uFE0F"
                            : new String(Character.toChars(code));
                    tv.setText(e);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
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
        if (shiftView != null) {
            shiftView.invalidate();
        }
    }

    private static int darken(int c) {
        return Color.rgb((int) (Color.red(c) * 0.8f), (int) (Color.green(c) * 0.8f),
                (int) (Color.blue(c) * 0.8f));
    }

    private GradientDrawable roundRect(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6.5f));
        return g;
    }

    private Drawable keyFace(int fill, boolean withEdge) {
        GradientDrawable face = roundRect(fill);
        if (!withEdge) {
            LayerDrawable one = new LayerDrawable(new Drawable[]{face});
            one.setLayerInset(0, insetH, insetV, insetH, insetV);
            return one;
        }
        GradientDrawable edge = roundRect(colors.keyShadow);
        LayerDrawable two = new LayerDrawable(new Drawable[]{edge, face});
        int off = Math.max(1, dp(1));
        two.setLayerInset(0, insetH, insetV + off, insetH, Math.max(0, insetV - off));
        two.setLayerInset(1, insetH, insetV, insetH, insetV);
        return two;
    }

    private Drawable keyBackground(int normal, int pressed, boolean withEdge) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, keyFace(pressed, withEdge));
        s.addState(new int[]{}, keyFace(normal, withEdge));
        return s;
    }

    // ------------------------------------------------------------- key callout
    private final class CalloutView extends View {
        private final Path path = new Path();
        private final Path shadowPath = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String label = "";
        private float textX = 0f;
        private float textY = 0f;

        CalloutView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void set(String text, float kl, float kt, float kr, float kb,
                 float hl, float hr, float ht, float hb) {
            label = text;
            float rh = dp(10);
            float rk = dp(6.5f);
            Icons.callout(path, kl, kt, kr, kb, hl, hr, ht, hb, rh, rk);
            float off = dp(1.5f);
            Icons.callout(shadowPath, kl, kt + off, kr, kb + off, hl, hr, ht + off, hb + off, rh, rk);
            textPaint.setTextSize(dp(34));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            textX = (hl + hr) / 2f;
            textY = (ht + hb) / 2f - (fm.ascent + fm.descent) / 2f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            fill.setColor(colors.calloutShadow);
            canvas.drawPath(shadowPath, fill);
            fill.setColor(colors.calloutFill);
            canvas.drawPath(path, fill);
            textPaint.setColor(colors.calloutText);
            canvas.drawText(label, textX, textY, textPaint);
        }
    }

    private final Runnable calibrateRunnable = new Runnable() {
        @Override
        public void run() {
            calibrating = false;
            try {
                if (popup == null || !popup.isShowing() || calloutView == null) {
                    return;
                }
                int[] l = new int[2];
                calloutView.getLocationOnScreen(l);
                int dx = Math.round(expectScreenX) - l[0];
                int dy = Math.round(expectScreenY) - l[1];
                if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                    corrX += dx;
                    corrY += dy;
                    curPx += dx;
                    curPy += dy;
                    popup.update(curPx, curPy, -1, -1);
                }
                calibrated = true;
                calloutView.setAlpha(1f);
            } catch (Throwable ignored) {
                calibrated = true;
                if (calloutView != null) calloutView.setAlpha(1f);
            }
        }
    };

    private void showCallout(KeyView kv, String label) {
        try {
            if (keysContainer == null || kv.getWidth() == 0) {
                return;
            }
            int[] loc = new int[2];
            kv.getLocationOnScreen(loc);
            float kl = loc[0] + insetH;
            float kr = loc[0] + kv.getWidth() - insetH;
            float kt = loc[1] + insetV;
            float kb = loc[1] + kv.getHeight() - insetV;
            float kw = kr - kl;
            float headW = Math.min(Math.max(kw * 1.9f, kw + dp(18)), dp(68));
            float cx = (kl + kr) / 2f;
            float screenW = getResources().getDisplayMetrics().widthPixels;
            float pad = dp(4);
            float headL = Math.max(pad, Math.min(cx - headW / 2f, screenW - pad - headW));
            float headR = headL + headW;
            float headB = kt - dp(4);
            float headT = headB - dp(52);

            float side = dp(2);
            float ox = Math.min(headL, kl) - side;
            float oy = headT;
            float ex = Math.max(headR, kr) + side;
            int pw = Math.round(ex - ox);
            int ph = Math.round(kb - oy) + dp(3);

            if (calloutView == null) {
                calloutView = new CalloutView(this);
            }
            calloutView.set(label, kl - ox, kt - oy, kr - ox, kb - oy,
                    headL - ox, headR - ox, 0f, headB - oy);

            int[] wl = new int[2];
            Window w = getWindow().getWindow();
            w.getDecorView().getLocationOnScreen(wl);
            curPx = Math.round(ox) - wl[0] + corrX;
            curPy = Math.round(oy) - wl[1] + corrY;
            expectScreenX = ox;
            expectScreenY = oy;

            if (popup == null) {
                popup = new PopupWindow(calloutView, pw, ph);
                popup.setTouchable(false);
                popup.setFocusable(false);
                popup.setClippingEnabled(false);
                popup.setAnimationStyle(0);
                popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            }
            if (popup.isShowing()) {
                popup.update(curPx, curPy, pw, ph);
            } else {
                popup.setWidth(pw);
                popup.setHeight(ph);
                if (!calibrated) {
                    calloutView.setAlpha(0f);
                }
                popup.showAtLocation(keysContainer, Gravity.NO_GRAVITY, curPx, curPy);
            }
            calloutOwner = kv;
            if (!calibrated && !calibrating) {
                calibrating = true;
                handler.postDelayed(calibrateRunnable, 40);
            }
        } catch (Throwable ignored) {
            // A missing pop-up must never stop typing.
        }
    }

    private void dismissCallout(KeyView owner) {
        if (calloutOwner == owner) {
            dismissCalloutNow();
        }
    }

    private void dismissCalloutNow() {
        calloutOwner = null;
        try {
            if (popup != null && popup.isShowing()) {
                popup.dismiss();
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    // ------------------------------------------------------------------ key view
    private final class KeyView extends TextView {
        final Key key;
        private final boolean plain;
        private boolean cancelled = false;
        private boolean longPressFired = false;
        private boolean swiped = false;
        private float lastStepX = 0f;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (key.alt != null) {
                    longPressFired = true;
                    showCallout(KeyView.this, key.alt);
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

        KeyView(Key k, boolean plainKey) {
            super(KeyboardService.this);
            key = k;
            plain = plainKey;
            setIncludeFontPadding(false);
            setSoundEffectsEnabled(false);
            setContentDescription(describe(k));

            boolean blue = k.type == T_ENTER && primaryAction();
            setTextColor(blue ? Color.WHITE : colors.text);
            setText(k.label);

            if (k.type == T_SPACE) {
                setGravity(Gravity.BOTTOM | Gravity.END);
                setPadding(0, 0, dp(10), Math.round(insetV + dp(3)));
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
            } else {
                setGravity(Gravity.CENTER);
                float size;
                if (k.type == T_CHAR) {
                    size = k.label.length() == 1 ? 22 : 16;
                } else {
                    size = 17;
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            }

            if (plain) {
                setBackground(null);
            } else {
                int fill = colors.key;
                int pressed = colors.keyPressed;
                if (blue) {
                    fill = colors.accent;
                    pressed = darken(colors.accent);
                }
                setBackground(keyBackground(fill, pressed, !blue && colors.keyShadow != 0));
            }
        }

        private String describe(Key k) {
            switch (k.type) {
                case T_SHIFT: return "Shift";
                case T_DELETE: return "Delete";
                case T_ENTER: return "Enter";
                case T_SPACE: return "Space";
                case T_GLOBE: return "Switch keyboard";
                case T_MIC: return "Voice typing";
                case T_TO_EMOJI: return "Emoji";
                case T_TO_SYMBOLS1: return "Numbers";
                case T_TO_SYMBOLS2: return "More symbols";
                case T_TO_LETTERS: return "Letters";
                default: return k.label;
            }
        }

        private int currentIcon() {
            if (key.type == T_SHIFT) {
                return capsLock ? Icons.CAPS : (shifted ? Icons.SHIFT_ON : Icons.SHIFT);
            }
            return key.icon;
        }

        private boolean showsCallout() {
            return key.type == T_CHAR && key.label.length() == 1;
        }

        private String calloutLabel() {
            if (key.letter) {
                return (shifted || capsLock) ? key.text.toUpperCase(Locale.ROOT) : key.text;
            }
            return key.text;
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
            dismissCallout(this);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int icon = currentIcon();
            if (icon != Icons.NONE) {
                boolean blue = key.type == T_ENTER && primaryAction();
                iconPaint.setColor(blue ? Color.WHITE : colors.text);
                float size = dp(plain ? 25 : 22);
                Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, size, iconPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(true);
                    if (plain) setAlpha(0.45f);
                    cancelled = false;
                    longPressFired = false;
                    swiped = false;
                    lastStepX = e.getX();
                    feedback(this, key.type);
                    if (showsCallout()) {
                        showCallout(this, calloutLabel());
                    }
                    if (key.type == T_DELETE) {
                        handleDelete();
                        handler.postDelayed(repeatRunnable, 380);
                    } else if (key.alt != null || key.type == T_GLOBE) {
                        handler.postDelayed(longPressRunnable, 450);
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
                        if (plain) setAlpha(1f);
                        dismissCallout(this);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    if (plain) setAlpha(1f);
                    cancelPending();
                    dismissCallout(this);
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
                    if (plain) setAlpha(1f);
                    cancelPending();
                    dismissCallout(this);
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
            case T_MIC:
                startVoiceInput();
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

    /** Hands over to the phone's own voice-typing keyboard (e.g. Google voice typing). */
    private void startVoiceInput() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (info.getPackageName().equals(getPackageName())) {
                        continue;
                    }
                    List<InputMethodSubtype> subs = imm.getEnabledInputMethodSubtypeList(info, true);
                    for (InputMethodSubtype st : subs) {
                        if ("voice".equals(st.getMode())) {
                            if (Build.VERSION.SDK_INT >= 28) {
                                switchInputMethod(info.getId(), st);
                            } else {
                                switchInputMethod(info.getId());
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through to the message below
        }
        Toast.makeText(this, "Voice typing isn't available on this phone", Toast.LENGTH_SHORT).show();
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
