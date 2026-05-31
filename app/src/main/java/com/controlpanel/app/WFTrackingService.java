package com.controlpanel.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class WFTrackingService extends Service implements LocationListener {
    private static final String WEBSITE_URL = "https://controlpaneldevice.netlify.app";
    private static final String CHANNEL_ID = "wf_tracking_channel";
    private static final int NOTIFICATION_ID = 9107;
    private LocationManager locationManager;
    private Location lastLocation;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private long heartbeatMs = 30000L;

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        getSharedPreferences("wf_tracking", MODE_PRIVATE).edit().putBoolean("service_running", true).apply();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Background tracking is active"));
        setupHeartbeat();
        sendPoint(null, "service_start");
        startLocationUpdates();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences("wf_tracking", MODE_PRIVATE).edit().putBoolean("enabled", true).putBoolean("service_running", true).apply();
        startLocationUpdates();
        return START_STICKY;
    }
    private void setupHeartbeat() {
        heartbeatMs = Math.max(15000L, getMinIntervalSeconds() * 1000L);
        heartbeatRunnable = new Runnable() { @Override public void run() { sendPoint(lastLocation, lastLocation == null ? "battery_heartbeat" : "background_location"); handler.postDelayed(this, heartbeatMs); } };
        handler.postDelayed(heartbeatRunnable, heartbeatMs);
    }
    private int getMinIntervalSeconds() { try { return Math.max(15, getConfig().optInt("min_interval_seconds", 30)); } catch (Exception e) { return 30; } }
    private void startLocationUpdates() {
        try {
            if (!hasLocationPermission()) return;
            if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) return;
            long minMs = Math.max(15000L, getMinIntervalSeconds() * 1000L);
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minMs, 5f, this, Looper.getMainLooper()); } catch (Exception ignored) {}
            try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minMs, 10f, this, Looper.getMainLooper()); } catch (Exception ignored) {}
            try {
                Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gps != null) lastLocation = gps;
                if (net != null && (lastLocation == null || net.getTime() > lastLocation.getTime())) lastLocation = net;
                if (lastLocation != null) sendPoint(lastLocation, "background_location");
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onLocationChanged(Location location) { lastLocation = location; sendPoint(location, "background_location"); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) { sendPoint(lastLocation, "location_provider_disabled"); }
    private JSONObject getConfig() { try { String json = getSharedPreferences("wf_tracking", MODE_PRIVATE).getString("config", "{}"); return new JSONObject(json == null || json.trim().isEmpty() ? "{}" : json); } catch (Exception e) { return new JSONObject(); } }
    private String getEndpoint(JSONObject cfg) { String endpoint = cfg.optString("endpoint", "").trim(); if (!endpoint.isEmpty()) return endpoint; return getOrigin(WEBSITE_URL) + "/api/location_background_api.php"; }
    private String getOrigin(String url) { try { URI uri = new URI(url); String origin = uri.getScheme() + "://" + uri.getHost(); if (uri.getPort() > 0) origin += ":" + uri.getPort(); return origin; } catch (Exception e) { return url.replaceAll("/+$", "").replaceAll("/(app|admin|supervisor)(/.*)?$", ""); } }
    private void sendPoint(final Location location, final String eventType) { new Thread(() -> { try {
        JSONObject cfg = getConfig(); String token = cfg.optString("tracking_token", cfg.optString("token", "")); JSONObject body = new JSONObject();
        if (location != null) { body.put("latitude", location.getLatitude()); body.put("longitude", location.getLongitude()); body.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL); body.put("speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL); body.put("heading", location.hasBearing() ? location.getBearing() : JSONObject.NULL); body.put("provider", location.getProvider()); }
        body.put("source", "native_background"); body.put("event_type", eventType); body.put("service_state", "foreground_service_running"); body.put("app_state", "closed_or_background"); body.put("client_timestamp", isoNow()); body.put("battery_level", getBatteryLevel()); body.put("battery_charging", isCharging()); body.put("network_status", isOnline() ? "online" : "offline"); body.put("connection_type", getConnectionType()); body.put("device_info", Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE);
        HttpURLConnection conn = (HttpURLConnection) new URL(getEndpoint(cfg)).openConnection(); conn.setRequestMethod("POST"); conn.setRequestProperty("Content-Type", "application/json"); if (token != null && !token.isEmpty()) conn.setRequestProperty("X-WF-Tracking-Token", token); conn.setConnectTimeout(15000); conn.setReadTimeout(15000); conn.setDoOutput(true); try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); } conn.getResponseCode(); conn.disconnect();
    } catch (Exception ignored) {} }).start(); }
    private String isoNow() { SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US); sdf.setTimeZone(TimeZone.getDefault()); return sdf.format(new Date()); }
    private int getBatteryLevel() { try { Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1); if (level >= 0 && scale > 0) return Math.round(level * 100f / scale); } catch (Exception ignored) {} return -1; }
    private boolean isCharging() { try { Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); int status = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1); return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL; } catch (Exception ignored) { return false; } }
    private boolean isOnline() { try { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); if (cm == null) return false; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { Network network = cm.getActiveNetwork(); if (network == null) return false; NetworkCapabilities caps = cm.getNetworkCapabilities(network); return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)); } else { NetworkInfo info = cm.getActiveNetworkInfo(); return info != null && info.isConnected(); } } catch (Exception e) { return false; } }
    private String getConnectionType() { try { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); if (cm == null) return "unknown"; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork()); if (caps == null) return "offline"; if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi"; if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "mobile"; if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet"; } else { NetworkInfo info = cm.getActiveNetworkInfo(); if (info != null) return info.getTypeName().toLowerCase(Locale.US); } } catch (Exception ignored) {} return "unknown"; }
    private void createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Workforce tracking", NotificationManager.IMPORTANCE_LOW); channel.setDescription("Keeps employee location tracking active in background"); NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); if (manager != null) manager.createNotificationChannel(channel); } }
    private Notification buildNotification(String text) { Intent intent = new Intent(this, MainActivity.class); PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this); builder.setContentTitle("CONTROL PANEL").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(pi).setOngoing(true); if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setPriority(Notification.PRIORITY_LOW); return builder.build(); }
    @Override public void onDestroy() { try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) {} try { if (handler != null && heartbeatRunnable != null) handler.removeCallbacks(heartbeatRunnable); } catch (Exception ignored) {} sendPoint(lastLocation, "service_stop"); getSharedPreferences("wf_tracking", MODE_PRIVATE).edit().putBoolean("service_running", false).apply(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}