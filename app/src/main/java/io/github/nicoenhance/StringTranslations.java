package io.github.nicoenhance;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Phrase-level dictionary backed by a compressed trie.
 *
 * <p>The previous implementation called {@link String#replace(CharSequence, CharSequence)} once
 * per dictionary entry. With 200+ phrases that translates to 200+ intermediate string
 * allocations per call, which on Compose hot paths adds noticeable GC pressure. The trie is
 * walked left to right and, at every position, the longest dictionary phrase starting there is
 * extended as far as possible before emitting it (leftmost-longest greedy).
 *
 * <p>Dictionaries are immutable after the first use, so the trie is built lazily on the
 * first call to {@link #replacePhrases(String)} and cached for subsequent calls.</p>
 */
public class StringTranslations {

    private static final Locale LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * Immutable snapshot of the dictionary. {@link Properties} extends the synchronised
     * {@link java.util.Hashtable}, so every {@code getProperty} takes a monitor; on Compose
     * hot paths that is pure contention. A plain map read is lock-free.
     */
    private final Map<String, String> entries;

    private volatile TrieNode trie;

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
        if (value == null) return null;
        if (value.startsWith("@string/")) return resolve(value.substring(8), depth - 1);
        return value;
    }

    /**
     * Replace every dictionary phrase that appears in {@code source}. Returns {@code null} when
     * nothing matched so callers can cheaply distinguish "no change" from "translated to itself".
     */
    public String replacePhrases(String source) {
        if (source == null || source.isEmpty()) return null;
        TrieNode root = ensureTrie();
        if (root == null) return null;

        // leftmost-longest greedy scan: at each position try to extend a phrase for as
        // far as the trie allows, remember the longest prefix found, then emit it once
        // the extension fails. This yields the maximal phrase starting at the leftmost
        // position, which is what replace-style translation intends.
        StringBuilder out = new StringBuilder(source.length() + 32);
        int writeStart = 0;
        int i = 0;
        final int n = source.length();
        while (i < n) {
            TrieNode node = root;
            int bestLen = -1;
            String bestValue = null;
            int j = i;
            while (j < n) {
                TrieNode next = node.children.get(source.charAt(j));
                if (next == null) break;
                node = next;
                j++;
                if (node.matchValue != null) {
                    bestLen = j - i;
                    bestValue = node.matchValue;
                }
            }
            if (bestLen > 0) {
                if (i > writeStart) out.append(source, writeStart, i);
                out.append(bestValue);
                writeStart = i + bestLen;
                i += bestLen;
            } else {
                i++;
            }
        }
        if (writeStart < n) {
            out.append(source, writeStart, n);
        }
        String result = out.toString();
        if (result.length() == source.length() && result.equals(source)) return null;
        return result;
    }

    public String format(String template, Object[] args) {
        if (args == null || args.length == 0) return template;
        try {
            return String.format(LOCALE, template, args);
        } catch (IllegalArgumentException e) {
            return template;
        }
    }

    private TrieNode ensureTrie() {
        TrieNode root = trie;
        if (root != null) return root;
        synchronized (this) {
            if (trie != null) return trie;
            if (entries.isEmpty()) return null;
            root = new TrieNode();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                String key = e.getKey();
                if (key.isEmpty()) continue;
                insert(root, key, e.getValue());
            }
            trie = root;
            return root;
        }
    }

    private static void insert(TrieNode root, String key, String value) {
        TrieNode cur = root;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            cur = cur.children.computeIfAbsent(c, k -> new TrieNode());
        }
        cur.matchValue = value;
    }

    private static final class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        String matchValue;
    }
}
