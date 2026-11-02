package dev.nico.folio;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Crea el PDF en segundo plano y lo guarda en Descargas/Folio. */
final class Exporter implements Runnable {
    static final String FOLDER = "Folio";

    interface Callback {
        void onProgress(int done, int total);

        void onDone(Uri uri, String fileName, long bytes, int skipped);

        void onError(String message);
    }

    /** {lado mayor en píxeles, calidad JPEG} para Alta, Media y Ligera. */
    static final int[][] QUALITY = {{3200, 92}, {2200, 85}, {1500, 72}};

    private final ContentResolver cr;
    private final Page[] pages;
    private final int[] rotations;
    private final String name;
    private final int size;
    private final boolean margin;
    private final int quality;
    private final Callback cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    Exporter(ContentResolver cr, List<Page> pages, String name, int size, boolean margin, int quality, Callback cb) {
        this.cr = cr;
        this.pages = pages.toArray(new Page[0]);
        this.rotations = new int[this.pages.length];
        for (int i = 0; i < this.pages.length; i++) rotations[i] = this.pages[i].rotation;
        this.name = name;
        this.size = size;
        this.margin = margin;
        this.quality = Math.max(0, Math.min(QUALITY.length - 1, quality));
        this.cb = cb;
    }

    void cancel() {
        cancelled = true;
    }

    @Override
    public void run() {
        Uri uri = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".pdf");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("No se pudo crear el archivo en Descargas.");

            int maxSide = QUALITY[quality][0], jpegQuality = QUALITY[quality][1];
            int skipped = 0;
            long bytes;
            OutputStream os = cr.openOutputStream(uri, "w");
            if (os == null) throw new IOException("No se pudo escribir el archivo.");
            try {
                PdfBuilder pdf = new PdfBuilder(os);
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream(1 << 20);
                for (int i = 0; i < pages.length; i++) {
                    if (cancelled) break;
                    progress(i, pages.length);
                    Bitmap page = null;
                    try {
                        page = render(pages[i], rotations[i], maxSide);
                    } catch (OutOfMemoryError e) {
                        System.gc();
                        try {
                            page = render(pages[i], rotations[i], maxSide / 2);
                        } catch (Throwable t) {
                            page = null;
                        }
                    } catch (Exception e) {
                        page = null;
                    }
                    if (page == null) {
                        skipped++;
                        continue;
                    }
                    jpeg.reset();
                    page.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpeg);
                    int w = page.getWidth(), h = page.getHeight();
                    page.recycle();
                    pdf.addJpegPage(jpeg.toByteArray(), w, h, PdfBuilder.layout(size, margin, w, h));
                }
                if (!cancelled) {
                    if (pdf.pageCount() == 0) throw new IOException("No se pudo leer ninguna imagen.");
                    progress(pages.length, pages.length);
                    pdf.finish(name);
                }
                bytes = pdf.bytesWritten();
            } finally {
                os.close();
            }

            if (cancelled) {
                cr.delete(uri, null, null);
                return;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            final Uri result = uri;
            final String fileName = displayName(uri, name + ".pdf");
            final long size = bytes;
            final int skip = skipped;
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onDone(result, fileName, size, skip);
                }
            });
        } catch (final Throwable t) {
            if (uri != null) {
                try {
                    cr.delete(uri, null, null);
                } catch (Exception ignored) {
                    // Nada más que hacer.
                }
            }
            if (cancelled) return;
            final String msg = t instanceof OutOfMemoryError
                    ? "Las imágenes son demasiado grandes. Prueba con la calidad «Ligera»."
                    : (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onError(msg);
                }
            });
        }
    }

    private Bitmap render(Page p, int rotation, int maxSide) throws IOException {
        Bitmap src = Images.decode(cr, p.uri, maxSide);
        try {
            return Images.prepare(src, rotation, maxSide);
        } finally {
            src.recycle();
        }
    }

    private void progress(final int done, final int total) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!cancelled) cb.onProgress(done, total);
            }
        });
    }

    private String displayName(Uri uri, String fallback) {
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Exception ignored) {
            // Usamos el nombre que pedimos.
        } finally {
            if (c != null) c.close();
        }
        return fallback;
    }
}
