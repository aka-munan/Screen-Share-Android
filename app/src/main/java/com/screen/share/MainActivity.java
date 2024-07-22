package com.screen.share;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatToggleButton;
import androidx.core.app.ActivityCompat;

import com.screen.share.utils.DoubleClickListener;
import com.screen.share.utils.MediaRecordUtils;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class MainActivity extends AppCompatActivity {

    private EditText editText;
    private Button castBtn;
    private AppCompatToggleButton audioToggle;
    private int state = 0;
    private final int STATE_INIT_SOCKET = 0;
    private final int STATE_STREAMING = 1;
    private final int STATE_SOCKET_CONNECTED = 2;
    public static int width;
    public static int height;
    public static int dp;
    private boolean mShouldUnbind;
    private boolean servicePreRunning = false;


    private WebSocketClient socket;
    private MediaProjectionManager projectionManager;
    private RecorderService recorderService;
    private MediaRecordUtils mediaRecord;

    private final ActivityResultLauncher<Intent> launcher
            = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),

            result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent data = result.getData();
                Intent serviceIntent = new Intent(this, RecorderService.class);
                serviceIntent.putExtra("data", data);
                startService(serviceIntent);
                doBindService();
            }
    );
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), (isGranted) -> {
        if (!isGranted) {
            Toast.makeText(MainActivity.this, "Please grant \"MICROPHONE\" permissions", Toast.LENGTH_SHORT).show();
        }
    });

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            recorderService = ((RecorderService.LocalBinder) service).getService();
            mediaRecord = recorderService.getMediaRecorder();
            state = STATE_STREAMING;
            if (servicePreRunning) {
                socket = mediaRecord.webSocketClient;
                audioToggle.setChecked(mediaRecord.isAudioRecording);
                audioToggle.setVisibility(View.VISIBLE);
                castBtn.setText(mediaRecord.isCasting ? "stop casting" : "cast");
                return;
            }
            recorderService.initSocket(socket);
            recorderService.startScreenRecording();
            mediaRecord = recorderService.getMediaRecorder();
            audioToggle.setVisibility(View.VISIBLE);

            castBtn.setText("Stop casting");
            Log.d("recorder service", "onServiceConnected: recorer service connected");
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d("recorder service", "onServiceDisconnected: recorderService disconnected");
            recorderService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.edit_text);
        castBtn = findViewById(R.id.cast);
        audioToggle = findViewById(R.id.audio_toggle);
        editText.setText("ws://192.168.10.37:8080");
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        //dp width height
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dp = metrics.densityDpi;
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        if (RecorderService.isRunning) {
            servicePreRunning = true;
            doBindService();
        }

        castBtn.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(View view) {
                if (mediaRecord != null) {
                    mediaRecord.stopStreaming();
                    recorderService.stopSelf();
                    mediaRecord=null;
                    recorderService=null;
                }
                doUnbindService();
                castBtn.setText("cast");
                audioToggle.setChecked(false);
                audioToggle.setVisibility(View.GONE);
                state = STATE_SOCKET_CONNECTED;
            }

            @Override
            public void onSingleClick(View view) {
                onCastClicked(state);
            }
        });

        audioToggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (!buttonView.isPressed())//button is not pressed manually
                return;
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    buttonView.setChecked(false);
                    return;
                }
                Log.d("audio recording", "onCreate: audio rec started");
                recorderService.getMediaRecorder().startAudioRec();
            } else
                recorderService.getMediaRecorder().stopAudioRec();

        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
        if (mediaRecord != null && (!(mediaRecord.isCasting || mediaRecord.isAudioRecording ))) {
            mediaRecord.stopStreaming();
            recorderService.stopSelf();
            mediaRecord=null;
            recorderService=null;
            Log.d("closing service", "onDestroy: closing service");
        }
    }

    private void onCastClicked(int state) {
        switch (state) {
            case STATE_INIT_SOCKET:
                initWebSocket(editText.getText().toString());
                break;
            case STATE_STREAMING:
                castBtn.setText(mediaRecord.isCasting ? "Cast" : "Stop casting");
                if (mediaRecord.isCasting)
                    mediaRecord.pauseVideoStreaming();
                else
                    mediaRecord.resumeVideoStreaming();
                break;
            case STATE_SOCKET_CONNECTED:
                cast();
                break;
        }
    }

    void doBindService() {
        if (bindService(new Intent(MainActivity.this, RecorderService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            mShouldUnbind = true;
        } else {
            Log.e("Service binding", "doBindService: failed to bind service ");
        }
    }

    void doUnbindService() {
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
        }
    }

    private void cast() {
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        launcher.launch(projectionManager.createScreenCaptureIntent());
        // startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE);
    }

    public void initWebSocket(String url) {
        try {
            URI uri = URI.create(url);
            Log.d("ws", "initWebSocket: trying to connect " + uri);
            socket = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d("ws", "onOpen: connected");
                    runOnUiThread(() -> {
                        castBtn.setText("Cast");
                        state = STATE_SOCKET_CONNECTED;
                    });
                }

                @Override
                public void onMessage(String message) {

                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(() -> {
                        castBtn.setOnClickListener((view) -> {
                            initWebSocket(editText.getText().toString());
                            castBtn.setText("Connect");

                        });
                    });
                }

                @Override
                public void onError(Exception ex) {
                    Log.e("ws err", "onError: " + ex);
                }
            };
            socket.connect();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}