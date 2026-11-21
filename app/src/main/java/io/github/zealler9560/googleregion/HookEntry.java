package io.github.zealler9560.googleregion;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.os.LocaleList;
import android.os.SystemClock;
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
 * Locale、TimeZone、Resources 的 Configuration、gsm.* 系统属性；
 * v1.1 起另有可选的 GPS 坐标伪装（默认关，设置里开启后才 hook Location）。
 * 不碰 IP、不碰网络流量。每个 hook 独立 try/catch，失败静默放行原结果，
 * 绝不因本模块让目标应用崩溃。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String MODULE_PACKAGE = "io.github.zealler9560.googleregion";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!isTarget(lpparam.packageName)) {
            return;
        }
        RegionConfig.Resolved cfg = loadConfig();
        ClassLoader cl = lpparam.classLoader;

        hookTelephony(cl, cfg);
        hookLocale(cfg);
        hookTimeZone(cfg);
        hookConfiguration(cl, cfg);
        hookSystemProperties(cl, cfg);
        if (cfg.gpsEnabled) {
            hookGps(cl, lpparam.packageName, cfg);
        }
    }

    private boolean isTarget(String pkg) {
        if (pkg == null) {
            return false;
        }
        // LSPosed 作用域是总闸：本模块只会被加载进用户勾选的应用。
        // 谷歌系包名一律放行——用户手动补勾的任何谷歌应用都真正生效，
        // 不再被写死的名单挡住（v1.2 及以前勾了也不生效的根源）。
        return pkg.startsWith("com.google.")
                || "com.android.vending".equals(pkg)
                || "com.android.chrome".equals(pkg);
    }

    /** 读设置界面保存的配置（分开的键）；任何失败逐项回退默认（美国/T-Mobile）。 */
    private RegionConfig.Resolved loadConfig() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(
                    MODULE_PACKAGE, RegionConfig.PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            // v1.0 迁移：只有旧 preset_id 时用它当国家
            String country = prefs.getString(RegionConfig.KEY_COUNTRY, null);
            if (country == null) {
                country = prefs.getString(RegionConfig.KEY_LEGACY_PRESET_ID, "us");
            }
            return RegionConfig.resolve(country,
                    prefs.getString(RegionConfig.KEY_CARRIER_NUMERIC, null),
                    prefs.getString(RegionConfig.KEY_GPS_CITY, null),
                    prefs.getBoolean(RegionConfig.KEY_GPS_ENABLED, false),
                    prefs.getString(RegionConfig.KEY_GPS_LAT, null),
                    prefs.getString(RegionConfig.KEY_GPS_LNG, null));
        } catch (Throwable t) {
            return RegionConfig.defaults();
        }
    }

    // ---------------- TelephonyManager ----------------

    private void hookTelephony(ClassLoader cl, final RegionConfig.Resolved cfg) {
        final Class<?> tm;
        try {
            tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl);
        } catch (Throwable t) {
            return;
        }
        String iso = cfg.country.iso;
        String numeric = cfg.carrier.numeric;
        String name = cfg.carrier.name;
        replace(tm, "getSimCountryIso", iso);
        replace(tm, "getNetworkCountryIso", iso);
        replace(tm, "getSimOperator", numeric);
        replace(tm, "getNetworkOperator", numeric);
        replace(tm, "getSimOperatorName", name);
        replace(tm, "getNetworkOperatorName", name);
        // 带 subId/phoneId 参数的隐藏重载（部分谷歌代码走这里），存在才 hook
        replaceWithInt(tm, "getSimCountryIso", iso);
        replaceWithInt(tm, "getNetworkCountryIso", iso);
        replaceWithInt(tm, "getSimOperator", numeric);
        replaceWithInt(tm, "getNetworkOperator", numeric);
        replaceWithInt(tm, "getSimOperatorName", name);
        replaceWithInt(tm, "getNetworkOperatorName", name);
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

    private void hookLocale(final RegionConfig.Resolved cfg) {
        final Locale fake = cfg.country.toLocale();
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

    private void hookTimeZone(final RegionConfig.Resolved cfg) {
        try {
            XposedHelpers.findAndHookMethod(TimeZone.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(TimeZone.getTimeZone(cfg.country.timeZoneId));
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Resources / Configuration ----------------

    private void hookConfiguration(ClassLoader cl, final RegionConfig.Resolved cfg) {
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
                            Locale fake = cfg.country.toLocale();
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

    private void hookSystemProperties(ClassLoader cl, final RegionConfig.Resolved cfg) {
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
                            value = cfg.country.iso;
                        } else if ("gsm.sim.operator.numeric".equals(key)
                                || "gsm.operator.numeric".equals(key)) {
                            value = cfg.carrier.numeric;
                        } else if ("gsm.sim.operator.alpha".equals(key)
                                || "gsm.operator.alpha".equals(key)) {
                            value = cfg.carrier.name;
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

    // ---------------- GPS（仅开关开启时） ----------------

    /**
     * GPS 伪装：给所选城市坐标加一个按「包名+城市」哈希出的稳定小偏移
     * （同设备同应用每次一致，约 ±130 米内），避免坐标精确落在市中心点上。
     * hook 面：Location.getLatitude/getLongitude（所有 Location 对象读数改写）、
     * LocationManager.getLastKnownLocation（返回构造的假 Location）。
     */
    private void hookGps(ClassLoader cl, String packageName, RegionConfig.Resolved cfg) {
        int h = (packageName + "|" + cfg.city.name).hashCode();
        final double fakeLat = cfg.gpsLat + (((h & 0xFF) - 128) / 128.0) * 0.0012;
        final double fakeLng = cfg.gpsLng + ((((h >> 8) & 0xFF) - 128) / 128.0) * 0.0012;

        try {
            Class<?> locClass = XposedHelpers.findClass("android.location.Location", cl);
            XposedHelpers.findAndHookMethod(locClass, "getLatitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(fakeLat);
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedHelpers.findAndHookMethod(locClass, "getLongitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(fakeLng);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> lmClass = XposedHelpers.findClass("android.location.LocationManager", cl);
            XposedHelpers.findAndHookMethod(lmClass, "getLastKnownLocation",
                    String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String provider = param.args != null && param.args.length > 0
                                        && param.args[0] instanceof String
                                        ? (String) param.args[0] : "gps";
                                Location fake = new Location(provider);
                                fake.setLatitude(fakeLat);
                                fake.setLongitude(fakeLng);
                                fake.setAccuracy(12f);
                                fake.setTime(System.currentTimeMillis());
                                try {
                                    fake.setElapsedRealtimeNanos(
                                            SystemClock.elapsedRealtimeNanos());
                                } catch (Throwable ignored) {
                                }
                                param.setResult(fake);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }
}
