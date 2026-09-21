package io.github.nicoenhance;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Whole-string dictionary: resource-name → translation (for {@code strings.properties}) or
 * source text → translation (for {@code exact.properties}).
 *
 * <p>Lookups are exact, with a whitespace-normalised retry for multi-line resources. There is
 * deliberately no substring/phrase substitution: replacing individual words rewrote arbitrary
 * text (user comments, video titles, descriptions) into mixed Japanese/Chinese output. Anything
 * without an exact full-string entry is left untouched.</p>
 */
public class StringTranslations {

    private static final Locale LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * Immutable snapshot of the dictionary. {@link Properties} extends the synchronised
     * {@link java.util.Hashtable}, so every {@code getProperty} takes a monitor; on Compose
     * hot paths that is pure contention. A plain map read is lock-free.
     */
    private final Map<String, String> entries;

    /**
     * Dictionary keys with runs of ASCII whitespace collapsed to a single space. Built lazily on
     * first {@link #getNormalized} call.
     *
     * <p>Needed because Android resources and the extracted dictionary disagree on multi-line
     * whitespace: aapt stores {@code "…。\n時間…"} while the dictionary was captured with the
     * source indentation ({@code "…。\n      時間…"}). An exact lookup misses; the normalised
     * lookup matches.
     */
    private volatile Map<String, String> normalizedEntries;

    public StringTranslations(Properties props) {
        Map<String, String> copy = new HashMap<>(Math.max(16, props.size() * 2));
        for (String name : props.stringPropertyNames()) {
            String value = props.getProperty(name);
            if (value != null) copy.put(name, value);
        }
        this.entries = copy;
    }

    public static StringTranslations empty() {
        return new StringTranslations(new Properties());
    }

    public static StringTranslations load(Reader reader) throws IOException {
        Properties p = new Properties();
        p.load(reader);
        return new StringTranslations(p);
    }

    public int size() {
        return entries.size();
    }

    public String get(String key) {
        return entries.get(key);
    }

    /**
     * Whitespace-insensitive lookup: matches {@code key} against dictionary entries after
     * collapsing runs of ASCII whitespace. Recovers multi-line resources whose indentation
     * differs between the app and the dictionary. Returns {@code null} when nothing matches.
     */
    public String getNormalized(String key) {
        if (key == null) return null;
        Map<String, String> map = normalizedEntries;
        if (map == null) {
            synchronized (this) {
                if (normalizedEntries == null) {
                    Map<String, String> built = new HashMap<>(Math.max(16, entries.size() * 2));
                    for (Map.Entry<String, String> e : entries.entrySet()) {
                        built.put(collapseWhitespace(e.getKey()), e.getValue());
                    }
                    normalizedEntries = built;
                }
                map = normalizedEntries;
            }
        }
        return map.get(collapseWhitespace(key));
    }

    private static String collapseWhitespace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean pendingSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && sb.length() > 0) sb.append(' ');
            pendingSpace = false;
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Resolve a dictionary value, following {@code @string/name} references recursively
     * (a pattern copied verbatim from the extracted resource that must be expanded to the
     * referenced entry's own translation). Returns {@code null} when the key or any
     * reference target is missing, so callers fall back to the original text.
     */
    public String resolve(String key) {
        return resolve(key, 8);
    }

    private String resolve(String key, int depth) {
        if (key == null || depth <= 0) return null;
        String value = entries.get(key);
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("@string/")) return resolve(value.substring(8), depth - 1);
        return value;
    }

    public String format(String template, Object[] args) {
        if (args == null || args.length == 0) return template;
        try {
            return String.format(LOCALE, template, args);
        } catch (IllegalArgumentException e) {
            return template;
        }
    }

}
