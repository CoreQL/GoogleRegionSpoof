package io.github.zealler9560.googleregion;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 配置存取（模块自身进程内用）：读写 region_config，
 * 并负责 v1.0 → v1.1 的一次性迁移、以及把配置文件放开读权限
 * 供目标进程的 XSharedPreferences 读取。
 */
public final class Prefs {

    private Prefs() {
    }

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(RegionConfig.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 首次运行落盘默认配置；有 v1.0 的 preset_id 时迁移成新键。 */
    public static void ensureDefaults(Context ctx) {
        SharedPreferences sp = get(ctx);
        if (sp.contains(RegionConfig.KEY_COUNTRY)) {
            return;
        }
        String countryId = "us";
        String legacy = sp.getString(RegionConfig.KEY_LEGACY_PRESET_ID, null);
        if (legacy != null) {
            countryId = RegionData.byId(legacy).id;
        }
        RegionData.Country c = RegionData.byId(countryId);
        RegionData.Carrier carrier = c.carriers[0];
        RegionData.City city = c.cities[0];
        sp.edit()
                .putString(RegionConfig.KEY_COUNTRY, c.id)
                .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                .putBoolean(RegionConfig.KEY_GPS_ENABLED, false)
                .putString(RegionConfig.KEY_GPS_CITY, city.name)
                .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                .commit();
    }

    /** 读当前配置并解析（界面显示用）。 */
    public static RegionConfig.Resolved current(Context ctx) {
        SharedPreferences sp = get(ctx);
        return RegionConfig.resolve(
                sp.getString(RegionConfig.KEY_COUNTRY, "us"),
                sp.getString(RegionConfig.KEY_CARRIER_NUMERIC, null),
                sp.getString(RegionConfig.KEY_GPS_CITY, null),
                sp.getBoolean(RegionConfig.KEY_GPS_ENABLED, false),
                sp.getString(RegionConfig.KEY_GPS_LAT, null),
                sp.getString(RegionConfig.KEY_GPS_LNG, null));
    }

    /** 切国家：运营商重置为该国第一家、GPS 城市重置为首都。 */
    public static void saveCountry(Context ctx, RegionData.Country c) {
        RegionData.Carrier carrier = c.carriers[0];
        RegionData.City city = c.cities[0];
        get(ctx).edit()
                .putString(RegionConfig.KEY_COUNTRY, c.id)
                .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                .putString(RegionConfig.KEY_GPS_CITY, city.name)
                .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveCarrier(Context ctx, RegionData.Carrier carrier) {
        get(ctx).edit()
                .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveCity(Context ctx, RegionData.City city) {
        get(ctx).edit()
                .putString(RegionConfig.KEY_GPS_CITY, city.name)
                .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveGpsEnabled(Context ctx, boolean enabled) {
        get(ctx).edit()
                .putBoolean(RegionConfig.KEY_GPS_ENABLED, enabled)
                .commit();
        makeWorldReadable(ctx);
    }

    /** 让目标进程的 XSharedPreferences 能读到配置文件。失败不影响保存本身。 */
    public static void makeWorldReadable(Context ctx) {
        try {
            File dataDir = new File(ctx.getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, RegionConfig.PREFS_NAME + ".xml");
            dataDir.setExecutable(true, false);
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
