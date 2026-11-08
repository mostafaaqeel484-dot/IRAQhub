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
import android.util.DisplayMetrics;
import java.util.ArrayList;

public final class AudioService extends Service {
    static final String PLAY = "com.gazal.audiosync.PLAY";
    static final String STOP = "com.gazal.audiosync.STOP";
    static final String TOGGLE = "com.gazal.audiosync.TOGGLE";
    static final String OVERLAY = "com.gazal.audiosync.OVERLAY";
    public static volatile String LAST_STATE = "READY";
    public static volatile String LAST_STATION = "No audio selected";
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
        LAST_STATE = value;
        LAST_STATION = station;
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
        removeOverlay();
        try {
            wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            int navy=0xFF0B233A, tile=0xFF173C5E, cyan=0xFF40C1EC;
            LinearLayout box=new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundColor(0xF0081F35);
            box.setPadding(14,12,14,12);
            box.setFocusableInTouchMode(true);
            box.setOnKeyListener((view,key,event)-> {
                if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN) return false;
                if(key==android.view.KeyEvent.KEYCODE_BACK||key==android.view.KeyEvent.KEYCODE_PROG_RED){
                    removeOverlay(); return true;
                }
                if(key==android.view.KeyEvent.KEYCODE_PROG_GREEN){
                    stopAudio();removeOverlay();stopForeground(true);stopSelf();return true;
                }
                if(key==android.view.KeyEvent.KEYCODE_PAGE_DOWN||key==android.view.KeyEvent.KEYCODE_PAGE_UP){
                    android.content.Intent a=new android.content.Intent(this,Ben10ConsoleActivity.class);
                    a.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(a); removeOverlay();return true;
                }
                return false;
            });
            LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
            overlayStatus=new TextView(this);
            overlayStatus.setTextColor(Color.WHITE);overlayStatus.setTextSize(17);
            overlayStatus.setText("BEN10 STYLE • AUTO TIME SHIFT\n"+station+" • "+state);
            header.addView(overlayStatus,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(overlayButton("−",v->playerVolume(-0.1f)));
            header.addView(overlayButton("+",v->playerVolume(0.1f)));
            header.addView(overlayButton("×",v->removeOverlay()));
            box.addView(header);
            TextView playlist=new TextView(this);
            playlist.setTextColor(cyan);playlist.setTextSize(14);
            playlist.setText("PLAYLIST • GAZAL • External audio / DVB");
            playlist.setPadding(4,10,4,10);box.addView(playlist);
            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(overlayButton("▣ M3U",v->openFullConsole()),new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(overlayButton("◇ CFG",v->openFullConsole()),new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(overlayButton("＋ SOURCE",v->openFullConsole()),new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(overlayButton("▶/Ⅱ",v->toggle()),new LinearLayout.LayoutParams(0,-2,1));
            box.addView(actions);
            LinearLayout columns=new LinearLayout(this);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout choices=new LinearLayout(this);
            choices.setOrientation(LinearLayout.VERTICAL);
            choices.setPadding(4,6,8,6);
            choices.setBackgroundColor(tile);
            TextView label=new TextView(this);
            label.setTextColor(cyan);label.setTextSize(14);label.setText("AUDIO SOURCES");
            choices.addView(label);
            ArrayList<PlaylistStore.Source> sources=PlaylistStore.load(this);
            for(int i=0;i<Math.min(6,sources.size());i++){
                PlaylistStore.Source src=sources.get(i);
                Button b=overlayButton(src.name,v->startAudio(src.url,src.name,0));
                b.setTextSize(12);
                LinearLayout.LayoutParams choiceParam=new LinearLayout.LayoutParams(-1,0,1);
                choiceParam.setMargins(3,3,3,3);choices.addView(b,choiceParam);
            }
            if(sources.isEmpty()){
                TextView empty=new TextView(this);empty.setTextColor(Color.WHITE);
                empty.setText("No sources • open full panel to import");
                choices.addView(empty);
            }
            TextView pages=new TextView(this);
            pages.setTextColor(Color.WHITE);pages.setTextSize(11);
            pages.setText("Showing first "+Math.min(6,sources.size())+" / "+sources.size()+" • Full panel for pages");
            choices.addView(pages);
            columns.addView(choices,new LinearLayout.LayoutParams(0,-1,4));
            LinearLayout details=new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(14,10,5,10);
            details.setBackgroundColor(navy);
            TextView now=new TextView(this);
            now.setTextSize(19);now.setTextColor(Color.WHITE);
            now.setText(station);
            details.addView(now);
            TextView epg=new TextView(this);
            epg.setTextSize(12);epg.setTextColor(0xFFB8D4E6);
            epg.setText("NOW • EPG not linked\nNEXT • Programme unavailable");
            epg.setPadding(2,10,2,10);details.addView(epg);
            TextView sound=new TextView(this);sound.setTextSize(13);sound.setTextColor(cyan);
            sound.setText("EXTERNAL AUDIO  •  "+state+"\nDVB PCM  •  tuner integration pending");
            details.addView(sound);
            TextView warning=new TextView(this);warning.setTextSize(12);warning.setTextColor(0xFFFFD488);
            warning.setText("AUTO SYNC UNVERIFIED\nVIDEO DELAY: requires OEM TV Input TimeShift\nAUDIO: external MediaPlayer output");
            warning.setPadding(3,12,3,12);details.addView(warning);
            details.addView(overlayButton("DVB TIMESHIFT",v->{
                android.content.Intent a=new android.content.Intent(this,DvbSessionActivity.class);
                a.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(a);removeOverlay();
            }));
            columns.addView(details,new LinearLayout.LayoutParams(0,-1,6));
            box.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout footer=new LinearLayout(this);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.addView(overlayButton("RESTORE DVB",v->{
                stopAudio();removeOverlay();stopForeground(true);stopSelf();
            }),new LinearLayout.LayoutParams(0,-2,1));
            footer.addView(overlayButton("FULL PANEL",v->openFullConsole()),new LinearLayout.LayoutParams(0,-2,1));
            footer.addView(overlayButton("CLOSE",v->removeOverlay()),new LinearLayout.LayoutParams(0,-2,1));
            box.addView(footer);
            DisplayMetrics metrics=getResources().getDisplayMetrics();
            int width=(int)(metrics.widthPixels*0.84);
            int height=(int)(metrics.heightPixels*0.80);
            int type=Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params=new WindowManager.LayoutParams(
                width,height,type,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            params.y=Math.max(8,(int)(metrics.heightPixels*0.03f));
            wm.addView(box,params);
            overlay=box;
            box.requestFocus();
        } catch(Exception e){
            overlay=null; overlayStatus=null;
            setState("Overlay failed: "+e.getClass().getSimpleName()+" "+e.getMessage());
        }
    }
    private void openFullConsole() {
        android.content.Intent a=new android.content.Intent(this,Ben10ConsoleActivity.class);
        a.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
        removeOverlay();
    }
    private float mediaVolume=1f;
    private void playerVolume(float delta) {
        mediaVolume=Math.max(0f,Math.min(1f,mediaVolume+delta));
        if(player!=null)player.setVolume(mediaVolume,mediaVolume);
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
