package io.github.nicoenhance;

import android.net.Uri;

/**
 * Single source of truth for module-wide string constants shared between the injected Xposed
 * module, the module UI (MainActivity), and the config ContentProvider. Consolidating them here
 * stops the authority / prefs name / config keys / package names from drifting apart across
 * files — previously a rename had to be repeated in three or four places and missing one would
 * silently break the module<->UI config bridge.
 *
 * <p>All fields are package-private: these are an implementation detail of this module, not a
 * public API surface.</p>
 */
final class ModuleConstants {

    private ModuleConstants() {
        // utility class, not meant to be instantiated
    }

    /** niconico package that this module injects into. */
    static final String TARGET_PACKAGE = "jp.nicovideo.android";

    /**
     * The module's own applicationId. Must stay in the {@code io.github.<user>.<app>} form so
     * LSPosed accepts the module's repository ownership for scope approval.
     */
    static final String MODULE_PACKAGE = "io.github.kotobawakusei.nicoenhance";

    /** ContentProvider authority pair wire-mounting the injected module to the module UI. */
    static final String CONFIG_AUTHORITY = "io.github.kotobawakusei.nicoenhance.config";
    static final Uri CONFIG_URI = Uri.parse("content://" + CONFIG_AUTHORITY);

    /** SharedPreferences file backing the module config. */
    static final String PREFS_NAME = "nicoenhance_config";

    /** Marker the provider sets once the config file has been created from a save. */
    static final String KEY_CONFIG_INITIALIZED = "config_initialized";

    /** Epoch millis recorded by the target process via {@code markActive} after it injects. */
    static final String KEY_LAST_TARGET_HOOK_TS = "last_target_hook_ts";

    static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    static final String KEY_RUNTIME_TEXT_TRANSLATION_ENABLED = "runtime_text_translation_enabled";
    static final String KEY_WEBVIEW_TRANSLATION_ENABLED = "webview_translation_enabled";
    static final String KEY_AD_REMOVAL_ENABLED = "ad_removal_enabled";
    static final String KEY_DEBUG_LOG_ENABLED = "debug_log_enabled";
    static final String KEY_PREMIUM_UNLOCK_ENABLED = "premium_unlock_enabled";

    /** Feature keys in the same order as {@link #CONFIG_DEFAULTS}; parallel-by-design. */
    static final String[] CONFIG_KEYS = {
            KEY_TRANSLATION_ENABLED,
            KEY_RUNTIME_TEXT_TRANSLATION_ENABLED,
            KEY_WEBVIEW_TRANSLATION_ENABLED,
            KEY_AD_REMOVAL_ENABLED,
            KEY_DEBUG_LOG_ENABLED,
            KEY_PREMIUM_UNLOCK_ENABLED,
    };

    /** Defaults matching {@link #CONFIG_KEYS} index-for-index. */
    static final boolean[] CONFIG_DEFAULTS = {true, true, true, true, false, true};
}