package com.custom.keyboard;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen keyboard, styled after the iOS keyboard: frosted translucent panel,
 * uniform glass keys, thin-line icons, pop-up key callouts and a QuickType-style bar.
 * Built entirely in code (no deprecated KeyboardView).
 *
 * v2.2: all keys sit on one exact grid (identical gap in both directions, rows laid out by
 * KeyRow), letters switch between lower/upper case as you type, emoji key moved to the old globe
 * spot, iOS-style key sounds, bundled fonts (SF-style UI font + Google Noto emoji).
 *
 * v2.4: bottom row under the keys is [emoji][clipboard] on the left and mic on the right. Clipboard is a list panel that replaces the key area; history is kept in memory
 * (max 20, newest first, no duplicates, nothing from password fields).
 *
 * v3.7: the key pop-up label is centred like the key labels (x-height / cap height), so a pressed letter and a
 * held number sit at the same height. The clipboard and emoji panels now run from the very top of the keyboard
 * to the very bottom: their progressive blur lives under the header / footer bars instead of inside the list
 * area. Emoji panel: bigger ABC / backspace tiles and the same edge blur.
 */
public class KeyboardService extends InputMethodService {

    // ------------------------------------------------------------------ layers
    private static final int LAYER_LETTERS = 0;
    private static final int LAYER_SYMBOLS1 = 1;
    private static final int LAYER_SYMBOLS2 = 2;
    private static final int LAYER_EMOJI = 3;
    private static final int LAYER_CLIP = 4;
    private static final int LAYER_EMOJI_SEARCH = 5;   // letters keyboard, typing goes to the emoji search

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
    private static final int T_TO_CLIP = 12;
    private static final int T_CLIP_CLOSE = 13;
    private static final int T_CLIP_CLEAR = 14;

    /** Icons drawn by this class itself (the shared Icons set has no back arrow). */
    private static final int GLYPH_BASE = 10000;
    private static final int G_BACK = GLYPH_BASE + 4;
    private static final int G_SEARCH = GLYPH_BASE + 5;
    private static final int G_EMOJI = GLYPH_BASE + 6;   // solid smiley, like the iOS emoji key
    private static final int G_MIC = GLYPH_BASE + 7;
    private static final int G_CLIP = GLYPH_BASE + 8;   // solid clipboard
    /** Emoji, clipboard and mic all measure this many dp from top to bottom (the same visual size). */
    private static final float TRAY_ICON_DP = 26f;
    /** Emoji / clipboard / mic icon colour in light mode: a soft off-black (dark mode: pure white). */
    private static final int TRAY_ICON_LIGHT = 0xFF48484C;
    /** Key press feel: shrink to this scale, then bounce back (with a small overshoot) while a soft glow fades. */
    private static final float PRESS_SCALE = 0.92f;
    private static final int G_SHIFT = GLYPH_BASE + 20;      // outline arrow
    private static final int G_SHIFT_ON = GLYPH_BASE + 21;   // solid arrow
    private static final int G_CAPS = GLYPH_BASE + 22;       // solid arrow + bar
    private static final int G_DELETE = GLYPH_BASE + 23;
    private static final int G_RETURN = GLYPH_BASE + 24;
    private static final int G_CAT_RECENT = GLYPH_BASE + 10;
    private static final int G_CAT_SMILEY = GLYPH_BASE + 11;
    private static final int G_CAT_ANIMAL = GLYPH_BASE + 12;
    private static final int G_CAT_FOOD = GLYPH_BASE + 13;
    private static final int G_CAT_BALL = GLYPH_BASE + 14;
    private static final int G_CAT_CAR = GLYPH_BASE + 15;
    private static final int G_CAT_BULB = GLYPH_BASE + 16;
    private static final int G_CAT_SYMBOLS = GLYPH_BASE + 17;
    private static final int G_CAT_FLAG = GLYPH_BASE + 18;

    private static final float EMOJI_KEY_H_DP = 50f;   // ABC / backspace in the emoji panel (same tile height as a letter key)
    private static final float EMOJI_BAR_PAD_DP = 12f; // their distance from the screen edges
    private static final float EMOJI_SIDE_KEY_W = 2.2f; // ABC / backspace width, in tenths of the bar (was 1.5)
    /** The header / footer bars of the emoji and clipboard panels float above the scrolling list. */
    private static final float PANEL_BAR_Z_DP = 2f;
    private static final float EDGE_ZONE_SCALE = 1.75f; // edge blur is this many times deeper than the header / footer bar, so it fades out gently

    private static final int CLIP_MAX = 20;
    private static final String K_CLIP_REMEMBER = "clip_remember";   // optional "remember across restarts"
    private static final String K_CLIP_SAVED = "clip_history_v2";

    private static final String WORD_BREAKS = ".,!?;:)";

    private static final int EMOJI_COLS = 8;

    /** Top corner radius of the keyboard panel. */
    private static final float PANEL_RADIUS_DP = 30f;
    /** iOS key spacing: 6 dp between neighbouring keys, 10 dp between rows. */
    private static final float KEY_GAP_H_DP = 6f;
    private static final float KEY_GAP_V_DP = 10f;
    /** Corner size of a key tile; the corner itself is a G2 superellipse (see g2Rect). */
    private static final float KEY_RADIUS_DP = 16f;
    /** Exponent of the superellipse corner (G2 Squircle kit default). */
    private static final float G2_POWER = 4f;

    private static final class Colors {
        boolean dark;
        int key, keyPressed, keyShadow, keyStroke, text, divider, accent, calloutFill, calloutText, calloutShadow;
    }

    private static final class Key {
        final int type;
        final String label;
        final String text;
        final float weight;
        final String alt;
        final boolean letter;
        final int icon;
        /** Moves the icon sideways inside its cell, as a fraction of the cell width. */
        float iconShift = 0f;
        /** Extra space (dp) between this key's face and its cell edge, on the left / right. */
        float padL = 0f;
        float padR = 0f;

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
    private LinearLayout sugWords;     // the three word suggestions (letters / symbols layers)
    private FrameLayout sugAlt;        // takes over the suggestion bar: emoji categories, clipboard header
    private int sugHeightPx = 110;
    private final ArrayList<KeyView> letterViews = new ArrayList<>();
    private final ArrayList<Object> emojiItems = new ArrayList<>();
    private final int[] emojiCatPos = new int[EmojiData.COUNT];
    private final View[] emojiTabs = new View[EmojiData.COUNT];
    private final StringBuilder emojiQuery = new StringBuilder();
    private TextView emojiQueryView;
    private LinearLayout emojiResultsRow;
    private HorizontalScrollView emojiResultsScroll;
    private int emojiSelectedTab = -1;
    private KeyView shiftView;
    private LinearLayout keysContainer;
    private KeyRow accessoryRow;         // emoji + clipboard (left) and mic (right), under the keys
    private View sugHost;                // the suggestion bar (its header takes over in the emoji / clipboard panels)
    private KeySounds sounds;

    // clipboard (history lives in memory; saved to prefs only when "remember across restarts" is on)
    private static final class ClipItem {
        final String text;
        boolean pinned;

        ClipItem(String text, boolean pinned) {
            this.text = text;
            this.pinned = pinned;
        }
    }

