package com.kernel.xiaomicarsickfix;

import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 让 MIUI「晕车缓解」的圆点悬浮窗不再把下层窗口的触摸标记为“被遮挡”。
 *
 * 现象：该窗口没有 PRIVATE_FLAG_TRUSTED_OVERLAY，输入系统会把经过它的触摸
 * 标记成 FLAG_WINDOW_IS_OBSCURED（实测与 alpha 无关，0.1 也一样），
 * 实现了防点击劫持过滤的应用（例如 GitHub）会直接丢弃这些事件。
 *
 * 做法：在窗口加入 WindowManager 之前，给它的 LayoutParams 补上
 * PRIVATE_FLAG_TRUSTED_OVERLAY（与 SystemUI 的防晕车辅助一致）。
 * 只作用于标题包含 carsick 的窗口，其它窗口不受影响。
 */
public final class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "CarSickFix: ";

    /** 需要作用的包（晕车缓解由 com.miui.securitycenter 的进程创建）。 */
    private static final Set<String> PKGS = new HashSet<>(Arrays.asList(
            "com.miui.securitycenter",
            "com.miui.securitycore",
            "com.miui.securityadd"));

    /** 目标窗口标题关键字（不区分大小写）。 */
    private static final String TITLE_KEY = "carsick";

    private static final Field FIELD_PRIVATE_FLAGS = findField("privateFlags");
    private static final int FLAG_TRUSTED_OVERLAY = readTrustedOverlayFlag();

    private static final XC_MethodHook ADD_VIEW_HOOK = new XC_MethodHook() {
        private boolean logged;

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            WindowManager.LayoutParams lp = findLayoutParams(param.args);
            if (lp == null) {
                return;
            }
            CharSequence cs = lp.getTitle();
            if (cs == null) {
                return;
            }
            String title = cs.toString();
            if (!title.toLowerCase().contains(TITLE_KEY)) {
                return;
            }
            int before = -1;
            int after = -1;
            if (FIELD_PRIVATE_FLAGS != null && FLAG_TRUSTED_OVERLAY != 0) {
                try {
                    before = FIELD_PRIVATE_FLAGS.getInt(lp);
                    after = before | FLAG_TRUSTED_OVERLAY;
                    FIELD_PRIVATE_FLAGS.setInt(lp, after);
                } catch (Throwable t) {
                    after = -1;
                    if (!logged) {
                        logged = true;
                        XposedBridge.log(TAG + "set privateFlags failed: " + t);
                    }
                }
            }
            if (!logged) {
                logged = true;
                XposedBridge.log(TAG + "patched '" + title
                        + "' trustedFlag=0x" + Integer.toHexString(FLAG_TRUSTED_OVERLAY)
                        + " privateFlags 0x" + Integer.toHexString(before)
                        + " -> 0x" + Integer.toHexString(after));
            }
        }
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null
                || !PKGS.contains(lpparam.packageName)) {
            return;
        }
        for (String className : new String[]{
                "android.view.WindowManagerImpl",
                "android.view.WindowManagerGlobal"}) {
            try {
                Class<?> clazz = Class.forName(className, false, lpparam.classLoader);
                for (Method m : clazz.getDeclaredMethods()) {
                    if (!"addView".equals(m.getName())) {
                        continue;
                    }
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, ADD_VIEW_HOOK);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook " + className + " failed: " + t);
            }
        }
        XposedBridge.log(TAG + "active in " + lpparam.processName
                + " trustedFlag=0x" + Integer.toHexString(FLAG_TRUSTED_OVERLAY));
    }

    private static Field findField(String name) {
        try {
            Field f = WindowManager.LayoutParams.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "field " + name + " unavailable: " + t);
            return null;
        }
    }

    private static int readTrustedOverlayFlag() {
        try {
            Field f = WindowManager.LayoutParams.class
                    .getField("PRIVATE_FLAG_TRUSTED_OVERLAY");
            return f.getInt(null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "no PRIVATE_FLAG_TRUSTED_OVERLAY: " + t);
            return 0;
        }
    }

    private static WindowManager.LayoutParams findLayoutParams(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof WindowManager.LayoutParams) {
                return (WindowManager.LayoutParams) arg;
            }
        }
        return null;
    }
}
