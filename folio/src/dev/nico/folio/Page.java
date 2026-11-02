package dev.nico.folio;

import android.graphics.Bitmap;
import android.net.Uri;

/** Una imagen de la lista: será una página del PDF. */
final class Page {
    final Uri uri;
    String name;
    long date;
    int rotation;
    Bitmap thumb;
    boolean failed;

    Page(Uri uri) {
        this.uri = uri;
    }
}
