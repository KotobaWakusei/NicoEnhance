package io.github.nicoenhance;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import static io.github.nicoenhance.ModuleConstants.CONFIG_URI;

/**
 * Snapshot of the module's feature toggles as seen by the injected niconico process.
 *
 * <p>The authoritative store is {@link ConfigProvider} (read through
 * {@code ContentResolver.call}); SharedPreferences is only the local fallback plus the carrier
 * for the one-shot legacy migration path. Fields are {@code volatile} because the UI dialog
 * writes them while resource/ad hooks read them from other threads.</p>
 */
final class ModuleConfig {

    /** Minimum interval between provider reads on hot paths (ad hooks, view tree). */
    private static final long REFRESH_THROTTLE_MS = 500L;

    private volatile boolean loaded;
    private volatile long lastRefreshElapsed;
    private volatile boolean translationEnabled = ModuleConstants.CONFIG_DEFAULTS[0];
    private volatile boolean runtimeTextTranslationEnabled = ModuleConstants.CONFIG_DEFAULTS[1];
    private volatile boolean webViewTranslationEnabled = ModuleConstants.CONFIG_DEFAULTS[2];
    private volatile boolean adRemovalEnabled = ModuleConstants.CONFIG_DEFAULTS[3];
    private volatile boolean debugLogEnabled = ModuleConstants.CONFIG_DEFAULTS[4];
    private volatile boolean premiumUnlockEnabled = ModuleConstants.CONFIG_DEFAULTS[5];

    void refresh(Context context) {
        Bundle remote = callProvider(context, "getConfig", null);
        SharedPreferences prefs = getPreferences(context);
        if (remote != null
                && !remote.getBoolean(ModuleConstants.KEY_CONFIG_INITIALIZED, false)
                && hasLegacyConfig(prefs)) {
            Bundle legacy = new Bundle();
            legacy.putBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[0]));
            legacy.putBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[1]));
            legacy.putBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[2]));
            legacy.putBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, ModuleConstants.CONFIG_DEFAULTS[3]));
            legacy.putBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, ModuleConstants.CONFIG_DEFAULTS[4]));
            legacy.putBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED,
                    prefs.getBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, ModuleConstants.CONFIG_DEFAULTS[5]));
            Bundle migrated = callProvider(context, "saveConfig", legacy);
            if (migrated != null && migrated.getBoolean("ok", false)) {
                remote = callProvider(context, "getConfig", null);
            }
        }
        if (remote != null) {
            applyFromBundle(remote);
            markRefreshed();
            return;
        }
        if (prefs == null) return;
        translationEnabled = prefs.getBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[0]);
        runtimeTextTranslationEnabled = prefs.getBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[1]);
        webViewTranslationEnabled = prefs.getBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[2]);
        adRemovalEnabled = prefs.getBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, ModuleConstants.CONFIG_DEFAULTS[3]);
        debugLogEnabled = prefs.getBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, ModuleConstants.CONFIG_DEFAULTS[4]);
        premiumUnlockEnabled = prefs.getBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, ModuleConstants.CONFIG_DEFAULTS[5]);
        markRefreshed();
    }

    private void applyFromBundle(Bundle remote) {
        translationEnabled = remote.getBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[0]);
        runtimeTextTranslationEnabled = remote.getBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[1]);
        webViewTranslationEnabled = remote.getBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[2]);
        adRemovalEnabled = remote.getBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, ModuleConstants.CONFIG_DEFAULTS[3]);
        debugLogEnabled = remote.getBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, ModuleConstants.CONFIG_DEFAULTS[4]);
        premiumUnlockEnabled = remote.getBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, ModuleConstants.CONFIG_DEFAULTS[5]);
    }

    private void markRefreshed() {
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime();
        loaded = true;
    }

    private boolean hasLegacyConfig(SharedPreferences prefs) {
        if (prefs == null) return false;
        for (String key : ModuleConstants.CONFIG_KEYS) {
            if (prefs.contains(key)) return true;
        }
        return false;
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
     * Throttled variant of {@link #refresh(Context)} for hot paths that would otherwise hit the
     * provider on every call (e.g. ad-view hooks fire many times per second). A user toggling an
     * option still takes effect within {@link #REFRESH_THROTTLE_MS}.
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
        values.putBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, translationEnabled);
        values.putBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled);
        values.putBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled);
        values.putBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, adRemovalEnabled);
        values.putBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, debugLogEnabled);
        values.putBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled);
        Bundle result = callProvider(context, "saveConfig", values);
        if (result == null || !result.getBoolean("ok", false)) {
            SharedPreferences prefs = getPreferences(context);
            if (prefs != null) {
                prefs.edit()
                        .putBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, translationEnabled)
                        .putBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, runtimeTextTranslationEnabled)
                        .putBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, webViewTranslationEnabled)
                        .putBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, adRemovalEnabled)
                        .putBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, debugLogEnabled)
                        .putBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, premiumUnlockEnabled)
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

    /**
     * One-shot migration of pre-provider SharedPreferences values into the provider store.
     * Called by NicoEnhance#ensureConfigLoaded before the lazy config snapshot is taken.
     * Returns true when values were migrated (the caller then re-reads the snapshot).
     */
    boolean migrateLegacyIfNeeded(Context context) {
        Bundle remote = callProvider(context, "getConfig", null);
        if (remote == null) return false;
        if (remote.getBoolean(ModuleConstants.KEY_CONFIG_INITIALIZED, false)) return false;
        SharedPreferences prefs = getPreferences(context);
        if (!hasLegacyConfig(prefs)) return false;
        Bundle legacy = new Bundle();
        legacy.putBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[0]));
        legacy.putBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_RUNTIME_TEXT_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[1]));
        legacy.putBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_WEBVIEW_TRANSLATION_ENABLED, ModuleConstants.CONFIG_DEFAULTS[2]));
        legacy.putBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_AD_REMOVAL_ENABLED, ModuleConstants.CONFIG_DEFAULTS[3]));
        legacy.putBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_DEBUG_LOG_ENABLED, ModuleConstants.CONFIG_DEFAULTS[4]));
        legacy.putBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED,
                prefs.getBoolean(ModuleConstants.KEY_PREMIUM_UNLOCK_ENABLED, ModuleConstants.CONFIG_DEFAULTS[5]));
        Bundle migrated = callProvider(context, "saveConfig", legacy);
        return migrated != null && migrated.getBoolean("ok", false);
    }

    /** Shallow copy, so a requesting hook family can refresh without mutating other readers. */
    ModuleConfig copy() {
        ModuleConfig c = new ModuleConfig();
        c.loaded = loaded;
        c.lastRefreshElapsed = lastRefreshElapsed;
        c.translationEnabled = translationEnabled;
        c.runtimeTextTranslationEnabled = runtimeTextTranslationEnabled;
        c.webViewTranslationEnabled = webViewTranslationEnabled;
        c.adRemovalEnabled = adRemovalEnabled;
        c.debugLogEnabled = debugLogEnabled;
        c.premiumUnlockEnabled = premiumUnlockEnabled;
        return c;
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
        return owner.getSharedPreferences(ModuleConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
