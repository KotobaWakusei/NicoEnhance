package io.github.nicoenhance;

import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NicoEnhance implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "jp.nicovideo.android";
    private static final String MODULE_PACKAGE = "io.github.nicoenhance";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals(TARGET_PACKAGE)) {
            XposedBridge.log("[NicoEnhance] \u589E\u5F3A\u6A21\u5757\u5DF2\u52A0\u8F7D -> " + TARGET_PACKAGE);
            hookResourcesGetString();
            hookResourcesGetStringWithFormat();
            hookResourcesGetText();
        } else if (lpparam.packageName.equals(MODULE_PACKAGE)) {
            XposedBridge.log("[NicoEnhance] \u81EA\u6211\u68C0\u6D4B\u6FC0\u6D3B -> " + MODULE_PACKAGE);
            hookSelfCheck(lpparam);
        }
    }

    private void hookResourcesGetString() {
        XposedHelpers.findAndHookMethod(
            Resources.class,
            "getString",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Resources res = (Resources) param.thisObject;
                        int id = (int) param.args[0];
                        String entryName = res.getResourceEntryName(id);
                        String typeName = res.getResourceTypeName(id);

                        if (!"string".equals(typeName)) return;

                        String translated = Translations.get(entryName);
                        if (translated != null) {
                            param.setResult(translated);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        );
    }

    private void hookResourcesGetStringWithFormat() {
        XposedHelpers.findAndHookMethod(
            Resources.class,
            "getString",
            int.class, Object[].class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Resources res = (Resources) param.thisObject;
                        int id = (int) param.args[0];
                        String entryName = res.getResourceEntryName(id);
                        String typeName = res.getResourceTypeName(id);

                        if (!"string".equals(typeName)) return;

                        String translated = Translations.get(entryName);
                        if (translated != null) {
                            Object[] formatArgs = (Object[]) param.args[1];
                            if (formatArgs != null && formatArgs.length > 0) {
                                param.setResult(String.format(translated, formatArgs));
                            } else {
                                param.setResult(translated);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        );
    }

    private void hookResourcesGetText() {
        XposedHelpers.findAndHookMethod(
            Resources.class,
            "getText",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Resources res = (Resources) param.thisObject;
                        int id = (int) param.args[0];
                        String entryName = res.getResourceEntryName(id);
                        String typeName = res.getResourceTypeName(id);

                        if (!"string".equals(typeName)) return;

                        String translated = Translations.get(entryName);
                        if (translated != null) {
                            param.setResult(translated);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        );
    }

    private void hookSelfCheck(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("[NicoEnhance] \u81EA\u6211\u68C0\u6D4B->\u521B\u5EFA\u6807\u5FD7\u6587\u4EF6");
        try {
            java.io.File flag = new java.io.File("/data/data/io.github.nicoenhance/files/.module_active");
            flag.getParentFile().mkdirs();
            flag.createNewFile();
            XposedBridge.log("[NicoEnhance] \u81EA\u6211\u68C0\u6D4B->\u6807\u5FD7\u6587\u4EF6\u5DF2\u521B\u5EFA");
        } catch (Throwable t) {
            XposedBridge.log("[NicoEnhance] \u81EA\u6211\u68C0\u6D4B->\u521B\u5EFA\u5931\u8D25: " + t);
        }
    }
}
