package com.screen.share;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.screen.share.utils.MediaRecordUtils;

import org.java_websocket.client.WebSocketClient;


public class RecorderService extends Service {
    private MediaProjection mediaProjection;
    private MediaRecordUtils mediaRecord;
    private WebSocketClient socket;
    public static boolean isRunning = false;
    private final IBinder mBinder = new LocalBinder();
    private static final String CHANNEL_ID = "ScreenCaptureServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());
        isRunning=true;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent data = intent.getParcelableExtra("data");
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(-1, data);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("recorder service", "onDestroy: service destroyed");
        isRunning=false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void initSocket(WebSocketClient socket) {
        this.socket = socket;
    }

    public void startScreenRecording() {
        mediaRecord = new MediaRecordUtils(getApplicationContext(), mediaProjection);
        mediaRecord.initWebSocket(socket);
        mediaRecord.startVideoStreaming(MainActivity.width, MainActivity.height);
    }



   public MediaRecordUtils getMediaRecorder(){
        return mediaRecord;
   }
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture Service")
                .setContentText("Screen capture in progress")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }


    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    public class LocalBinder extends Binder {
        RecorderService getService() {
            return RecorderService.this;
        }
    }
}
