package io.github.nicoenhance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class NicoEnhance extends XposedModule {

    private static final String TAG = "NicoEnhance";
    private static final String TARGET = "jp.nicovideo.android";
    private static final String MODULE = "io.github.nicoenhance";
    private static final String COPYRIGHT_ASSET_URL = "file:///android_asset/copyright/copyright.html";
    private static final String COPYRIGHT_BASE_URL = "file:///android_asset/copyright/";
    private static final String SUPPORTER_RENDERER_ASSET_URL = "file:///android_asset/supporter_renderer/index.html";
    private static final String SUPPORTER_RENDERER_BASE_URL = "file:///android_asset/supporter_renderer/";
    private static final String SUPPORTER_RENDERER_SCRIPT_ASSET = "supporter_renderer/index.js";
    private static final String SETTINGS_FRAGMENT_CLASS = "jp.nicovideo.android.ui.setting.SettingFragment";
    private static final String SETTINGS_BUTTON_TAG = "nicoenhance_settings_button";
    private static final String SETTINGS_BUTTON_SPACER_TAG = "nicoenhance_settings_button_spacer";
    private static final String SETTINGS_FALLBACK_ROW_TAG = "nicoenhance_settings_fallback_row";
    private static final String SETTINGS_ENTRY_TITLE = "NicoEnhance";
    private static final String SETTINGS_ENTRY_SUMMARY = "\u7ffb\u8bd1\u4e0e\u589e\u5f3a\u8bbe\u7f6e";
    private static final String ABOUT_APP_JA = "\u3053\u306e\u30a2\u30d7\u30ea\u306b\u3064\u3044\u3066";
    private static final String ABOUT_APP_ZH = "\u5173\u4e8e\u672c\u5e94\u7528";
    private static final String CONFIG_DIALOG_TITLE = "NicoEnhance";
    private static final String CONFIG_TRANSLATION_ENABLED = "\u542f\u7528\u7ffb\u8bd1\u4e0e\u589e\u5f3a";
    private static final String CONFIG_RUNTIME_TRANSLATION_ENABLED = "\u7ffb\u8bd1\u5e94\u7528\u6587\u672c";
    private static final String CONFIG_WEBVIEW_TRANSLATION_ENABLED = "\u7ffb\u8bd1 WebView \u5185\u5bb9";
    private static final String CONFIG_AD_REMOVAL_ENABLED = "\u53bb\u9664\u5e7f\u544a";
    private static final String CONFIG_DEBUG_LOG_ENABLED = "\u8c03\u8bd5\u65e5\u5fd7";
    private static final String CONFIG_PREMIUM_UNLOCK = "\u89e3\u9501\u4f1a\u5458\u7279\u6743";
    private static final String CONFIG_SAVE = "\u4fdd\u5b58";
    private static final String CONFIG_CANCEL = "\u53d6\u6d88";
    private static final String CONFIG_SAVED = "\u5df2\u4fdd\u5b58\uff0c\u90e8\u5206\u9875\u9762\u9700\u91cd\u65b0\u8fdb\u5165\u6216\u91cd\u542f niconico";

    private TranslationRepository repo;
    private final ModuleConfig config = new ModuleConfig();
    private final AtomicBoolean resourceHooksInstalled = new AtomicBoolean();
    private boolean appHooksInstalled;
    private WeakReference<Activity> currentActivity = new WeakReference<>(null);

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (TARGET.equals(pkg)) {
            log(Log.INFO, TAG, "NicoEnhance: package loaded for " + TARGET);
            if (repo == null) repo = TranslationRepository.fromModuleApk(getModuleApplicationInfo().sourceDir);
            installResourceHooks();
        } else if (MODULE.equals(pkg)) {
            writeSelfCheckFlag();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) return;
        log(Log.INFO, TAG, "NicoEnhance: package ready for " + TARGET);
        if (repo == null) repo = TranslationRepository.fromModuleApk(getModuleApplicationInfo().sourceDir);
        installResourceHooks();
        installAppHooks(param.getClassLoader());
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        return true;
    }

    private void writeSelfCheckFlag() {
        try {
            java.io.File f = new java.io.File("/data/data/" + MODULE + "/files/.module_active");
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to write self-check flag", t);
        }
    }

    private void installResourceHooks() {
        if (!resourceHooksInstalled.compareAndSet(false, true)) return;
        try {
            hookStringMethods();
            hookTextMethods();
            hookQuantityMethods();
            hookArrayMethods();
            hookTypedArrayMethods();
            hookTextViewMethods();
            hookViewMethods();
            hookActivityMethods();
            hookToastMethods();
            hookWebViewMethods();
            log(Log.INFO, TAG, "Resource hooks installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install resource hooks", t);
        }
    }

    private void installAppHooks(ClassLoader classLoader) {
        if (appHooksInstalled) return;
        appHooksInstalled = true;
        try {
            hookNicoSettingsEntry(classLoader);
            hookAboutAppComposeEntry(classLoader);
            hookAdRemoval(classLoader);
            hookComposeTextMethods(classLoader);
            hookPreferenceMethods(classLoader);
            hookPremiumUnlock(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install app hooks", t);
        }
    }

    private void hookStringMethods() throws NoSuchMethodException {
        hook(Resources.class.getMethod("getString", int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findString((Resources) chain.getThisObject(), (int) chain.getArg(0));
                return t != null ? t : chain.proceed();
            });

        hook(Resources.class.getMethod("getString", int.class, Object[].class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = findString(res, id);
                if (t == null) return chain.proceed();
                Object[] args = (Object[]) chain.getArg(1);
                return (args != null && args.length > 0) ? repo.format(t, args) : t;
            });
    }

    private void hookTextMethods() throws NoSuchMethodException {
        hook(Resources.class.getMethod("getText", int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findString((Resources) chain.getThisObject(), (int) chain.getArg(0));
                return t != null ? t : chain.proceed();
            });
    }

    private void hookQuantityMethods() throws NoSuchMethodException {
        for (Method m : Arrays.asList(
            Resources.class.getMethod("getQuantityText", int.class, int.class),
            Resources.class.getMethod("getQuantityString", int.class, int.class)
        )) {
            hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String t = findQuantityString((Resources) chain.getThisObject(), (int) chain.getArg(0));
                    return t != null ? t : chain.proceed();
                });
        }

        hook(Resources.class.getMethod("getQuantityString", int.class, int.class, Object[].class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = findQuantityString(res, id);
                if (t == null) return chain.proceed();
                return repo.format(t, (Object[]) chain.getArg(2));
            });
    }

    private void hookArrayMethods() throws NoSuchMethodException {
        hook(Resources.class.getMethod("getStringArray", int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                return r instanceof String[]
                    ? translateStringArray((Resources) chain.getThisObject(), (int) chain.getArg(0), (String[]) r)
                    : r;
            });

        hook(Resources.class.getMethod("getTextArray", int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                return r instanceof CharSequence[]
                    ? translateCharArray((Resources) chain.getThisObject(), (int) chain.getArg(0), (CharSequence[]) r)
                    : r;
            });
    }

    private void hookTypedArrayMethods() throws NoSuchMethodException {
        for (Method m : Arrays.asList(
            TypedArray.class.getMethod("getText", int.class),
            TypedArray.class.getMethod("getString", int.class)
        )) {
            hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    TypedArray arr = (TypedArray) chain.getThisObject();
                    int resid = arr.getResourceId((int) chain.getArg(0), 0);
                    String t = resid != 0 ? findString(arr.getResources(), resid) : null;
                    return t != null ? t : chain.proceed();
                });
        }

        hook(TypedArray.class.getMethod("getTextArray", int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                if (!(r instanceof CharSequence[])) return r;
                TypedArray arr = (TypedArray) chain.getThisObject();
                int resid = arr.getResourceId((int) chain.getArg(0), 0);
                return resid != 0 ? translateCharArray(arr.getResources(), resid, (CharSequence[]) r) : r;
            });
    }

    private void hookTextViewMethods() throws NoSuchMethodException {
        hook(TextView.class.getMethod("setText", CharSequence.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findExactText((CharSequence) chain.getArg(0));
                return t != null ? chain.proceed(new Object[]{t}) : chain.proceed();
            });

        hook(TextView.class.getMethod("setText", CharSequence.class, TextView.BufferType.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findExactText((CharSequence) chain.getArg(0));
                return t != null
                    ? chain.proceed(new Object[]{t, chain.getArg(1)})
                    : chain.proceed();
            });

        hook(TextView.class.getMethod("setHint", CharSequence.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findExactText((CharSequence) chain.getArg(0));
                return t != null ? chain.proceed(new Object[]{t}) : chain.proceed();
            });

        Method setTextId = TextView.class.getMethod("setText", int.class);
        hook(setTextId).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setText(t);
                return null;
            });

        Method setTextIdBuf = TextView.class.getMethod("setText", int.class, TextView.BufferType.class);
        hook(setTextIdBuf).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setText(t, (TextView.BufferType) chain.getArg(1));
                return null;
            });

        Method setHintId = TextView.class.getMethod("setHint", int.class);
        hook(setHintId).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setHint(t);
                return null;
            });
    }

    private void hookViewMethods() throws NoSuchMethodException {
        hook(View.class.getMethod("setContentDescription", CharSequence.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findExactText((CharSequence) chain.getArg(0));
                return t != null ? chain.proceed(new Object[]{t}) : chain.proceed();
            });

        Method oa = View.class.getDeclaredMethod("onAttachedToWindow");
        oa.setAccessible(true);
        hook(oa).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                translateViewTree((View) chain.getThisObject());
                return r;
            });
    }

    private void hookActivityMethods() throws NoSuchMethodException {
        Method or = Activity.class.getDeclaredMethod("onResume");
        or.setAccessible(true);
        hook(or).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                Activity a = (Activity) chain.getThisObject();
                currentActivity = new WeakReference<>(a);
                config.refresh(a);
                android.view.Window w = a.getWindow();
                if (w != null) translateViewTree(w.getDecorView());
                return r;
            });
    }

    private void hookToastMethods() throws NoSuchMethodException {
        hook(Toast.class.getMethod("makeText", Context.class, CharSequence.class, int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = findExactText((CharSequence) chain.getArg(1));
                return t != null ? chain.proceed(new Object[]{chain.getArg(0), t, chain.getArg(2)}) : chain.proceed();
            });

        hook(Toast.class.getMethod("makeText", Context.class, int.class, int.class))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Context ctx = (Context) chain.getArg(0);
                String t = findString(ctx.getResources(), (int) chain.getArg(1));
                return t != null ? chain.proceed(new Object[]{ctx, t, chain.getArg(2)}) : chain.proceed();
            });
    }

    private void hookWebViewMethods() throws NoSuchMethodException {
        Method loadUrl = WebView.class.getMethod("loadUrl", String.class);
        hook(loadUrl)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    WebView wv = (WebView) chain.getThisObject();
                    if (loadTranslatedLocalAsset(wv, (String) chain.getArg(0))) return null;
                    return chain.proceed();
                });

        Method loadUrlWithHeaders = WebView.class.getMethod("loadUrl", String.class, Map.class);
        hook(loadUrlWithHeaders)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    WebView wv = (WebView) chain.getThisObject();
                    if (loadTranslatedLocalAsset(wv, (String) chain.getArg(0))) return null;
                    return chain.proceed();
                });

        Method loadData = WebView.class.getMethod("loadData", String.class, String.class, String.class);
        hook(loadData)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String translated = translateWebContent((String) chain.getArg(0));
                    if (translated == null) return chain.proceed();
                    return chain.proceed(new Object[]{translated, chain.getArg(1), chain.getArg(2)});
                });

        Method loadDataWithBaseUrl = WebView.class.getMethod(
                "loadDataWithBaseURL", String.class, String.class, String.class, String.class, String.class);
        hook(loadDataWithBaseUrl)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String translated = translateWebContent((String) chain.getArg(1));
                    if (translated == null) return chain.proceed();
                    return chain.proceed(new Object[]{
                            chain.getArg(0), translated, chain.getArg(2), chain.getArg(3), chain.getArg(4)
                    });
                });
    }

    // ── Config-aware translation wrappers ──

    private String findString(Resources res, int id) {
        if (!shouldTranslateRuntimeText()) return null;
        return repo.findString(res, id);
    }

    private String findQuantityString(Resources res, int id) {
        if (!shouldTranslateRuntimeText()) return null;
        return repo.findQuantityString(res, id);
    }

    private String findArrayItem(Resources res, int id, int index) {
        if (!shouldTranslateRuntimeText()) return null;
        return repo.findArrayItem(res, id, index);
    }

    private String findExactText(CharSequence source) {
        if (!shouldTranslateRuntimeText()) return null;
        return repo.findExactText(source);
    }

    private String translateText(CharSequence source) {
        if (source == null || !shouldTranslateRuntimeText()) return null;
        return repo.translateText(source.toString());
    }

    private String translateText(String source) {
        return translateText((CharSequence) source);
    }

    // ── Settings injection ──

    private void hookNicoSettingsEntry(ClassLoader classLoader) {
        Class<?> fragmentClass = findSettingFragmentClass(classLoader);
        if (fragmentClass == null) {
            log(Log.WARN, TAG, "SettingFragment not found; settings entry skipped");
            return;
        }
        Method onCreateView = findDeclaredOnCreateView(fragmentClass);
        if (onCreateView == null) {
            log(Log.WARN, TAG, "SettingFragment.onCreateView not found in " + fragmentClass.getName());
            return;
        }
        onCreateView.setAccessible(true);
        hook(onCreateView)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof View) {
                        View root = (View) result;
                        config.refresh(root.getContext());
                        attachSettingsButtonToTitleBar(root, 0);
                    }
                    return result;
                });
        log(Log.INFO, TAG, "Settings entry hook installed: " + fragmentClass.getName());
    }

    private Class<?> findSettingFragmentClass(ClassLoader classLoader) {
        try {
            return Class.forName(SETTINGS_FRAGMENT_CLASS, false, classLoader);
        } catch (ClassNotFoundException ignored) {}

        try (DexKitLocator locator = DexKitLocator.fromClassLoader(classLoader)) {
            if (!locator.isValid()) return null;
            String[][] fingerprints = {
                    {"\u8a2d\u5b9a"}, {"\u30a2\u30ab\u30a6\u30f3\u30c8"}, {"\u30d8\u30eb\u30d7"}, {"setting"}
            };
            for (String[] fp : fingerprints) {
                Class<?> c = chooseSettingFragmentCandidate(locator.findClassesUsingStrings(fp));
                if (c != null) return c;
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "DexKit failed to locate SettingFragment", t);
        }
        return null;
    }

    private Class<?> chooseSettingFragmentCandidate(List<Class<?>> candidates) {
        Class<?> fallback = null;
        for (Class<?> c : candidates) {
            if (!isFragmentSubclass(c) || findDeclaredOnCreateView(c) == null) continue;
            if (c.getName().toLowerCase(Locale.ROOT).contains("setting")) return c;
            if (fallback == null) fallback = c;
        }
        return fallback;
    }

    private boolean isFragmentSubclass(Class<?> c) {
        Class<?> cur = c;
        while (cur != null) {
            String n = cur.getName();
            if ("androidx.fragment.app.Fragment".equals(n) || "android.app.Fragment".equals(n)) return true;
            cur = cur.getSuperclass();
        }
        return false;
    }

    private Method findDeclaredOnCreateView(Class<?> fragmentClass) {
        try {
            return fragmentClass.getDeclaredMethod("onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private void hookAboutAppComposeEntry(ClassLoader classLoader) {
        try {
            Class<?> settingComponents = Class.forName("hp.e0", false, classLoader);
            Class<?> function0 = Class.forName("qr.a", false, classLoader);
            Class<?> composer = Class.forName("androidx.compose.runtime.Composer", false, classLoader);
            Method settingTextItemByRes = settingComponents.getDeclaredMethod(
                    "k", int.class, function0, composer, int.class, int.class);
            Method settingTextItemByText = settingComponents.getDeclaredMethod(
                    "l", String.class, function0, composer, int.class, int.class);
            settingTextItemByRes.setAccessible(true);
            settingTextItemByText.setAccessible(true);
            int aboutAppTitleRes = Class.forName("mf.l0", false, classLoader)
                    .getField("config_application_info").getInt(null);
            Object nauxClick = createNauxiliaryClickCallback(function0, classLoader);
            hook(settingTextItemByRes)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if ((Integer) chain.getArg(0) == aboutAppTitleRes) {
                            try {
                                settingTextItemByText.invoke(null, SETTINGS_ENTRY_TITLE, nauxClick, chain.getArg(2), 0, 0);
                            } catch (Throwable ignored) {}
                        }
                        return result;
                    });
            log(Log.INFO, TAG, "Compose settings entry hook installed on hp.e0.k");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook Compose settings entry", t);
        }
    }

    private Object createNauxiliaryClickCallback(Class<?> function0, ClassLoader classLoader) {
        Object unit = findKotlinUnit(classLoader);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("invoke".equals(name) && method.getParameterTypes().length == 0) {
                Activity a = currentActivity.get();
                if (a != null && !a.isFinishing()) a.runOnUiThread(() -> showConfigDialog(a));
                return unit;
            }
            if ("toString".equals(name)) return "NicoEnhanceSettingsClick";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];
            return unit;
        };
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{function0}, handler);
    }

    private Object findKotlinUnit(ClassLoader classLoader) {
        String[] candidates = {"cr.j0", "kotlin.Unit"};
        String[] fields = {"f61931a", "INSTANCE"};
        for (String cn : candidates) {
            try {
                Class<?> uc = Class.forName(cn, false, classLoader);
                for (String fn : fields) {
                    try { return uc.getField(fn).get(null); } catch (NoSuchFieldException ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void attachSettingsButtonToTitleBar(View root, int attempt) {
        Context ctx = root.getContext();
        if (ctx == null) return;
        Activity activity = findActivity(ctx);
        View decor = activity != null ? activity.getWindow().getDecorView() : root;
        ViewGroup titleBar = findSettingsTitleContainer(decor);
        if (titleBar == null) titleBar = findSettingsTitleContainer(root);
        if (titleBar == null) titleBar = findTitleBar(root);
        if (titleBar == null) titleBar = findTitleBar(decor);
        if (titleBar != null) {
            if (findTaggedView(decor, SETTINGS_BUTTON_TAG) != null) return;
            View button = createSettingsEntryButton(ctx);
            button.setTag(SETTINGS_BUTTON_TAG);
            addButtonToTitleBar(titleBar, button);
            return;
        }
        if (attempt < 8) {
            root.postDelayed(() -> attachSettingsButtonToTitleBar(root, attempt + 1), 150);
        } else if (!attachAboutAppFallback(root)) {
            log(Log.WARN, TAG, "Settings title bar and about-app row not found; entry skipped");
        }
    }

    private View createSettingsEntryButton(Context ctx) {
        TextView btn = new TextView(ctx);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(dp(ctx, 48));
        btn.setMinWidth(dp(ctx, 88));
        btn.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        btn.setSingleLine(true);
        btn.setText(SETTINGS_ENTRY_TITLE);
        btn.setTextSize(14);
        btn.setTextColor(resolveColor(ctx, android.R.attr.colorAccent, 0xFF0099FF));
        btn.setClickable(true);
        btn.setFocusable(true);
        Drawable bg = resolveDrawable(ctx, android.R.attr.selectableItemBackgroundBorderless);
        if (bg == null) bg = resolveDrawable(ctx, android.R.attr.selectableItemBackground);
        if (bg != null) btn.setBackground(bg);
        btn.setOnClickListener(v -> showConfigDialog(v.getContext()));
        return btn;
    }

    private void addButtonToTitleBar(ViewGroup titleBar, View button) {
        if (titleBar instanceof LinearLayout
                && ((LinearLayout) titleBar).getOrientation() == LinearLayout.HORIZONTAL) {
            addButtonToHorizontalTitleBar((LinearLayout) titleBar, button);
            return;
        }
        ViewGroup.LayoutParams params = createTitleBarButtonParams(titleBar, button.getContext());
        try { titleBar.addView(button, params); } catch (Throwable ignored) {}
    }

    private void addButtonToHorizontalTitleBar(LinearLayout titleBar, View button) {
        Context ctx = button.getContext();
        if (findTaggedView(titleBar, SETTINGS_BUTTON_SPACER_TAG) == null) {
            View spacer = new View(ctx);
            spacer.setTag(SETTINGS_BUTTON_SPACER_TAG);
            titleBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.max(dp(ctx, 40), Math.min(dp(ctx, 56), titleBar.getHeight())));
        params.gravity = Gravity.CENTER_VERTICAL;
        params.setMargins(dp(ctx, 8), 0, dp(ctx, 8), 0);
        try { titleBar.addView(button, params); } catch (Throwable ignored) {}
    }

    private ViewGroup.LayoutParams createTitleBarButtonParams(ViewGroup titleBar, Context ctx) {
        int h = Math.max(dp(ctx, 40), Math.min(dp(ctx, 56), titleBar.getHeight()));
        ViewGroup.LayoutParams toolbarParams = createToolbarLayoutParams(titleBar, h);
        if (toolbarParams != null) return toolbarParams;
        if (titleBar instanceof FrameLayout) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, h, Gravity.END | Gravity.CENTER_VERTICAL);
            p.setMargins(0, 0, dp(ctx, 8), 0);
            return p;
        }
        if (titleBar instanceof RelativeLayout) {
            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, h);
            p.addRule(RelativeLayout.ALIGN_PARENT_END);
            p.addRule(RelativeLayout.CENTER_VERTICAL);
            p.setMargins(0, 0, dp(ctx, 8), 0);
            return p;
        }
        ViewGroup.LayoutParams constraintParams = createConstraintLayoutParams(titleBar, ctx, h);
        if (constraintParams != null) return constraintParams;
        if (titleBar instanceof LinearLayout) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, h);
            p.gravity = Gravity.CENTER_VERTICAL;
            p.setMargins(dp(ctx, 8), 0, dp(ctx, 8), 0);
            return p;
        }
        ViewGroup.MarginLayoutParams p = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, h);
        p.setMargins(dp(ctx, 8), 0, dp(ctx, 8), 0);
        return p;
    }

    private ViewGroup.LayoutParams createToolbarLayoutParams(ViewGroup titleBar, int h) {
        if (!titleBar.getClass().getName().toLowerCase(Locale.ROOT).contains("toolbar")) return null;
        String[] paramClasses = {
                "androidx.appcompat.widget.Toolbar$LayoutParams",
                "android.widget.Toolbar$LayoutParams"
        };
        for (String cn : paramClasses) {
            try {
                Class<?> pc = Class.forName(cn, false, titleBar.getClass().getClassLoader());
                Object p = pc.getConstructor(int.class, int.class, int.class)
                        .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, h, Gravity.END | Gravity.CENTER_VERTICAL);
                if (p instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) p).setMargins(0, 0, dp(titleBar.getContext(), 8), 0);
                }
                return (ViewGroup.LayoutParams) p;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private ViewGroup.LayoutParams createConstraintLayoutParams(ViewGroup titleBar, Context ctx, int h) {
        if (!titleBar.getClass().getName().toLowerCase(Locale.ROOT).contains("constraintlayout")) return null;
        try {
            Class<?> pc = Class.forName(
                    "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams",
                    false, titleBar.getClass().getClassLoader());
            Object p = pc.getConstructor(int.class, int.class)
                    .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, h);
            pc.getField("endToEnd").setInt(p, 0);
            pc.getField("topToTop").setInt(p, 0);
            pc.getField("bottomToBottom").setInt(p, 0);
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) p).setMargins(0, 0, dp(ctx, 8), 0);
            }
            return (ViewGroup.LayoutParams) p;
        } catch (Throwable ignored) { return null; }
    }

    private ViewGroup findTitleBar(View view) {
        if (!(view instanceof ViewGroup) || view.getVisibility() != View.VISIBLE) return null;
        ViewGroup g = (ViewGroup) view;
        if (isTitleBarCandidate(g)) return g;
        for (int i = 0; i < g.getChildCount(); i++) {
            ViewGroup found = findTitleBar(g.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private ViewGroup findSettingsTitleContainer(View root) {
        View titleView = findSettingsTitleView(root);
        if (titleView == null) return null;
        ViewParent parent = titleView.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) parent;
            if (isInsertableTitleContainer(g)) return g;
            parent = g.getParent();
        }
        return null;
    }

    private boolean isInsertableTitleContainer(ViewGroup g) {
        if (g.getVisibility() != View.VISIBLE) return false;
        String cn = g.getClass().getName().toLowerCase(Locale.ROOT);
        if (cn.contains("toolbar") || cn.contains("appbar") || cn.contains("actionbar")) return true;
        int h = g.getHeight();
        if (h < dp(g.getContext(), 40) || h > dp(g.getContext(), 112)) return false;
        if (g instanceof LinearLayout) return ((LinearLayout) g).getOrientation() == LinearLayout.HORIZONTAL;
        return g instanceof FrameLayout || g instanceof RelativeLayout || cn.contains("constraintlayout");
    }

    private boolean isTitleBarCandidate(ViewGroup g) {
        String cn = g.getClass().getName().toLowerCase(Locale.ROOT);
        if (cn.contains("toolbar") || cn.contains("appbar") || cn.contains("actionbar")) return true;
        int h = g.getHeight();
        if (h < dp(g.getContext(), 40) || h > dp(g.getContext(), 96)) return false;
        return containsSettingsTitle(g);
    }

    private boolean containsSettingsTitle(View view) {
        if (view instanceof TextView) {
            if (isSettingsTitle(((TextView) view).getText())) return true;
        }
        if (isSettingsTitle(view.getContentDescription())) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) view;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (containsSettingsTitle(g.getChildAt(i))) return true;
        }
        return false;
    }

    private View findSettingsTitleView(View view) {
        if (view instanceof TextView && isSettingsTitle(((TextView) view).getText())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) view;
        for (int i = 0; i < g.getChildCount(); i++) {
            View found = findSettingsTitleView(g.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean isSettingsTitle(CharSequence text) {
        if (text == null) return false;
        String v = text.toString();
        return v.contains("\u8a2d\u5b9a") || v.contains("\u8bbe\u7f6e") || v.toLowerCase(Locale.ROOT).contains("settings");
    }

    private boolean attachAboutAppFallback(View root) {
        if (!(root instanceof ViewGroup)) return false;
        if (findTaggedView(root, SETTINGS_BUTTON_TAG) != null
                || findTaggedView(root, SETTINGS_FALLBACK_ROW_TAG) != null) return true;
        TextView aboutTitle = findAboutAppTextView(root);
        if (aboutTitle == null) {
            Activity a = findActivity(root.getContext());
            if (a != null) aboutTitle = findAboutAppTextView(a.getWindow().getDecorView());
        }
        if (aboutTitle == null) return false;
        View row = findSettingsRow(aboutTitle);
        if (row == null) row = aboutTitle;
        if (insertFallbackRowAfter(row)) return true;
        reuseAboutAppRow(row, aboutTitle);
        return true;
    }

    private TextView findAboutAppTextView(View view) {
        if (view instanceof TextView && isAboutAppText(((TextView) view).getText())) return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) view;
        for (int i = 0; i < g.getChildCount(); i++) {
            TextView found = findAboutAppTextView(g.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean isAboutAppText(CharSequence text) {
        if (text == null) return false;
        String v = text.toString();
        return ABOUT_APP_JA.equals(v) || ABOUT_APP_ZH.equals(v);
    }

    private View findSettingsRow(View titleView) {
        View cur = titleView;
        ViewParent parent = titleView.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) parent;
            int h = g.getHeight();
            if ((g.isClickable() || g.isFocusable() || h >= dp(g.getContext(), 48))
                    && h <= dp(g.getContext(), 112) && g.getParent() instanceof ViewGroup) return g;
            cur = g;
            parent = cur.getParent();
        }
        return cur;
    }

    private boolean insertFallbackRowAfter(View templateRow) {
        ViewParent parent = templateRow.getParent();
        if (!(parent instanceof ViewGroup)) return false;
        ViewGroup pg = (ViewGroup) parent;
        if (findTaggedView(pg, SETTINGS_FALLBACK_ROW_TAG) != null) return true;
        int idx = pg.indexOfChild(templateRow);
        if (idx < 0) return false;
        View row = createFallbackSettingsRow(templateRow.getContext(), templateRow);
        row.setTag(SETTINGS_FALLBACK_ROW_TAG);
        try { pg.addView(row, idx + 1, createFallbackRowLayoutParams(templateRow)); return true; }
        catch (Throwable ignored) { return false; }
    }

    private View createFallbackSettingsRow(Context ctx, View templateRow) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Math.max(dp(ctx, 64), templateRow.getHeight()));
        row.setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), dp(ctx, 8));
        row.setClickable(true);
        row.setFocusable(true);
        Drawable bg = resolveDrawable(ctx, android.R.attr.selectableItemBackground);
        if (bg != null) row.setBackground(bg);
        TextView title = new TextView(ctx);
        title.setText(SETTINGS_ENTRY_TITLE);
        title.setTextSize(16);
        title.setTextColor(resolveColor(ctx, android.R.attr.textColorPrimary, Color.WHITE));
        title.setSingleLine(true);
        TextView summary = new TextView(ctx);
        summary.setText(SETTINGS_ENTRY_SUMMARY);
        summary.setTextSize(12);
        summary.setTextColor(resolveColor(ctx, android.R.attr.textColorSecondary, Color.LTGRAY));
        summary.setSingleLine(true);
        row.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(summary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> showConfigDialog(v.getContext()));
        return row;
    }

    private ViewGroup.LayoutParams createFallbackRowLayoutParams(View templateRow) {
        ViewGroup.LayoutParams orig = templateRow.getLayoutParams();
        if (orig instanceof LinearLayout.LayoutParams)
            return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) orig);
        if (orig instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) orig);
            p.height = orig.height;
            return p;
        }
        int h = orig != null ? orig.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
    }

    private void reuseAboutAppRow(View row, TextView aboutTitle) {
        aboutTitle.setText(SETTINGS_ENTRY_TITLE);
        row.setTag(SETTINGS_FALLBACK_ROW_TAG);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> showConfigDialog(v.getContext()));
    }

    // ── Config dialog ──

    private void showConfigDialog(Context context) {
        config.refresh(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), 0);

        Switch translationSwitch = createConfigSwitch(context, CONFIG_TRANSLATION_ENABLED, config.isTranslationEnabled());
        Switch runtimeSwitch = createConfigSwitch(context, CONFIG_RUNTIME_TRANSLATION_ENABLED, config.isRuntimeTextTranslationSwitchEnabled());
        Switch webViewSwitch = createConfigSwitch(context, CONFIG_WEBVIEW_TRANSLATION_ENABLED, config.isWebViewTranslationSwitchEnabled());
        Switch adRemovalSwitch = createConfigSwitch(context, CONFIG_AD_REMOVAL_ENABLED, config.isAdRemovalEnabled());
        Switch debugSwitch = createConfigSwitch(context, CONFIG_DEBUG_LOG_ENABLED, config.isDebugLogEnabled());
        Switch premiumSwitch = createConfigSwitch(context, CONFIG_PREMIUM_UNLOCK, config.isPremiumUnlockEnabled());

        content.addView(translationSwitch);
        content.addView(runtimeSwitch);
        content.addView(webViewSwitch);
        content.addView(adRemovalSwitch);
        content.addView(debugSwitch);
        content.addView(premiumSwitch);

        new AlertDialog.Builder(context)
                .setTitle(CONFIG_DIALOG_TITLE)
                .setView(content)
                .setNegativeButton(CONFIG_CANCEL, null)
                .setPositiveButton(CONFIG_SAVE, (dialog, which) -> {
                    config.save(context,
                            translationSwitch.isChecked(),
                            runtimeSwitch.isChecked(),
                            webViewSwitch.isChecked(),
                            adRemovalSwitch.isChecked(),
                            debugSwitch.isChecked(),
                            premiumSwitch.isChecked());
                    Toast.makeText(context, CONFIG_SAVED, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private Switch createConfigSwitch(Context context, String text, boolean checked) {
        Switch s = new Switch(context);
        s.setText(text);
        s.setTextSize(15);
        s.setChecked(checked);
        s.setPadding(0, dp(context, 8), 0, dp(context, 8));
        return s;
    }

    // ── Ad removal ──

    private void hookAdRemoval(ClassLoader classLoader) {
        int count = 0;
        count += hookInAppAdFactory(classLoader);
        count += hookInAppAdController(classLoader);
        count += hookInAppAdViewClass(classLoader, "jp.nicovideo.android.ui.inappad.InAppAdMobView");
        count += hookInAppAdViewClass(classLoader, "jp.nicovideo.android.ui.inappad.InAppAdGenerationView");
        count += hookComposeAdBanner(classLoader);
        count += hookPlayerVideoAdView(classLoader);
        if (count > 0) {
            log(Log.INFO, TAG, "Ad removal hooks installed: " + count);
        } else {
            log(Log.WARN, TAG, "No ad removal hooks were installed");
        }
    }

    private int hookInAppAdFactory(ClassLoader classLoader) {
        int count = 0;
        try {
            Class<?> factoryClass = Class.forName("ul.i", false, classLoader);
            count += hookInAppAdFactoryMethods(factoryClass, "known");
        } catch (ClassNotFoundException ignored) {}
        if (count == 0) count += hookInAppAdFactoryWithDexKit(classLoader);
        return count;
    }

    private int hookInAppAdFactoryWithDexKit(ClassLoader classLoader) {
        int count = 0;
        List<Method> candidates = new ArrayList<>();
        try (DexKitLocator locator = DexKitLocator.fromClassLoader(classLoader)) {
            if (!locator.isValid()) return 0;
            addMethods(candidates, locator.findMethodsUsingStrings("adUnitId", "baseFrameSizeDp"));
            addMethods(candidates, locator.findMethodsUsingStrings("oxInAppAd"));
            for (Method m : candidates) {
                if (isInAppAdFactoryMethod(m)) count += hookInAppAdFactoryMethod(m, "dexkit");
            }
        } catch (Throwable ignored) {}
        return count;
    }

    private int hookInAppAdFactoryMethods(Class<?> factoryClass, String source) {
        int count = 0;
        for (Method m : factoryClass.getDeclaredMethods()) {
            if (isInAppAdFactoryMethod(m)) count += hookInAppAdFactoryMethod(m, source);
        }
        return count;
    }

    private boolean isInAppAdFactoryMethod(Method m) {
        Class<?>[] pts = m.getParameterTypes();
        return pts.length > 0 && Context.class.isAssignableFrom(pts[0]) && isAdFacadeClass(m.getReturnType());
    }

    private boolean isAdFacadeClass(Class<?> candidate) {
        if (!candidate.isInterface()) return false;
        try { return View.class.isAssignableFrom(candidate.getMethod("getAdView").getReturnType()); }
        catch (NoSuchMethodException e) { return false; }
    }

    private int hookInAppAdFactoryMethod(Method method, String source) {
        method.setAccessible(true);
        try {
            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context ctx = (Context) chain.getArg(0);
                        if (!shouldRemoveAds(ctx)) return chain.proceed();
                        return createNoOpAdFacade(method.getReturnType(), ctx);
                    });
            return 1;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook in-app ad factory method", t);
            return 0;
        }
    }

    private int hookInAppAdController(ClassLoader classLoader) {
        try {
            Class<?> ctrlClass = Class.forName("uf.g", false, classLoader);
            int count = 0;
            count += hookAdControllerMethod(ctrlClass, "h");
            count += hookAdControllerMethod(ctrlClass, "j");
            count += hookAdControllerMethod(ctrlClass, "k");
            count += hookAdControllerMethod(ctrlClass, "m");
            return count;
        } catch (ClassNotFoundException e) { return 0; }
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook in-app ad controller", t); return 0; }
    }

    private int hookAdControllerMethod(Class<?> ctrlClass, String methodName) {
        int count = 0;
        for (Method m : ctrlClass.getDeclaredMethods()) {
            if (!methodName.equals(m.getName()) || m.getReturnType() != Void.TYPE) continue;
            m.setAccessible(true);
            try {
                hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (!shouldRemoveAds(chain.getThisObject())) return chain.proceed();
                            hideControllerContainer(chain.getThisObject());
                            return null;
                        });
                count++;
            } catch (Throwable ignored) {}
        }
        return count;
    }

    private int hookInAppAdViewClass(ClassLoader classLoader, String className) {
        try {
            Class<?> adViewClass = Class.forName(className, false, classLoader);
            int count = 0;
            count += hookAdViewNoArgMethod(adViewClass, "start");
            count += hookAdViewNoArgMethod(adViewClass, "a");
            return count;
        } catch (ClassNotFoundException e) { return 0; }
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook ad view: " + className, t); return 0; }
    }

    private int hookAdViewNoArgMethod(Class<?> adViewClass, String methodName) {
        try {
            Method m = adViewClass.getMethod(methodName);
            if (m.getReturnType() != Void.TYPE) return 0;
            hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!shouldRemoveAds(chain.getThisObject())) return chain.proceed();
                        hideAdView(chain.getThisObject());
                        return null;
                    });
            return 1;
        } catch (NoSuchMethodException e) { return 0; }
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook ad view method", t); return 0; }
    }

    private int hookComposeAdBanner(ClassLoader classLoader) {
        int count = hookKnownComposeAdBanner(classLoader);
        if (count == 0) count += hookComposeAdBannerWithDexKit(classLoader);
        return count;
    }

    private int hookKnownComposeAdBanner(ClassLoader classLoader) {
        try {
            Class<?> containerClass = Class.forName("jk.c", false, classLoader);
            int count = 0;
            for (Method m : containerClass.getDeclaredMethods()) {
                if (isComposeAdBannerMethod(m)) count += hookComposeAdBannerMethod(m, "known");
            }
            return count;
        } catch (ClassNotFoundException e) { return 0; }
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook known Compose ad banner", t); return 0; }
    }

    private int hookComposeAdBannerWithDexKit(ClassLoader classLoader) {
        int count = 0;
        try (DexKitLocator locator = DexKitLocator.fromClassLoader(classLoader)) {
            if (!locator.isValid()) return 0;
            for (Method m : locator.findMethodsUsingStrings(
                    "jp.nicovideo.android.ui.base.compose.container.AdBannerContainer (AdBannerContainer.kt:33)")) {
                if (isComposeAdBannerMethod(m)) count += hookComposeAdBannerMethod(m, "dexkit");
            }
        } catch (Throwable ignored) {}
        return count;
    }

    private boolean isComposeAdBannerMethod(Method m) {
        if (m.getReturnType() != Void.TYPE) return false;
        for (Class<?> pt : m.getParameterTypes()) {
            if ("androidx.compose.runtime.Composer".equals(pt.getName())) return true;
        }
        return false;
    }

    private int hookComposeAdBannerMethod(Method method, String source) {
        method.setAccessible(true);
        try {
            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object adEntry = chain.getArgs().isEmpty() ? null : chain.getArg(0);
                        View adView = getAdEntryView(adEntry);
                        if (!shouldRemoveAds(adView != null ? adView : adEntry)) return chain.proceed();
                        hideAdView(adView);
                        stopAdEntry(adEntry);
                        return null;
                    });
            return 1;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook Compose ad banner method", t);
            return 0;
        }
    }

    private int hookPlayerVideoAdView(ClassLoader classLoader) {
        try {
            Class<?> playerAdClass = Class.forName(
                    "jp.nicovideo.android.ui.player.panel.PlayerVideoAdvertisementView", false, classLoader);
            int count = 0;
            for (Method m : playerAdClass.getDeclaredMethods()) {
                if (("l".equals(m.getName()) && m.getParameterTypes().length == 0)
                        || ("n".equals(m.getName()) && m.getParameterTypes().length == 1)) {
                    count += hookPlayerVideoAdMethod(m);
                }
            }
            return count;
        } catch (ClassNotFoundException e) { return 0; }
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook player video ad", t); return 0; }
    }

    private int hookPlayerVideoAdMethod(Method method) {
        method.setAccessible(true);
        try {
            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!shouldRemoveAds(chain.getThisObject())) return chain.proceed();
                        hideAdView(chain.getThisObject());
                        if ("n".equals(method.getName())) invokePlayerVideoAdSkip(chain.getThisObject());
                        return null;
                    });
            return 1;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook player video ad method", t);
            return 0;
        }
    }

    // ── Ad helper methods ──

    private Object createNoOpAdFacade(Class<?> facadeClass, Context context) {
        View adView = createEmptyAdView(context);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getAdView".equals(name) && method.getParameterTypes().length == 0) return adView;
            if ("toString".equals(name)) return "NicoEnhanceNoOpAdFacade";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];
            return defaultValue(method.getReturnType());
        };
        return Proxy.newProxyInstance(facadeClass.getClassLoader(), new Class<?>[]{facadeClass}, handler);
    }

    private View createEmptyAdView(Context context) {
        FrameLayout v = new FrameLayout(context);
        v.setVisibility(View.GONE);
        v.setMinimumWidth(0);
        v.setMinimumHeight(0);
        v.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
        return v;
    }

    private Object defaultValue(Class<?> type) {
        if (type == Void.TYPE || !type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Character.TYPE) return (char) 0;
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        return null;
    }

    private boolean shouldRemoveAds(Object source) {
        Context ctx = extractContext(source);
        if (ctx == null) ctx = currentActivity.get();
        if (ctx != null) config.refresh(ctx);
        return config.isAdRemovalEnabled();
    }

    private Context extractContext(Object source) {
        if (source instanceof Context) return (Context) source;
        if (source instanceof View) return ((View) source).getContext();
        return null;
    }

    private void hideControllerContainer(Object controller) {
        try {
            Method m = controller.getClass().getMethod("f");
            hideAdView(m.invoke(controller));
        } catch (Throwable ignored) {}
    }

    private View getAdEntryView(Object adEntry) {
        if (adEntry == null) return null;
        try {
            Method m = adEntry.getClass().getMethod("b");
            Object v = m.invoke(adEntry);
            return v instanceof View ? (View) v : null;
        } catch (Throwable e) { return null; }
    }

    private void stopAdEntry(Object adEntry) {
        if (adEntry == null) return;
        try {
            adEntry.getClass().getMethod("h").invoke(adEntry);
        } catch (Throwable ignored) {}
    }

    private void hideAdView(Object object) {
        if (!(object instanceof View)) return;
        View v = (View) object;
        v.setVisibility(View.GONE);
        v.setMinimumWidth(0);
        v.setMinimumHeight(0);
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p != null) {
            p.width = 0;
            p.height = 0;
            v.setLayoutParams(p);
        }
        if (v instanceof ViewGroup) ((ViewGroup) v).removeAllViews();
    }

    private void invokePlayerVideoAdSkip(Object playerAdView) {
        Class<?> cur = playerAdView.getClass();
        while (cur != null) {
            for (Field f : cur.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (!ft.getName().startsWith("jp.nicovideo.android.ui.player.panel.PlayerVideoAdvertisementView$")) continue;
                try {
                    f.setAccessible(true);
                    Object listener = f.get(playerAdView);
                    if (listener == null) continue;
                    ft.getMethod("a").invoke(listener);
                    return;
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }
    }

    // ── Premium unlock ──

    private void hookPremiumUnlock(ClassLoader classLoader) {
        int count = 0;
        count += hookNicoSessionGetter(classLoader);
        count += hookNicoSessionReturn(classLoader);
        count += hookSettingUiStatePremium(classLoader);
        count += hookDataModelPremiumWithDexKit(classLoader);
        if (count > 0) {
            log(Log.INFO, TAG, "Premium unlock hooks installed: " + count);
        } else {
            log(Log.WARN, TAG, "No premium unlock hooks were installed");
        }
    }

    private int hookNicoSessionGetter(ClassLoader classLoader) {
        try {
            Class<?> sessionClass = Class.forName(
                    "jp.co.dwango.niconico.domain.user.NicoSession", false, classLoader);
            Method isPremium = sessionClass.getDeclaredMethod("isPremium");
            isPremium.setAccessible(true);
            hook(isPremium)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!shouldUnlockPremium()) return chain.proceed();
                        return true;
                    });
            return 1;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook NicoSession.isPremium", t);
            return 0;
        }
    }

    private int hookNicoSessionReturn(ClassLoader classLoader) {
        try {
            Class<?> ctxClass = Class.forName("aj.b", false, classLoader);
            Method jMethod = ctxClass.getDeclaredMethod("j");
            jMethod.setAccessible(true);
            hook(jMethod)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (!shouldUnlockPremium() || result == null) return result;
                        forcePremiumField(result);
                        return result;
                    });
            return 1;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook aj.b.j", t);
            return 0;
        }
    }

    private void forcePremiumField(Object nicoSession) {
        try {
            Field f = nicoSession.getClass().getDeclaredField("isPremium");
            f.setAccessible(true);
            makeFieldModifiable(f);
            f.setBoolean(nicoSession, true);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to force isPremium field on NicoSession", t);
        }
    }

    private void makeFieldModifiable(Field field) {
        try {
            Field af = Field.class.getDeclaredField("accessFlags");
            af.setAccessible(true);
            af.setInt(field, field.getModifiers() & ~0x10);
        } catch (NoSuchFieldException e) {
            try {
                Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(field, field.getModifiers() & ~0x10);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private int hookSettingUiStatePremium(ClassLoader classLoader) {
        int count = 0;
        try {
            Class<?> uiStateClass = Class.forName("gp.y1", false, classLoader);
            for (Method m : uiStateClass.getDeclaredMethods()) {
                if (m.getReturnType() != Boolean.TYPE || m.getParameterTypes().length != 0) continue;
                if ("e".equals(m.getName())) {
                    m.setAccessible(true);
                    hook(m)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (!shouldUnlockPremium()) return chain.proceed();
                                return true;
                            });
                    count++;
                    break;
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook gp.y1.e", t);
        }
        return count;
    }

    private int hookDataModelPremiumWithDexKit(ClassLoader classLoader) {
        int count = 0;
        try (DexKitLocator locator = DexKitLocator.fromClassLoader(classLoader)) {
            if (!locator.isValid()) return 0;
            for (Class<?> clazz : locator.findClassesUsingStrings("isPremium")) {
                count += hookBooleanGettersOnClass(clazz);
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "DexKit failed to find premium data models", t);
        }
        return count;
    }

    private int hookBooleanGettersOnClass(Class<?> clazz) {
        int count = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getReturnType() != Boolean.TYPE || m.getParameterTypes().length != 0) continue;
            String n = m.getName();
            if ("equals".equals(n) || "hashCode".equals(n) || "toString".equals(n)) continue;
            if (m.getDeclaringClass().equals(Object.class)) continue;
            m.setAccessible(true);
            try {
                hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (!shouldUnlockPremium()) return chain.proceed();
                            return true;
                        });
                count++;
            } catch (Throwable ignored) {}
        }
        return count;
    }

    private boolean shouldUnlockPremium() {
        ensureConfigLoaded();
        return config.isPremiumUnlockEnabled();
    }

    // ── WebView translation helpers ──

    private boolean loadTranslatedLocalAsset(WebView webView, String url) {
        config.refresh(webView.getContext());
        if (!shouldTranslateWebContent()) return false;
        if (COPYRIGHT_ASSET_URL.equals(url)) {
            String html = "<!DOCTYPE html>"
                    + "<html><head>"
                    + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />"
                    + "<meta http-equiv=\"Content-Script-Type\" content=\"text/javascript\" />"
                    + "<title>niconico | \u7248\u6743\u4fe1\u606f</title>"
                    + "<link rel=\"stylesheet\" href=\"style.css\" type=\"text/css\" />"
                    + "<meta name=\"viewport\" content=\"width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0\" />"
                    + "</head><body><div id=\"content\">"
                    + "<span style=\"color:#0099ff; font-size:18px\"><strong>\u7248\u6743\u4fe1\u606f</strong></span>"
                    + "<div class=\"contentBlock\"><p>niconico (niconico \u52a8\u753b / niconico \u76f4\u64ad)</p>"
                    + "<p>(C) DWANGO Co., Ltd.</p></div>"
                    + "</div></body></html>";
            webView.loadDataWithBaseURL(COPYRIGHT_BASE_URL, html, "text/html", "UTF-8", null);
            return true;
        }
        if (SUPPORTER_RENDERER_ASSET_URL.equals(url)) return loadTranslatedSupporterRenderer(webView);
        return false;
    }

    private boolean loadTranslatedSupporterRenderer(WebView webView) {
        try {
            String script = translateSupporterRendererScript(readTargetAsset(webView, SUPPORTER_RENDERER_SCRIPT_ASSET));
            String html = "<!DOCTYPE html>"
                    + "<html><head>"
                    + "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">"
                    + "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>"
                    + "<link href=\"https://fonts.googleapis.com/css2?family=M+PLUS+Rounded+1c&display=swap\" rel=\"stylesheet\">"
                    + "<link rel=\"stylesheet\" href=\"./index.css\">"
                    + "</head><body><div id=\"app\" width=\"1280\" height=\"720\"></div>"
                    + "<script>" + script.replace("</script", "<\\/script") + "</script>"
                    + "</body></html>";
            webView.loadDataWithBaseURL(SUPPORTER_RENDERER_BASE_URL, html, "text/html", "UTF-8", null);
            return true;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to load translated supporter renderer", t);
            return false;
        }
    }

    private String readTargetAsset(WebView webView, String path) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                webView.getContext().getAssets().open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = r.read(buf)) != -1) sb.append(buf, 0, read);
            return sb.toString();
        }
    }

    private String translateSupporterRendererScript(String script) {
        return script
                .replace("\"ja-jp\":\"\u63d0\u3000\u4f9b\"", "\"ja-jp\":\"\u8d5e\u3000\u52a9\"")
                .replace("\"zh-tw\":\"\u8d0a\u3000\u52a9\"", "\"zh-tw\":\"\u8d5e\u3000\u52a9\"")
                .replace("\"\u5de6\u5074\u30b5\u30a4\u30c9\u30d0\u30ca\u30fc\"", "\"\u5de6\u4fa7\u8fb9\u680f\u6a2a\u5e45\"")
                .replace("\"\u53f3\u5074\u30b5\u30a4\u30c9\u30d0\u30ca\u30fc\"", "\"\u53f3\u4fa7\u8fb9\u680f\u6a2a\u5e45\"")
                .replace("\"ready()\u304c\u5b8c\u4e86\u3057\u3066\u3044\u307e\u305b\u3093\"", "\"ready() \u5c1a\u672a\u5b8c\u6210\"");
    }

    private String translateWebContent(String data) {
        if (data == null || !shouldTranslateWebContent()) return null;
        return repo.translateText(data);
    }

    private boolean shouldTranslateWebContent() {
        ensureConfigLoaded();
        return config.isWebViewTranslationEnabled();
    }

    // ── Compose text hooks ──

    private void hookComposeTextMethods(ClassLoader classLoader) {
        hookComposeTextClass(classLoader, "androidx.compose.material3.TextKt");
        hookComposeTextClass(classLoader, "androidx.compose.material.TextKt");
        hookComposeTextClass(classLoader, "androidx.compose.foundation.text.BasicTextKt");
    }

    private void hookComposeTextClass(ClassLoader classLoader, String className) {
        try {
            Class<?> textClass = Class.forName(className, false, classLoader);
            Method[] methods = textClass.getDeclaredMethods();
            int count = 0;
            for (Method m : methods) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 0 || pts[0] != String.class) continue;
                try {
                    hook(m)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                String translated = translateText((String) chain.getArg(0));
                                if (translated == null) return chain.proceed();
                                Object[] args = chain.getArgs().toArray();
                                args[0] = translated;
                                return chain.proceed(args);
                            });
                    count++;
                } catch (Throwable ignored) {}
            }
            if (count > 0) log(Log.INFO, TAG, "Compose text hooks for " + className + ": " + count);
        } catch (ClassNotFoundException ignored) {}
    }

    // ── Preference hooks ──

    private void hookPreferenceMethods(ClassLoader classLoader) {
        try {
            Class<?> prefClass = Class.forName("androidx.preference.Preference", false, classLoader);
            Method getContext = prefClass.getMethod("getContext");
            hookPreferenceTextSetter(prefClass, "setTitle");
            hookPreferenceTextSetter(prefClass, "setSummary");
            hookPreferenceTextGetter(prefClass, "getTitle");
            hookPreferenceTextGetter(prefClass, "getSummary");
            hookPreferenceResourceSetter(prefClass, getContext, "setTitle");
            hookPreferenceResourceSetter(prefClass, getContext, "setSummary");
            hookPreferenceBindViewHolder(classLoader, prefClass);
            hookDialogPreferenceMethods(classLoader);
            hookListPreferenceMethods(classLoader);
            log(Log.INFO, TAG, "Preference translation hooks installed");
        } catch (ClassNotFoundException ignored) {}
        catch (Throwable t) { log(Log.WARN, TAG, "Failed to hook Preference methods", t); }
    }

    private void hookPreferenceTextSetter(Class<?> prefClass, String methodName) throws NoSuchMethodException {
        Method m = prefClass.getMethod(methodName, CharSequence.class);
        hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String t = findExactText((CharSequence) chain.getArg(0));
                    return t != null ? chain.proceed(new Object[]{t}) : chain.proceed();
                });
    }

    private void hookPreferenceTextGetter(Class<?> prefClass, String methodName) throws NoSuchMethodException {
        Method m = prefClass.getMethod(methodName);
        hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object r = chain.proceed();
                    return r instanceof String ? translateResult((String) r) : r;
                });
    }

    private void hookPreferenceBindViewHolder(ClassLoader classLoader, Class<?> prefClass) {
        try {
            Class<?> holderClass = Class.forName("androidx.preference.PreferenceViewHolder", false, classLoader);
            Field itemView = Class.forName("androidx.recyclerview.widget.RecyclerView$ViewHolder", false, classLoader)
                    .getField("itemView");
            Method m = prefClass.getMethod("onBindViewHolder", holderClass);
            hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();
                        translateViewTree((View) itemView.get(chain.getArg(0)));
                        return r;
                    });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook Preference.onBindViewHolder", t);
        }
    }

    private void hookPreferenceResourceSetter(Class<?> prefClass, Method getContext, String methodName) throws NoSuchMethodException {
        Method resMethod = prefClass.getMethod(methodName, int.class);
        Method textMethod = prefClass.getMethod(methodName, CharSequence.class);
        hook(resMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Context ctx = (Context) getContext.invoke(chain.getThisObject());
                    String t = findString(ctx.getResources(), (Integer) chain.getArg(0));
                    if (t == null) return chain.proceed();
                    textMethod.invoke(chain.getThisObject(), t);
                    return null;
                });
    }

    private void hookDialogPreferenceMethods(ClassLoader classLoader) {
        hookPreferenceSubclassTextSetter(classLoader, "androidx.preference.DialogPreference", "setDialogTitle");
        hookPreferenceSubclassTextSetter(classLoader, "androidx.preference.DialogPreference", "setDialogMessage");
        hookPreferenceSubclassTextSetter(classLoader, "androidx.preference.DialogPreference", "setPositiveButtonText");
        hookPreferenceSubclassTextSetter(classLoader, "androidx.preference.DialogPreference", "setNegativeButtonText");
        hookPreferenceSubclassResourceSetter(classLoader, "androidx.preference.DialogPreference", "setDialogTitle");
        hookPreferenceSubclassResourceSetter(classLoader, "androidx.preference.DialogPreference", "setDialogMessage");
        hookPreferenceSubclassResourceSetter(classLoader, "androidx.preference.DialogPreference", "setPositiveButtonText");
        hookPreferenceSubclassResourceSetter(classLoader, "androidx.preference.DialogPreference", "setNegativeButtonText");
    }

    private void hookListPreferenceMethods(ClassLoader classLoader) {
        hookPreferenceEntriesSetter(classLoader, "androidx.preference.ListPreference");
        hookPreferenceEntriesSetter(classLoader, "androidx.preference.MultiSelectListPreference");
        hookPreferenceSubclassTextGetter(classLoader, "androidx.preference.ListPreference", "getEntry");
    }

    private void hookPreferenceSubclassTextGetter(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> c = Class.forName(className, false, classLoader);
            Method m = c.getMethod(methodName);
            hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object r = chain.proceed();
                        return r instanceof String ? translateResult((String) r) : r;
                    });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook " + className + "." + methodName, t);
        }
    }

    private void hookPreferenceSubclassTextSetter(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> c = Class.forName(className, false, classLoader);
            Method m = c.getMethod(methodName, CharSequence.class);
            hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        String t = findExactText((CharSequence) chain.getArg(0));
                        return t != null ? chain.proceed(new Object[]{t}) : chain.proceed();
                    });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook " + className + "." + methodName, t);
        }
    }

    private void hookPreferenceSubclassResourceSetter(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> c = Class.forName(className, false, classLoader);
            Method getContext = c.getMethod("getContext");
            Method resMethod = c.getMethod(methodName, int.class);
            Method textMethod = c.getMethod(methodName, CharSequence.class);
            hook(resMethod)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context ctx = (Context) getContext.invoke(chain.getThisObject());
                        String t = findString(ctx.getResources(), (Integer) chain.getArg(0));
                        if (t == null) return chain.proceed();
                        textMethod.invoke(chain.getThisObject(), t);
                        return null;
                    });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook " + className + "." + methodName + " resource", t);
        }
    }

    private void hookPreferenceEntriesSetter(ClassLoader classLoader, String className) {
        try {
            Class<?> c = Class.forName(className, false, classLoader);
            Method setEntries = c.getMethod("setEntries", CharSequence[].class);
            hook(setEntries)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        CharSequence[] translated = translateTextArray((CharSequence[]) chain.getArg(0));
                        if (translated == chain.getArg(0)) return chain.proceed();
                        return chain.proceed(new Object[]{translated});
                    });
            Method getContext = c.getMethod("getContext");
            Method setEntriesRes = c.getMethod("setEntries", int.class);
            hook(setEntriesRes)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context ctx = (Context) getContext.invoke(chain.getThisObject());
                        CharSequence[] original = ctx.getResources().getTextArray((Integer) chain.getArg(0));
                        CharSequence[] translated = translateTextArray(original);
                        if (translated == original) return chain.proceed();
                        setEntries.invoke(chain.getThisObject(), new Object[]{translated});
                        return null;
                    });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook " + className + ".setEntries", t);
        }
    }

    private CharSequence[] translateTextArray(CharSequence[] original) {
        if (original == null) return null;
        CharSequence[] translated = Arrays.copyOf(original, original.length);
        boolean changed = false;
        for (int i = 0; i < translated.length; i++) {
            String item = translateText(original[i]);
            if (item != null) { translated[i] = item; changed = true; }
        }
        return changed ? translated : original;
    }

    private String translateResult(String result) {
        String t = translateText(result);
        return t != null ? t : result;
    }

    // ── Common helpers ──

    private void addMethods(List<Method> target, List<Method> source) {
        for (Method m : source) {
            if (!target.contains(m)) target.add(m);
        }
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int resolveColor(Context context, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, tv, true)) return fallback;
        if (tv.resourceId != 0) {
            try { return context.getColor(tv.resourceId); } catch (Throwable ignored) { return fallback; }
        }
        return tv.data;
    }

    private Drawable resolveDrawable(Context context, int attr) {
        TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, tv, true) || tv.resourceId == 0) return null;
        try { return context.getDrawable(tv.resourceId); } catch (Throwable ignored) { return null; }
    }

    private Activity findActivity(Context context) {
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) return (Activity) cur;
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return cur instanceof Activity ? (Activity) cur : null;
    }

    private View findTaggedView(View view, Object tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) view;
        for (int i = 0; i < g.getChildCount(); i++) {
            View found = findTaggedView(g.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private void ensureConfigLoaded() {
        if (config.isLoaded()) return;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getMethod("currentApplication");
            Object app = currentApplication.invoke(null);
            if (app instanceof Context) config.refresh((Context) app);
        } catch (Throwable ignored) {}
    }

    private boolean shouldTranslateRuntimeText() {
        ensureConfigLoaded();
        return config.isRuntimeTextTranslationEnabled();
    }

    // ── Translation helpers (existing) ──

    private String[] translateStringArray(Resources res, int id, String[] orig) {
        String[] r = Arrays.copyOf(orig, orig.length);
        boolean changed = false;
        for (int i = 0; i < r.length; i++) {
            String item = findArrayItem(res, id, i);
            if (item == null) item = translateText(orig[i]);
            if (item != null) { r[i] = item; changed = true; }
        }
        return changed ? r : orig;
    }

    private CharSequence[] translateCharArray(Resources res, int id, CharSequence[] orig) {
        CharSequence[] r = Arrays.copyOf(orig, orig.length);
        boolean changed = false;
        for (int i = 0; i < r.length; i++) {
            String item = findArrayItem(res, id, i);
            if (item == null) item = translateText(orig[i].toString());
            if (item != null) { r[i] = item; changed = true; }
        }
        return changed ? r : orig;
    }

    private void translateViewTree(View view) {
        if (view == null) return;
        String cd = findExactText(view.getContentDescription());
        if (cd != null) view.setContentDescription(cd);
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String t = findExactText(tv.getText());
            if (t != null) tv.setText(t);
            String h = findExactText(tv.getHint());
            if (h != null) tv.setHint(h);
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) translateViewTree(g.getChildAt(i));
        }
    }


}
