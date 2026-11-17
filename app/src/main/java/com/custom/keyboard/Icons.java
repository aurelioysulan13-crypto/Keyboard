package com.custom.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Hand-drawn, thin-line icons and the key-callout balloon shape (no image files needed). */
final class Icons {
    private Icons() {}

    static final int NONE = 0;
    static final int SHIFT = 1;
    static final int SHIFT_ON = 2;
    static final int CAPS = 3;
    static final int DELETE = 4;
    static final int RETURN = 5;
    static final int SEARCH = 6;
    static final int GLOBE = 7;
    static final int EMOJI = 8;
    static final int MIC = 9;

    private static final Path PATH = new Path();
    private static final RectF RECT = new RectF();

    /** Draws an icon centred on (cx, cy) inside a square of side s. The caller sets the paint colour. */
    static void draw(Canvas c, int type, float cx, float cy, float s, Paint p) {
        p.setStrokeWidth(Math.max(1.6f, s * 0.085f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case SHIFT:
            case SHIFT_ON:
            case CAPS:
                drawShift(c, type, cx, cy, s, p);
                break;
            case DELETE:
                drawDelete(c, cx, cy, s, p);
                break;
            case RETURN:
                drawReturn(c, cx, cy, s, p);
                break;
            case SEARCH:
                drawSearch(c, cx, cy, s, p);
                break;
            case GLOBE:
                drawGlobe(c, cx, cy, s, p);
                break;
            case EMOJI:
                drawEmoji(c, cx, cy, s, p);
                break;
            case MIC:
                drawMic(c, cx, cy, s, p);
                break;
            default:
                break;
        }
    }

    private static void drawShift(Canvas c, int type, float cx, float cy, float s, Paint p) {
        float w = s * 0.78f;
        float h = (type == CAPS) ? s * 0.64f : s * 0.80f;
        float mid = (type == CAPS) ? cy - s * 0.06f : cy;
        float l = cx - w / 2f;
        float r = cx + w / 2f;
        float t = mid - h / 2f;
        float b = mid + h / 2f;
        float stem = w * 0.21f;
        float shoulder = t + h * 0.52f;
        PATH.reset();
        PATH.moveTo(cx, t);
        PATH.lineTo(r, shoulder);
        PATH.lineTo(cx + stem, shoulder);
        PATH.lineTo(cx + stem, b);
        PATH.lineTo(cx - stem, b);
        PATH.lineTo(cx - stem, shoulder);
        PATH.lineTo(l, shoulder);
        PATH.close();
        p.setStyle(type == SHIFT ? Paint.Style.STROKE : Paint.Style.FILL_AND_STROKE);
        c.drawPath(PATH, p);
        if (type == CAPS) {
            p.setStyle(Paint.Style.STROKE);
            float by = b + s * 0.15f;
            c.drawLine(cx - stem, by, cx + stem, by, p);
        }
    }

    private static void drawDelete(Canvas c, float cx, float cy, float s, Paint p) {
        float w = s * 1.0f;
        float h = s * 0.70f;
        float l = cx - w / 2f;
        float r = cx + w / 2f;
        float t = cy - h / 2f;
        float b = cy + h / 2f;
        float bodyL = l + w * 0.30f;
        p.setStyle(Paint.Style.STROKE);
        PATH.reset();
        PATH.moveTo(l, cy);
        PATH.lineTo(bodyL, t);
        PATH.lineTo(r, t);
        PATH.lineTo(r, b);
        PATH.lineTo(bodyL, b);
        PATH.close();
        c.drawPath(PATH, p);
        float xc = (bodyL + r) / 2f;
        float xs = h * 0.19f;
        c.drawLine(xc - xs, cy - xs, xc + xs, cy + xs, p);
        c.drawLine(xc - xs, cy + xs, xc + xs, cy - xs, p);
    }

    private static void drawReturn(Canvas c, float cx, float cy, float s, Paint p) {
        float w = s * 0.86f;
        float h = s * 0.52f;
        float l = cx - w / 2f;
        float r = cx + w / 2f;
        float top = cy - h / 2f;
        float y = cy + h * 0.22f;
        float head = h * 0.34f;
        p.setStyle(Paint.Style.STROKE);
        PATH.reset();
        PATH.moveTo(r, top);
        PATH.lineTo(r, y);
        PATH.lineTo(l, y);
        c.drawPath(PATH, p);
        PATH.reset();
        PATH.moveTo(l + head, y - head);
        PATH.lineTo(l, y);
        PATH.lineTo(l + head, y + head);
        c.drawPath(PATH, p);
    }

    private static void drawSearch(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        float r = s * 0.30f;
        float ox = cx - s * 0.07f;
        float oy = cy - s * 0.07f;
        c.drawCircle(ox, oy, r, p);
        float d = r * 0.72f;
        c.drawLine(ox + d, oy + d, cx + s * 0.40f, cy + s * 0.40f, p);
    }

    private static void drawGlobe(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.4f, s * 0.07f));
        float r = s * 0.46f;
        c.drawCircle(cx, cy, r, p);
        RECT.set(cx - r * 0.44f, cy - r, cx + r * 0.44f, cy + r);
        c.drawOval(RECT, p);
        c.drawLine(cx - r, cy, cx + r, cy, p);
    }

    private static void drawEmoji(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        float r = s * 0.46f;
        c.drawCircle(cx, cy, r, p);
        RECT.set(cx - r * 0.56f, cy - r * 0.50f, cx + r * 0.56f, cy + r * 0.56f);
        c.drawArc(RECT, 25f, 130f, false, p);
        p.setStyle(Paint.Style.FILL);
        float eye = Math.max(1.4f, s * 0.055f);
        c.drawCircle(cx - r * 0.36f, cy - r * 0.22f, eye, p);
        c.drawCircle(cx + r * 0.36f, cy - r * 0.22f, eye, p);
    }

    private static void drawMic(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        float cw = s * 0.15f;
        RECT.set(cx - cw, cy - s * 0.47f, cx + cw, cy + s * 0.07f);
        c.drawRoundRect(RECT, cw, cw, p);
        RECT.set(cx - s * 0.30f, cy - s * 0.25f, cx + s * 0.30f, cy + s * 0.30f);
        c.drawArc(RECT, 0f, 180f, false, p);
        c.drawLine(cx, cy + s * 0.30f, cx, cy + s * 0.47f, p);
        c.drawLine(cx - s * 0.14f, cy + s * 0.47f, cx + s * 0.14f, cy + s * 0.47f, p);
    }

    /**
     * iOS-style key callout: a wide rounded "head" joined to the pressed key by a smooth neck.
     * Everything is in the same coordinate space.
     */
    static void callout(Path out, float keyL, float keyT, float keyR, float keyB,
                        float headL, float headR, float headT, float headB,
                        float headRadius, float keyRadius) {
        // The neck swings from the head's width to the key's width, finishing a little way down the key.
        float neckEnd = keyT + (keyB - keyT) * 0.34f;
        float dy = neckEnd - headB;
        out.reset();
        out.moveTo(headL + headRadius, headT);
        out.lineTo(headR - headRadius, headT);
        out.quadTo(headR, headT, headR, headT + headRadius);
        out.lineTo(headR, headB);
        out.cubicTo(headR, headB + dy * 0.52f, keyR, neckEnd - dy * 0.52f, keyR, neckEnd);
        out.lineTo(keyR, keyB - keyRadius);
        out.quadTo(keyR, keyB, keyR - keyRadius, keyB);
        out.lineTo(keyL + keyRadius, keyB);
        out.quadTo(keyL, keyB, keyL, keyB - keyRadius);
        out.lineTo(keyL, neckEnd);
        out.cubicTo(keyL, neckEnd - dy * 0.52f, headL, headB + dy * 0.52f, headL, headB);
        out.lineTo(headL, headT + headRadius);
        out.quadTo(headL, headT, headL + headRadius, headT);
        out.close();
    }
}
