package com.ptchess.club.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ptchess.club.R;
import com.ptchess.club.ui.SplashActivity;

/** Notification channel setup and posting for incoming pushes. */
public final class Notifications {

    public static final String CHANNEL_ID = "ptchess_general";
    private static int nextId = 1000;

    private Notifications() { }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(ctx.getString(R.string.notif_channel_desc));
        nm.createNotificationChannel(ch);
    }

    public static void show(Context ctx, String title, String body) {
        if (title == null && body == null) return;
        ensureChannel(ctx);

        Intent intent = new Intent(ctx, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(ctx, R.color.blue_primary))
                .setContentTitle(title != null ? title : ctx.getString(R.string.app_name))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        try {
            NotificationManagerCompat.from(ctx).notify(nextId++, b.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted (API 33+) — drop silently.
        }
    }
}
