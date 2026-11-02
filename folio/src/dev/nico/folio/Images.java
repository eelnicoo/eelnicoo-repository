package dev.nico.folio;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Size;

import java.io.IOException;

/** Lectura de imágenes (respeta la orientación EXIF) y sus datos. */
final class Images {
    private Images() {}

    /**
     * Decodifica la imagen reduciéndola por potencias de 2 sin bajar de {@code minSide}
     * en su lado mayor. El resultado puede medir hasta el doble.
     */
    static Bitmap decode(ContentResolver cr, Uri uri, final int minSide) throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(cr, uri);
        return ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                Size sz = info.getSize();
                int m = Math.max(sz.getWidth(), sz.getHeight());
                int sample = 1;
                while (m / (sample * 2) >= minSide) sample *= 2;
                if (sample > 1) d.setTargetSampleSize(sample);
            }
        });
    }

    /** Miniatura con el lado mayor de, como mucho, {@code maxSide} píxeles. */
    static Bitmap thumbnail(ContentResolver cr, Uri uri, int maxSide) throws IOException {
        Bitmap b = decode(cr, uri, maxSide);
        int m = Math.max(b.getWidth(), b.getHeight());
        if (m <= maxSide) return b;
        float s = maxSide / (float) m;
        Bitmap scaled = Bitmap.createScaledBitmap(b,
                Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
        if (scaled != b) b.recycle();
        return scaled;
    }

    /**
     * Prepara una página: la escala para que su lado mayor no pase de {@code maxSide},
     * la gira y la pone sobre fondo blanco (para las imágenes con transparencia).
     */
    static Bitmap prepare(Bitmap src, int rotation, int maxSide) {
        int sw = src.getWidth(), sh = src.getHeight();
        float scale = Math.min(1f, maxSide / (float) Math.max(sw, sh));
        int w = Math.max(1, Math.round(sw * scale)), h = Math.max(1, Math.round(sh * scale));
        boolean swap = rotation == 90 || rotation == 270;
        int ow = swap ? h : w, oh = swap ? w : h;
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        Matrix m = new Matrix();
        m.postScale(w / (float) sw, h / (float) sh);
        m.postTranslate(-w / 2f, -h / 2f);
        m.postRotate(rotation);
        m.postTranslate(ow / 2f, oh / 2f);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG));
        return out;
    }

    /** Rellena el nombre y la fecha de la imagen si el proveedor los da. */
    static void readMeta(ContentResolver cr, Page p) {
        Cursor c = null;
        try {
            c = cr.query(p.uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !c.isNull(i)) p.name = c.getString(i);
                i = c.getColumnIndex("datetaken");
                if (i >= 0 && !c.isNull(i)) p.date = c.getLong(i);
                if (p.date <= 0) {
                    i = c.getColumnIndex("last_modified");
                    if (i >= 0 && !c.isNull(i)) p.date = c.getLong(i);
                }
                if (p.date <= 0) {
                    i = c.getColumnIndex("date_modified");
                    if (i >= 0 && !c.isNull(i)) p.date = c.getLong(i) * 1000L;
                }
            }
        } catch (Exception ignored) {
            // Algunos proveedores no dejan consultar; nos quedamos sin nombre ni fecha.
        } finally {
            if (c != null) c.close();
        }
        if (p.name == null) {
            String last = p.uri.getLastPathSegment();
            p.name = last != null ? last : "imagen";
        }
    }

    /** Compara nombres teniendo en cuenta los números («foto 2» va antes que «foto 10»). */
    static int naturalCompare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int i = 0, j = 0, na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (si < na && a.charAt(si) == '0') si++;
                while (sj < nb && b.charAt(sj) == '0') sj++;
                int ei = si, ej = sj;
                while (ei < na && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < nb && Character.isDigit(b.charAt(ej))) ej++;
                int la = ei - si, lb = ej - sj;
                if (la != lb) return la - lb;
                for (int k = 0; k < la; k++) {
                    int d = a.charAt(si + k) - b.charAt(sj + k);
                    if (d != 0) return d;
                }
                i = ei;
                j = ej;
            } else {
                int d = Character.toLowerCase(ca) - Character.toLowerCase(cb);
                if (d != 0) return d;
                i++;
                j++;
            }
        }
        return (na - i) - (nb - j);
    }
}
