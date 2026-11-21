package io.github.zealler9560.googleregion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * 模块自有界面的配色：浅色/深色/跟随系统 × 主题色。
 * 全部用代码里的色值，不依赖系统主题资源，保证两种模式下排版一致、不裁切。
 */
public final class UiTheme {

    public final boolean dark;
    public final int accent;
    public final int bg;        // 页面背景
    public final int card;      // 卡片背景
    public final int cardBorder;
    public final int text;      // 主文字
    public final int subText;   // 次文字
    public final int accentSoft; // 主题色浅底（选中态）

    private UiTheme(boolean dark, int accent) {
        this.dark = dark;
        this.accent = accent;
        if (dark) {
            bg = Color.rgb(18, 18, 20);
            card = Color.rgb(32, 32, 36);
            cardBorder = Color.rgb(58, 58, 64);
            text = Color.rgb(242, 242, 242);
            subText = Color.rgb(168, 168, 174);
        } else {
            bg = Color.rgb(245, 245, 247);
            card = Color.WHITE;
            cardBorder = Color.rgb(224, 224, 228);
            text = Color.rgb(26, 26, 28);
            subText = Color.rgb(110, 110, 115);
        }
        accentSoft = Color.argb(dark ? 70 : 36,
                Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    public static final String[] ACCENT_KEYS = {"orange", "blue", "green", "purple", "red"};
    public static final String[] ACCENT_NAMES = {"橙", "蓝", "绿", "紫", "红"};

    public static int accentColor(String key) {
        if ("blue".equals(key)) return Color.rgb(47, 128, 237);
        if ("green".equals(key)) return Color.rgb(33, 150, 83);
        if ("purple".equals(key)) return Color.rgb(155, 81, 224);
        if ("red".equals(key)) return Color.rgb(235, 87, 87);
        return Color.rgb(255, 138, 0); // orange 默认，同 GlobalKit
    }

    /** 读配置并解析出当前配色。themeMode: 0 跟随系统 / 1 浅色 / 2 深色。 */
    public static UiTheme load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(RegionConfig.PREFS_NAME,
                Context.MODE_PRIVATE);
        int mode = sp.getInt(RegionConfig.KEY_UI_THEME, 0);
        String accentKey = sp.getString(RegionConfig.KEY_UI_ACCENT, "orange");
        boolean dark;
        if (mode == 1) {
            dark = false;
        } else if (mode == 2) {
            dark = true;
        } else {
            int night = ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        }
        return new UiTheme(dark, accentColor(accentKey));
    }
}
