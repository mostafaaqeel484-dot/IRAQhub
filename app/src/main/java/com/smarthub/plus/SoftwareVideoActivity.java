package com.smarthub.plus;

import android.net.Uri;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

/**
 * Explicit software-decoding fallback for IPTV/file streams.
 * Hardware decoding is disabled intentionally. This activity does not claim
 * hardware 4:2:2 support and does not control the OEM DVB tuner.
 */
public final class SoftwareVideoActivity extends HubActivity {
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private TextView status;
    private boolean released;
    private LiveTsRelay satelliteRelay;
    private LocalInfoBarController localInfo;
    private final java.util.concurrent.ExecutorService relayThread=java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        videoLayout = new VLCVideoLayout(this);
        root.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setText(tx("تهيئة فك الترميز البرمجي…", "Preparing software decoder…"));
        status.setTextColor(0xffffffff);
        status.setBackgroundColor(0xb0000000);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(status, statusParams);
        TextView tracks = new TextView(this);
        tracks.setText(tx("مسارات الصوت والترجمة", "Audio / subtitle tracks"));
        tracks.setTextColor(0xffffffff);
        tracks.setBackgroundColor(0xb0000000);
        tracks.setPadding(dp(16), dp(10), dp(16), dp(10));
        tracks.setFocusable(true);
        tracks.setOnClickListener(v -> showTrackMenu());
        FrameLayout.LayoutParams trackParams =
                new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        root.addView(tracks, trackParams);
        setContentView(root);
        localInfo=new LocalInfoBarController(this,root);

