package com.controlpanel.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class NotificationWorker extends Worker {

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("bp_prefs", Context.MODE_PRIVATE);
        String cookies = prefs.getString("session_cookies", null);
        String websiteUrl = prefs.getString("website_url", null);
        int lastNotifId = prefs.getInt("last_notif_id", 0);

        if (cookies == null || websiteUrl == null) return Result.success();

        try {
            URL url = new URL(websiteUrl + "/notifications.php?ajax=check");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Cookie", cookies);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            int unread = json.optInt("unread", 0);

            if (json.has("latest") && !json.isNull("latest")) {
                JSONObject latest = json.getJSONObject("latest");
                int notifId = latest.optInt("id", 0);

                if (notifId > lastNotifId && lastNotifId > 0 && unread > 0) {
                    String title = latest.optString("title", "New Notification");
                    String message = latest.optString("message", "");
                    showNotification(ctx, title, message);
                }

                if (notifId > 0) {
                    prefs.edit().putInt("last_notif_id", notifId).apply();
                }
            }
        } catch (Exception e) {
            // Silent fail — best effort
        }

        return Result.success();
    }

    private void showNotification(Context ctx, String title, String body) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "bp_notifications";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "App Notifications", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Background notifications");
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }
}