    private final ArrayList<ClipItem> clipItems = new ArrayList<>();   // newest first
    private ClipboardManager clipboard;
    private ListView clipList;
    private TextView clipEmpty;
    private KeyView clipClearKey;
    private ClipAdapter clipAdapter;
    private PopupWindow clipMenu;             // long-press menu (Pin / Delete) over a clipboard tile
    private long clipMenuClosedAt = 0;
    private String lastSeenClip = null;
    private boolean inputActive = false;
    private Typeface uiFont = Typeface.DEFAULT;
    private Typeface uiMedium = Typeface.DEFAULT;
    private Colors colors = new Colors();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyphPath = new Path();
    private float density = 2f;
    private int keyHeightPx = 150;
    private int accessoryHeightPx = 110;
    private int insetH = 8;
    private int insetV = 14;
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
                    } else if (Prefs.K_CLIP_CLEAR_TOKEN.equals(key)) {
                        clipItems.clear();
                        refreshClipList();
                    } else if (K_CLIP_REMEMBER.equals(key)) {
                        clipSave(); // switched on: write what is in memory; switched off: wipe the saved copy
                    } else if (Prefs.K_THEME.equals(key) || Prefs.K_ACCENT.equals(key)
                            || Prefs.K_HEIGHT.equals(key) || Prefs.K_BLUR.equals(key)
                            || Prefs.K_ROUNDED.equals(key)) {
                        rebuildPending = true;
                    }
                }
            };

    // --------------------------------------------------------------- lifecycle
    @Override
    public void onCreate() {
        setTheme(R.style.KeyboardTheme); // fully transparent keyboard window (must come before super)
        super.onCreate();
        uiFont = Fonts.ui(this);
        uiMedium = Fonts.medium(this);
        EmojiData.prepareAsync(this);
        prefs = Prefs.get(this);
        density = getResources().getDisplayMetrics().density;
        suggester = new Suggester(this);
        sounds = new KeySounds(this);
        clipLoad();
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.addPrimaryClipChangedListener(clipListener);
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onDestroy() {
        dismissCalloutNow();
        dismissClipMenu();
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        handler.removeCallbacksAndMessages(null);
        if (clipboard != null) {
            clipboard.removePrimaryClipChangedListener(clipListener);
        }
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        return buildKeyboard();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && layer == LAYER_CLIP) {
            leaveClipboard();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && layer == LAYER_EMOJI_SEARCH) {
            exitEmojiSearch();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
        dismissClipMenu();
        inputActive = false;
        if (layer == LAYER_CLIP || layer == LAYER_EMOJI_SEARCH) {
            emojiQuery.setLength(0);
            layer = homeLayer();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        inputActive = false;
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        classifyField(info);
        inputActive = true;
        if (layer == LAYER_CLIP || layer == LAYER_EMOJI_SEARCH) {
            emojiQuery.setLength(0);
            layer = homeLayer();
        }
        captureClip(true);
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
            c.keyStroke = 0x26FFFFFF;    // faint light rim, like the edge of a glass tile
            c.text = 0xFFFFFFFF;
            c.divider = 0x33FFFFFF;
            c.calloutFill = 0xFF6A6C72;
            c.calloutText = 0xFFFFFFFF;
            c.calloutShadow = 0x55000000;
        } else {
            c.key = 0xB3FFFFFF;          // glass keys: white at ~70 %, the blurred panel shows through
            c.keyPressed = 0xD6BEC3CC;
            c.keyShadow = 0;             // (the old opaque edge layer would show through the glass)
            c.keyStroke = 0x1A000000;    // hairline rim
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
        float r = Prefs.on(prefs, Prefs.K_ROUNDED) ? dp(PANEL_RADIUS_DP) : 0f;
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
        // Every key is separated from its neighbours by 2 * inset: 6 dp sideways, 10 dp between rows.
        insetH = Math.max(1, Math.round(dp(KEY_GAP_H_DP) / 2f));
        insetV = Math.max(1, Math.round(dp(KEY_GAP_V_DP) / 2f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // The emoji and clipboard lists run under the header and footer bars, i.e. beyond the key area.
        root.setClipChildren(false);
        root.setPadding(dp(3), dp(2), dp(3), 0);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!applyWindowStyle()) {
            // Could not style the window: fall back to painting the panel ourselves.
            root.setBackground(panelDrawable(panelColor(false, false)));
        }

        sugHeightPx = dp(landscape ? 36 : 44);
        sugHost = buildSuggestionBar();
        root.addView(sugHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx));

        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setClipChildren(false);
        keysContainer.setMotionEventSplittingEnabled(true);
        root.addView(keysContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        accessoryRow = new KeyRow(this, accessoryHeightPx);
        root.addView(accessoryRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, accessoryHeightPx));

        renderKeys();
        return root;
    }

    private View buildSuggestionBar() {
        FrameLayout host = new FrameLayout(this);
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
            tv.setTypeface(uiFont);
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
        sugWords = bar;
        host.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sugAlt = new FrameLayout(this);
        sugAlt.setVisibility(View.GONE);
        host.addView(sugAlt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return host;
    }

    /** The emoji panel and the clipboard panel use the suggestion bar for their own controls. */
    private void updateSuggestionBarMode() {
        if (sugAlt == null || sugWords == null) {
            return;
        }
        sugAlt.removeAllViews();
        boolean alt = layer == LAYER_EMOJI || layer == LAYER_CLIP || layer == LAYER_EMOJI_SEARCH;
        // In the emoji and clipboard panels the list scrolls under this bar, so the bar must draw on top.
        if (sugHost != null) {
            sugHost.setTranslationZ(layer == LAYER_EMOJI || layer == LAYER_CLIP ? dp(PANEL_BAR_Z_DP) : 0f);
        }
        sugAlt.setVisibility(alt ? View.VISIBLE : View.GONE);
        sugWords.setVisibility(alt ? View.INVISIBLE : View.VISIBLE);
        if (layer == LAYER_EMOJI_SEARCH) {
            buildEmojiSearchBar();
        }
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
            out[i] = new Key(T_CHAR, s, s, weight, a, letters, Icons.NONE);
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
        // No emoji key here: 123 | space | return. Emoji lives in the tool tab under the keys.
        if (isEmailField) {
            k.add(fn(toggleType, toggleLabel, 1.6f));
            k.add(space(4.2f, ""));
            k.add(ch("@", 1.0f));
            k.add(ch(".", 1.0f));
            k.add(returnKey(2.2f));
        } else if (isUriField) {
            k.add(fn(toggleType, toggleLabel, 1.5f));
            k.add(space(3.9f, ""));
            k.add(ch(".", 1.0f));
            k.add(ch("/", 1.0f));
            k.add(new Key(T_CHAR, ".com", ".com", 1.3f, null, false, Icons.NONE));
            k.add(returnKey(1.3f));
        } else {
            k.add(fn(toggleType, toggleLabel, 2.4f));
            k.add(space(5.2f, "EN"));
            k.add(returnKey(2.4f));
        }
        return k.toArray(new Key[0]);
    }

    /** iOS keeps shift / delete a little further from the letters: 13 dp instead of 6 dp. */
    private static final float SIDE_KEY_GAP_EXTRA_DP = 7.2f;

    private static Key facingRight(Key k) {   // key sits at the left end of its row
        k.padR = SIDE_KEY_GAP_EXTRA_DP;
        return k;
    }

    private static Key facingLeft(Key k) {    // key sits at the right end of its row
        k.padL = SIDE_KEY_GAP_EXTRA_DP;
        return k;
    }

    private Key[][] lettersLayout() {
        return new Key[][]{
                chars("qwertyuiop", "1234567890", true, 1f),
                row(fn(T_SPACER, "", 0.5f), chars("asdfghjkl", null, true, 1f), fn(T_SPACER, "", 0.5f)),
                row(facingRight(ic(T_SHIFT, Icons.SHIFT, 1.5f)), chars("zxcvbnm", null, true, 1f),
                        facingLeft(ic(T_DELETE, Icons.DELETE, 1.5f))),
                bottomRow(true)
        };
    }

    private Key[][] symbols1Layout() {
        return new Key[][]{
                chars("1234567890", null, false, 1f),
                chars("-/:;()$&@\"", null, false, 1f),
                row(facingRight(fn(T_TO_SYMBOLS2, "#+=", 1.5f)), chars(".,?!'", null, false, 1.4f),
                        facingLeft(ic(T_DELETE, Icons.DELETE, 1.5f))),
                bottomRow(false)
        };
    }

    private Key[][] symbols2Layout() {
        return new Key[][]{
                chars("[]{}#%^*+=", null, false, 1f),
                chars("_\\|~<>\u20AC\u00A3\u00A5\u2022", null, false, 1f),
                row(facingRight(fn(T_TO_SYMBOLS1, "123", 1.5f)), chars(".,?!'", null, false, 1.4f),
                        facingLeft(ic(T_DELETE, Icons.DELETE, 1.5f))),
                bottomRow(false)
        };
    }

    // ---------------------------------------------------------------- rendering
    private void renderKeys() {
        if (keysContainer == null) {
            return;
        }
        dismissCalloutNow();
        dismissClipMenu();
        keysContainer.removeAllViews();
        letterViews.clear();
        shiftView = null;
        updateSuggestionBarMode();
        if (layer == LAYER_EMOJI) {
            renderEmoji();
        } else if (layer == LAYER_CLIP) {
            renderClipboard();
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
        // In the clipboard panel the list scrolls under this row (the footer), so it must draw on top.
        accessoryRow.setTranslationZ(layer == LAYER_CLIP ? dp(PANEL_BAR_Z_DP) : 0f);
        if (layer == LAYER_EMOJI) {
            accessoryRow.setVisibility(View.GONE);
            return;
        }
        accessoryRow.setVisibility(View.VISIBLE);
        // Under the keys: [emoji][clipboard] bottom left, mic bottom right. The emoji cell is exactly as
        // wide as the mic cell (the Return key's width), so the emoji icon sits the same distance from
        // the left screen edge as the mic icon does from the right edge. Clipboard follows it.
        // Row units are 1/10 of the width. The mic icon is centred 1.2 units from the right edge, so the
        // emoji icon is centred 1.2 units from the left edge (cell 1.75 wide, icon nudged right to get there),
        // and the clipboard icon sits one icon-width plus a small gap after it (cell 1.1 wide, 44 dp apart).
        Key emojiKey = ic(T_TO_EMOJI, G_EMOJI, 1.75f);
        emojiKey.iconShift = (1.2f - 1.75f / 2f) / 1.75f;
        accessoryRow.add(new KeyView(emojiKey, true), 1.75f);
        accessoryRow.add(new KeyView(ic(T_TO_CLIP, G_CLIP, 1.1f), true), 1.1f);
        accessoryRow.add(new View(this), 4.75f);
        accessoryRow.add(new KeyView(ic(T_MIC, G_MIC, 2.4f), true), 2.4f);
    }

    private int homeLayer() {
        return numberMode ? LAYER_SYMBOLS1 : LAYER_LETTERS;
    }

    /** Draws the line icons this class owns, on a 24 x 24 grid scaled to `size`. */
    private void drawGlyph(Canvas c, int g, float cx, float cy, float size, int color) {
        Paint p = glyphPaint;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        float sc = size / 24f;
        float w = dp(g == G_BACK ? 2.2f : 1.7f) / sc;
        p.setStrokeWidth(w);
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(sc, sc);
        Path path = glyphPath;
        path.reset();
        switch (g) {
            case G_BACK:
                path.moveTo(15.6f, 4.6f);
                path.lineTo(8.4f, 12f);
                path.lineTo(15.6f, 19.4f);
                c.drawPath(path, p);
                break;
            case G_EMOJI: {
                // solid face; eyes, the open mouth are cut out and the teeth are filled back in
                p.setStyle(Paint.Style.FILL);
                path.setFillType(Path.FillType.EVEN_ODD);
                path.addCircle(12f, 12f, 11f, Path.Direction.CW);
                path.addCircle(8.6f, 9.5f, 1.35f, Path.Direction.CW);
                path.addCircle(15.4f, 9.5f, 1.35f, Path.Direction.CW);
                path.moveTo(4.8f, 12.9f);
                path.lineTo(19.2f, 12.9f);
                path.cubicTo(18.6f, 18.2f, 15.6f, 19.9f, 12f, 19.9f);
                path.cubicTo(8.4f, 19.9f, 5.4f, 18.2f, 4.8f, 12.9f);
                path.close();
                path.addRoundRect(new RectF(6.2f, 13.7f, 17.8f, 15.9f), 1f, 1f, Path.Direction.CW);
                c.drawPath(path, p);
                path.setFillType(Path.FillType.WINDING);
                break;
            }
            case G_MIC: {
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(8.8f, 1f, 15.2f, 15.3f), 3.2f, 3.2f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f) / sc);
                path.moveTo(5f, 9.8f);
                path.lineTo(5f, 11.5f);
                path.arcTo(new RectF(5f, 4.5f, 19f, 18.5f), 180f, -180f, false);
                path.lineTo(19f, 9.8f);
                c.drawPath(path, p);
                c.drawLine(12f, 18.5f, 12f, 22.1f, p);
                c.drawLine(6.9f, 22.1f, 17.1f, 22.1f, p);
                break;
            }
            case G_SHIFT:
            case G_SHIFT_ON: {
                // broad short head, stubby stem (about 45 % of the head), rounded corners
                p.setStrokeWidth(dp(1.6f) / sc);
                path.moveTo(12f, 3.6f);
                path.lineTo(2f, 13f);
                path.lineTo(7.3f, 13f);
                path.lineTo(7.3f, 20.4f);
                path.lineTo(16.7f, 20.4f);
                path.lineTo(16.7f, 13f);
                path.lineTo(22f, 13f);
                path.close();
                if (g == G_SHIFT_ON) {
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    p.setStrokeWidth(dp(1.8f) / sc);
                }
                c.drawPath(path, p);
                break;
            }
            case G_CAPS: {
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                p.setStrokeWidth(dp(1.8f) / sc);
                path.moveTo(12f, 2.8f);
                path.lineTo(2f, 11.8f);
                path.lineTo(7.3f, 11.8f);
                path.lineTo(7.3f, 16.2f);
                path.lineTo(16.7f, 16.2f);
                path.lineTo(16.7f, 11.8f);
                path.lineTo(22f, 11.8f);
                path.close();
                c.drawPath(path, p);
                c.drawRoundRect(new RectF(7.3f, 18.9f, 16.7f, 21.3f), 1.2f, 1.2f, p);
                break;
            }
            case G_DELETE: {
                // tag with a pointed, rounded left end and a bold x
                p.setStrokeWidth(dp(1.5f) / sc);
                path.moveTo(7.6f, 3.5f);
                path.lineTo(20.3f, 3.5f);
                path.quadTo(22.7f, 3.5f, 22.7f, 5.9f);
                path.lineTo(22.7f, 18.1f);
                path.quadTo(22.7f, 20.5f, 20.3f, 20.5f);
                path.lineTo(7.6f, 20.5f);
                path.lineTo(1.7f, 12.9f);
                path.quadTo(1.1f, 12f, 1.7f, 11.1f);
                path.close();
                c.drawPath(path, p);
                p.setStrokeWidth(dp(1.7f) / sc);
                c.drawLine(11.9f, 8.9f, 18.1f, 15.1f, p);
                c.drawLine(18.1f, 8.9f, 11.9f, 15.1f, p);
                break;
            }
            case G_RETURN: {
                // arrow pointing left; the line turns up at the right and ends in a short top cap
                p.setStrokeWidth(dp(1.5f) / sc);
                path.moveTo(12.7f, 3.8f);
                path.lineTo(19.5f, 3.8f);
                path.quadTo(21.9f, 3.8f, 21.9f, 6.2f);
                path.lineTo(21.9f, 12.2f);
                path.quadTo(21.9f, 14.6f, 19.5f, 14.6f);
                path.lineTo(2.1f, 14.6f);
                path.moveTo(7.5f, 9f);
                path.lineTo(2.1f, 14.6f);
                path.lineTo(7.5f, 20.2f);
                c.drawPath(path, p);
                break;
            }
            case G_CLIP: {
                // solid board with a gap around the clip and three cut-out lines (y 1.4 .. 22.6 of the grid)
                p.setStyle(Paint.Style.FILL);
                path.setFillType(Path.FillType.EVEN_ODD);
                path.addRoundRect(new RectF(4f, 3.2f, 20f, 22.6f), 3.2f, 3.2f, Path.Direction.CW);
                path.addRoundRect(new RectF(7.2f, 1.9f, 16.8f, 7.6f), 2.2f, 2.2f, Path.Direction.CW);
                path.addRoundRect(new RectF(7.8f, 10.6f, 16.2f, 12.2f), 0.8f, 0.8f, Path.Direction.CW);
                path.addRoundRect(new RectF(7.8f, 14.2f, 16.2f, 15.8f), 0.8f, 0.8f, Path.Direction.CW);
                path.addRoundRect(new RectF(7.8f, 17.8f, 16.2f, 19.4f), 0.8f, 0.8f, Path.Direction.CW);
                c.drawPath(path, p);
                path.setFillType(Path.FillType.WINDING);
                c.drawRoundRect(new RectF(8.4f, 1.4f, 15.6f, 6.4f), 1.8f, 1.8f, p);
                break;
            }
            case G_SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, p);
                c.drawLine(15.3f, 15.3f, 20f, 20f, p);
                break;
            case G_CAT_RECENT:
                c.drawCircle(12f, 12f, 9f, p);
                path.moveTo(12f, 7f);
                path.lineTo(12f, 12f);
                path.lineTo(15.5f, 14f);
                c.drawPath(path, p);
                break;
            case G_CAT_SMILEY:
                c.drawCircle(12f, 12f, 9f, p);
                c.drawArc(new RectF(7.6f, 7.4f, 16.4f, 16.6f), 25f, 130f, false, p);
                p.setStrokeWidth(w * 1.5f);
                c.drawPoint(9f, 9.6f, p);
                c.drawPoint(15f, 9.6f, p);
                break;
            case G_CAT_ANIMAL: // paw print
                c.drawOval(new RectF(7.5f, 12f, 16.5f, 20f), p);
                c.drawCircle(5.8f, 10.2f, 1.7f, p);
                c.drawCircle(9.6f, 6.2f, 1.7f, p);
                c.drawCircle(14.4f, 6.2f, 1.7f, p);
                c.drawCircle(18.2f, 10.2f, 1.7f, p);
                break;
            case G_CAT_FOOD: // cup
                path.moveTo(5f, 8f);
                path.lineTo(17f, 8f);
                path.lineTo(17f, 14f);
                path.quadTo(17f, 20f, 11f, 20f);
                path.quadTo(5f, 20f, 5f, 14f);
                path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(17f, 10f);
                path.lineTo(18.4f, 10f);
                path.quadTo(21f, 10f, 21f, 12.6f);
                path.quadTo(21f, 15.2f, 18.4f, 15.2f);
                path.lineTo(16.8f, 15.2f);
                c.drawPath(path, p);
                c.drawLine(9f, 3.6f, 9f, 5.4f, p);
                c.drawLine(13f, 3.6f, 13f, 5.4f, p);
                break;
            case G_CAT_BALL:
                c.drawCircle(12f, 12f, 9f, p);
                c.drawLine(12f, 3f, 12f, 21f, p);
                c.drawLine(3f, 12f, 21f, 12f, p);
                path.moveTo(5.6f, 5.6f);
                path.quadTo(9.6f, 12f, 5.6f, 18.4f);
                path.moveTo(18.4f, 5.6f);
                path.quadTo(14.4f, 12f, 18.4f, 18.4f);
                c.drawPath(path, p);
                break;
            case G_CAT_CAR:
                path.moveTo(3f, 14.5f);
                path.lineTo(5.4f, 8.6f);
                path.lineTo(18.6f, 8.6f);
                path.lineTo(21f, 14.5f);
                path.lineTo(21f, 18.2f);
                path.lineTo(3f, 18.2f);
                path.close();
                c.drawPath(path, p);
                c.drawLine(3f, 14.5f, 21f, 14.5f, p);
                c.drawCircle(7.4f, 18.2f, 1.7f, p);
                c.drawCircle(16.6f, 18.2f, 1.7f, p);
                break;
            case G_CAT_BULB:
                c.drawArc(new RectF(6f, 3f, 18f, 15f), 120f, 300f, false, p);
                path.moveTo(9f, 14.2f);
                path.lineTo(9f, 17f);
                path.lineTo(15f, 17f);
                path.lineTo(15f, 14.2f);
                c.drawPath(path, p);
                c.drawLine(10f, 20f, 14f, 20f, p);
                break;
            case G_CAT_SYMBOLS: // hash
                c.drawLine(9.4f, 4f, 7.6f, 20f, p);
                c.drawLine(16.4f, 4f, 14.6f, 20f, p);
                c.drawLine(4.6f, 9f, 19.6f, 9f, p);
                c.drawLine(4.4f, 15f, 19.4f, 15f, p);
                break;
            case G_CAT_FLAG:
                c.drawLine(6f, 3f, 6f, 21f, p);
                path.moveTo(6f, 4.5f);
                path.lineTo(18.5f, 4.5f);
                path.lineTo(15.5f, 9f);
                path.lineTo(18.5f, 13.5f);
                path.lineTo(6f, 13.5f);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    /**
     * One row of keys. Children are placed by exact pixel edges computed from their weights, so every
     * row spans exactly the same width, keys of equal weight are equal width (+/- 1 px) and the gap
     * between two neighbouring keys is always the same.
     */
    private final class KeyRow extends ViewGroup {
        private final ArrayList<Float> weights = new ArrayList<>();
        private final int rowHeight;

        KeyRow(Context c, int rowHeight) {
            super(c);
            this.rowHeight = rowHeight;
            setMotionEventSplittingEnabled(true);
            setClipChildren(false);
        }

        void add(View v, float weight) {
            weights.add(weight);
            addView(v);
        }

        @Override
        public void removeAllViews() {
            super.removeAllViews();
            weights.clear();
        }

        private float total() {
            float t = 0f;
            for (int i = 0; i < weights.size(); i++) {
                t += weights.get(i);
            }
            return t <= 0f ? 1f : t;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            if (width <= 0) {
                width = getResources().getDisplayMetrics().widthPixels;
            }
            float total = total();
            float acc = 0f;
            int left = 0;
            int n = Math.min(getChildCount(), weights.size());
            for (int i = 0; i < n; i++) {
                acc += weights.get(i);
                int right = Math.round(width * acc / total);
                getChildAt(i).measure(
                        MeasureSpec.makeMeasureSpec(Math.max(0, right - left), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
                left = right;
            }
            setMeasuredDimension(width, rowHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            float total = total();
            float acc = 0f;
            int left = 0;
            int n = Math.min(getChildCount(), weights.size());
            for (int i = 0; i < n; i++) {
                acc += weights.get(i);
                int right = Math.round(width * acc / total);
                getChildAt(i).layout(left, 0, right, rowHeight);
                left = right;
            }
        }
    }

    private void addKeyRow(Key[] keys) {
        KeyRow rowView = new KeyRow(this, keyHeightPx);
        for (Key k : keys) {
            if (k.type == T_SPACER) {
                rowView.add(new View(this), k.weight);
                continue;
            }
            KeyView kv = new KeyView(k, false);
            rowView.add(kv, k.weight);
            if (k.type == T_SHIFT) {
                shiftView = kv;
            }
            if (k.letter) {
                letterViews.add(kv);
            }
        }
        keysContainer.addView(rowView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx));
    }

    // ------------------------------------------------------------- emoji panel
    private final Runnable emojiRetry = new Runnable() {
        @Override
        public void run() {
            if (layer != LAYER_EMOJI) {
                return;
            }
            if (EmojiData.isReady()) {
                renderKeys();
            } else {
                handler.postDelayed(this, 250);
            }
        }
    };

    private List<String> loadRecentEmoji() {
        ArrayList<String> out = new ArrayList<>();
        String saved = prefs.getString(Prefs.K_EMOJI_RECENT, "");
        if (saved != null && !saved.isEmpty()) {
            for (String s : saved.split(",")) {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private void rememberEmoji(String e) {
        List<String> list = loadRecentEmoji();
        list.remove(e);
        list.add(0, e);
        while (list.size() > 32) {
            list.remove(list.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs.edit().putString(Prefs.K_EMOJI_RECENT, sb.toString()).apply();
    }

    private void buildEmojiItems() {
        emojiItems.clear();
        for (int c = 0; c < EmojiData.COUNT; c++) {
            List<String> list = (c == 0) ? loadRecentEmoji() : EmojiData.get(c);
            if (list.isEmpty()) {
                emojiCatPos[c] = -1;
                continue;
            }
            emojiCatPos[c] = emojiItems.size();
            emojiItems.add(EmojiData.NAMES[c]);
            for (int i = 0; i < list.size(); i += EMOJI_COLS) {
                String[] row = new String[EMOJI_COLS];
                for (int j = 0; j < EMOJI_COLS; j++) {
                    row[j] = (i + j < list.size()) ? list.get(i + j) : null;
                }
                emojiItems.add(row);
            }
        }
    }

    private final class EmojiAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return emojiItems.size();
        }

        @Override
        public Object getItem(int position) {
            return emojiItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return (emojiItems.get(position) instanceof String) ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = emojiItems.get(position);
            if (item instanceof String) {
                TextView h = (TextView) convertView;
                if (h == null) {
                    h = new TextView(KeyboardService.this);
                    h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    h.setTypeface(uiMedium);
                    h.setGravity(Gravity.CENTER_VERTICAL);
                    h.setPadding(dp(12), 0, dp(12), 0);
                    h.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
                }
                h.setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
                h.setText((String) item);
                return h;
            }
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = new LinearLayout(KeyboardService.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                row.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx));
                for (int j = 0; j < EMOJI_COLS; j++) {
                    TextView tv = new TextView(KeyboardService.this);
                    tv.setGravity(Gravity.CENTER);
                    tv.setIncludeFontPadding(false);
                    Typeface noto = Fonts.emoji(KeyboardService.this); // Google emoji
                    if (noto != null) {
                        tv.setTypeface(noto);
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                    tv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Object tag = v.getTag();
                            if (tag instanceof String) {
                                feedback(v, T_CHAR);
                                commitPlain((String) tag);
                                rememberEmoji((String) tag);
                            }
                        }
                    });
                    row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                }
            }
            String[] cells = (String[]) item;
            for (int j = 0; j < EMOJI_COLS; j++) {
                TextView tv = (TextView) row.getChildAt(j);
                tv.setText(cells[j] == null ? "" : cells[j]);
                tv.setTag(cells[j]);
                tv.setClickable(cells[j] != null);
            }
            return row;
        }
    }

    private void updateEmojiTab(int firstVisible) {
        int sel = 0;
        for (int c = 0; c < EmojiData.COUNT; c++) {
            if (emojiCatPos[c] >= 0 && emojiCatPos[c] <= firstVisible) {
                sel = c;
            }
        }
        if (sel == emojiSelectedTab) {
            return;
        }
        emojiSelectedTab = sel;
        for (int c = 0; c < EmojiData.COUNT; c++) {
            if (emojiTabs[c] != null) {
                emojiTabs[c].setAlpha(c == sel ? 1f : 0.4f);
            }
        }
    }

    private void renderEmoji() {
        if (!EmojiData.isReady()) {
            TextView loading = new TextView(this);
            loading.setText("Loading emoji\u2026");
            loading.setGravity(Gravity.CENTER);
            loading.setTypeface(uiFont);
            loading.setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
            keysContainer.addView(loading, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, keyHeightPx * 4 + accessoryHeightPx - dp(EMOJI_KEY_H_DP) - dp(EMOJI_BAR_PAD_DP)));
            addEmojiBar(null);
            handler.removeCallbacks(emojiRetry);
            handler.postDelayed(emojiRetry, 250);
            return;
        }
        buildEmojiItems();
        // Same layout and blur as the clipboard panel: the list scrolls vertically through every emoji, from
        // the very top (under the category bar) to the very bottom (under ABC / backspace).
        int barH = dp(EMOJI_KEY_H_DP) + dp(EMOJI_BAR_PAD_DP);
        EdgeBlurLayout holder = new EdgeBlurLayout(this, sugHeightPx, barH);
        final ListView list = new ListView(this);
        holder.list = list;
        list.setPadding(0, sugHeightPx, 0, barH);
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalFadingEdgeEnabled(false);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setAdapter(new EmojiAdapter());
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                updateEmojiTab(first);
            }
        });
        holder.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The key area keeps its height (4 rows + the hidden footer row); the holder reaches up over the
        // category bar and down over the ABC / backspace bar that is added right after it.
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx + keyHeightPx * 4 + accessoryHeightPx);
        hlp.topMargin = -sugHeightPx;
        hlp.bottomMargin = -barH;
        keysContainer.addView(holder, hlp);
        addEmojiBar(list);
        // open on the recent emoji if there are any, otherwise on smileys
        emojiSelectedTab = -1;
        updateEmojiTab(0);
    }

    /** A plain line icon (one of this class's own glyphs) that can be tapped. */
    private final class GlyphView extends View {
        final int glyph;

        GlyphView(Context c, int glyph) {
            super(c);
            this.glyph = glyph;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawGlyph(canvas, glyph, getWidth() / 2f, getHeight() / 2f, dp(22), colors.text);
        }
    }

    private int glyphForCategory(int c) {
        if (c == 0) {
            return G_CAT_RECENT;
        }
        String n = (c < EmojiData.NAMES.length && EmojiData.NAMES[c] != null)
                ? EmojiData.NAMES[c].toLowerCase(Locale.ROOT) : "";
        if (n.contains("recent") || n.contains("frequent")) return G_CAT_RECENT;
        if (n.contains("smiley") || n.contains("people") || n.contains("face") || n.contains("emotion")) return G_CAT_SMILEY;
        if (n.contains("animal") || n.contains("nature")) return G_CAT_ANIMAL;
        if (n.contains("food") || n.contains("drink")) return G_CAT_FOOD;
        if (n.contains("activit") || n.contains("sport")) return G_CAT_BALL;
        if (n.contains("travel") || n.contains("place")) return G_CAT_CAR;
        if (n.contains("object")) return G_CAT_BULB;
        if (n.contains("symbol")) return G_CAT_SYMBOLS;
        if (n.contains("flag")) return G_CAT_FLAG;
        return G_CAT_SMILEY;
    }

    private void addEmojiBar(final ListView list) {
        // Category icons live in the suggestion bar: a search icon first, then plain line icons.
        KeyRow tabsRow = new KeyRow(this, sugHeightPx);
        if (list != null) {
            GlyphView search = new GlyphView(this, G_SEARCH);
            search.setAlpha(0.75f);
            search.setContentDescription("Search emoji");
            search.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback(v, T_CHAR);
                    emojiQuery.setLength(0);
                    setLayer(LAYER_EMOJI_SEARCH);
                }
            });
            tabsRow.add(search, 1f);
        }
        for (int c = 0; c < EmojiData.COUNT; c++) {
            emojiTabs[c] = null;
            if (list == null || emojiCatPos[c] < 0) {
                continue;
            }
            final int cat = c;
            GlyphView tab = new GlyphView(this, glyphForCategory(c));
            tab.setAlpha(0.4f);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback(v, T_CHAR);
                    list.setSelection(emojiCatPos[cat]);
                    updateEmojiTab(emojiCatPos[cat]);
                }
            });
            emojiTabs[c] = tab;
            tabsRow.add(tab, 1f);
        }
        sugAlt.addView(tabsRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx));

        // Bottom row: small ABC and backspace keys, kept clear of the screen's rounded corners and bottom edge.
        int keyH = dp(EMOJI_KEY_H_DP);
        int pad = dp(EMOJI_BAR_PAD_DP);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, 0, pad, pad);
        KeyRow bar = new KeyRow(this, keyH);
        bar.add(new KeyView(fn(T_TO_LETTERS, "ABC", EMOJI_SIDE_KEY_W), false), EMOJI_SIDE_KEY_W);
        bar.add(new View(this), 10f - 2f * EMOJI_SIDE_KEY_W);
        bar.add(new KeyView(ic(T_DELETE, Icons.DELETE, EMOJI_SIDE_KEY_W), false), EMOJI_SIDE_KEY_W);
        wrap.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyH));
        keysContainer.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyH + pad));
    }

    // ----------------------------------------------------------- emoji search
    /** A small keyword list for the most used emoji (the emoji data itself carries no names). */
    private static final String[] EMOJI_KEYWORDS = {
        "\uD83D\uDE00|grin smile happy face", "\uD83D\uDE03|smile happy joy", "\uD83D\uDE04|smile happy laugh",
        "\uD83D\uDE01|grin beam happy", "\uD83D\uDE06|laugh haha squint", "\uD83D\uDE05|sweat laugh nervous relief",
        "\uD83D\uDE02|joy laugh cry lol tears funny", "\uD83E\uDD23|rofl laugh lol funny rolling",
        "\uD83D\uDE0A|blush smile happy shy", "\uD83D\uDE07|angel halo innocent", "\uD83D\uDE42|smile slight",
        "\uD83D\uDE09|wink flirt", "\uD83D\uDE0D|love heart eyes crush", "\uD83E\uDD70|love hearts adore",
        "\uD83D\uDE18|kiss blow love", "\uD83D\uDE0B|yum tasty delicious tongue", "\uD83D\uDE1C|silly wink tongue crazy",
        "\uD83E\uDD14|think thinking hmm wonder", "\uD83D\uDE0E|cool sunglasses", "\uD83E\uDD29|star wow excited",
        "\uD83D\uDE0F|smirk", "\uD83D\uDE12|unamused bored annoyed", "\uD83D\uDE44|eyeroll roll eyes",
        "\uD83D\uDE2D|cry sob sad tears", "\uD83D\uDE22|cry sad tear", "\uD83D\uDE1E|sad disappointed",
        "\uD83D\uDE21|angry mad rage", "\uD83D\uDE20|angry mad", "\uD83E\uDD2C|swear curse angry",
        "\uD83D\uDE31|scream shocked scared omg", "\uD83D\uDE28|fear scared afraid", "\uD83D\uDE34|sleep sleeping tired zzz",
        "\uD83E\uDD71|yawn tired bored", "\uD83E\uDD12|sick fever ill", "\uD83E\uDD2E|vomit sick",
        "\uD83E\uDD75|hot heat sweat", "\uD83E\uDD76|cold freezing", "\uD83E\uDD73|party celebrate birthday",
        "\uD83E\uDD7A|please begging puppy eyes", "\uD83D\uDE4F|pray thanks please folded hands",
        "\uD83D\uDC4D|thumbs up like yes ok good", "\uD83D\uDC4E|thumbs down dislike no bad", "\uD83D\uDC4C|ok perfect",
        "\u270C\uFE0F|peace victory", "\uD83E\uDD1E|luck fingers crossed hope", "\uD83D\uDC4B|wave hello hi bye",
        "\uD83D\uDC4F|clap applause bravo", "\uD83D\uDE4C|raised hands hooray praise", "\uD83D\uDCAA|strong muscle flex gym",
        "\uD83E\uDD1D|handshake deal agree", "\u270B|stop hand high five", "\uD83D\uDC40|eyes look see watch",
        "\uD83E\uDDE0|brain smart mind", "\u2764\uFE0F|heart love red", "\uD83D\uDC94|broken heart sad",
        "\uD83D\uDC99|blue heart", "\uD83D\uDC9A|green heart", "\uD83D\uDC9B|yellow heart", "\uD83D\uDC9C|purple heart",
        "\uD83D\uDDA4|black heart", "\uD83D\uDC95|hearts love two", "\u2728|sparkles shine magic new",
        "\uD83D\uDD25|fire hot lit flame", "\uD83D\uDCAF|hundred perfect score", "\uD83C\uDF89|party tada celebrate congrats",
        "\uD83C\uDF82|birthday cake", "\uD83C\uDF81|gift present", "\uD83D\uDCA9|poop", "\uD83D\uDC80|skull dead",
        "\uD83D\uDC7B|ghost halloween", "\uD83E\uDD16|robot", "\uD83D\uDC36|dog puppy pet", "\uD83D\uDC31|cat kitten pet",
        "\uD83D\uDC2D|mouse", "\uD83D\uDC3B|bear", "\uD83D\uDC3C|panda", "\uD83E\uDD81|lion", "\uD83D\uDC37|pig",
        "\uD83D\uDC38|frog", "\uD83D\uDC35|monkey", "\uD83D\uDC14|chicken", "\uD83D\uDC27|penguin", "\uD83D\uDC22|turtle",
        "\uD83D\uDC0D|snake", "\uD83D\uDC1F|fish", "\uD83D\uDC2C|dolphin", "\uD83D\uDC33|whale", "\uD83E\uDD8B|butterfly",
        "\uD83D\uDC1D|bee", "\uD83C\uDF39|rose flower", "\uD83C\uDF3B|sunflower flower", "\uD83C\uDF34|palm tree beach",
        "\uD83C\uDF31|plant seedling grow", "\uD83C\uDF0A|wave ocean sea water", "\uD83C\uDF0D|earth world globe",
        "\u2600\uFE0F|sun sunny weather", "\uD83C\uDF19|moon night", "\u2B50|star", "\u2601\uFE0F|cloud weather",
        "\uD83C\uDF27\uFE0F|rain weather", "\u26A1|lightning bolt power energy", "\u2744\uFE0F|snow cold winter",
        "\uD83C\uDF08|rainbow", "\uD83C\uDF4E|apple fruit red", "\uD83C\uDF4C|banana fruit", "\uD83C\uDF4A|orange fruit",
        "\uD83C\uDF53|strawberry fruit", "\uD83C\uDF49|watermelon fruit", "\uD83C\uDF4D|pineapple fruit", "\uD83E\uDD51|avocado",
        "\uD83C\uDF54|burger hamburger food", "\uD83C\uDF55|pizza food", "\uD83C\uDF5F|fries food", "\uD83C\uDF2E|taco food",
        "\uD83C\uDF63|sushi food", "\uD83C\uDF5C|noodles ramen food", "\uD83C\uDF5A|rice food", "\uD83C\uDF70|cake dessert sweet",
        "\uD83C\uDF69|donut dessert", "\uD83C\uDF6B|chocolate candy", "\uD83C\uDF66|ice cream dessert", "\u2615|coffee tea drink hot",
        "\uD83C\uDF7A|beer drink", "\uD83C\uDF77|wine drink", "\uD83E\uDD64|drink soda cup", "\uD83D\uDCA7|water drop",
        "\u26BD|soccer football ball sport", "\uD83C\uDFC0|basketball sport", "\uD83C\uDFBE|tennis sport", "\uD83C\uDFC6|trophy win winner",
        "\uD83C\uDFAE|game gaming controller video", "\uD83C\uDFB5|music note song", "\uD83C\uDFA7|headphones music",
        "\uD83C\uDFA4|microphone sing karaoke", "\uD83C\uDFAC|movie film clapper", "\uD83D\uDE97|car auto drive",
        "\uD83D\uDE95|taxi cab", "\uD83D\uDE8C|bus", "\uD83D\uDE86|train", "\u2708\uFE0F|plane airplane flight travel",
        "\uD83D\uDE80|rocket space launch", "\uD83D\uDEB2|bike bicycle", "\uD83C\uDFE0|house home", "\uD83C\uDFD6\uFE0F|beach vacation",
        "\uD83D\uDCA1|bulb idea light", "\uD83D\uDCF1|phone mobile cellphone", "\uD83D\uDCBB|laptop computer", "\uD83D\uDCF7|camera photo",
        "\uD83D\uDCE7|email mail message", "\uD83D\uDCDD|note memo write", "\uD83D\uDCDA|books study read school",
        "\u270F\uFE0F|pencil write", "\uD83D\uDCB0|money cash bag", "\uD83D\uDCB5|money dollar cash", "\uD83D\uDD12|lock secure",
        "\uD83D\uDD11|key", "\u23F0|alarm clock time", "\u231B|hourglass time wait", "\uD83D\uDD14|bell notification",
        "\u2705|check done yes ok", "\u274C|cross no wrong x", "\u26A0\uFE0F|warning caution", "\u2753|question",
        "\u2757|exclamation important", "\uD83D\uDEA9|flag red", "\uD83C\uDFC1|finish flag race checkered",
        "\uD83C\uDDF5\uD83C\uDDED|philippines flag", "\uD83C\uDDFA\uD83C\uDDF8|usa america flag"
    };

    /** Matches the query against the keyword list and the category names. Empty query: recents. */
    private List<String> emojiSearchResults(String query) {
        ArrayList<String> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            out.addAll(loadRecentEmoji());
            if (out.isEmpty() && EmojiData.isReady() && EmojiData.COUNT > 1) {
                List<String> first = EmojiData.get(1);
                for (int i = 0; i < first.size() && i < 24; i++) out.add(first.get(i));
            }
            return out;
        }
        String[] words = q.split("\\s+");
        // best matches first: a word that starts with the query word, then ones that merely contain it
        for (int pass = 0; pass < 2; pass++) {
            for (String entry : EMOJI_KEYWORDS) {
                int bar = entry.indexOf('|');
                String e = entry.substring(0, bar);
                String[] keys = entry.substring(bar + 1).split(" ");
                boolean all = true;
                for (String w : words) {
                    boolean hit = false;
                    for (String k : keys) {
                        if (pass == 0 ? k.startsWith(w) : k.contains(w)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        all = false;
                        break;
                    }
                }
                if (all && !out.contains(e)) {
                    out.add(e);
                }
            }
        }
        // typing a category name ("food", "animals", "flags") lists that whole category
        if (EmojiData.isReady()) {
            for (int c = 1; c < EmojiData.COUNT; c++) {
                String name = (c < EmojiData.NAMES.length && EmojiData.NAMES[c] != null)
                        ? EmojiData.NAMES[c].toLowerCase(Locale.ROOT) : "";
                if (q.length() >= 3 && name.contains(q)) {
                    List<String> l = EmojiData.get(c);
                    for (int i = 0; i < l.size() && out.size() < 60; i++) {
                        if (!out.contains(l.get(i))) out.add(l.get(i));
                    }
                }
            }
        }
        return out;
    }

    /** Suggestion bar while searching: [back] [query box] [scrolling results]. */
    private void buildEmojiSearchBar() {
        KeyRow row = new KeyRow(this, sugHeightPx);
        GlyphView back = new GlyphView(this, G_BACK);
        back.setContentDescription("Back to emoji");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedback(v, T_CHAR);
                exitEmojiSearch();
            }
        });
        row.add(back, 1.0f);

        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(2), dp(5), dp(4), dp(5));
        emojiQueryView = new TextView(this);
        emojiQueryView.setTypeface(uiFont);
        emojiQueryView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
        emojiQueryView.setGravity(Gravity.CENTER_VERTICAL);
        emojiQueryView.setSingleLine(true);
        emojiQueryView.setEllipsize(TextUtils.TruncateAt.START);
        emojiQueryView.setPadding(dp(10), 0, dp(10), 0);
        emojiQueryView.setBackground(glassRect(colors.key));
        box.addView(emojiQueryView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.add(box, 3.4f);

        emojiResultsScroll = new HorizontalScrollView(this);
        emojiResultsScroll.setHorizontalScrollBarEnabled(false);
        emojiResultsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        emojiResultsRow = new LinearLayout(this);
        emojiResultsRow.setOrientation(LinearLayout.HORIZONTAL);
        emojiResultsScroll.addView(emojiResultsRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.add(emojiResultsScroll, 5.6f);

        sugAlt.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx));
        refreshEmojiSearch();
    }

    private void refreshEmojiSearch() {
        if (layer != LAYER_EMOJI_SEARCH || emojiQueryView == null || emojiResultsRow == null) {
            return;
        }
        if (emojiQuery.length() == 0) {
            emojiQueryView.setText("Search emoji");
            emojiQueryView.setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
        } else {
            emojiQueryView.setText(emojiQuery.toString());
            emojiQueryView.setTextColor(colors.text);
        }
        emojiResultsRow.removeAllViews();
        List<String> res = emojiSearchResults(emojiQuery.toString());
        Typeface noto = Fonts.emoji(this);
        if (res.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No matches");
            none.setTypeface(uiFont);
            none.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14));
            none.setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
            none.setGravity(Gravity.CENTER_VERTICAL);
            none.setPadding(dp(10), 0, dp(10), 0);
            emojiResultsRow.addView(none, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        for (final String e : res) {
            TextView tv = new TextView(this);
            tv.setText(e);
            if (noto != null) {
                tv.setTypeface(noto);
            }
            tv.setIncludeFontPadding(false);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback(v, T_CHAR);
                    pickSearchedEmoji(e);
                }
            });
            emojiResultsRow.addView(tv, new LinearLayout.LayoutParams(
                    dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        emojiResultsScroll.scrollTo(0, 0);
    }

    private void searchAppend(String t) {
        if (t == null || t.isEmpty() || emojiQuery.length() > 40) {
            return;
        }
        emojiQuery.append(t.toLowerCase(Locale.ROOT));
        refreshEmojiSearch();
    }

    private void searchBackspace() {
        if (emojiQuery.length() > 0) {
            emojiQuery.setLength(emojiQuery.length() - 1);
            refreshEmojiSearch();
        }
    }

    private void exitEmojiSearch() {
        emojiQuery.setLength(0);
        setLayer(LAYER_EMOJI);
    }

    private void pickSearchedEmoji(String e) {
        emojiQuery.setLength(0);
        layer = homeLayer();
        commitPlain(e);
        rememberEmoji(e);
        setLayer(homeLayer());
    }

    /** Letter keys draw their label from the current shift / caps state, so just ask them to redraw. */
    private void updateShiftVisuals() {
        for (KeyView kv : letterViews) {
            kv.invalidate();
        }
        if (shiftView != null) {
            shiftView.invalidate();
        }
    }

    private static int darken(int c) {
        return Color.rgb((int) (Color.red(c) * 0.8f), (int) (Color.green(c) * 0.8f),
                (int) (Color.blue(c) * 0.8f));
    }

    // ------------------------------------------------------------------ G2 shapes
    /** Adds one superellipse corner: x = r*sgn(cos a)*|cos a|^(2/n), y likewise (G2 where it meets the edges). */
    private static void g2Corner(Path p, float cx, float cy, float r, double a0, double a1, float n) {
        final int seg = 20;
        double e = 2.0 / Math.max(2.01f, n);
        for (int i = 1; i <= seg; i++) {
            double a = a0 + (a1 - a0) * i / seg;
            double c = Math.cos(a);
            double sn = Math.sin(a);
            p.lineTo((float) (cx + r * Math.signum(c) * Math.pow(Math.abs(c), e)),
                    (float) (cy + r * Math.signum(sn) * Math.pow(Math.abs(sn), e)));
        }
    }

    /** A rectangle with G2 continuous-curvature corners. */
    private static void g2Rect(Path p, float l, float t, float r, float b, float radius, float n) {
        p.reset();
        float rad = Math.max(0f, Math.min(radius, Math.min(r - l, b - t) / 2f));
        if (rad < 0.5f) {
            p.addRect(l, t, r, b, Path.Direction.CW);
            return;
        }
        p.moveTo(l + rad, t);
        p.lineTo(r - rad, t);
        g2Corner(p, r - rad, t + rad, rad, -Math.PI / 2, 0, n);
        p.lineTo(r, b - rad);
        g2Corner(p, r - rad, b - rad, rad, 0, Math.PI / 2, n);
        p.lineTo(l + rad, b);
        g2Corner(p, l + rad, b - rad, rad, Math.PI / 2, Math.PI, n);
        p.lineTo(l, t + rad);
        g2Corner(p, l + rad, t + rad, rad, Math.PI, Math.PI * 1.5, n);
        p.close();
    }

    /**
     * The key pop-up: a wide head with G2 corners that narrows through two smooth S-curves into the
     * key itself, which keeps its own G2 bottom corners (one continuous outline, like the iOS bubble).
     */
    private static void g2Callout(Path p, float kl, float kr, float kb,
                                  float hl, float hr, float ht, float yS, float yE,
                                  float rh, float rk) {
        p.reset();
        float m = (yE - yS) * 0.55f;
        p.moveTo(hl + rh, ht);
        p.lineTo(hr - rh, ht);
        g2Corner(p, hr - rh, ht + rh, rh, -Math.PI / 2, 0, G2_POWER);
        p.lineTo(hr, yS);
        p.cubicTo(hr, yS + m, kr, yE - m, kr, yE);
        p.lineTo(kr, kb - rk);
        g2Corner(p, kr - rk, kb - rk, rk, 0, Math.PI / 2, G2_POWER);
        p.lineTo(kl + rk, kb);
        g2Corner(p, kl + rk, kb - rk, rk, Math.PI / 2, Math.PI, G2_POWER);
        p.lineTo(kl, yE);
        p.cubicTo(kl, yE - m, hl, yS + m, hl, yS);
        p.lineTo(hl, ht + rh);
        g2Corner(p, hl + rh, ht + rh, rh, Math.PI, Math.PI * 1.5, G2_POWER);
        p.close();
    }

    /** A tile filled (optionally top-to-bottom gradient) with a hairline rim, shaped with g2Rect. */
    private static final class G2Drawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int top;
        private final int bottom;
        private final float radius;
        private final float rim;
        private final boolean hasRim;

        G2Drawable(int top, int bottom, int rimColor, float rimPx, float radiusPx) {
            this.top = top;
            this.bottom = bottom;
            this.radius = radiusPx;
            this.hasRim = rimColor != 0 && rimPx > 0f;
            this.rim = rimPx;
            fillPaint.setStyle(Paint.Style.FILL);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(rimPx);
            rimPaint.setColor(rimColor);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float half = hasRim ? rim / 2f : 0f;
            g2Rect(path, b.left + half, b.top + half, b.right - half, b.bottom - half, radius, G2_POWER);
            if (top == bottom) {
                fillPaint.setShader(null);
                fillPaint.setColor(top);
            } else {
                fillPaint.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
            }
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fillPaint);
            if (hasRim) {
                c.drawPath(path, rimPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            rimPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            fillPaint.setColorFilter(cf);
            rimPaint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static int scaleAlpha(int c, float f) {
        int a = Math.min(255, Math.max(0, Math.round(Color.alpha(c) * f)));
        return (a << 24) | (c & 0x00FFFFFF);
    }

    /** Solid G2 tile (the blue action key). */
    private Drawable roundRect(int color) {
        return new G2Drawable(color, color, 0, 0f, dp(KEY_RADIUS_DP));
    }

    /** A translucent glass tile: slightly brighter at the top, hairline rim, see-through to the blurred panel. */
    private Drawable glassRect(int fill) {
        return new G2Drawable(fill, scaleAlpha(fill, 0.84f), colors.keyStroke,
                Math.max(1, dp(0.75f)), dp(KEY_RADIUS_DP));
    }

    /** `glass` = translucent tile with a hairline rim; false = solid fill (the blue action key). */
    private Drawable keyFace(int fill, boolean glass, int extraL, int extraR) {
        Drawable face = glass ? glassRect(fill) : roundRect(fill);
        LayerDrawable one = new LayerDrawable(new Drawable[]{face});
        one.setLayerInset(0, insetH + extraL, insetV, insetH + extraR, insetV);
        return one;
    }

    private Drawable keyBackground(int normal, int pressed, boolean glass, int extraL, int extraR) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, keyFace(pressed, glass, extraL, extraR));
        s.addState(new int[]{}, keyFace(normal, glass, extraL, extraR));
        return s;
    }

    // ------------------------------------------------------------- key callout
    private final class CalloutView extends View {
        private final Path path = new Path();
        private final Path shadowPath = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String label = "";
        private final Rect textBounds = new Rect();
        private float textX = 0f;
        private float textY = 0f;

        CalloutView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(uiMedium);
        }

        void set(String text, float kl, float kt, float kr, float kb,
                 float hl, float hr, float ht, float hb) {
            label = text;
            float rh = dp(16);                 // head corners
            float rk = dp(KEY_RADIUS_DP);      // the key's own bottom corners
            float yS = hb - dp(22);            // where the head's sides start to curve in
            float yE = kt + dp(12);            // where they meet the key's sides
            g2Callout(path, kl, kr, kb, hl, hr, ht, yS, yE, rh, rk);
            float off = dp(1.5f);
            g2Callout(shadowPath, kl, kr, kb + off, hl, hr, ht + off, yS + off, yE + off, rh, rk);
            textPaint.setTextSize(dp(34));
            // Same rule as the key labels (KeyView.onDraw): centre the x-height of a lowercase letter, or
            // the cap height of everything else (digits, capitals, symbols), on the middle of the head.
            // So a letter pressed and a number held both sit at the same optical height; centring the
            // font's ascent..descent box instead left letters (small x-height, descenders) low.
            boolean lower = label.length() == 1 && Character.isLowerCase(label.charAt(0));
            textPaint.getTextBounds(lower ? "x" : "H", 0, 1, textBounds);
            textX = (hl + hr) / 2f;
            textY = (ht + hb) / 2f - (textBounds.top + textBounds.bottom) / 2f;
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
            float headW = Math.min(Math.max(kw * 1.7f, kw + dp(14)), dp(60));
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

    // ---------------------------------------------------------------- clipboard
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    captureClip(false);
                }
            };

    private boolean clipRemember() {
        return prefs != null && prefs.getBoolean(K_CLIP_REMEMBER, false);
    }

    /** Adds text as the newest item. Duplicates move to the top (keeping their pin). Max 20 items. */
    private boolean clipAdd(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        boolean pinned = false;
        for (int i = 0; i < clipItems.size(); i++) {
            if (clipItems.get(i).text.equals(text)) {
                pinned = clipItems.get(i).pinned;
                clipItems.remove(i);
                break;
            }
        }
        clipItems.add(0, new ClipItem(text, pinned));
        while (clipItems.size() > CLIP_MAX) {
            int drop = -1;
            for (int i = clipItems.size() - 1; i >= 0; i--) {
                if (!clipItems.get(i).pinned) {
                    drop = i;
                    break;
                }
            }
            if (drop < 0) {
                clipItems.remove(0); // 20 pinned items already: there is no room for a new one
                return false;
            }
            clipItems.remove(drop);
        }
        clipSave();
        return true;
    }

    private void clipTogglePin(ClipItem it) {
        it.pinned = !it.pinned;
        clipSave();
    }

    private void clipRemove(ClipItem it) {
        clipItems.remove(it);
        clipSave();
    }

    private void clipClearUnpinned() {
        for (int i = clipItems.size() - 1; i >= 0; i--) {
            if (!clipItems.get(i).pinned) {
                clipItems.remove(i);
            }
        }
        clipSave();
        refreshClipList();
    }

    /** Memory only by default. With "remember across restarts" on, the list is also kept in prefs. */
    private void clipSave() {
        if (prefs == null) {
            return;
        }
        try {
            if (!clipRemember()) {
                if (prefs.contains(K_CLIP_SAVED)) {
                    prefs.edit().remove(K_CLIP_SAVED).apply();
                }
                return;
            }
            JSONArray arr = new JSONArray();
            for (ClipItem it : clipItems) {
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("p", it.pinned);
                arr.put(o);
            }
            prefs.edit().putString(K_CLIP_SAVED, arr.toString()).apply();
        } catch (Throwable ignored) {
            // never crash typing because of history storage
        }
    }

    private void clipLoad() {
        clipItems.clear();
        if (!clipRemember()) {
            return;
        }
        try {
            String saved = prefs.getString(K_CLIP_SAVED, null);
            if (saved == null) {
                return;
            }
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length() && clipItems.size() < CLIP_MAX; i++) {
                JSONObject o = arr.getJSONObject(i);
                String t = o.optString("t", "");
                if (!t.isEmpty()) {
                    clipItems.add(new ClipItem(t, o.optBoolean("p", false)));
                }
            }
        } catch (Throwable ignored) {
            clipItems.clear();
        }
    }

    /**
     * Reads the phone's current clip into the history (text only). Only runs while the keyboard is
     * showing, never saves anything copied in a password field, and never saves clips that the
     * source marked as sensitive.
     */
    private void captureClip(boolean fromStart) {
        if (clipboard == null || !inputActive || !Prefs.on(prefs, Prefs.K_CLIP)) {
            return;
        }
        try {
            ClipData cd = clipboard.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) {
                return;
            }
            CharSequence t = cd.getItemAt(0).getText();
            if (t == null) {
                return;
            }
            String s = t.toString();
            if (fromStart && s.equals(lastSeenClip)) {
                return; // same clip we already have: do not shuffle the list just because the keyboard opened
            }
            boolean sensitive = false;
            ClipDescription d = cd.getDescription();
            if (d != null && d.getExtras() != null
                    && d.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) {
                sensitive = true; // password managers mark what they copy as sensitive
            }
            lastSeenClip = s;
            if (isPasswordField || sensitive) {
                return; // remembered as "seen" so it is not picked up later in a normal field
            }
            if (clipAdd(s) && layer == LAYER_CLIP) {
                refreshClipList();
            }
        } catch (Throwable ignored) {
            // clipboard access can be refused by the system; never crash typing
        }
    }

    /** Pinned items first, then the rest newest first. */
    private List<ClipItem> clipDisplayList() {
        ArrayList<ClipItem> out = new ArrayList<>();
        for (ClipItem it : clipItems) {
            if (it.pinned) out.add(it);
        }
        for (ClipItem it : clipItems) {
            if (!it.pinned) out.add(it);
        }
        return out;
    }

    /** The clipboard panel: it takes the place of the key area, exactly like the emoji panel. */
    private void renderClipboard() {

        // Header (back, title, Clear all) lives in the suggestion bar, so the list gets the whole key area.
        KeyRow header = new KeyRow(this, sugHeightPx);
        header.add(new KeyView(ic(T_CLIP_CLOSE, G_BACK, 1.4f), true), 1.4f);
        TextView title = new TextView(this);
        title.setText("Clipboard");
        title.setTypeface(uiMedium);
        title.setTextColor(colors.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(17));
        title.setGravity(Gravity.CENTER);
        header.add(title, 5.2f);
        clipClearKey = new KeyView(fn(T_CLIP_CLEAR, "Clear all", 2.6f), true);
        header.add(clipClearKey, 2.6f);
        sugAlt.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx));

        // The list runs from the very top of the keyboard (under the header) to the very bottom (under the
        // footer row). The progressive blur lives in those two bars, so no list space is lost to it.
        EdgeBlurLayout holder = new EdgeBlurLayout(this, sugHeightPx, accessoryHeightPx);
        clipList = new ListView(this);
        holder.list = clipList;
        clipList.setPadding(0, sugHeightPx, 0, accessoryHeightPx);
        clipList.setClipToPadding(false);
        clipList.setDivider(null);
        clipList.setVerticalScrollBarEnabled(false);
        clipList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // The soft blur transition at the top and bottom edge is drawn by EdgeBlurLayout (see below).
        clipList.setVerticalFadingEdgeEnabled(false);
        clipList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState != SCROLL_STATE_IDLE) {
                    dismissClipMenu();
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });
        clipList.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        clipAdapter = new ClipAdapter();
        clipList.setAdapter(clipAdapter);
        holder.addView(clipList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clipEmpty = new TextView(this);
        clipEmpty.setText("Nothing copied yet.\nText you copy will show up here.");
        clipEmpty.setGravity(Gravity.CENTER);
        clipEmpty.setTypeface(uiFont);
        clipEmpty.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14));
        clipEmpty.setTextColor(colors.dark ? 0x99FFFFFF : 0x99000000);
        clipEmpty.setPadding(0, sugHeightPx, 0, accessoryHeightPx);   // centred between header and footer
        holder.addView(clipEmpty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The key area stays four rows tall, so the keyboard never changes size; the holder reaches up over
        // the header (negative top margin) and down over the footer row (negative bottom margin).
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sugHeightPx + keyHeightPx * 4 + accessoryHeightPx);
        hlp.topMargin = -sugHeightPx;
        hlp.bottomMargin = -accessoryHeightPx;
        keysContainer.addView(holder, hlp);
        refreshClipList();
    }

    /**
     * Holds a scrolling list (clipboard, emoji). Content that scrolls into the top or bottom edge zone does not
     * get cut off: it melts into a progressive blur (sharp at the inner side, strongest at the outer edge) and
     * fades out. The two zones are as deep as the header and footer bars that sit on top of them.
     * The effect only grows as rows really scroll past an edge, so a list at rest looks completely sharp.
     * Android 12+ uses real blur (RenderEffect); older versions use the fade alone.
     * The blur is built from many thin strips (STEPS, pixel-snapped) whose radius and opacity change by a tiny
     * amount from one strip to the next, which reads as one smooth gradient with no visible horizontal lines.
     */
    private final class EdgeBlurLayout extends FrameLayout {
        private static final int STEPS = 64;   // thin strips (about 3 px each) so the blur steps cannot be seen as lines
        ListView list;
        private final float zoneTop;           // depth of the top zone (= header height)
        private final float zoneBottom;        // depth of the bottom zone (= footer height)
        private final float maxBlur = dp(16);   // blur radius at the very edge
        private final Paint maskPaint = new Paint();
        private Object engine;   // BlurEngine, only created on Android 12+

        EdgeBlurLayout(Context c, float zoneTopPx, float zoneBottomPx) {
            super(c);
            zoneTop = Math.max(1f, zoneTopPx);
            zoneBottom = Math.max(1f, zoneBottomPx);
            maskPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        private float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        /** smoothstep: starts and ends with zero slope, so the effect eases in instead of switching on. */
        private float ease(float v) {
            return v * v * (3f - 2f * v);
        }

        private float topStrength() {
            ListView lv = list;
            if (lv == null || lv.getChildCount() == 0) return 0f;
            if (lv.getFirstVisiblePosition() > 0) return 1f;
            return ease(clamp01((lv.getPaddingTop() - lv.getChildAt(0).getTop()) / zoneTop));
        }

        private float bottomStrength() {
            ListView lv = list;
            if (lv == null || lv.getChildCount() == 0) return 0f;
            if (lv.getLastVisiblePosition() < lv.getCount() - 1) return 1f;
            View last = lv.getChildAt(lv.getChildCount() - 1);
            return ease(clamp01((last.getBottom() - (lv.getHeight() - lv.getPaddingBottom())) / zoneBottom));
        }

        void superDispatchDraw(Canvas c) {
            super.dispatchDraw(c);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float sTop = topStrength();
            float sBot = bottomStrength();
            int w = getWidth();
            int h = getHeight();
            if (sTop <= 0.001f && sBot <= 0.001f || w <= 0 || h <= 0) {
                super.dispatchDraw(canvas);
                return;
            }
            // the blur / fade zones reach past the header / footer bars into the list, so the content fades out softly
            // instead of being cut at the bar's edge (at rest the strength is 0, so nothing there is blurred)
            float bzTop = Math.min(zoneTop * EDGE_ZONE_SCALE, h * 0.45f);
            float bzBot = Math.min(zoneBottom * EDGE_ZONE_SCALE, h * 0.45f);
            if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()) {
                try {
                    if (engine == null) {
                        engine = new BlurEngine();
                    }
                    ((BlurEngine) engine).draw(this, canvas, w, h, sTop, sBot, bzTop, bzBot, maxBlur, STEPS);
                    return;
                } catch (Throwable t) {
                    engine = null; // fall through to the plain fade
                }
            }
            int layer = canvas.saveLayer(0, 0, w, h, null);
            super.dispatchDraw(canvas);
            float zfTop = bzTop / h;
            float zfBot = bzBot / h;
            final int n = 8;   // eased fade: several stops instead of one straight ramp
            int[] cols = new int[2 * (n + 1)];
            float[] pos = new float[2 * (n + 1)];
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n;                          // 0 = outer edge, 1 = inner side
                cols[i] = Color.argb((int) (255 * (1f - sTop * ease(1f - t))), 0, 0, 0);
                pos[i] = zfTop * t;
                cols[n + 1 + i] = Color.argb((int) (255 * (1f - sBot * ease(t))), 0, 0, 0);
                pos[n + 1 + i] = 1f - zfBot * (1f - t);
            }
            maskPaint.setShader(new LinearGradient(0, 0, 0, h, cols, pos, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, maskPaint);
            canvas.restoreToCount(layer);
        }
    }

    /** Android 12+: records the list once, then redraws the edge zones as thin strips with growing blur. */
    @android.annotation.TargetApi(31)
    private static final class BlurEngine {
        private final android.graphics.RenderNode content = new android.graphics.RenderNode("clipContent");
        private final android.graphics.RenderNode[] strips = new android.graphics.RenderNode[2 * 64];   // top + bottom zone, up to 64 strips each

        void draw(EdgeBlurLayout host, Canvas canvas, int w, int h, float sTop, float sBot,
                  float zoneTop, float zoneBot, float maxBlur, int steps) {
            content.setPosition(0, 0, w, h);
            Canvas rec = content.beginRecording(w, h);
            try {
                host.superDispatchDraw(rec);
            } finally {
                content.endRecording();
            }
            // sharp part in the middle; an edge zone is only taken out where its strip is drawn below
            canvas.save();
            canvas.clipRect(0, sTop > 0.001f ? zoneTop : 0, w, sBot > 0.001f ? h - zoneBot : h);
            canvas.drawRenderNode(content);
            canvas.restore();
            steps = Math.min(steps, 64);
            float shTop = zoneTop / steps;
            float shBot = zoneBot / steps;
            int k = 0;
            for (int i = 0; i < steps; i++) {
                float inner = (i + 0.5f) / steps;               // 0 = outer edge, 1 = inner side
                // strip edges are snapped to whole pixels: two neighbouring strips that meet inside a pixel would
                // both be anti-aliased there and leave a faint line
                if (sTop > 0.001f) {
                    strip(canvas, k++, w, h, Math.round(i * shTop), Math.round((i + 1) * shTop), inner, sTop, maxBlur);
                }
                if (sBot > 0.001f) {
                    strip(canvas, k++, w, h, Math.round(h - (i + 1) * shBot), Math.round(h - i * shBot), inner, sBot, maxBlur);
                }
            }
        }

        private static float smooth(float t) {
            return t * t * (3f - 2f * t);
        }

        private static float smoother(float t) {
            return t * t * t * (t * (6f * t - 15f) + 10f);
        }

        private void strip(Canvas canvas, int k, int w, int h, float y0, float y1,
                           float inner, float strength, float maxBlur) {
            float depth = 1f - inner;                            // 1 at the outer edge, 0 at the inner side
            if (y1 <= y0) {
                return;
            }
            // eased curves (zero slope at the inner side): the blur grows slowly at first, so there is no visible
            // start line, and the content fades out gradually instead of being cut off at the edge
            float radius = maxBlur * smoother(depth) * strength;
            float alpha = 1f - strength * smooth(depth);
            int pad = (int) Math.ceil(radius * 2.5f);
            int ny0 = Math.max(0, (int) y0 - pad);
            int ny1 = Math.min(h, (int) Math.ceil(y1) + pad);
            android.graphics.RenderNode node = strips[k];
            if (node == null) {
                node = new android.graphics.RenderNode("clipStrip" + k);
                strips[k] = node;
            }
            node.setPosition(0, ny0, w, ny1);
            Canvas rc = node.beginRecording(w, ny1 - ny0);
            try {
                rc.translate(0, -ny0);
                rc.drawRenderNode(content);
            } finally {
                node.endRecording();
            }
            node.setRenderEffect(radius > 0.5f
                    ? android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
            node.setAlpha(alpha);
            canvas.save();
            canvas.clipRect(0, y0, w, y1);
            canvas.drawRenderNode(node);
            canvas.restore();
        }
    }

    private void refreshClipList() {
        dismissClipMenu();
        if (clipAdapter == null || layer != LAYER_CLIP) {
            return;
        }
        List<ClipItem> l = clipDisplayList();
        clipAdapter.set(l);
        if (clipEmpty != null) {
            clipEmpty.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (clipClearKey != null) {
            clipClearKey.setAlpha(l.isEmpty() ? 0.35f : 1f);
        }
    }

    /** Back button, Back key, and after pasting: return to the letters. */
    private void leaveClipboard() {
        dismissClipMenu();
        if (layer == LAYER_CLIP) {
            setLayer(homeLayer());
        }
    }

    private void pasteClip(String text) {
        commitPlain(text);
        leaveClipboard();
    }


    /** Small pin / delete button on a history row. */
    private final class IconButton extends View {
        private final int icon;
        boolean active = false;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconButton(int icon) {
            super(KeyboardService.this);
            this.icon = icon;
            setClickable(true);
            setSoundEffectsEnabled(false);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setAlpha(pressed ? 0.4f : 1f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setColor(active ? colors.accent : (colors.dark ? 0x99FFFFFF : 0x99000000));
            Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, dp(17), paint);
        }
    }

    private final class ClipHolder {
        FrameLayout card;
        LinearLayout content;
        TextView text;
        IconButton pin;
        ClipItem item;
        boolean swallowTap;   // this touch only closed the long-press menu, so it must not paste
    }

    // -------------------------------------------------- long-press menu (Pin / Delete)
    private void dismissClipMenu() {
        try {
            if (clipMenu != null && clipMenu.isShowing()) {
                clipMenu.dismiss();
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private TextView clipMenuRow(String label, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(uiMedium);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(16));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setClickable(true);
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed},
                new android.graphics.drawable.ColorDrawable(colors.dark ? 0x33FFFFFF : 0x1F000000));
        st.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        t.setBackground(st);
        return t;
    }

    /** Long-press on a clipboard tile: a small glass pop-up with Pin (or Unpin) and Delete. */
    private void showClipMenu(final View card, final ClipItem item) {
        dismissClipMenu();
        try {
            final int rowH = dp(46);
            final int menuW = dp(168);
            final int divH = Math.max(1, dp(0.75f));
            final int menuH = rowH * 2 + divH;
            final int pad = dp(14);   // room around the menu so its shadow is not clipped

            LinearLayout menu = new LinearLayout(this);
            menu.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(colors.dark ? 0xF22C2E34 : 0xF7FFFFFF);
            bg.setCornerRadius(dp(14));
            bg.setStroke(Math.max(1, dp(0.75f)), colors.keyStroke);
            menu.setBackground(bg);
            if (Build.VERSION.SDK_INT >= 21) {
                menu.setClipToOutline(true);
                menu.setElevation(dp(8));
            }

            TextView pinRow = clipMenuRow(item.pinned ? "Unpin" : "Pin", colors.text);
            TextView delRow = clipMenuRow("Delete", 0xFFFF3B30);
            View div = new View(this);
            div.setBackgroundColor(colors.divider);
            menu.addView(pinRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
            menu.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, divH));
            menu.addView(delRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));

            FrameLayout wrap = new FrameLayout(this);
            wrap.setClipChildren(false);
            wrap.setClipToPadding(false);
            wrap.setPadding(pad, pad, pad, pad);
            wrap.addView(menu, new FrameLayout.LayoutParams(menuW, menuH));

            pinRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback(v, T_SHIFT);
                    dismissClipMenu();
                    clipTogglePin(item);
                    refreshClipList();
                }
            });
            delRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback(v, T_DELETE);
                    dismissClipMenu();
                    clipRemove(item);
                    refreshClipList();
                }
            });

            final PopupWindow pw = new PopupWindow(wrap, menuW + 2 * pad, menuH + 2 * pad);
            pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            pw.setClippingEnabled(false);
            pw.setFocusable(false);
            pw.setOutsideTouchable(true);          // touching anywhere else closes it
            pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            pw.setAnimationStyle(0);
            pw.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    card.setSelected(false);
                    clipMenuClosedAt = SystemClock.uptimeMillis();
                }
            });
            clipMenu = pw;

            // Below the tile if it fits inside the list, otherwise above it. Right edge lines up with the tile.
            View row = (View) card.getParent();
            int bottomInList = row.getTop() + card.getBottom();
            boolean below = clipList == null
                    || bottomInList + dp(2) + menuH <= clipList.getHeight() - clipList.getPaddingBottom();
            int xoff = card.getWidth() - menuW - pad - dp(10);
            int yoff = below ? dp(2) - pad : -card.getHeight() - dp(2) - menuH - pad;
            card.setSelected(true);
            pw.showAsDropDown(card, xoff, yoff);
        } catch (Throwable t) {
            card.setSelected(false);   // a missing menu must never break the clipboard
        }
    }

    private final class ClipAdapter extends BaseAdapter {
        private List<ClipItem> data = new ArrayList<>();

        void set(List<ClipItem> d) {
            data = d;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private LinearLayout buildRow() {
            final ClipHolder h = new ClipHolder();
            LinearLayout outer = new LinearLayout(KeyboardService.this);
            outer.setPadding(insetH, insetV, insetH, insetV);   // same gap as between keys
            outer.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout card = new FrameLayout(KeyboardService.this);
            card.setMinimumHeight(keyHeightPx - 2 * insetV);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed}, glassRect(colors.keyPressed));
            bg.addState(new int[]{android.R.attr.state_selected}, glassRect(colors.keyPressed));
            bg.addState(new int[]{}, glassRect(colors.key));
            card.setBackground(bg);
            card.setClickable(true);
            h.card = card;

            // two-line preview (+ pin mark)
            LinearLayout content = new LinearLayout(KeyboardService.this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(KeyboardService.this);
            tv.setTypeface(uiFont);
            tv.setTextColor(colors.text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(15));
            tv.setMaxLines(2);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(14), dp(9), dp(8), dp(9));
            h.text = tv;
            h.pin = new IconButton(Icons.PIN);
            h.pin.setClickable(false);
            content.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(h.pin, new LinearLayout.LayoutParams(dp(30), dp(30)));
            ((LinearLayout.LayoutParams) h.pin.getLayoutParams()).rightMargin = dp(8);
            h.content = content;
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.gravity = Gravity.CENTER_VERTICAL;
            card.addView(content, clp);

            outer.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // A touch that lands right after the menu was closed by touching outside it only closes the menu.
            card.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        h.swallowTap = SystemClock.uptimeMillis() - clipMenuClosedAt < 150;
                    }
                    return false;
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (h.swallowTap) {
                        h.swallowTap = false;
                        return;
                    }
                    feedback(v, T_CHAR);
                    if (h.item != null) {
                        pasteClip(h.item.text);          // tap: paste and go back to the letters
                    }
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (h.item == null) {
                        return false;
                    }
                    feedback(v, T_SHIFT);
                    showClipMenu(v, h.item);             // long press: pop-up with Pin / Delete
                    return true;
                }
            });
            outer.setTag(h);
            return outer;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (convertView instanceof LinearLayout) ? (LinearLayout) convertView : buildRow();
            ClipHolder h = (ClipHolder) row.getTag();
            ClipItem it = data.get(position);
            h.item = it;
            h.text.setText(it.text.trim());
            h.pin.setVisibility(it.pinned ? View.VISIBLE : View.GONE);
            h.pin.active = it.pinned;
            h.pin.invalidate();
            h.card.setSelected(false);
            return row;
        }
    }

    // ------------------------------------------------------------------ key view
    /**
     * A key. It paints its own label every time it is drawn, using the live shift / caps state, so
     * letters always show lowercase or uppercase exactly as they will be typed.
     */
    private final class KeyView extends View {
        final Key key;
        private final boolean plain;
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect textBounds = new Rect();
        private boolean cancelled = false;
        private boolean longPressFired = false;
        private boolean swiped = false;
        private float lastStepX = 0f;
        private float glow = 0f;                 // 0..1, press glow
        private boolean fxDown = false;
        private ValueAnimator glowAnim;
        private final Path glowPath = new Path();
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** Tactile press: the key squeezes in quickly, then springs back with a little bounce; a glow follows. */
        private void pressFx(boolean down) {
            if (fxDown == down) {
                return;
            }
            fxDown = down;
            animate().cancel();
            if (down) {
                animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(70)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new OvershootInterpolator(3.2f)).start();
            }
            if (glowAnim != null) {
                glowAnim.cancel();
            }
            glowAnim = ValueAnimator.ofFloat(glow, down ? 1f : 0f);
            glowAnim.setDuration(down ? 90 : 420);
            glowAnim.setInterpolator(new DecelerateInterpolator());
            glowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    glow = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            glowAnim.start();
        }

        /** Soft light inside the key plus a thin halo around it (kept within the key's own inset). */
        private void drawGlow(Canvas c) {
            int base = (key.type == T_ENTER && primaryAction()) ? Color.WHITE : colors.accent;
            float w = getWidth();
            float h = getHeight();
            glowPaint.setShader(null);
            if (plain) {
                float cxp = w * (0.5f + key.iconShift);
                float r = dp(26);
                glowPaint.setStyle(Paint.Style.FILL);
                glowPaint.setShader(new RadialGradient(cxp, h / 2f, r, scaleAlpha(base, 0.34f * glow),
                        scaleAlpha(base, 0f), Shader.TileMode.CLAMP));
                c.drawCircle(cxp, h / 2f, r, glowPaint);
                glowPaint.setShader(null);
                return;
            }
            float l = insetH + dp(key.padL);
            float t = insetV;
            float r2 = w - insetH - dp(key.padR);
            float b = h - insetV;
            g2Rect(glowPath, l, t, r2, b, dp(KEY_RADIUS_DP), G2_POWER);
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setShader(new RadialGradient((l + r2) / 2f, (t + b) / 2f, Math.max(r2 - l, b - t) * 0.7f,
                    scaleAlpha(base, 0.30f * glow), scaleAlpha(base, 0.05f * glow), Shader.TileMode.CLAMP));
            c.drawPath(glowPath, glowPaint);
            glowPaint.setShader(null);
            glowPaint.setStyle(Paint.Style.STROKE);
            for (int k = 1; k <= 4; k++) {
                glowPaint.setStrokeWidth(dp(1.4f * k));
                glowPaint.setColor(scaleAlpha(base, 0.22f * glow / k));
                c.drawPath(glowPath, glowPaint);
            }
        }

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (key.alt != null) {
                    longPressFired = true;
                    showCallout(KeyView.this, key.alt);
                    typeChar(key.alt, false);
                } else if (key.type == T_TO_EMOJI) {
                    longPressFired = true;
                    showKeyboardPicker(); // hold the emoji key to switch keyboards
                }
            }
        };

        private final Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                handleDelete();
                playSound(KeySounds.DELETE);
                handler.postDelayed(this, 45);
            }
        };

        KeyView(Key k, boolean plainKey) {
            super(KeyboardService.this);
            key = k;
            plain = plainKey;
            setSoundEffectsEnabled(false);
            setContentDescription(describe(k));

            boolean blue = k.type == T_ENTER && primaryAction();
            labelPaint.setTypeface(uiFont);
            labelPaint.setColor(blue ? Color.WHITE : colors.text);
            if (k.type == T_CLIP_CLEAR) {
                labelPaint.setColor(colors.accent);
            }
            if (k.type == T_SPACE) {
                labelPaint.setTextAlign(Paint.Align.RIGHT);
                labelPaint.setTextSize(dp(10.5f));
                labelPaint.setColor(colors.dark ? 0x99FFFFFF : 0x99000000);
            } else {
                labelPaint.setTextAlign(Paint.Align.CENTER);
                float size;
                if (k.type == T_CHAR) {
                    size = k.label.length() == 1 ? 24f : 16f;
                } else if (k.type == T_TO_SYMBOLS1 || k.type == T_TO_SYMBOLS2 || k.type == T_TO_LETTERS
                        || k.type == T_ENTER) {
                    size = 19f;
                } else {
                    size = 17f;
                }
                labelPaint.setTextSize(dp(size));
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
                setBackground(keyBackground(fill, pressed, !blue, dp(k.padL), dp(k.padR)));
            }
        }

        String displayLabel() {
            if (key.letter && (shifted || capsLock)) {
                return key.label.toUpperCase(Locale.ROOT);
            }
            return key.label;
        }

        private String describe(Key k) {
            switch (k.type) {
                case T_SHIFT: return "Shift";
                case T_DELETE: return "Delete";
                case T_ENTER: return "Enter";
                case T_SPACE: return "Space";
                case T_GLOBE: return "Switch keyboard";
                case T_MIC: return "Voice typing";
                case T_TO_EMOJI: return "Emoji. Hold to switch keyboard";
                case T_TO_SYMBOLS1: return "Numbers";
                case T_TO_SYMBOLS2: return "More symbols";
                case T_TO_LETTERS: return "Letters";
                case T_TO_CLIP: return "Clipboard";
                case T_CLIP_CLOSE: return "Back to keyboard";
                case T_CLIP_CLEAR: return "Clear all clipboard history";
                default: return k.label;
            }
        }

        /** Shift, delete and return are drawn by drawGlyph (iOS shapes), not by the shared Icons set. */
        private int ownGlyph(int icon) {
            if (key.type == T_SHIFT) {
                return capsLock ? G_CAPS : (shifted ? G_SHIFT_ON : G_SHIFT);
            }
            if (key.type == T_DELETE) {
                return G_DELETE;
            }
            if (key.type == T_ENTER && icon != Icons.NONE) {
                return icon == Icons.SEARCH ? G_SEARCH : G_RETURN;
            }
            return icon;
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
            if (glowAnim != null) {
                glowAnim.cancel();
            }
            animate().cancel();
            setScaleX(1f);
            setScaleY(1f);
            fxDown = false;
            glow = 0f;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); // key background
            if (glow > 0.01f) {
                drawGlow(canvas);
            }
            float cx = getWidth() * (0.5f + key.iconShift) + dp(key.padL - key.padR) / 2f;
            float cy = getHeight() / 2f;
            int icon = ownGlyph(currentIcon());
            if (icon >= GLYPH_BASE) {
                // one optical size per icon kind, the same on every key that shows it; always centred
                float gs = 22f;
                // glyph box = wanted height * 24 / (height the drawing really uses inside the 24 grid)
                if (icon == G_EMOJI) gs = TRAY_ICON_DP * 24f / 22f;
                else if (icon == G_MIC) gs = TRAY_ICON_DP * 24f / 21.85f;
                else if (icon == G_CLIP) gs = TRAY_ICON_DP * 24f / 21.2f;
                else if (icon == G_SHIFT || icon == G_SHIFT_ON || icon == G_CAPS) gs = 24f;
                else if (icon == G_DELETE) gs = 23f;
                else if (icon == G_RETURN) gs = 22.5f;
                else if (icon == G_SEARCH && key.type == T_ENTER) gs = 23f;
                boolean whiteIcon = key.type == T_ENTER && primaryAction();
                int iconColor = colors.text;
                if (key.type == T_TO_EMOJI || key.type == T_TO_CLIP || key.type == T_MIC) {
                    iconColor = colors.dark ? Color.WHITE : TRAY_ICON_LIGHT;
                }
                drawGlyph(canvas, icon, cx, cy, dp(gs), whiteIcon ? Color.WHITE : iconColor);
                return;
            }
            if (icon != Icons.NONE) {
                boolean blue = key.type == T_ENTER && primaryAction();
                iconPaint.setColor(blue ? Color.WHITE : colors.text);
                float size = dp(plain ? 24 : 22);
                Icons.draw(canvas, icon, cx, cy, size, iconPaint);
                return;
            }
            String label = displayLabel();
            if (label == null || label.isEmpty()) {
                return;
            }
            if (key.type == T_SPACE) {
                float baseline = getHeight() - insetV - dp(7);
                canvas.drawText(label, getWidth() - insetH - dp(10), baseline, labelPaint);
                return;
            }
            // Same baseline for every key in the same case: centre the x-height (lowercase) or the
            // cap height (everything else) on the middle of the key.
            boolean lower = key.letter && !(shifted || capsLock);
            labelPaint.getTextBounds(lower ? "x" : "H", 0, 1, textBounds);
            float baseline = cy - (textBounds.top + textBounds.bottom) / 2f;
            canvas.drawText(label, cx, baseline, labelPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(true);
                    pressFx(true);
                    if (plain) setAlpha(0.75f);
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
                    } else if (key.alt != null || key.type == T_GLOBE || key.type == T_TO_EMOJI) {
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
                        pressFx(false);
                        if (plain) setAlpha(1f);
                        dismissCallout(this);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    pressFx(false);
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
                    pressFx(false);
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
        int kind = KeySounds.KEY;
        if (keyType == T_DELETE) {
            kind = KeySounds.DELETE;
        } else if (keyType == T_SHIFT || keyType == T_TO_SYMBOLS1 || keyType == T_TO_SYMBOLS2
                || keyType == T_TO_LETTERS || keyType == T_TO_EMOJI || keyType == T_GLOBE
                || keyType == T_MIC || keyType == T_TO_CLIP || keyType == T_CLIP_CLOSE) {
            kind = KeySounds.MODIFIER;
        }
        playSound(kind);
    }

    private void playSound(int kind) {
        if (sounds != null && Prefs.on(prefs, Prefs.K_SOUND)) {
            sounds.play(kind);
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
            case T_TO_CLIP:
                setLayer(LAYER_CLIP);
                break;
            case T_CLIP_CLOSE:
                leaveClipboard();
                break;
            case T_CLIP_CLEAR:
                clipClearUnpinned();
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
        if (layer == LAYER_EMOJI_SEARCH) {
            searchAppend(s);
            if (isLetter && shifted && !capsLock) {
                shifted = false;
                updateShiftVisuals();
            }
            return;
        }
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
        if (layer == LAYER_EMOJI_SEARCH) {
            searchBackspace();
            return;
        }
        spaceAfterSuggestion = false;
        lastOwnEdit = SystemClock.uptimeMillis();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void handleSpace() {
        if (layer == LAYER_EMOJI_SEARCH) {
            searchAppend(" ");
            return;
        }
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
        if (layer == LAYER_EMOJI_SEARCH) {
            List<String> res = emojiSearchResults(emojiQuery.toString());
            if (res.isEmpty() || emojiQuery.length() == 0) {
                exitEmojiSearch();
            } else {
                pickSearchedEmoji(res.get(0));   // Return inserts the best match
            }
            return;
        }
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
