package com.gazal.audiosync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AudioService extends Service {
    static final String PLAY = "com.gazal.audiosync.PLAY";
    static final String STOP = "com.gazal.audiosync.STOP";
    static final String TOGGLE = "com.gazal.audiosync.TOGGLE";
    static final String OVERLAY = "com.gazal.audiosync.OVERLAY";
    private static final int NOTIFICATION_ID = 54;
    private static final String CHANNEL_ID = "gazal_audio";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView overlayStatus;
    private String station = "Gazal Audio";
    private String state = "Ready";
    private boolean prepared;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Gazal Audio Playback",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("External audio playback and TV overlay");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (STOP.equals(action)) {
            stopAudio();
            removeOverlay();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try { startForeground(NOTIFICATION_ID, notification()); }
        catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (PLAY.equals(action)) {
            startAudio(intent.getStringExtra("url"), intent.getStringExtra("name"), intent.getIntExtra("delay_ms",0));
        } else if (TOGGLE.equals(action)) {
            toggle();
        } else if (OVERLAY.equals(action)) {
            showOverlay();
        }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent p = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return n.setContentTitle(station).setContentText(state)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(p).setOngoing(true).build();
    }
    private void setState(String value) {
        state = value;
        if (overlayStatus != null) overlayStatus.setText(station + " • " + state);
        try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification()); }
        catch (Exception ignored) {}
    }
    private void startAudio(String url, String name, int delay) {
        stopAudio();
        if (!PlaylistStore.valid(url)) { setState("Invalid audio URL"); return; }
        station = name == null || name.isEmpty() ? "External Audio" : name;
        setState("Connecting...");
        MediaPlayer p = new MediaPlayer();
        player = p;
        prepared = false;
        try {
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                prepared = true;
                int ms = Math.max(0, Math.min(30000, delay));
                setState(ms > 0 ? "Ready • start in " + (ms/1000f) + "s" : "Starting...");
                handler.postDelayed(() -> {
                    if (player != mp) return;
                    try { mp.start(); setState("PLAYING"); }
                    catch (Exception e) { setState("Start failed: " + e.getClass().getSimpleName()); }
                }, ms);
            });
            p.setOnErrorListener((mp,what,extra) -> {
                if (mp == player) setState("Audio error " + what + "/" + extra);
                return true;
            });
            p.setOnCompletionListener(mp -> {
                if (mp == player) setState("Completed");
            });
            p.setDataSource(url);
            p.prepareAsync();
        } catch (Exception e) {
            setState("Open failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            stopAudio();
        }
    }
    private void toggle() {
        MediaPlayer p = player;
        if (p == null || !prepared) { setState("Select an audio source first"); return; }
        try {
            if (p.isPlaying()) { p.pause(); setState("PAUSED"); }
            else { p.start(); setState("PLAYING"); }
        } catch (Exception e) { setState("Toggle failed: " + e.getClass().getSimpleName()); }
    }
    private void stopAudio() {
        MediaPlayer old = player;
        player = null;
        prepared = false;
        if (old != null) {
            try { old.reset(); } catch (Exception ignored) {}
            try { old.release(); } catch (Exception ignored) {}
        }
        setState("Stopped");
    }
    private Button overlayButton(String label, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF245584));
        b.setOnClickListener(action);
        return b;
    }
    private void showOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            setState("Overlay permission required");
            return;
        }
        if (overlay != null) return;
        try {
            wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(10,8,10,8);
            bar.setBackgroundColor(0xE8102439);
            overlayStatus = new TextView(this);
            overlayStatus.setTextColor(Color.WHITE);
            overlayStatus.setTextSize(15);
            overlayStatus.setText(station + " • " + state);
            bar.addView(overlayStatus, new LinearLayout.LayoutParams(0, -2, 1f));
            bar.addView(overlayButton("⏯", v -> toggle()));
            bar.addView(overlayButton("×", v -> removeOverlay()));
            int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 30;
            wm.addView(bar, params);
            overlay = bar;
            setState(state);
        } catch (Exception e) {
            overlay = null; overlayStatus = null;
            setState("Overlay failed: " + e.getClass().getSimpleName());
        }
    }
    private void removeOverlay() {
        if (wm != null && overlay != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null; overlayStatus = null;
    }
    @Override public void onDestroy() {
        removeOverlay();
        stopAudio();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
