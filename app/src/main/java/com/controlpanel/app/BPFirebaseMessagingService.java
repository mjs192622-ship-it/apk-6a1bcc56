package com.controlpanel.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BPFirebaseMessagingService extends FirebaseMessagingService {

    private static final String SERVER_URL = "https://controlpaneldevice.netlify.app/fcm_token.php";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        sendTokenToServer(token);
    }

    private void sendTokenToServer(final String token) {
        new Thread(() -> {
            try {
                String endpoint = SERVER_URL == null ? "" : SERVER_URL.trim();
                if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                    endpoint = "https://" + endpoint;
                }
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                String body = "{\"token\":\"" + token + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode(); // trigger request
                conn.disconnect();

                // Fallback: send as GET query param if POST did not succeed
                if (status < 200 || status >= 300) {
                    String fallback = endpoint + (endpoint.contains("?") ? "&" : "?") + "t=" + java.net.URLEncoder.encode(token, "UTF-8");
                    HttpURLConnection fb = (HttpURLConnection) new URL(fallback).openConnection();
                    fb.setRequestMethod("GET");
                    fb.setConnectTimeout(10000);
                    fb.setReadTimeout(10000);
                    fb.getResponseCode();
                    fb.disconnect();
                }
            } catch (Exception e) {
                // Silent fail
            }
        }).start();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        String title = "Notification";
        String body = "";
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                ? remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null
                ? remoteMessage.getNotification().getBody() : body;
        } else if (!remoteMessage.getData().isEmpty()) {
            title = remoteMessage.getData().getOrDefault("title", title);
            body = remoteMessage.getData().getOrDefault("body", body);
        }
        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "bp_fcm_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "Push Notifications", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
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