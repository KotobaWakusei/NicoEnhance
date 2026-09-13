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
import java.util.Arrays;
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
     * <p>Resolution strategy:
     * <ul>
     *   <li>No fingerprints: trust the hardcoded {@code fallback} only (fast path).</li>
     *   <li>With fingerprints: run both the fallback load and a DexKit fingerprint search,
     *       then cross-check. If the fallback class is among the DexKit hits, it is trustworthy.
     *       If the fallback resolves but is NOT in the DexKit results (or DexKit found others),
     *       prefer a DexKit hit — a drifted short name usually collides with an unrelated class.
     *       If DexKit finds nothing, fall back to the hardcoded name as a last resort (the
     *       fingerprints may be stale but the name might still be valid).</li>
     * </ul>
     *
     * <p>Previously the fallback was tried first and, on success, fingerprints were never
     * consulted. Because obfuscated short names (e.g. {@code ul.i}) still resolve after a
     * version bump but point at unrelated classes, this silently installed hooks on the wrong
     * targets. Cross-checking against DexKit avoids that trap while still trusting fully-qualified
     * names (e.g. {@code jp.co.dwango.niconico.domain.user.NicoSession}) when they match.
     *
     * @param fallback     hardcoded fully-qualified class name, used as last resort
     * @param fingerprints string fingerprints used by DexKit for cross-checking; may be empty
     * @return the resolved class, or null when neither route succeeds
     */
    public Class<?> get(String fallback, String... fingerprints) {
        String cacheKey = fallback != null ? fallback : fingerprintsKey(fingerprints);
        Class<?> hit = resolved.get(cacheKey);
        if (hit != null) return hit;
        if (failures.containsKey(cacheKey)) return null;

        boolean hasFingerprints = fingerprints != null && fingerprints.length > 0;
        if (!hasFingerprints) {
            Class<?> byFallback = loadFallback(fallback);
            if (byFallback != null) {
                resolved.put(cacheKey, byFallback);
                return byFallback;
            }
            if (fallback != null) Log.w(TAG, "Class not found: " + fallback);
            failures.put(cacheKey, new ClassNotFoundException(fallback));
            return null;
        }

        // Run DexKit fingerprint search and the fallback load, then cross-check.
        List<Class<?>> dexkitHits = findAllViaDexKit(fingerprints);
        Class<?> byFallback = loadFallback(fallback);

        if (byFallback != null && contains(dexkitHits, byFallback)) {
            resolved.put(cacheKey, byFallback);
            return byFallback;
        }
        if (!dexkitHits.isEmpty()) {
            Class<?> chosen = dexkitHits.get(0);
            String note = byFallback != null
                    ? "fallback " + fallback + " not in DexKit hits, using " + chosen.getName()
                    : "DexKit hit " + chosen.getName() + " for " + Arrays.toString(fingerprints);
            Log.i(TAG, note);
            resolved.put(cacheKey, chosen);
            return chosen;
        }
        if (byFallback != null) {
            Log.w(TAG, "DexKit found nothing for " + Arrays.toString(fingerprints)
                    + "; trusting unverified fallback " + fallback);
            resolved.put(cacheKey, byFallback);
            return byFallback;
        }

        if (fallback != null) Log.w(TAG, "Class not found: " + fallback + " (no DexKit fallback matched)");
        failures.put(cacheKey, new ClassNotFoundException(fallback));
        return null;
    }

    private static boolean contains(List<Class<?>> hits, Class<?> target) {
        if (hits.isEmpty()) return false;
        for (Class<?> c : hits) if (c == target) return true;
        return false;
    }

    private List<Class<?>> findAllViaDexKit(String... fingerprints) {
        List<Class<?>> out = new ArrayList<>();
        if (!bridgeReady || fingerprints == null || fingerprints.length == 0) return out;
        try {
            FindClass query = FindClass.create()
                    .matcher(ClassMatcher.create().usingEqStrings(fingerprints));
            for (ClassData data : bridge.findClass(query)) {
                Class<?> c = data.getInstance(classLoader);
                if (c != null && !contains(out, c)) out.add(c);
            }
        } catch (Throwable t) {
            Log.w(TAG, "DexKit search failed", t);
        }
        return out;
    }

    private static String fingerprintsKey(String[] fingerprints) {
        StringBuilder sb = new StringBuilder("?");
        if (fingerprints != null) for (String f : fingerprints) sb.append('|').append(f);
        return sb.toString();
    }

    private Class<?> loadFallback(String className) {
        if (className == null) return null;
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
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
                } catch (NoSuchMethodException e) {
                    Log.v(TAG, "Method not found during DexKit search", e);
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
