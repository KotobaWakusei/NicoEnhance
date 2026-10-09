package io.github.nicoenhance;

import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.content.SharedPreferences;

final class ModuleConfig {
    static final String PREFS_NAME = "nicoenhance_config";
    /** Minimum interval between SharedPreferences reads on hot paths (ad hooks, view tree). */
    private static final long REFRESH_THROTTLE_MS = 500L;
    private static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    private static final String KEY_RUNTIME_TEXT_TRANSLATION_ENABLED = "runtime_text_translation_enabled";
    private static final String KEY_WEBVIEW_TRANSLATION_ENABLED = "webview_translation_enabled";
    private static final String KEY_AD_REMOVAL_ENABLED = "ad_removal_enabled";
    private static final String KEY_DEBUG_LOG_ENABLED = "debug_log_enabled";
    private static final String KEY_PREMIUM_UNLOCK_ENABLED = "premium_unlock_enabled";
    private static final Uri CONFIG_URI = Uri.parse("content://io.github.kotobawakusei.nicoenhance.config");

    private volatile boolean loaded;
    private volatile long lastRefreshElapsed;
    private volatile boolean translationEnabled = true;
    private volatile boolean runtimeTextTranslationEnabled = true;
    private volatile boolean webViewTranslationEnabled = true;
    private volatile boolean adRemovalEnabled = true;
    private volatile boolean debugLogEnabled;
    private volatile boolean premiumUnlockEnabled = true;

    void refresh(Context context) {
        Bundle remote = callProvider(context, "getConfig", null);
        SharedPreferences prefs = getPreferences(context);
        if (remote != null && !remote.getBoolean("config_initialized", true)
                && hasLegacyConfig(prefs)) {
            Bundle legacy = new Bundle();
            legacy.putBoolean(KEY_TRANSLATION_ENABLED, prefs.getBoolean(KEY_TRANSLATION_ENABLED, true));
            legacy.putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, prefs.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true));
            legacy.putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, prefs.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true));
            legacy.putBoolean(KEY_AD_REMOVAL_ENABLED, prefs.getBoolean(KEY_AD_REMOVAL_ENABLED, true));
            legacy.putBoolean(KEY_DEBUG_LOG_ENABLED, prefs.getBoolean(KEY_DEBUG_LOG_ENABLED, false));
            legacy.putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, prefs.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true));
            Bundle migrated = callProvider(context, "saveConfig", legacy);
            if (migrated != null && migrated.getBoolean("ok", false)) {
                remote = callProvider(context, "getConfig", null);
            }
        }
        if (remote != null) {
            translationEnabled = remote.getBoolean(KEY_TRANSLATION_ENABLED, true);
            runtimeTextTranslationEnabled = remote.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true);
            webViewTranslationEnabled = remote.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true);
            adRemovalEnabled = remote.getBoolean(KEY_AD_REMOVAL_ENABLED, true);
            debugLogEnabled = remote.getBoolean(KEY_DEBUG_LOG_ENABLED, false);
            premiumUnlockEnabled = remote.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true);
            lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
            loaded = true;
            return;
        }
        if (prefs == null) return;
        translationEnabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, true);
        runtimeTextTranslationEnabled = prefs.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true);
        webViewTranslationEnabled = prefs.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true);
        adRemovalEnabled = prefs.getBoolean(KEY_AD_REMOVAL_ENABLED, true);
        debugLogEnabled = prefs.getBoolean(KEY_DEBUG_LOG_ENABLED, false);
        premiumUnlockEnabled = prefs.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true);
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
        loaded = true;
    }

    private boolean hasLegacyConfig(SharedPreferences prefs) {
        return prefs != null && (prefs.contains(KEY_TRANSLATION_ENABLED)
                || prefs.contains(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED)
                || prefs.contains(KEY_WEBVIEW_TRANSLATION_ENABLED)
                || prefs.contains(KEY_AD_REMOVAL_ENABLED)
                || prefs.contains(KEY_DEBUG_LOG_ENABLED)
                || prefs.contains(KEY_PREMIUM_UNLOCK_ENABLED));
    }

    private Bundle callProvider(Context context, String method, Bundle extras) {
        if (context == null) return null;
        try {
            ContentResolver resolver = context.getContentResolver();
            return resolver.call(CONFIG_URI, method, null, extras);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Throttled variant of {@link #refresh(Context)} for hot paths that would otherwise hit
     * SharedPreferences on every call (e.g. ad-view hooks fire many times per second). A user
     * toggling an option in the config dialog still takes effect within {@link #REFRESH_THROTTLE_MS}.
     */
    void refreshThrottled(Context context) {
        if (loaded && android.os.SystemClock.elapsedRealtime() - lastRefreshElapsed < REFRESH_THROTTLE_MS) {
            return;
        }
        refresh(context);
    }

    void save(
            Context context,
            boolean translationEnabled,
            boolean runtimeTextTranslationEnabled,
            boolean webViewTranslationEnabled,
            boolean adRemovalEnabled,
            boolean debugLogEnabled,
            boolean premiumUnlockEnabled
    ) {
        Bundle values = new Bundle();
        values.putBoolean(KEY_TRANSLATION_ENABLED, translationEnabled);
        values.putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled);
        values.putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled);
        values.putBoolean(KEY_AD_REMOVAL_ENABLED, adRemovalEnabled);
        values.putBoolean(KEY_DEBUG_LOG_ENABLED, debugLogEnabled);
        values.putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled);
        Bundle result = callProvider(context, "saveConfig", values);
        if (result == null || !result.getBoolean("ok", false)) {
            SharedPreferences prefs = getPreferences(context);
            if (prefs != null) {
                prefs.edit()
                        .putBoolean(KEY_TRANSLATION_ENABLED, translationEnabled)
                        .putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled)
                        .putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled)
                        .putBoolean(KEY_AD_REMOVAL_ENABLED, adRemovalEnabled)
                        .putBoolean(KEY_DEBUG_LOG_ENABLED, debugLogEnabled)
                        .putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled)
                        .apply();
            }
        }
        this.translationEnabled = translationEnabled;
        this.runtimeTextTranslationEnabled = runtimeTextTranslationEnabled;
        this.webViewTranslationEnabled = webViewTranslationEnabled;
        this.adRemovalEnabled = adRemovalEnabled;
        this.debugLogEnabled = debugLogEnabled;
        this.premiumUnlockEnabled = premiumUnlockEnabled;
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
        loaded = true;
    }

    boolean isTranslationEnabled() { return translationEnabled; }
    boolean isRuntimeTextTranslationEnabled() { return translationEnabled && runtimeTextTranslationEnabled; }
    boolean isRuntimeTextTranslationSwitchEnabled() { return runtimeTextTranslationEnabled; }
    boolean isWebViewTranslationEnabled() { return translationEnabled && webViewTranslationEnabled; }
    boolean isWebViewTranslationSwitchEnabled() { return webViewTranslationEnabled; }
    boolean isAdRemovalEnabled() { return adRemovalEnabled; }
    boolean isDebugLogEnabled() { return debugLogEnabled; }
    boolean isPremiumUnlockEnabled() { return premiumUnlockEnabled; }
    boolean isLoaded() { return loaded; }

    private SharedPreferences getPreferences(Context context) {
        if (context == null) return null;
        Context appContext = context.getApplicationContext();
        Context owner = appContext != null ? appContext : context;
        return owner.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
