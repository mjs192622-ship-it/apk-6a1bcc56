package com.controlpanel.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class WFBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try { boolean enabled = context.getSharedPreferences("wf_tracking", Context.MODE_PRIVATE).getBoolean("enabled", false); if (!enabled) return; Intent serviceIntent = new Intent(context, WFTrackingService.class); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent); else context.startService(serviceIntent); } catch (Exception ignored) {}
    }
}