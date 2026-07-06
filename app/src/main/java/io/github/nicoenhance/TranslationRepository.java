package io.github.nicoenhance;

import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TranslationRepository {
    private static final String TAG = "NicoEnhance";
    private static final String TARGET_PACKAGE = "jp.nicovideo.android";
    private static final String STRING_TYPE = "string";
    private static final String PLURALS_TYPE = "plurals";
    private static final String ARRAY_TYPE = "array";
    private static final String STRING_ARRAY_TYPE = "string-array";

    private static final String STRINGS_PATH = "assets/translations/zh-CN/strings.properties";
    private static final String EXACT_PATH = "assets/translations/zh-CN/exact.properties";
    private static final String PHRASES_PATH = "assets/translations/zh-CN/phrases.properties";

    private final StringTranslations strings;
    private final StringTranslations exact;
    private final StringTranslations phrases;

    private final Map<Integer, Optional<String>> stringCache = new ConcurrentHashMap<>();
    private final Map<Integer, Optional<String>> pluralCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> exactCache = new ConcurrentHashMap<>();

    public TranslationRepository(StringTranslations strings, StringTranslations exact, StringTranslations phrases) {
        this.strings = strings;
        this.exact = exact;
        this.phrases = phrases;
    }

    public static TranslationRepository fromModuleApk(String apkPath) {
        Log.i(TAG, "Loading translations from: " + apkPath);
        try (ZipFile zip = new ZipFile(apkPath)) {
            StringTranslations s = loadAsset(zip, STRINGS_PATH);
            StringTranslations e = loadAsset(zip, EXACT_PATH);
            StringTranslations p = loadAsset(zip, PHRASES_PATH);
            Log.i(TAG, "Loaded: " + s.size() + " strings, " + e.size() + " exact, " + p.size() + " phrases");
            return new TranslationRepository(s, e, p);
        } catch (IOException ex) {
            Log.e(TAG, "Failed to load translations", ex);
            return new TranslationRepository(
                StringTranslations.empty(), StringTranslations.empty(), StringTranslations.empty());
        }
    }

    private static StringTranslations loadAsset(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            Log.w(TAG, "Asset not found: " + path);
            return StringTranslations.empty();
        }
        try (InputStream in = zip.getInputStream(entry);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return StringTranslations.load(r);
        }
    }

    public String findString(Resources res, int id) {
        return stringCache.computeIfAbsent(id, key -> {
            try {
                if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) return Optional.empty();
                String type = res.getResourceTypeName(id);
                if (!STRING_TYPE.equals(type)) return Optional.empty();
                String name = res.getResourceEntryName(id);
                String t = strings.get(name);
                if (t == null) {
                    Log.w(TAG, "MISS: " + name);
                }
                return Optional.ofNullable(t);
            } catch (Throwable t) {
                Log.w(TAG, "findString error", t);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public String findQuantityString(Resources res, int id) {
        return pluralCache.computeIfAbsent(id, key -> {
            try {
                if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) return Optional.empty();
                String type = res.getResourceTypeName(id);
                if (!PLURALS_TYPE.equals(type)) return Optional.empty();
                return Optional.ofNullable(strings.get("plurals." + res.getResourceEntryName(id)));
            } catch (Throwable t) {
                Log.w(TAG, "findQuantityString error", t);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public String findArrayItem(Resources res, int id, int index) {
        try {
            if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) return null;
            String type = res.getResourceTypeName(id);
            if (!ARRAY_TYPE.equals(type) && !STRING_ARRAY_TYPE.equals(type)) return null;
            return strings.get("array." + res.getResourceEntryName(id) + "." + index);
        } catch (Throwable t) {
            Log.w(TAG, "findArrayItem error", t);
            return null;
        }
    }

    public String findExactText(CharSequence source) {
        if (source == null) return null;
        String text = source.toString();
        return exactCache.computeIfAbsent(text, key -> {
            String result = exact.get(key);
            if (result != null) return Optional.of(result);
            if (!containsJapanese(key)) return Optional.empty();
            return Optional.ofNullable(phrases.replacePhrases(key));
        }).orElse(null);
    }

    public String translateText(String source) {
        if (source == null) return null;
        String result = exact.get(source);
        if (result != null) return result;
        if (!containsJapanese(source)) return null;
        return phrases.replacePhrases(source);
    }

    public String format(String template, Object[] args) {
        return strings.format(template, args);
    }

    public static boolean containsJapanese(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            // Hiragana, Katakana, CJK Unified Ideographs, CJK Compatibility Ideographs,
            // CJK Extension A/B, CJK Symbols and Punctuation, Fullwidth forms
            if ((cp >= 0x3040 && cp <= 0x30ff) ||
                (cp >= 0x31f0 && cp <= 0x31ff) ||
                (cp >= 0x3400 && cp <= 0x4dbf) ||
                (cp >= 0x4e00 && cp <= 0x9fff) ||
                (cp >= 0xf900 && cp <= 0xfaff) ||
                (cp >= 0x20000 && cp <= 0x2ebef) ||
                (cp >= 0x3000 && cp <= 0x303f) ||
                (cp >= 0xff00 && cp <= 0xffef))
                return true;
            i += Character.charCount(cp);
        }
        return false;
    }
}
