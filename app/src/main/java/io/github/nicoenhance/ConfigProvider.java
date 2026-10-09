package io.github.nicoenhance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.kotobawakusei.nicoenhance.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    private static final String PREFS_NAME = "nicoenhance_config";
    private static final String TARGET_PACKAGE = "jp.nicovideo.android";
    private static final String[] KEYS = {
            "translation_enabled", "runtime_text_translation_enabled",
            "webview_translation_enabled", "ad_removal_enabled",
            "debug_log_enabled", "premium_unlock_enabled"
    };
    private static final boolean[] DEFAULTS = {true, true, true, true, false, true};

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isAllowedCaller()) throw new SecurityException("Caller is not authorized for NicoEnhance config");
        Context context = getContext();
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        if ("getConfig".equals(method)) {
            for (int i = 0; i < KEYS.length; i++) result.putBoolean(KEYS[i], prefs.getBoolean(KEYS[i], DEFAULTS[i]));
            return result;
        }
        if ("saveConfig".equals(method)) {
            if (extras == null) return result;
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < KEYS.length; i++) {
                editor.putBoolean(KEYS[i], extras.getBoolean(KEYS[i], prefs.getBoolean(KEYS[i], DEFAULTS[i])));
            }
            editor.apply();
            result.putBoolean("ok", true);
            return result;
        }
        if ("markActive".equals(method)) {
            prefs.edit().putLong("last_target_hook_ts", System.currentTimeMillis()).apply();
            result.putBoolean("ok", true);
            return result;
        }
        return result;
    }

    private boolean isAllowedCaller() {
        if (Binder.getCallingUid() == Process.myUid()) return true;
        Context context = getContext();
        if (context == null) return false;
        String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) if (TARGET_PACKAGE.equals(packageName)) return true;
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Use call()");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Use call()"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Use call()"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Use call()"); }
}