        long dvbChannel=getIntent().getLongExtra("dvb_channel",-1L);
        if(dvbChannel>=0){startSatellite(dvbChannel);return;}
        String uri = getIntent().getStringExtra("media_uri");
        if (uri == null || uri.trim().isEmpty()) {
            status.setText(tx("رابط الوسائط غير موجود", "Media URI is missing"));
            return;
        }
        open(uri);
    }

    private void startSatellite(long channelId){
      status.setText(tx("تهيئة قناة DVB لـ4:2:2 داخل IRAQhub…","Preparing satellite channel for software 4:2:2…"));
      relayThread.execute(()->{
        try{
          ReceiverCatalog db=ReceiverCatalog.get(this);
          db.ensure();
          ReceiverCatalog.Channel channel=db.channel(channelId);
          if(channel==null)throw new java.io.IOException("CHANNEL_NOT_FOUND");
          if(channel.ca!=0||channel.tv!=0)throw new java.io.IOException("SOFTWARE_FTA_ONLY");
          ReceiverSignalReading tuner=DvbTuner.ensure(this,channel);
          prefs().edit().putBoolean("dvb_tuning",false).apply();
          org.json.JSONObject request=DvbPlayRequest.create(channel.data,(int)channel.id,channel.sid,
              channel.angle,channel.frequency,channel.pol,channel.symbolRate,tuner.tp,0,false);
          request.put("tp_id",tuner.tp);
          if(released)return;
          LiveTsRelay relay=new LiveTsRelay(this,channel,request,new LiveTsRelay.Listener(){
            @Override public void ready(String uri){
              if(released)return;
              android.content.SharedPreferences.Editor prefsEdit=prefs().edit();
              prefsEdit.putBoolean("software_dvb_422_active",true)
                .putString("decoder_channel","DVB:"+channel.physical())
                .remove("decoder_bitrate").remove("decoder_width").remove("decoder_height")
                .putString("own_ca_channel","DVB:"+channel.physical())
                .putInt("own_ca_flag",0).putString("own_ca_systems","").apply();
              SatelliteState previous=SatelliteState.read(SoftwareVideoActivity.this);
              SatelliteState next=previous.copy();
              if(!previous.channelId.equals("DVB:"+channel.physical())){
                next.title="";next.nextTitle="";next.image="";next.nextImage="";
                next.startMs=0;next.endMs=0;next.nextStartMs=0;next.nextEndMs=0;
              }
              next.channelId="DVB:"+channel.physical();
              next.channelName=channel.name;
              next.source="DVB · IRAQhub · LibVLC";
              next.observedAt=System.currentTimeMillis();
              next.save(SoftwareVideoActivity.this);
              status.setText(tx("التقاط بث DVB نشط؛ جارٍ فك الفيديو عبر LibVLC…",
                 "DVB MPEG-TS capture active; decoding via LibVLC…"));
              open(uri);
              if(localInfo!=null)localInfo.show();
            }
            @Override public void error(String cause){
              if(!released)status.setText(tx("تعذر التقاط MPEG-TS: ","MPEG-TS capture failed: ")+cause);
            }
          });
          satelliteRelay=relay;
          relay.start();
        }catch(Exception e){
          prefs().edit().putBoolean("dvb_tuning",false).apply();
          String reason=e.getMessage()==null?"DVB_CAPTURE_UNAVAILABLE":e.getMessage();
          runOnUiThread(()->{if(!released)status.setText(tx("فشل تشغيل 4:2:2: ","4:2:2 playback failed: ")+reason);});
        }
      });
    }

    private void open(String source) {
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--avcodec-hw=none");
            options.add("--aout=audiotrack");
            options.add("--no-video-title-show");
            // Same software-avcodec path identified in the compiled 3.7.0 APK.
            options.add("--codec=avcodec");
            int decodingThreads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1));
            options.add("--avcodec-threads="+decodingThreads);
            options.add("--deinterlace=0");
            options.add(":demux=avformat");
            options.add(":file-caching=1000");
            options.add("--network-caching=2500");
            options.add("--live-caching=1500");

            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            mediaPlayer.setEventListener(event -> runOnUiThread(() -> {
                if (released || status == null) return;
                switch (event.type) {
                    case MediaPlayer.Event.Opening:
                        status.setText(tx("فتح البث…", "Opening stream…"));
                        break;
                    case MediaPlayer.Event.Buffering:
                        status.setText(tx("تخزين مؤقت…", "Buffering…"));
                        break;
                    case MediaPlayer.Event.Playing:
                        if(localInfo!=null&&getIntent().getLongExtra("dvb_channel",-1L)>=0)localInfo.show();
                        status.setText(tx("بدأ تشغيل LibVLC البرمجي؛ تحقق من ظهور صورة 4:2:2 فعلياً", "LibVLC software playback started; verify decoded 4:2:2 video frames"));
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        status.setText(tx("فشل فك الترميز أو قراءة البث", "Decoder or stream error"));
                        break;
                    case MediaPlayer.Event.EndReached:
                        status.setText(tx("انتهى البث", "Stream ended"));
                        break;
                    default:
                        break;
                }
            }));

            Media media = new Media(libVLC, Uri.parse(source));
            media.setHWDecoderEnabled(false, false);
            media.addOption(":avcodec-hw=none");
            media.addOption(":codec=avcodec");
            media.addOption(":avcodec-corrupted=1");
            media.addOption(":file-caching=1000");
            media.addOption(":network-caching=2500");
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
        } catch (Throwable failure) {
            if (status != null) {
                String detail = failure.getMessage();
                status.setText(tx("تعذر تشغيل LibVLC", "LibVLC startup failed")
                        + (detail == null ? "" : " · " + detail));
            }
        }
    }

    private void showTrackMenu() {
        if (mediaPlayer == null || released || !mediaPlayer.hasMedia()) {
            status.setText(tx("انتظر حتى يبدأ البث", "Wait until playback starts"));
            return;
        }
        String[] choices = {
                tx("مسار الصوت", "Audio track"),
                tx("مسار الترجمة", "Subtitle track")
        };
        new AlertDialog.Builder(this)
                .setTitle(tx("إعدادات المسارات", "Track selection"))
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) showAudioTracks();
                    else showSubtitleTracks();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    private void showAudioTracks() {
        MediaPlayer.TrackDescription[] tracks;
        try { tracks = mediaPlayer.getAudioTracks(); }
        catch (Throwable error) { tracks = null; }
        if (tracks == null || tracks.length == 0) {
            status.setText(tx("لا توجد مسارات صوت متاحة", "No audio tracks available"));
            return;
        }
        final MediaPlayer.TrackDescription[] audioTracks = tracks;
        String[] labels = new String[audioTracks.length];
        int selected = -1;
        int current = mediaPlayer.getAudioTrack();
        for (int i = 0; i < audioTracks.length; i++) {
            labels[i] = audioTracks[i].name == null ? ("Audio " + audioTracks[i].id) : audioTracks[i].name;
            if (audioTracks[i].id == current) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(tx("اختر مسار الصوت", "Select audio track"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    boolean ok = mediaPlayer != null && mediaPlayer.setAudioTrack(audioTracks[which].id);
                    if (status != null) status.setText(ok
                            ? tx("تم تغيير مسار الصوت", "Audio track changed")
                            : tx("تعذر تغيير مسار الصوت", "Could not change audio track"));
                    dialog.dismiss();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    private void showSubtitleTracks() {
        MediaPlayer.TrackDescription[] tracks;
        try { tracks = mediaPlayer.getSpuTracks(); }
        catch (Throwable error) { tracks = null; }
        if (tracks == null || tracks.length == 0) {
            status.setText(tx("لا توجد ترجمات داخل البث", "No embedded subtitles available"));
            return;
        }
        final MediaPlayer.TrackDescription[] subtitleTracks = tracks;
        String[] labels = new String[subtitleTracks.length + 1];
        labels[0] = tx("إيقاف الترجمة", "Disable subtitles");
        int selected = mediaPlayer.getSpuTrack() == -1 ? 0 : -1;
        for (int i = 0; i < subtitleTracks.length; i++) {
            labels[i + 1] = subtitleTracks[i].name == null ? ("Subtitle " + subtitleTracks[i].id) : subtitleTracks[i].name;
            if (subtitleTracks[i].id == mediaPlayer.getSpuTrack()) selected = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(tx("اختر الترجمة", "Select subtitle"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    int trackId = which == 0 ? -1 : subtitleTracks[which - 1].id;
                    boolean ok = mediaPlayer != null && mediaPlayer.setSpuTrack(trackId);
                    if (status != null) status.setText(ok
                            ? tx("تم تغيير الترجمة", "Subtitle selection changed")
                            : tx("تعذر تغيير الترجمة", "Could not change subtitle"));
                    dialog.dismiss();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int k=event.getKeyCode();
        if(k==android.view.KeyEvent.KEYCODE_INFO||k==android.view.KeyEvent.KEYCODE_PROG_BLUE||k==1202){
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0
              &&localInfo!=null&&getIntent().getLongExtra("dvb_channel",-1)>=0)localInfo.show();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onPause() {
        if(localInfo!=null)localInfo.dismiss();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !released) mediaPlayer.play();
    }

    @Override protected void onDestroy() {
        released = true;
        if(localInfo!=null)localInfo.close();
        prefs().edit().putBoolean("software_dvb_422_active",false).putBoolean("dvb_tuning",false).apply();
        LiveTsRelay capture=satelliteRelay;
        if(capture!=null)new Thread(capture::close,"iraqhub-capture-stop").start();
        relayThread.shutdownNow();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Throwable ignored) {}
            try { mediaPlayer.detachViews(); } catch (Throwable ignored) {}
            try { mediaPlayer.release(); } catch (Throwable ignored) {}
            mediaPlayer = null;
        }
        if (libVLC != null) {
            try { libVLC.release(); } catch (Throwable ignored) {}
            libVLC = null;
        }
        super.onDestroy();
    }
}
