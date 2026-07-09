package com.careertuner.app;

import android.os.Bundle;

import com.careertuner.app.planner.PlannerNativePlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PlannerNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
