package io.github.nicoenhance;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Phrase-level dictionary backed by an Aho-Corasick automaton.
 *
 * <p>The previous implementation called {@link String#replace(CharSequence, CharSequence)} once
 * per dictionary entry. With 200+ phrases that translates to 200+ intermediate string
 * allocations per call, which on Compose hot paths adds noticeable GC pressure. The automaton
 * walks the input exactly once and replaces longest matches greedily from left to right.</p>
 *
 * <p>Dictionaries are immutable after the first use, so the automaton is built lazily on the
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
     * Replace every dictionary phrase that appears in {@code source}. Returns {@code null} when
     * nothing matched so callers can cheaply distinguish "no change" from "translated to itself".
     */
    public String replacePhrases(String source) {
        if (source == null || source.isEmpty()) return null;
        AcNode root = ensureAutomaton();
        if (root == null) return null;

        StringBuilder out = new StringBuilder(source.length() + 32);
        int writeStart = 0;
        int i = 0;
        AcNode node = root;
        while (i < source.length()) {
            char c = source.charAt(i);
            AcNode next = node.step(c);
            if (next == null) {
                if (node != root) {
                    node = root;
                    continue;
                }
                out.append(c);
                i++;
                continue;
            }
            node = next;
            i++;
            if (node.match != null) {
                int matchStart = i - node.match.length();
                if (matchStart > writeStart) out.append(source, writeStart, matchStart);
                out.append(node.matchValue);
                writeStart = i;
                node = root;
            }
        }
        if (writeStart < source.length()) {
            out.append(source, writeStart, source.length());
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
            buildFailures(root);
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
        if (cur.match == null || key.length() > cur.match.length()) {
            cur.match = key;
            cur.matchValue = value;
        }
    }

    private static void buildFailures(AcNode root) {
        ArrayDeque<AcNode> queue = new ArrayDeque<>();
        for (AcNode child : root.children.values()) {
            if (child == null) continue;
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            AcNode cur = queue.poll();
            for (Map.Entry<Character, AcNode> e : cur.children.entrySet()) {
                char c = e.getKey();
                AcNode child = e.getValue();
                if (child == null) continue;
                queue.add(child);
                AcNode fail = cur.fail;
                while (fail != null && fail.children.get(c) == null) {
                    fail = fail.fail;
                }
                child.fail = fail == null ? root : fail.children.get(c);
                if (child.fail == null) child.fail = root;
                if (child.fail.match != null && (child.match == null
                        || child.fail.match.length() > child.match.length())) {
                    child.match = child.fail.match;
                    child.matchValue = child.fail.matchValue;
                }
            }
        }
    }

    private static final class AcNode {
        final Map<Character, AcNode> children = new HashMap<>();
        AcNode fail;
        String match;
        String matchValue;

        AcNode step(char c) {
            AcNode cur = this;
            while (cur != null) {
                AcNode next = cur.children.get(c);
                if (next != null) return next;
                if (cur == this) return null;
                cur = cur.fail;
            }
            return null;
        }
    }
}
