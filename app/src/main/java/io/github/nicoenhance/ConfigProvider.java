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
    public static final String AUTHORITY = ModuleConstants.CONFIG_AUTHORITY;
    public static final Uri CONTENT_URI = ModuleConstants.CONFIG_URI;

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isAllowedCaller()) throw new SecurityException("Caller is not authorized for NicoEnhance config");
        Context context = getContext();
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(ModuleConstants.PREFS_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        if ("getConfig".equals(method)) {
            for (int i = 0; i < ModuleConstants.CONFIG_KEYS.length; i++) result.putBoolean(ModuleConstants.CONFIG_KEYS[i], prefs.getBoolean(ModuleConstants.CONFIG_KEYS[i], ModuleConstants.CONFIG_DEFAULTS[i]));
            result.putBoolean(ModuleConstants.KEY_CONFIG_INITIALIZED, prefs.getBoolean(ModuleConstants.KEY_CONFIG_INITIALIZED, false));
            return result;
        }
        if ("saveConfig".equals(method)) {
            if (extras == null) return result;
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < ModuleConstants.CONFIG_KEYS.length; i++) {
                editor.putBoolean(ModuleConstants.CONFIG_KEYS[i], extras.getBoolean(ModuleConstants.CONFIG_KEYS[i], prefs.getBoolean(ModuleConstants.CONFIG_KEYS[i], ModuleConstants.CONFIG_DEFAULTS[i])));
            }
            editor.putBoolean(ModuleConstants.KEY_CONFIG_INITIALIZED, true);
            editor.apply();
            result.putBoolean("ok", true);
            return result;
        }
        if ("markActive".equals(method)) {
            // The module UI shares this provider UID, but must never be able to report a
            // successful target-process injection itself. Only niconico may refresh this signal.
            if (!isTargetCaller()) {
                throw new SecurityException("Only niconico may report target hook activity");
            }
            prefs.edit().putLong(ModuleConstants.KEY_LAST_TARGET_HOOK_TS, System.currentTimeMillis()).apply();
            result.putBoolean("ok", true);
            return result;
        }
        return result;
    }

    private boolean isTargetCaller() {
        Context context = getContext();
        if (context == null) return false;
        String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if (ModuleConstants.TARGET_PACKAGE.equals(packageName)) return true;
        }
        return false;
    }

    private boolean isAllowedCaller() {
        if (Binder.getCallingUid() == Process.myUid()) return true;
        Context context = getContext();
        if (context == null) return false;
        String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) if (ModuleConstants.TARGET_PACKAGE.equals(packageName)) return true;
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
