package io.github.nicoenhance;

import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Centralised lookup for obfuscated niconico classes.
 *
 * Strategy for every class: <ol>
 *   <li>Try the hardcoded {@code fallback} constant (fast path, works on most versions).</li>
 *   <li>If that fails, search the DEX for any class containing every {@code fingerprint} string.</li>
 *   <li>If still nothing, give up and return null (caller degrades the feature).</li>
 * </ol>
 *
 * Resolved results are cached so the cost is paid once per niconico install.
 *
 * The owner creates one instance per package load and closes it after all hooks are
 * installed via {@link #close()}, which releases the underlying DexKit bridge.
 */
public final class ClassNameProvider implements Closeable {
    private static final String TAG = "NicoEnhance";

    private final DexKitBridge bridge;
    private final ClassLoader classLoader;
    private final boolean bridgeReady;

    private final ConcurrentMap<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Throwable> failures = new ConcurrentHashMap<>();

    public ClassNameProvider(ClassLoader classLoader) {
        this.classLoader = classLoader;
        DexKitBridge b = null;
        boolean ready = false;
        try {
            b = DexKitBridge.create(classLoader, true);
            ready = b.isValid();
        } catch (Throwable t) {
            Log.w(TAG, "DexKit bridge failed to initialise; using hardcoded fallbacks only", t);
        }
        this.bridge = b;
        this.bridgeReady = ready;
    }

    public static ClassNameProvider open(ClassLoader classLoader) {
        return new ClassNameProvider(classLoader);
    }

    public boolean bridgeReady() {
        return bridgeReady;
    }

    /**
     * Resolve a class.
     *
     * @param fallback     hardcoded fully-qualified class name, used first when non-null
     * @param fingerprints string fingerprints used by DexKit as a fallback search; may be empty
     * @return the resolved class, or null when neither route succeeds
     */
    public Class<?> get(String fallback, String... fingerprints) {
        String cacheKey = fallback;
        if (cacheKey == null) {
            StringBuilder sb = new StringBuilder("?");
            for (String f : fingerprints) sb.append('|').append(f);
            cacheKey = sb.toString();
        }
        Class<?> hit = resolved.get(cacheKey);
        if (hit != null) return hit;
        if (failures.containsKey(cacheKey)) return null;

        Class<?> byFallback = loadFallback(fallback);
        if (byFallback != null) {
            resolved.put(cacheKey, byFallback);
            return byFallback;
        }

        Class<?> byDexKit = loadViaDexKit(fingerprints);
        if (byDexKit != null) {
            Log.i(TAG, "DexKit fallback hit for " + cacheKey + " -> " + byDexKit.getName());
            resolved.put(cacheKey, byDexKit);
            return byDexKit;
        }

        if (fallback != null) {
            Log.w(TAG, "Class not found: " + fallback + " (no DexKit fallback matched)");
        }
        failures.put(cacheKey, new ClassNotFoundException(fallback));
        return null;
    }

    private Class<?> loadFallback(String className) {
        if (className == null) return null;
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Class<?> loadViaDexKit(String[] fingerprints) {
        if (!bridgeReady || fingerprints == null || fingerprints.length == 0) return null;
        try {
            FindClass query = FindClass.create()
                    .matcher(ClassMatcher.create().usingEqStrings(fingerprints));
            for (ClassData data : bridge.findClass(query)) {
                Class<?> c = data.getInstance(classLoader);
                if (c != null) return c;
            }
        } catch (Throwable t) {
            Log.w(TAG, "DexKit search failed", t);
        }
        return null;
    }

    /**
     * DexKit-wide search for classes whose body contains every listed string.
     * Returns an empty list rather than null when the bridge is unavailable so
     * callers can branch on emptiness without an extra null check.
     */
    public List<Class<?>> findClassesUsingStrings(String... strings) {
        if (!bridgeReady || strings == null || strings.length == 0) return new ArrayList<>();
        List<Class<?>> classes = new ArrayList<>();
        try {
            FindClass query = FindClass.create()
                    .matcher(ClassMatcher.create().usingEqStrings(strings));
            for (ClassData data : bridge.findClass(query)) {
                Class<?> c = data.getInstance(classLoader);
                if (c != null && !classes.contains(c)) classes.add(c);
            }
        } catch (Throwable t) {
            Log.w(TAG, "DexKit class search failed", t);
        }
        return classes;
    }

    /**
     * DexKit-wide search for methods whose body contains every listed string.
     */
    public List<Method> findMethodsUsingStrings(String... strings) {
        if (!bridgeReady || strings == null || strings.length == 0) return new ArrayList<>();
        List<Method> methods = new ArrayList<>();
        try {
            FindMethod query = FindMethod.create()
                    .matcher(MethodMatcher.create().usingEqStrings(strings));
            for (MethodData data : bridge.findMethod(query)) {
                try {
                    Method m = data.getMethodInstance(classLoader);
                    if (m != null && !methods.contains(m)) methods.add(m);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "DexKit method search failed", t);
        }
        return methods;
    }

    @Override
    public void close() {
        resolved.clear();
        failures.clear();
        if (bridge != null) {
            try {
                bridge.close();
            } catch (Throwable t) {
                Log.w(TAG, "DexKit bridge close failed", t);
            }
        }
    }
}
