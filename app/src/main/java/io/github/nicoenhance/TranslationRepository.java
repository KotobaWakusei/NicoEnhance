package io.github.nicoenhance;

import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Map<Integer, String> stringCache = new ConcurrentHashMap<>();
    private static final Map<Integer, String> pluralCache = new ConcurrentHashMap<>();
    private static final Map<String, String> exactCache = new ConcurrentHashMap<>();

    private static final int EXACT_CACHE_LIMIT = 2048;

    /**
     * Strings longer than this are translated but never memoised: WebView documents passed to
     * {@code translateText} can be hundreds of KB and would evict the whole cache on every load.
     */
    private static final int CACHEABLE_TEXT_MAX = 256;

    /**
     * Insert into an unbounded numeric/string cache; when it passes {@link #EXACT_CACHE_LIMIT}
     * entries the whole cache is cleared so long-lived UIs (comment streams, dynamic lists)
     * cannot grow memory without bound.
     */
    private static void putBounded(Map<String, String> cache, String key, String value) {
        if (cache.size() >= EXACT_CACHE_LIMIT) cache.clear();
        cache.put(key, value);
    }

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
        String cached = stringCache.get(id);
        if (cached != null) return cached.length() == 0 ? null : cached;
        if (stringCache.containsKey(id)) return null;
        String result = lookupString(res, id);
        stringCache.put(id, result == null ? "" : result);
        return result;
    }

    private String lookupString(Resources res, int id) {
        try {
            if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) return null;
            String type = res.getResourceTypeName(id);
            if (!STRING_TYPE.equals(type)) return null;
            return strings.resolve(res.getResourceEntryName(id));
        } catch (Throwable t) {
            Log.w(TAG, "findString error", t);
            return null;
        }
    }

    public String findQuantityString(Resources res, int id) {
        String cached = pluralCache.get(id);
        if (cached != null) return cached.length() == 0 ? null : cached;
        if (pluralCache.containsKey(id)) return null;
        String result;
        try {
            if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) result = null;
            else {
                String type = res.getResourceTypeName(id);
                result = PLURALS_TYPE.equals(type)
                        ? strings.resolve("plurals." + res.getResourceEntryName(id))
                        : null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "findQuantityString error", t);
            result = null;
        }
        pluralCache.put(id, result == null ? "" : result);
        return result;
    }

    public String findArrayItem(Resources res, int id, int index) {
        try {
            if (!TARGET_PACKAGE.equals(res.getResourcePackageName(id))) return null;
            String type = res.getResourceTypeName(id);
            if (!ARRAY_TYPE.equals(type) && !STRING_ARRAY_TYPE.equals(type)) return null;
            return strings.resolve("array." + res.getResourceEntryName(id) + "." + index);
        } catch (Throwable t) {
            Log.w(TAG, "findArrayItem error", t);
            return null;
        }
    }

    public String findExactText(CharSequence source) {
        if (source == null) return null;
        String text = source.toString();
        String exactHit = exact.get(text);
        if (exactHit != null) return exactHit;
        boolean cacheable = text.length() <= CACHEABLE_TEXT_MAX;
        if (cacheable) {
            String cached = exactCache.get(text);
            if (cached != null) return cached.length() == 0 ? null : cached;
            if (exactCache.containsKey(text)) return null;
        }
        if (!containsJapanese(text)) {
            // Negative-cache non-Japanese strings so repeated lookups on the same UI text
            // (hot Compose/View paths) skip the codepoint scan entirely.
            if (cacheable) putBounded(exactCache, text, "");
            return null;
        }
        String translated = phrases.replacePhrases(text);
        if (cacheable) putBounded(exactCache, text, translated == null ? "" : translated);
        return translated;
    }

    public String translateText(String source) {
        // Identical semantics to findExactText, but reusing it also gives the many short strings
        // from Compose / preferences / arrays the same bounded memoisation.
        return findExactText(source);
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
