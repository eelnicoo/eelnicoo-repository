package dev.nico.pulso;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import moe.shizuku.api.BinderContainer;

/**
 * Recibe el binder de Shizuku. Mismo contrato que rikka.shizuku.ShizukuProvider:
 * autoridad «paquete.shizuku», exportado, no multiproceso y protegido con
 * INTERACT_ACROSS_USERS_FULL (solo el sistema y Shizuku pueden llamarlo).
 */
public class ShizukuProvider extends ContentProvider {
    private static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (extras == null) return null;
        extras.setClassLoader(BinderContainer.class.getClassLoader());
        if ("sendBinder".equals(method)) {
            BinderContainer c = extras.getParcelable(EXTRA_BINDER);
            if (c != null && c.binder != null && getContext() != null) {
                Shizuku.onBinderReceived(c.binder, getContext().getPackageName());
            }
        }
        return new Bundle();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
