package io.github.nicoenhance;

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

public final class DexKitLocator implements Closeable {
    public static final String DEPENDENCY = "org.luckypray:dexkit:2.2.0";

    private final DexKitBridge bridge;
    private final ClassLoader classLoader;

    private DexKitLocator(DexKitBridge bridge, ClassLoader classLoader) {
        this.bridge = bridge;
        this.classLoader = classLoader;
    }

    public static DexKitLocator fromClassLoader(ClassLoader classLoader) {
        return new DexKitLocator(DexKitBridge.create(classLoader, true), classLoader);
    }

    public boolean isValid() {
        return bridge.isValid();
    }

    public List<Class<?>> findClassesUsingStrings(String... strings) throws ClassNotFoundException {
        requireStrings(strings);
        FindClass query = FindClass.create()
                .matcher(ClassMatcher.create().usingEqStrings(strings));
        List<Class<?>> classes = new ArrayList<>();
        for (ClassData data : bridge.findClass(query)) {
            classes.add(data.getInstance(classLoader));
        }
        return classes;
    }

    public List<Method> findMethodsUsingStrings(String... strings) throws ClassNotFoundException {
        requireStrings(strings);
        FindMethod query = FindMethod.create()
                .matcher(MethodMatcher.create().usingEqStrings(strings));
        List<Method> methods = new ArrayList<>();
        for (MethodData data : bridge.findMethod(query)) {
            try {
                methods.add(data.getMethodInstance(classLoader));
            } catch (NoSuchMethodException ignored) {
            }
        }
        return methods;
    }

    @Override
    public void close() {
        bridge.close();
    }

    private static void requireStrings(String[] strings) {
        if (strings == null || strings.length == 0) {
            throw new IllegalArgumentException("At least one DexKit string fingerprint is required.");
        }
    }
}
