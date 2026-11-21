package com.custom.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Setup + settings screen: enable the keyboard, pick it, tweak it, and try it out. */
public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private float density;
    private boolean dark;
    private int bg, cardColor, textColor, subColor, segColor, accent;

    private TextView step1Status;
    private TextView step2Status;
    private Button step2Button;
    private final Button[] themeButtons = new Button[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        density = getResources().getDisplayMetrics().density;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;
        bg = dark ? 0xFF121316 : 0xFFF2F4F8;
        cardColor = dark ? 0xFF1F2126 : 0xFFFFFFFF;
        textColor = dark ? 0xFFECEDEF : 0xFF1B1C20;
        subColor = dark ? 0xFF9A9DA5 : 0xFF666A73;
        segColor = dark ? 0xFF2C2F36 : 0xFFE8EAF0;
        accent = Prefs.ACCENTS[Prefs.accent(prefs)];

        getWindow().setStatusBarColor(bg);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        View content = buildUi();
        Fonts.applyToTree(this, content);
        setContentView(content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    // ----------------------------------------------------------------- status
    private boolean isEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (info.getPackageName().equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSelected() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return id != null && id.startsWith(getPackageName() + "/");
    }

    private void refreshStatus() {
        boolean enabled = isEnabled();
        boolean selected = isSelected();

        step1Status.setText(enabled ? "\u2713  Enabled" : "Not enabled yet");
        step1Status.setTextColor(enabled ? 0xFF2FB36D : 0xFFE0903A);

        if (selected) {
            step2Status.setText("\u2713  Active \u2013 this is your current keyboard");
            step2Status.setTextColor(0xFF2FB36D);
        } else if (enabled) {
            step2Status.setText("Not selected yet");
            step2Status.setTextColor(0xFFE0903A);
        } else {
            step2Status.setText("Finish step 1 first");
            step2Status.setTextColor(subColor);
        }
        step2Button.setEnabled(enabled);
        step2Button.setAlpha(enabled ? 1f : 0.45f);
    }

    // ---------------------------------------------------------------- helpers
    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardColor);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? Color.WHITE : textColor);
        b.setBackground(pill(primary ? accent : segColor));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private void addSwitch(LinearLayout parent, String label, final String key) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sw.setTextColor(textColor);
        sw.setChecked(Prefs.on(prefs, key));
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
            }
        });
        parent.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // --------------------------------------------------------------------- UI
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(text("Custom Keyboard", 30, textColor, true));
        TextView sub = text("Your own on-screen keyboard. Switch it on in two quick steps.", 15, subColor, false);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);

        // ---- Step 1
        LinearLayout c1 = card();
        c1.addView(text("1   Enable the keyboard", 18, textColor, true));
        TextView d1 = text("Turn on \"Custom Keyboard\" in the list of on-screen keyboards.", 14, subColor, false);
        d1.setPadding(0, dp(6), 0, dp(8));
        c1.addView(d1);
        step1Status = text("", 15, subColor, true);
        c1.addView(step1Status);
        Button b1 = button("Open keyboard settings", true);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        c1.addView(b1);
        col.addView(c1);

        // ---- Step 2
        LinearLayout c2 = card();
        c2.addView(text("2   Switch to it", 18, textColor, true));
        TextView d2 = text("Pick Custom Keyboard as the keyboard you type with.", 14, subColor, false);
        d2.setPadding(0, dp(6), 0, dp(8));
        c2.addView(d2);
        step2Status = text("", 15, subColor, true);
        c2.addView(step2Status);
        step2Button = button("Choose Custom Keyboard", true);
        step2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        c2.addView(step2Button);
        col.addView(c2);

        // ---- Try it
        LinearLayout c3 = card();
        c3.addView(text("Try it out", 18, textColor, true));
        EditText et = new EditText(this);
        et.setHint("Tap here and start typing\u2026");
        et.setHintTextColor(subColor);
        et.setTextColor(textColor);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setBackground(pill(segColor));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(10);
        c3.addView(et, elp);
        col.addView(c3);

        // ---- Appearance
        LinearLayout c4 = card();
        c4.addView(text("Appearance", 18, textColor, true));

        TextView themeLabel = text("Theme  (System follows your phone's light / dark mode)", 14, subColor, false);
        themeLabel.setPadding(0, dp(10), 0, dp(6));
        c4.addView(themeLabel);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"System", "Light", "Dark"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button tb = new Button(this);
            tb.setText(names[i]);
            tb.setAllCaps(false);
            tb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt(Prefs.K_THEME, idx).apply();
                    refreshThemeButtons();
                }
            });
            themeButtons[i] = tb;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.rightMargin = (i < 2) ? dp(8) : 0;
            seg.addView(tb, lp);
        }
        c4.addView(seg);
        refreshThemeButtons();

        addSwitch(c4, "Frosted glass (blur + transparency)", Prefs.K_BLUR);
        TextView blurNote = text("The keyboard becomes see-through with a blur, like iOS. Real blur needs "
                + "Android 12+ with window blur enabled on your phone; otherwise you get a soft "
                + "see-through look. Turn this off for a solid keyboard.", 12, subColor, false);
        blurNote.setPadding(0, 0, 0, dp(4));
        c4.addView(blurNote);

        TextView accentLabel = text("Accent color (Return / Go / Search key)", 14, subColor, false);
        accentLabel.setPadding(0, dp(16), 0, dp(8));
        c4.addView(accentLabel);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        int current = Prefs.accent(prefs);
        for (int i = 0; i < Prefs.ACCENTS.length; i++) {
            final int idx = i;
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Prefs.ACCENTS[i]);
            if (i == current) {
                g.setStroke(dp(3), textColor);
            }
            sw.setBackground(g);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt(Prefs.K_ACCENT, idx).apply();
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(12);
            swatches.addView(sw, lp);
        }
        c4.addView(swatches);

        final TextView heightLabel = text("", 14, subColor, false);
        heightLabel.setPadding(0, dp(18), 0, dp(4));
        c4.addView(heightLabel);
        SeekBar seek = new SeekBar(this);
        seek.setMax(Prefs.MAX_HEIGHT - Prefs.MIN_HEIGHT);
        seek.setProgress(Prefs.height(prefs) - Prefs.MIN_HEIGHT);
        heightLabel.setText("Key height: " + Prefs.height(prefs) + " dp");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int h = Prefs.MIN_HEIGHT + progress;
                heightLabel.setText("Key height: " + h + " dp");
                if (fromUser) {
                    prefs.edit().putInt(Prefs.K_HEIGHT, h).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        c4.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(c4);

        // ---- Typing
        LinearLayout c5 = card();
        c5.addView(text("Typing", 18, textColor, true));
        addSwitch(c5, "Word suggestions", Prefs.K_SUGGEST);
        addSwitch(c5, "Auto-correct common typos", Prefs.K_AUTOCORRECT);
        addSwitch(c5, "Auto-capitalize sentences", Prefs.K_AUTOCAP);
        addSwitch(c5, "Double-space for period", Prefs.K_DBLSPACE);
        addSwitch(c5, "Learn new words (stored on this phone only)", Prefs.K_LEARN);
        addSwitch(c5, "Vibrate on keypress", Prefs.K_HAPTIC);
        addSwitch(c5, "Sound on keypress", Prefs.K_SOUND);
        Button clear = button("Clear learned words", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove(Prefs.K_LEARNED)
                        .putLong(Prefs.K_CLEAR_TOKEN, System.currentTimeMillis()).apply();
                Toast.makeText(MainActivity.this, "Learned words cleared", Toast.LENGTH_SHORT).show();
            }
        });
        c5.addView(clear);
        col.addView(c5);

        // ---- Tips + privacy
        LinearLayout c6 = card();
        c6.addView(text("Tips", 18, textColor, true));
        TextView tips = text(
                "\u2022 Tap \u21E7 once for one capital, twice for caps lock.\n"
                        + "\u2022 Hold a top-row letter to type its number.\n"
                        + "\u2022 Slide your finger along the space bar to move the cursor.\n"
                        + "\u2022 Hold \u232B to keep deleting.\n"
                        + "\u2022 Tap the globe to switch keyboards, hold it for the full list.\n"
                        + "\u2022 Tap the mic to switch to your phone's voice typing.\n"
                        + "\u2022 Open this app any time to change the look.", 14, subColor, false);
        tips.setPadding(0, dp(8), 0, dp(8));
        c6.addView(tips);
        TextView privacy = text("Privacy: this app has no internet permission, so nothing you type can leave your phone.",
                13, subColor, false);
        privacy.setPadding(0, dp(6), 0, 0);
        c6.addView(privacy);
        col.addView(c6);

        return scroll;
    }

    private void refreshThemeButtons() {
        int sel = Prefs.theme(prefs);
        for (int i = 0; i < themeButtons.length; i++) {
            boolean on = (i == sel);
            themeButtons[i].setBackground(pill(on ? accent : segColor));
            themeButtons[i].setTextColor(on ? Color.WHITE : textColor);
        }
    }
}
