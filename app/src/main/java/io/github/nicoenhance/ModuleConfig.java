package io.github.nicoenhance;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class ModuleConfig {
    static final String PREFS_NAME = "nicoenhance_config";
    private static final long REFRESH_THROTTLE_MS = 500L;
    private static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    private static final String KEY_RUNTIME_TEXT_TRANSLATION_ENABLED = "runtime_text_translation_enabled";
    private static final String KEY_WEBVIEW_TRANSLATION_ENABLED = "webview_translation_enabled";
    private static final String KEY_AD_REMOVAL_ENABLED = "ad_removal_enabled";
    private static final String KEY_DEBUG_LOG_ENABLED = "debug_log_enabled";
    private static final String KEY_PREMIUM_UNLOCK_ENABLED = "premium_unlock_enabled";

    private volatile boolean loaded;
    private volatile long lastRefreshElapsed;
    private volatile boolean translationEnabled = true;
    private volatile boolean runtimeTextTranslationEnabled = true;
    private volatile boolean webViewTranslationEnabled = true;
    private volatile boolean adRemovalEnabled = true;
    private volatile boolean debugLogEnabled;
    private volatile boolean premiumUnlockEnabled = true;

    void refresh(Context context) {
        if (context == null) return;
        try {
            Bundle values = context.getContentResolver().call(
                    ConfigProvider.CONTENT_URI, "get_config", null, null);
            if (values != null) {
                if (!values.getBoolean("initialized", false)) {
                    // One-time migration from the old target-app private preferences.
                    SharedPreferences legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    Bundle migrated = new Bundle();
                    migrated.putBoolean(KEY_TRANSLATION_ENABLED, legacy.getBoolean(KEY_TRANSLATION_ENABLED, true));
                    migrated.putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, legacy.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true));
                    migrated.putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, legacy.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true));
                    migrated.putBoolean(KEY_AD_REMOVAL_ENABLED, legacy.getBoolean(KEY_AD_REMOVAL_ENABLED, true));
                    migrated.putBoolean(KEY_DEBUG_LOG_ENABLED, legacy.getBoolean(KEY_DEBUG_LOG_ENABLED, false));
                    migrated.putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, legacy.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true));
                    context.getContentResolver().call(ConfigProvider.CONTENT_URI, "save_config", null, migrated);
                    values = context.getContentResolver().call(ConfigProvider.CONTENT_URI, "get_config", null, null);
                }
                apply(values);
                return;
            }
        } catch (Throwable ignored) {
            // Keep the old in-target-app behavior as a compatibility fallback if the provider
            // cannot be reached (e.g. an older APK is installed).
        }
        refreshLegacy(context);
    }

    private void apply(Bundle b) {
        if (b == null) return;
        translationEnabled = b.getBoolean(KEY_TRANSLATION_ENABLED, true);
        runtimeTextTranslationEnabled = b.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true);
        webViewTranslationEnabled = b.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true);
        adRemovalEnabled = b.getBoolean(KEY_AD_REMOVAL_ENABLED, true);
        debugLogEnabled = b.getBoolean(KEY_DEBUG_LOG_ENABLED, false);
        premiumUnlockEnabled = b.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true);
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
        loaded = true;
    }

    private void refreshLegacy(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        translationEnabled = p.getBoolean(KEY_TRANSLATION_ENABLED, true);
        runtimeTextTranslationEnabled = p.getBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, true);
        webViewTranslationEnabled = p.getBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, true);
        adRemovalEnabled = p.getBoolean(KEY_AD_REMOVAL_ENABLED, true);
        debugLogEnabled = p.getBoolean(KEY_DEBUG_LOG_ENABLED, false);
        premiumUnlockEnabled = p.getBoolean(KEY_PREMIUM_UNLOCK_ENABLED, true);
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
        loaded = true;
    }

    void refreshThrottled(Context context) {
        if (loaded && android.os.SystemClock.elapsedRealtime() - lastRefreshElapsed < REFRESH_THROTTLE_MS) return;
        refresh(context);
    }

    void save(Context context, boolean translationEnabled, boolean runtimeTextTranslationEnabled,
              boolean webViewTranslationEnabled, boolean adRemovalEnabled,
              boolean debugLogEnabled, boolean premiumUnlockEnabled) {
        Bundle values = new Bundle();
        values.putBoolean(KEY_TRANSLATION_ENABLED, translationEnabled);
        values.putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled);
        values.putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled);
        values.putBoolean(KEY_AD_REMOVAL_ENABLED, adRemovalEnabled);
        values.putBoolean(KEY_DEBUG_LOG_ENABLED, debugLogEnabled);
        values.putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled);
        boolean saved = false;
        try {
            Bundle result = context.getContentResolver().call(ConfigProvider.CONTENT_URI, "save_config", null, values);
            saved = result != null && result.getBoolean("success", false);
        } catch (Throwable ignored) {}
        if (!saved) {
            SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            p.edit().putBoolean(KEY_TRANSLATION_ENABLED, translationEnabled)
                    .putBoolean(KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled)
                    .putBoolean(KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled)
                    .putBoolean(KEY_AD_REMOVAL_ENABLED, adRemovalEnabled)
                    .putBoolean(KEY_DEBUG_LOG_ENABLED, debugLogEnabled)
                    .putBoolean(KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled).apply();
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
}
