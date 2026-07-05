package io.github.nicoenhance;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class StringTranslations {
    private static final Locale LOCALE = Locale.SIMPLIFIED_CHINESE;

    private final Properties props;

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

    public String replacePhrases(String source) {
        String result = source;
        List<String> keys = new ArrayList<>(props.stringPropertyNames());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        for (String key : keys) {
            String value = props.getProperty(key);
            if (value != null && !key.isEmpty()) {
                result = result.replace(key, value);
            }
        }
        return result.equals(source) ? null : result;
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
