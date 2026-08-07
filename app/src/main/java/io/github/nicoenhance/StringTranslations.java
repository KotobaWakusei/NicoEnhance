package io.github.nicoenhance;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final Properties props;

    private volatile AcNode automaton;

    public StringTranslations(Properties props) {
        this.props = props;
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
        return props.size();
    }

    public String get(String key) {
        return props.getProperty(key);
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
        String value = props.getProperty(key);
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
        AcNode root = ensureAutomaton();
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
            AcNode node = root;
            int bestLen = -1;
            String bestValue = null;
            int j = i;
            while (j < n) {
                AcNode next = node.children.get(source.charAt(j));
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

    private AcNode ensureAutomaton() {
        AcNode root = automaton;
        if (root != null) return root;
        synchronized (this) {
            if (automaton != null) return automaton;
            List<String> keys = new ArrayList<>(props.stringPropertyNames());
            if (keys.isEmpty()) return null;
            root = new AcNode();
            for (String key : keys) {
                String value = props.getProperty(key);
                if (value == null || key.isEmpty()) continue;
                insert(root, key, value);
            }
            automaton = root;
            return root;
        }
    }

    private static void insert(AcNode root, String key, String value) {
        AcNode cur = root;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            cur = cur.children.computeIfAbsent(c, k -> new AcNode());
        }
        cur.matchValue = value;
    }

    private static final class AcNode {
        final Map<Character, AcNode> children = new HashMap<>();
        String matchValue;
    }
}
