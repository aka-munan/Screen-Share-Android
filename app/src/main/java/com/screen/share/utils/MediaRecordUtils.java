package com.screen.share.utils;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import com.screen.share.MainActivity;

import org.java_websocket.client.WebSocketClient;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MediaRecordUtils {
    private final Context context;
    private final MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int bufferSize;
    private AudioRecord audioRecord;
    public boolean isAudioRecording;
    public boolean isCasting=false;
    public WebSocketClient webSocketClient;


    public MediaRecordUtils(Context context, MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
        this.context = context;
    }

    public void startVideoStreaming(int witdh, int height) {
        setupImageReader(witdh, height);
        setupVirtualDisplay();
        virtualDisplay.setSurface(imageReader.getSurface());
        setUpAudioRecord();
        isCasting=true;
    }
    public void resumeVideoStreaming(){
        virtualDisplay.setSurface(imageReader.getSurface());
        isCasting=true;
    }
    public void pauseVideoStreaming() {
        virtualDisplay.setSurface(null);
                isCasting=false;
    }

    public void stopStreaming(){
        mediaProjection.stop();
        virtualDisplay.release();
        imageReader.close();
        isCasting=false;
        isAudioRecording=false;
        if(audioRecord==null)
            return;
        audioRecord.stop();
        audioRecord.release();

    }
    public void initWebSocket(WebSocketClient client) {
        webSocketClient = client;
    }


    private void setupImageReader(int width, int height) {
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * image.getWidth();

                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    // Convert bitmap to byte array and send to WebSocket server
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 20, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    sendData((byte) 0, byteArray);
                    Thread.sleep(1000 / 30);//30fps
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, null);
    }

    private void setupVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",  MainActivity.width, MainActivity.height, MainActivity.dp,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, null);
    }

    private void setUpAudioRecord() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bufferSize = AudioRecord.getMinBufferSize(48000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.getAudioSessionId()).setEnabled(true);
        }

        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.getAudioSessionId()).setEnabled(true);
        }

        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.getAudioSessionId()).setEnabled(true);
        }
    }

    public void startAudioRec() throws RuntimeException {
        if (audioRecord == null)
            setUpAudioRecord();
        audioRecord.startRecording();
        isAudioRecording = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[bufferSize];
                while (isAudioRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        sendData((byte) 1, buffer);
                    }
                }
            }
        }).start();
    }

    public void stopAudioRec() throws RuntimeException {
        if (audioRecord == null)
            throw new RuntimeException(" Audio record not initialised");
        audioRecord.stop();
        isAudioRecording=false;
    }

    private void sendData(byte type, byte[] data) {
        if (webSocketClient == null)
            Log.e("ws", "sendData: socket is null");
        int offset = (type == 0 ? 1 : 2);
        byte[] message = new byte[data.length + offset];//
        message[0] = type;
        System.arraycopy(data, 0, message, offset, data.length);
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(message);
        }

    }

}
