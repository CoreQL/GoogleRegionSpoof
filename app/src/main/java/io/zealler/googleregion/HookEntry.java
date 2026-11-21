package io.zealler.googleregion;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.TimeZone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 区域伪装 hook 入口（assets/xposed_init 指向本类）。
 *
 * 只改应用读到的客户端区域信号：TelephonyManager 的 SIM/网络国家与运营商、
 * Locale、TimeZone、Resources 的 Configuration、以及 gsm.* 系统属性。
 * 不碰 IP、不碰网络流量。每个 hook 独立 try/catch，失败静默放行原结果，
 * 绝不因本模块让目标应用崩溃。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String MODULE_PACKAGE = "io.zealler.googleregion";
    private static final String PREFS_NAME = "region_config";
    private static final String KEY_PRESET_ID = "preset_id";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!isTarget(lpparam.packageName)) {
            return;
        }
        RegionPreset preset = loadPreset();
        ClassLoader cl = lpparam.classLoader;

        hookTelephony(cl, preset);
        hookLocale(preset);
        hookTimeZone(preset);
        hookConfiguration(cl, preset);
        hookSystemProperties(cl, preset);
    }

    private boolean isTarget(String pkg) {
        return "com.google.android.gms".equals(pkg)
                || "com.android.vending".equals(pkg)
                || "com.google.android.googlequicksearchbox".equals(pkg)
                || "com.google.android.apps.bard".equals(pkg)
                || "com.google.android.youtube".equals(pkg);
    }

    /** 读设置界面保存的预设；任何失败回退默认（美国）。 */
    private RegionPreset loadPreset() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            return RegionPreset.byId(prefs.getString(KEY_PRESET_ID, "us"));
        } catch (Throwable t) {
            return RegionPreset.byId("us");
        }
    }

    // ---------------- TelephonyManager ----------------

    private void hookTelephony(ClassLoader cl, final RegionPreset p) {
        final Class<?> tm;
        try {
            tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl);
        } catch (Throwable t) {
            return;
        }
        replace(tm, "getSimCountryIso", p.countryIso);
        replace(tm, "getNetworkCountryIso", p.countryIso);
        replace(tm, "getSimOperator", p.operatorNumeric);
        replace(tm, "getNetworkOperator", p.operatorNumeric);
        replace(tm, "getSimOperatorName", p.operatorName);
        replace(tm, "getNetworkOperatorName", p.operatorName);
        // 带 subId/phoneId 参数的隐藏重载（部分谷歌代码走这里），存在才 hook
        replaceWithInt(tm, "getSimCountryIso", p.countryIso);
        replaceWithInt(tm, "getNetworkCountryIso", p.countryIso);
        replaceWithInt(tm, "getSimOperator", p.operatorNumeric);
        replaceWithInt(tm, "getNetworkOperator", p.operatorNumeric);
        replaceWithInt(tm, "getSimOperatorName", p.operatorName);
        replaceWithInt(tm, "getNetworkOperatorName", p.operatorName);
    }

    private void replace(Class<?> clazz, String method, final Object value) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(value);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
            // 方法不存在/签名不同：跳过，不影响其他 hook
        }
    }

    private void replaceWithInt(Class<?> clazz, String method, final Object value) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(value);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Locale ----------------

    private void hookLocale(final RegionPreset p) {
        final Locale fake = p.toLocale();
        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(fake);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault",
                    Locale.Category.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                param.setResult(fake);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- TimeZone ----------------

    private void hookTimeZone(final RegionPreset p) {
        try {
            XposedHelpers.findAndHookMethod(TimeZone.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(TimeZone.getTimeZone(p.timeZoneId));
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Resources / Configuration ----------------

    private void hookConfiguration(ClassLoader cl, final RegionPreset p) {
        try {
            Class<?> resClass = XposedHelpers.findClass("android.content.res.Resources", cl);
            XposedHelpers.findAndHookMethod(resClass, "getConfiguration", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object result = param.getResult();
                        if (result instanceof Configuration) {
                            // 返回改过 locale 的副本，不动应用手里的原对象
                            Configuration copy = new Configuration((Configuration) result);
                            Locale fake = p.toLocale();
                            copy.setLocale(fake);
                            copy.setLocales(new LocaleList(fake));
                            param.setResult(copy);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- SystemProperties（gsm.* 只读键） ----------------

    private void hookSystemProperties(ClassLoader cl, final RegionPreset p) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1
                                || !(param.args[0] instanceof String)) {
                            return;
                        }
                        String key = (String) param.args[0];
                        String value = null;
                        if ("gsm.sim.operator.iso-country".equals(key)
                                || "gsm.operator.iso-country".equals(key)) {
                            value = p.countryIso;
                        } else if ("gsm.sim.operator.numeric".equals(key)
                                || "gsm.operator.numeric".equals(key)) {
                            value = p.operatorNumeric;
                        } else if ("gsm.sim.operator.alpha".equals(key)
                                || "gsm.operator.alpha".equals(key)) {
                            value = p.operatorName;
                        }
                        if (value != null) {
                            param.setResult(value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, hook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, hook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }
}
