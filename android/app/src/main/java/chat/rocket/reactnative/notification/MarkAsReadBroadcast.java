package chat.rocket.reactnative.notification;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles the "mark as read" action surfaced on MessagingStyle notifications.
 * Android Auto requires a mark-as-read action with SEMANTIC_ACTION_MARK_AS_READ
 * and setShowsUserInterface(false) so the user can clear a conversation without
 * opening the app while projecting.
 */
public class MarkAsReadBroadcast extends BroadcastReceiver {
    private static final String TAG = "RocketChat.MarkAsRead";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getBundleExtra("pushNotification");
        if (bundle == null) {
            bundle = intent.getExtras();
        }
        if (bundle == null) {
            return;
        }

        String notId = bundle.getString("notId");
        if (notId == null) {
            return;
        }

        Ejson ejson = new Gson().fromJson(bundle.getString("ejson", "{}"), Ejson.class);
        if (ejson == null) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(notId);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid notification ID: " + notId, e);
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        markRoomRead(ejson, id, notificationManager);
    }

    private void markRoomRead(final Ejson ejson, final int notId, final NotificationManager notificationManager) {
        String serverURL = ejson.serverURL();
        String rid = ejson.rid;

        if (serverURL == null || rid == null) {
            return;
        }

        String token = ejson.token();
        String userId = ejson.userId();
        if (token == null || token.isEmpty() || userId == null || userId.isEmpty()) {
            Log.w(TAG, "Missing auth credentials, cannot mark as read");
            return;
        }

        final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        Map<String, String> payload = new HashMap<>();
        payload.put("rid", rid);
        String json = new GsonBuilder().create().toJson(payload);

        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .header("x-auth-token", token)
                .header("x-user-id", userId)
                .header("User-Agent", NotificationHelper.getUserAgent())
                .url(String.format("%s/api/v1/subscriptions.read", serverURL))
                .post(body)
                .build();

        new OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, String.format("MarkAsRead FAILED exception %s", e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "MarkAsRead SUCCESS");
                    CustomPushNotification.clearMessages(notId);
                    if (notificationManager != null) {
                        notificationManager.cancel(notId);
                    }
                } else {
                    Log.i(TAG, String.format("MarkAsRead FAILED status %s", response.code()));
                }
                response.close();
            }
        });
    }
}
