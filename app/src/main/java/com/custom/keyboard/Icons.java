
package com.custom.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Hand-drawn keyboard icons and the key-callout balloon shape.
 * No external image files are required.
 */
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
    static final int PIN = 10;

    private static final Path PATH = new Path();
    private static final RectF RECT = new RectF();

    /**
     * Draws an icon centred on (cx, cy), inside a square of side s.
     * The caller is responsible for setting the paint colour.
     */
    static void draw(Canvas canvas, int type, float cx, float cy,
                     float s, Paint paint) {

        paint.setStrokeWidth(Math.max(1.6f, s * 0.085f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        switch (type) {
            case SHIFT:
            case SHIFT_ON:
            case CAPS:
                drawShift(canvas, type, cx, cy, s, paint);
                break;

            case DELETE:
                drawDelete(canvas, cx, cy, s, paint);
                break;

            case RETURN:
                drawReturn(canvas, cx, cy, s, paint);
                break;

            case SEARCH:
                drawSearch(canvas, cx, cy, s, paint);
                break;

            case GLOBE:
                drawGlobe(canvas, cx, cy, s, paint);
                break;

            case EMOJI:
                drawEmoji(canvas, cx, cy, s, paint);
                break;

            case MIC:
                drawMic(canvas, cx, cy, s, paint);
                break;

            case PIN:
                drawPin(canvas, cx, cy, s, paint);
                break;

            default:
                break;
        }
    }

    private static void drawShift(Canvas canvas, int type,
                                  float cx, float cy, float s, Paint paint) {

        float w = s * 0.78f;
        float h = type == CAPS ? s * 0.64f : s * 0.80f;
        float mid = type == CAPS ? cy - s * 0.06f : cy;

        float left = cx - w / 2f;
        float right = cx + w / 2f;
        float top = mid - h / 2f;
        float bottom = mid + h / 2f;

        float stem = w * 0.21f;
        float shoulder = top + h * 0.52f;

        PATH.reset();
        PATH.moveTo(cx, top);
        PATH.lineTo(right, shoulder);
        PATH.lineTo(cx + stem, shoulder);
        PATH.lineTo(cx + stem, bottom);
        PATH.lineTo(cx - stem, bottom);
        PATH.lineTo(cx - stem, shoulder);
        PATH.lineTo(left, shoulder);
        PATH.close();

        paint.setStyle(type == SHIFT
                ? Paint.Style.STROKE
                : Paint.Style.FILL_AND_STROKE);

        canvas.drawPath(PATH, paint);

        if (type == CAPS) {
            paint.setStyle(Paint.Style.STROKE);

            float barY = bottom + s * 0.15f;
            canvas.drawLine(
                    cx - stem, barY,
                    cx + stem, barY,
                    paint
            );
        }
    }

    private static void drawDelete(Canvas canvas, float cx,
                                   float cy, float s, Paint paint) {

        float w = s;
        float h = s * 0.70f;

        float left = cx - w / 2f;
        float right = cx + w / 2f;
        float top = cy - h / 2f;
        float bottom = cy + h / 2f;

        float bodyLeft = left + w * 0.30f;

        paint.setStyle(Paint.Style.STROKE);

        PATH.reset();
        PATH.moveTo(left, cy);
        PATH.lineTo(bodyLeft, top);
        PATH.lineTo(right, top);
        PATH.lineTo(right, bottom);
        PATH.lineTo(bodyLeft, bottom);
        PATH.close();

        canvas.drawPath(PATH, paint);

        float x = (bodyLeft + right) / 2f;
        float size = h * 0.19f;

        canvas.drawLine(
                x - size, cy - size,
                x + size, cy + size,
                paint
        );

        canvas.drawLine(
                x - size, cy + size,
                x + size, cy - size,
                paint
        );
    }

    private static void drawReturn(Canvas canvas, float cx,
                                   float cy, float s, Paint paint) {

        float w = s * 0.86f;
        float h = s * 0.52f;

        float left = cx - w / 2f;
        float right = cx + w / 2f;
        float top = cy - h / 2f;

        float y = cy + h * 0.22f;
        float head = h * 0.34f;

        paint.setStyle(Paint.Style.STROKE);

        PATH.reset();
        PATH.moveTo(right, top);
        PATH.lineTo(right, y);
        PATH.lineTo(left, y);

        canvas.drawPath(PATH, paint);

        PATH.reset();
        PATH.moveTo(left + head, y - head);
        PATH.lineTo(left, y);
        PATH.lineTo(left + head, y + head);

        canvas.drawPath(PATH, paint);
    }

    private static void drawSearch(Canvas canvas, float cx,
                                   float cy, float s, Paint paint) {

        paint.setStyle(Paint.Style.STROKE);

        float radius = s * 0.30f;
        float ox = cx - s * 0.07f;
        float oy = cy - s * 0.07f;

        canvas.drawCircle(ox, oy, radius, paint);

        float distance = radius * 0.72f;

        canvas.drawLine(
                ox + distance,
                oy + distance,
                cx + s * 0.40f,
                cy + s * 0.40f,
                paint
        );
    }

    private static void drawGlobe(Canvas canvas, float cx,
                                  float cy, float s, Paint paint) {

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.4f, s * 0.07f));

        float radius = s * 0.46f;

        canvas.drawCircle(cx, cy, radius, paint);

        RECT.set(
                cx - radius * 0.44f,
                cy - radius,
                cx + radius * 0.44f,
                cy + radius
        );

        canvas.drawOval(RECT, paint);

        canvas.drawLine(
                cx - radius, cy,
                cx + radius, cy,
                paint
        );
    }

    private static void drawEmoji(Canvas canvas, float cx,
                                  float cy, float s, Paint paint) {

        paint.setStyle(Paint.Style.STROKE);

        float radius = s * 0.46f;

        canvas.drawCircle(cx, cy, radius, paint);

        RECT.set(
                cx - radius * 0.56f,
                cy - radius * 0.50f,
                cx + radius * 0.56f,
                cy + radius * 0.56f
        );

        canvas.drawArc(RECT, 25f, 130f, false, paint);

        paint.setStyle(Paint.Style.FILL);

        float eye = Math.max(1.4f, s * 0.055f);

        canvas.drawCircle(
                cx - radius * 0.36f,
                cy - radius * 0.22f,
                eye,
                paint
        );

        canvas.drawCircle(
                cx + radius * 0.36f,
                cy - radius * 0.22f,
                eye,
                paint
        );
    }

    private static void drawMic(Canvas canvas, float cx,
                                float cy, float s, Paint paint) {

        paint.setStyle(Paint.Style.STROKE);

        float width = s * 0.15f;

        RECT.set(
                cx - width,
                cy - s * 0.47f,
                cx + width,
                cy + s * 0.07f
        );

        canvas.drawRoundRect(RECT, width, width, paint);

        RECT.set(
                cx - s * 0.30f,
                cy - s * 0.25f,
                cx + s * 0.30f,
                cy + s * 0.30f
        );

        canvas.drawArc(RECT, 0f, 180f, false, paint);

        canvas.drawLine(
                cx, cy + s * 0.30f,
                cx, cy + s * 0.47f,
                paint
        );

        canvas.drawLine(
                cx - s * 0.14f, cy + s * 0.47f,
                cx + s * 0.14f, cy + s * 0.47f,
                paint
        );
    }

    private static void drawPin(Canvas canvas, float cx,
                                float cy, float s, Paint paint) {

        paint.setStyle(Paint.Style.STROKE);

        float radius = s * 0.25f;
        float top = cy - s * 0.43f;
        float bottom = cy + s * 0.43f;

        PATH.reset();
        PATH.moveTo(cx, bottom);
        PATH.cubicTo(
                cx - s * 0.10f, cy + s * 0.18f,
                cx - s * 0.38f, cy - s * 0.02f,
                cx - s * 0.38f, cy - s * 0.19f
        );

        PATH.cubicTo(
                cx - s * 0.38f, top,
                cx + s * 0.38f, top,
                cx + s * 0.38f, cy - s * 0.19f
        );

        PATH.cubicTo(
                cx + s * 0.38f, cy - s * 0.02f,
                cx + s * 0.10f, cy + s * 0.18f,
                cx, bottom
        );

        canvas.drawPath(PATH, paint);
        canvas.drawCircle(cx, cy - s * 0.19f, radius * 0.42f, paint);
    }

    /**
     * Builds the iOS-style key callout shape.
     * All coordinates use the same coordinate space.
     */
    static void callout(
            Path out,
            float keyL,
            float keyT,
            float keyR,
            float keyB,
            float headL,
            float headR,
            float headT,
            float headB,
            float headRadius,
            float keyRadius
    ) {

        float neckEnd = keyT + (keyB - keyT) * 0.34f;
        float dy = neckEnd - headB;

        out.reset();

        out.moveTo(headL + headRadius, headT);
        out.lineTo(headR - headRadius, headT);

        out.quadTo(
                headR, headT,
                headR, headT + headRadius
        );

        out.lineTo(headR, headB);

        out.cubicTo(
                headR,
                headB + dy * 0.52f,
                keyR,
                neckEnd - dy * 0.52f,
                keyR,
                neckEnd
        );

        out.lineTo(keyR, keyB - keyRadius);

        out.quadTo(
                keyR, keyB,
                keyR - keyRadius, keyB
        );

        out.lineTo(keyL + keyRadius, keyB);

        out.quadTo(
                keyL, keyB,
                keyL, keyB - keyRadius
        );

        out.lineTo(keyL, neckEnd);

        out.cubicTo(
                keyL,
                neckEnd - dy * 0.52f,
                headL,
                headB + dy * 0.52f,
                headL,
                headB
        );

        out.lineTo(headL, headT + headRadius);

        out.quadTo(
                headL, headT,
                headL + headRadius, headT
        );

        out.close();
    }
}
