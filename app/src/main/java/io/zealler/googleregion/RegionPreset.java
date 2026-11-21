package io.zealler.googleregion;

import java.util.Locale;

/**
 * 区域预设：一组互相匹配的区域信号（国家码 / 运营商 / 语言 / 时区）。
 * 纯 Java、无 Android 依赖，设置界面与 hook 侧共用同一份数据，
 * 保证两边看到的预设永远一致。
 */
public final class RegionPreset {

    public final String id;            // 预设标识（存配置用）
    public final String displayName;   // 界面显示名
    public final String countryIso;    // ISO 国家码（小写，两位）
    public final String operatorNumeric; // MCC+MNC
    public final String operatorName;  // 运营商名
    public final String language;      // Locale 语言
    public final String country;       // Locale 国家（大写）
    public final String timeZoneId;    // 时区 ID

    public RegionPreset(String id, String displayName, String countryIso,
                        String operatorNumeric, String operatorName,
                        String language, String country, String timeZoneId) {
        this.id = id;
        this.displayName = displayName;
        this.countryIso = countryIso;
        this.operatorNumeric = operatorNumeric;
        this.operatorName = operatorName;
        this.language = language;
        this.country = country;
        this.timeZoneId = timeZoneId;
    }

    public Locale toLocale() {
        return new Locale(language, country);
    }

    /** 全部预设。第一个即默认值（美国）。 */
    public static final RegionPreset[] ALL = {
            new RegionPreset("us", "美国", "us", "310260", "T-Mobile",
                    "en", "US", "America/New_York"),
            new RegionPreset("jp", "日本", "jp", "44010", "NTT docomo",
                    "ja", "JP", "Asia/Tokyo"),
            new RegionPreset("sg", "新加坡", "sg", "52501", "Singtel",
                    "en", "SG", "Asia/Singapore"),
            new RegionPreset("hk", "香港", "hk", "45400", "CMHK",
                    "zh", "HK", "Asia/Hong_Kong"),
            new RegionPreset("tw", "台湾", "tw", "46692", "Chunghwa Telecom",
                    "zh", "TW", "Asia/Taipei"),
            new RegionPreset("gb", "英国", "gb", "23430", "EE",
                    "en", "GB", "Europe/London"),
            new RegionPreset("de", "德国", "de", "26201", "Telekom.de",
                    "de", "DE", "Europe/Berlin"),
    };

    public static RegionPreset byId(String id) {
        if (id != null) {
            for (RegionPreset p : ALL) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return ALL[0]; // 读不到 / 不认识的 id 一律回退默认（美国）
    }
}
