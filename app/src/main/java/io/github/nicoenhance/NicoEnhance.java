package io.github.nicoenhance;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class NicoEnhance extends XposedModule {

    private static final String TAG = "NicoEnhance";
    private static final String TARGET_PACKAGE = "jp.nicovideo.android";
    private static final String MODULE_PACKAGE = "io.github.nicoenhance";

    private TranslationRepository repository;
    private boolean hooksInstalled;

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (TARGET_PACKAGE.equals(pkg)) {
            installHooks();
        } else if (MODULE_PACKAGE.equals(pkg)) {
            writeSelfCheckFlag();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (TARGET_PACKAGE.equals(param.getPackageName())) {
            installHooks();
        }
    }

    private void writeSelfCheckFlag() {
        log(Log.INFO, TAG, "Creating self-check flag file");
        try {
            java.io.File flag = new java.io.File("/data/data/" + MODULE_PACKAGE + "/files/.module_active");
            flag.getParentFile().mkdirs();
            flag.createNewFile();
            log(Log.INFO, TAG, "Self-check flag file created");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to create self-check flag: " + t);
        }
    }

    private void installHooks() {
        if (hooksInstalled) return;
        hooksInstalled = true;

        repository = TranslationRepository.fromModuleApk(getModuleApplicationInfo().sourceDir);

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
            log(Log.INFO, TAG, "All resource hooks installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install hooks", t);
        }
    }

    private void hookStringMethods() throws NoSuchMethodException {
        Method getString = Resources.class.getMethod("getString", int.class);
        hook(getString)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findString(res, id);
                return t != null ? t : translateResult(chain.proceed());
            });

        Method getStringFmt = Resources.class.getMethod("getString", int.class, Object[].class);
        hook(getStringFmt)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findString(res, id);
                if (t == null) return translateResult(chain.proceed());
                return repository.format(t, (Object[]) chain.getArg(1));
            });
    }

    private void hookTextMethods() throws NoSuchMethodException {
        Method getText = Resources.class.getMethod("getText", int.class);
        hook(getText)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findString(res, id);
                return t != null ? t : translateResult(chain.proceed());
            });
    }

    private void hookQuantityMethods() throws NoSuchMethodException {
        Method getQtyText = Resources.class.getMethod("getQuantityText", int.class, int.class);
        hook(getQtyText)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findQuantityString(res, id);
                return t != null ? t : translateResult(chain.proceed());
            });

        Method getQtyString = Resources.class.getMethod("getQuantityString", int.class, int.class);
        hook(getQtyString)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findQuantityString(res, id);
                return t != null ? t : translateResult(chain.proceed());
            });

        Method getQtyStringFmt = Resources.class.getMethod("getQuantityString", int.class, int.class, Object[].class);
        hook(getQtyStringFmt)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Resources res = (Resources) chain.getThisObject();
                int id = (int) chain.getArg(0);
                String t = repository.findQuantityString(res, id);
                if (t == null) return translateResult(chain.proceed());
                return repository.format(t, (Object[]) chain.getArg(2));
            });
    }

    private void hookArrayMethods() throws NoSuchMethodException {
        Method getStrArray = Resources.class.getMethod("getStringArray", int.class);
        hook(getStrArray)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                if (!(result instanceof String[])) return translateResult(result);
                return translateStringArray((Resources) chain.getThisObject(), (int) chain.getArg(0), (String[]) result);
            });

        Method getTextArray = Resources.class.getMethod("getTextArray", int.class);
        hook(getTextArray)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                if (!(result instanceof CharSequence[])) return translateResult(result);
                return translateCharArray((Resources) chain.getThisObject(), (int) chain.getArg(0), (CharSequence[]) result);
            });
    }

    private void hookTypedArrayMethods() throws NoSuchMethodException {
        Method getText = TypedArray.class.getMethod("getText", int.class);
        hook(getText)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TypedArray arr = (TypedArray) chain.getThisObject();
                String t = findTypedArrayString(arr, (int) chain.getArg(0));
                return t != null ? t : translateResult(chain.proceed());
            });

        Method getString = TypedArray.class.getMethod("getString", int.class);
        hook(getString)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TypedArray arr = (TypedArray) chain.getThisObject();
                String t = findTypedArrayString(arr, (int) chain.getArg(0));
                return t != null ? t : translateResult(chain.proceed());
            });

        Method getTextArray = TypedArray.class.getMethod("getTextArray", int.class);
        hook(getTextArray)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                if (!(result instanceof CharSequence[])) return translateResult(result);
                TypedArray arr = (TypedArray) chain.getThisObject();
                int resid = arr.getResourceId((int) chain.getArg(0), 0);
                if (resid == 0) return result;
                return translateCharArray(arr.getResources(), resid, (CharSequence[]) result);
            });
    }

    private void hookTextViewMethods() throws NoSuchMethodException {
        Method setText = TextView.class.getMethod("setText", CharSequence.class);
        hook(setText)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = repository.findExactText((CharSequence) chain.getArg(0));
                if (t == null) return chain.proceed();
                return chain.proceed(new Object[]{t});
            });

        Method setTextBuf = TextView.class.getMethod("setText", CharSequence.class, TextView.BufferType.class);
        hook(setTextBuf)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = repository.findExactText((CharSequence) chain.getArg(0));
                if (t == null) return chain.proceed();
                return chain.proceed(new Object[]{t, chain.getArg(1)});
            });

        Method setTextRes = TextView.class.getMethod("setText", int.class);
        hook(setTextRes)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = repository.findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setText(t);
                return null;
            });

        Method setTextResBuf = TextView.class.getMethod("setText", int.class, TextView.BufferType.class);
        hook(setTextResBuf)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = repository.findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setText(t, (TextView.BufferType) chain.getArg(1));
                return null;
            });

        Method setHint = TextView.class.getMethod("setHint", CharSequence.class);
        hook(setHint)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = repository.findExactText((CharSequence) chain.getArg(0));
                if (t == null) return chain.proceed();
                return chain.proceed(new Object[]{t});
            });

        Method setHintRes = TextView.class.getMethod("setHint", int.class);
        hook(setHintRes)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                TextView tv = (TextView) chain.getThisObject();
                String t = repository.findString(tv.getResources(), (int) chain.getArg(0));
                if (t == null) return chain.proceed();
                tv.setHint(t);
                return null;
            });
    }

    private void hookViewMethods() throws NoSuchMethodException {
        Method setContentDesc = View.class.getMethod("setContentDescription", CharSequence.class);
        hook(setContentDesc)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = repository.findExactText((CharSequence) chain.getArg(0));
                if (t == null) return chain.proceed();
                return chain.proceed(new Object[]{t});
            });

        Method onAttached = View.class.getDeclaredMethod("onAttachedToWindow");
        onAttached.setAccessible(true);
        hook(onAttached)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                translateViewTree((View) chain.getThisObject());
                return result;
            });
    }

    private void hookActivityMethods() throws NoSuchMethodException {
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        onResume.setAccessible(true);
        hook(onResume)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                Activity act = (Activity) chain.getThisObject();
                translateViewTree(act.getWindow().getDecorView());
                return result;
            });
    }

    private void hookToastMethods() throws NoSuchMethodException {
        Method makeText = Toast.class.getMethod("makeText", Context.class, CharSequence.class, int.class);
        hook(makeText)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                String t = repository.findExactText((CharSequence) chain.getArg(1));
                if (t == null) return chain.proceed();
                return chain.proceed(new Object[]{chain.getArg(0), t, chain.getArg(2)});
            });

        Method makeTextRes = Toast.class.getMethod("makeText", Context.class, int.class, int.class);
        hook(makeTextRes)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Context ctx = (Context) chain.getArg(0);
                String t = repository.findString(ctx.getResources(), (int) chain.getArg(1));
                if (t == null) return chain.proceed();
                return Toast.makeText(ctx, t, (int) chain.getArg(2));
            });
    }

    private String findTypedArrayString(TypedArray arr, int index) {
        int resid = arr.getResourceId(index, 0);
        if (resid == 0) return null;
        return repository.findString(arr.getResources(), resid);
    }

    private String[] translateStringArray(Resources res, int id, String[] original) {
        String[] result = Arrays.copyOf(original, original.length);
        boolean changed = false;
        for (int i = 0; i < result.length; i++) {
            String item = repository.findArrayItem(res, id, i);
            if (item == null) item = repository.translateText(original[i]);
            if (item != null) { result[i] = item; changed = true; }
        }
        return changed ? result : original;
    }

    private CharSequence[] translateCharArray(Resources res, int id, CharSequence[] original) {
        CharSequence[] result = Arrays.copyOf(original, original.length);
        boolean changed = false;
        for (int i = 0; i < result.length; i++) {
            String item = repository.findArrayItem(res, id, i);
            if (item == null) item = repository.translateText(original[i].toString());
            if (item != null) { result[i] = item; changed = true; }
        }
        return changed ? result : original;
    }

    private Object translateResult(Object result) {
        if (result instanceof String) {
            String t = repository.translateText((String) result);
            return t != null ? t : result;
        }
        if (result instanceof CharSequence) {
            String t = repository.translateText(((CharSequence) result).toString());
            return t != null ? t : result;
        }
        return result;
    }

    private void translateViewTree(View view) {
        if (view == null) return;
        translateViewContentDesc(view);
        if (view instanceof TextView) translateTextView((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                translateViewTree(group.getChildAt(i));
            }
        }
    }

    private void translateTextView(TextView tv) {
        String t = repository.findExactText(tv.getText());
        if (t != null) tv.setText(t);
        String h = repository.findExactText(tv.getHint());
        if (h != null) tv.setHint(h);
    }

    private void translateViewContentDesc(View view) {
        String t = repository.findExactText(view.getContentDescription());
        if (t != null) view.setContentDescription(t);
    }
}
