package com.ptchess.club.push;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.ptchess.club.data.firebase.PushTokens;

/**
 * Receives FCM messages. A background message that carries a {@code notification}
 * payload is shown by the system directly (using the default icon/color/channel
 * declared in the manifest); this handler covers foreground and data messages.
 */
public class PtMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        PushTokens.save(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = null;
        String body = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (title == null && body == null && message.getData() != null) {
            title = message.getData().get("title");
            body = message.getData().get("body");
        }
        Notifications.show(getApplicationContext(), title, body);
    }
}
