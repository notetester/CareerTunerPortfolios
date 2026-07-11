package com.careertuner.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import com.careertuner.app.configuration.NativeConfigurationPlugin;
import com.careertuner.app.planner.PlannerNativePlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeConfigurationPlugin.class);
        registerPlugin(PlannerNativePlugin.class);
        super.onCreate(savedInstanceState);

        // Capacitor 8은 Android 15+에서 WebView 부모에 system-bar inset padding을 적용한다.
        // 이 padding 영역은 웹 CSS 밖이라 기본 흰색이 남을 수 있으므로, 앱의 고정 dark
        // system chrome 색으로 직접 채워 상태/gesture 아이콘 대비를 보장한다.
        int systemChromeColor = Color.rgb(5, 5, 6);
        getWindow().getDecorView().setBackgroundColor(systemChromeColor);
        if (getBridge() != null && getBridge().getWebView() != null) {
            View webViewParent = (View) getBridge().getWebView().getParent();
            if (webViewParent != null) {
                webViewParent.setBackgroundColor(systemChromeColor);
            }
        }
    }
}
