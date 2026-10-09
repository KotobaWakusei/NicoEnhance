package io.github.nicoenhance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Cross-process settings bridge. NicoEnhance's module UI runs in this package, while hooks run
 * inside niconico under a different UID; SharedPreferences are not shared between those apps.
 */
public final class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.kotobawakusei.nicoenhance.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");
    private static final String PREFS = "nicoenhance_config";
    private static final String INITIALIZED = "config_initialized";
    private static final String MODULE_PACKAGE = "io.github.kotobawakusei.nicoenhance";
    private static final String TARGET_PACKAGE = "jp.nicovideo.android";
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "translation_enabled", "runtime_text_translation_enabled",
            "webview_translation_enabled", "ad_removal_enabled",
            "debug_log_enabled", "premium_unlock_enabled"));

    @Override public boolean onCreate() { return getContext() != null; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isAllowedCaller()) throw new SecurityException("Caller is not NicoEnhance or niconico");
        SharedPreferences p = getContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        if ("get_config".equals(method)) {
            Bundle out = new Bundle();
            out.putBoolean("initialized", p.getBoolean(INITIALIZED, false));
            out.putBoolean("translation_enabled", p.getBoolean("translation_enabled", true));
            out.putBoolean("runtime_text_translation_enabled", p.getBoolean("runtime_text_translation_enabled", true));
            out.putBoolean("webview_translation_enabled", p.getBoolean("webview_translation_enabled", true));
            out.putBoolean("ad_removal_enabled", p.getBoolean("ad_removal_enabled", true));
            out.putBoolean("debug_log_enabled", p.getBoolean("debug_log_enabled", false));
            out.putBoolean("premium_unlock_enabled", p.getBoolean("premium_unlock_enabled", true));
            return out;
        }
        if ("save_config".equals(method)) {
            if (extras == null) throw new IllegalArgumentException("Missing config values");
            SharedPreferences.Editor edit = p.edit();
            for (String key : KEYS) if (extras.containsKey(key)) edit.putBoolean(key, extras.getBoolean(key));
            edit.putBoolean(INITIALIZED, true).apply();
            Bundle out = new Bundle();
            out.putBoolean("success", true);
            return out;
        }
        throw new IllegalArgumentException("Unknown method: " + method);
    }

    private boolean isAllowedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages == null) return false;
        for (String pkg : packages) if (MODULE_PACKAGE.equals(pkg) || TARGET_PACKAGE.equals(pkg)) return true;
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.io.github.kotobawakusei.nicoenhance.config"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
