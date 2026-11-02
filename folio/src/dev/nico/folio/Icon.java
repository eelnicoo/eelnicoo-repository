package dev.nico.folio;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Iconos de línea dibujados a mano sobre una rejilla de 24×24. */
final class Icon extends Drawable {
    static final int PLUS = 1;
    static final int CLOSE = 2;
    static final int MORE = 3;
    static final int ROTATE = 4;
    static final int TO_START = 5;
    static final int TO_END = 6;
    static final int TRASH = 7;
    static final int PHOTOS = 8;
    static final int FOLDER = 9;
    static final int FILE = 10;
    static final int CHECK = 11;
    static final int SHARE = 12;
    static final int OPEN = 13;
    static final int REVERSE = 14;
    static final int SORT_NAME = 15;
    static final int SORT_DATE = 16;
    static final int ALERT = 17;

    private final int type;
    private final int size;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    Icon(int type, int color, int sizeDp) {
        this(type, color, sizeDp, 1.8f);
    }

    Icon(int type, int color, int sizeDp, float strokeWidth) {
        this.type = type;
        this.size = Ui.dp(sizeDp);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(strokeWidth);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        if (s <= 0) return;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        switch (type) {
            case PLUS:
                line(c, 12, 5, 12, 19);
                line(c, 5, 12, 19, 12);
                break;
            case CLOSE:
                line(c, 6.5f, 6.5f, 17.5f, 17.5f);
                line(c, 17.5f, 6.5f, 6.5f, 17.5f);
                break;
            case MORE:
                c.drawCircle(5.5f, 12, 1.7f, fill);
                c.drawCircle(12, 12, 1.7f, fill);
                c.drawCircle(18.5f, 12, 1.7f, fill);
                break;
            case ROTATE:
                path.reset();
                path.moveTo(20.5f, 12);
                oval.set(3.5f, 3.5f, 20.5f, 20.5f);
                path.arcTo(oval, 0, 300, false);
                c.drawPath(path, stroke);
                // Punta de flecha al final del arco (a 300°), en sentido horario.
                poly(c, 13.2f, 5.5f, 16.25f, 4.64f, 15.4f, 1.6f);
                break;
            case TO_START:
                line(c, 5, 4.5f, 19, 4.5f);
                poly(c, 7, 13, 12, 8, 17, 13);
                line(c, 12, 8, 12, 20);
                break;
            case TO_END:
                line(c, 5, 19.5f, 19, 19.5f);
                poly(c, 7, 11, 12, 16, 17, 11);
                line(c, 12, 16, 12, 4);
                break;
            case TRASH:
                line(c, 4, 6.5f, 20, 6.5f);
                poly(c, 9, 6.5f, 9, 4, 15, 4, 15, 6.5f);
                poly(c, 6, 6.5f, 7, 20, 17, 20, 18, 6.5f);
                line(c, 10, 10.5f, 10, 16);
                line(c, 14, 10.5f, 14, 16);
                break;
            case PHOTOS:
                oval.set(3.5f, 3.5f, 20.5f, 20.5f);
                c.drawRoundRect(oval, 3.5f, 3.5f, stroke);
                c.drawCircle(9, 9, 1.6f, stroke);
                poly(c, 20.5f, 15, 15.5f, 10, 5, 20.5f);
                break;
            case FOLDER:
                path.reset();
                path.moveTo(3.5f, 6);
                path.lineTo(9.5f, 6);
                path.lineTo(11.5f, 8.5f);
                path.lineTo(20.5f, 8.5f);
                path.lineTo(20.5f, 18.5f);
                path.lineTo(3.5f, 18.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case FILE:
                path.reset();
                path.moveTo(6, 3);
                path.lineTo(14, 3);
                path.lineTo(19, 8);
                path.lineTo(19, 21);
                path.lineTo(6, 21);
                path.close();
                c.drawPath(path, stroke);
                poly(c, 14, 3, 14, 8, 19, 8);
                break;
            case CHECK:
                poly(c, 5, 12.5f, 10, 17.5f, 19, 7);
                break;
            case SHARE:
                poly(c, 4.5f, 12.5f, 4.5f, 20, 19.5f, 20, 19.5f, 12.5f);
                poly(c, 8, 7, 12, 3, 16, 7);
                line(c, 12, 3, 12, 15);
                break;
            case OPEN:
                poly(c, 18, 13.5f, 18, 19, 5, 19, 5, 6, 10.5f, 6);
                poly(c, 14, 4, 20, 4, 20, 10);
                line(c, 20, 4, 11, 13);
                break;
            case REVERSE:
                poly(c, 3.5f, 16, 7.5f, 20, 11.5f, 16);
                line(c, 7.5f, 20, 7.5f, 4);
                poly(c, 12.5f, 8, 16.5f, 4, 20.5f, 8);
                line(c, 16.5f, 4, 16.5f, 20);
                break;
            case SORT_NAME:
                line(c, 4, 6, 20, 6);
                line(c, 4, 12, 15, 12);
                line(c, 4, 18, 10, 18);
                break;
            case SORT_DATE:
                c.drawCircle(12, 12, 8.5f, stroke);
                poly(c, 12, 7.5f, 12, 12, 15, 14);
                break;
            case ALERT:
                c.drawCircle(12, 12, 8.5f, stroke);
                line(c, 12, 8, 12, 12.5f);
                c.drawCircle(12, 16, 1.1f, fill);
                break;
            default:
                break;
        }
        c.restore();
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void poly(Canvas c, float... p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        c.drawPath(path, stroke);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
