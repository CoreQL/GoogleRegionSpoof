package io.github.zealler9560.googleregion;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * 系统栏 insets 处理：应用是 edge-to-edge 绘制，根布局默认不避让状态栏/导航栏，
 * 内容会顶进状态栏。用框架原生 API 监听 WindowInsets，把系统栏
 * （状态栏 + 导航栏 + 刘海/挖孔）高度并入根布局 padding：
 * 每边取 max(原 padding, inset)，既避让系统栏又不丢原有间距。
 */
final class SystemBarInsets {

    private SystemBarInsets() {
    }

    static void applyTo(final View root) {
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                final int left, top, right, bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
                    left = bars.left;
                    top = bars.top;
                    right = bars.right;
                    bottom = bars.bottom;
                } else {
                    // API 26–29 回退：系统窗口 insets（已含状态栏/导航栏/挖孔避让）
                    left = insets.getSystemWindowInsetLeft();
                    top = insets.getSystemWindowInsetTop();
                    right = insets.getSystemWindowInsetRight();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(
                        Math.max(baseLeft, left),
                        Math.max(baseTop, top),
                        Math.max(baseRight, right),
                        Math.max(baseBottom, bottom));
                // 不消费，子视图仍可收到 insets
                return insets;
            }
        });
        // 立即请求一次分发，避免首帧先贴顶再跳
        root.requestApplyInsets();
    }
}
