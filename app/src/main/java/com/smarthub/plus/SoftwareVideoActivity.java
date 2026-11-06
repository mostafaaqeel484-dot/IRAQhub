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
    private String playbackUri;
    private int streamRetries;
    private long lastBufferStatus;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
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
        android.widget.Button modeButton=button("4:2:2  ⚙");
        modeButton.setContentDescription(tx("أوضاع أداء Amlogic 4:2:2",
            "Amlogic software 4:2:2 performance profiles"));
        FrameLayout.LayoutParams modeParams=new FrameLayout.LayoutParams(
            dp(128),dp(47),Gravity.TOP|Gravity.END);
        modeParams.setMargins(dp(8),dp(75),dp(15),dp(8));
        root.addView(modeButton,modeParams);
        modeButton.setOnClickListener(view->choosePerformance());

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
        long channelId=getIntent().getLongExtra("dvb_channel",-1L);
        if(channelId>=0){
            android.widget.Button biss=button("BISS");
            biss.setContentDescription(tx("القائمة العائمة لإدخال شفرة BISS",
                    "Floating BISS key input"));
            biss.setTextColor(0xffffd86c);
            FrameLayout.LayoutParams options=new FrameLayout.LayoutParams(
                    dp(104),dp(47),Gravity.BOTTOM|Gravity.START);
            options.setMargins(dp(14),dp(10),dp(10),dp(15));
            root.addView(biss,options);
            biss.setOnClickListener(view->showBiss());
        }
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
          if(channel.ca<0||channel.tv!=0)throw new java.io.IOException("SOFTWARE_TV_METADATA_REQUIRED");
          if(channel.ca!=0)runOnUiThread(()->getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
          ReceiverSignalReading tuner=DvbTuner.ensure(this,channel);
          prefs().edit().putBoolean("dvb_tuning",false)
             .putBoolean("software_dvb_422_active",true)
             .putString("software_422_runtime","CAPTURE_PREPARING").apply();
          org.json.JSONObject request=DvbPlayRequest.create(channel.data,(int)channel.id,channel.sid,
              channel.angle,channel.frequency,channel.pol,channel.symbolRate,tuner.tp,0,false);
          if(channel.ca!=0&&channel.data.optJSONArray("CaSystemIdList")!=null&&
             channel.data.optJSONArray("CaSystemIdList").length()>0)
            FirmwareCaRequest.apply(request,channel.ca,channel.data.optJSONArray("CaSystemIdList"),
              db.transportServices(channel));
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
                .putInt("own_ca_flag",channel.ca)
                .putString("own_ca_systems",ReceiverTechnicalBadges.caIds(channel.data.optJSONArray("CaSystemIdList")))
                .apply();
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
              prefs().edit().putString("software_422_runtime","TS_READY").apply();
              status.setText(tx(channel.ca==0?"التقاط بث DVB نشط؛ جارٍ فك الفيديو عبر LibVLC…":"تم التحقق من بث TS غير مشفر بعد معالجة الرسيفر؛ جارٍ تشغيل LibVLC…",
                 "DVB MPEG-TS capture active; decoding via LibVLC…"));
              open(uri);
              if(localInfo!=null)localInfo.show();
            }
            @Override public void error(String cause){
              if(!released){prefs().edit().putBoolean("software_dvb_422_active",false)
                  .putString("software_422_runtime","CAPTURE_ERROR:"+cause).apply();
                  status.setText(tx("تعذر التقاط MPEG-TS: ","MPEG-TS capture failed: ")+cause);}

            }
          });
          satelliteRelay=relay;
          relay.start();
        }catch(Exception e){
          prefs().edit().putBoolean("dvb_tuning",false)
            .putBoolean("software_dvb_422_active",false).apply();
          String reason=e.getMessage()==null?"DVB_CAPTURE_UNAVAILABLE":e.getMessage();
          runOnUiThread(()->{if(!released)status.setText(tx("فشل تشغيل 4:2:2: ","4:2:2 playback failed: ")+reason);});
        }
      });
    }

    private void open(String source) {
        if(released)return;
        playbackUri=source;
        streamRetries=0;
        try {
            closeDecoder();
            // Real 4:2:2 requires FFmpeg software decode unless the actual
            // receiver advertises and proves a compatible hardware decoder.
            ArrayList<String> options=Software422Profile.options(this);
            // Never force avformat on an MPEG-TS stream: LibVLC's TS demuxer
            // must see the real PAT/PMT and timestamps.
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
                        if(android.os.SystemClock.elapsedRealtime()-lastBufferStatus>650){
                            lastBufferStatus=android.os.SystemClock.elapsedRealtime();
                            status.setText(tx("مزامنة بيانات الفيديو والصوت…","Synchronising video and audio…"));
                        }
                        break;
                    case MediaPlayer.Event.Playing:
                        if(localInfo!=null&&getIntent().getLongExtra("dvb_channel",-1L)>=0)localInfo.show();
                        status.setText(tx("LibVLC يعمل برمجياً · تحقق من ظهور صورة فعلية · "+Software422Profile.current(this),
                           "Software decoder running · verify visible picture · "+Software422Profile.current(this)));
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        status.setText(tx("انقطاع بث TS أو خطأ فك الصورة، فحص إعادة الاتصال…",
                                          "TS stream or video decoding error; checking reconnect…"));
                        recoverStream();
                        break;
                    case MediaPlayer.Event.EndReached:
                        status.setText(tx("انتهى بث TS","TS stream ended"));
                        recoverStream();
                        break;
                    default:
                        break;
                }
            }));

            Media media = new Media(libVLC, Uri.parse(source));
            configure(media,source);
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

    private void configure(Media media,String source){
        media.setHWDecoderEnabled(false,false);
        media.addOption(":avcodec-hw=none");
        media.addOption(":codec=avcodec");
        int cache=Software422Profile.QUALITY.equals(Software422Profile.current(this))?1700:
          Software422Profile.SMOOTH.equals(Software422Profile.current(this))?950:1250;
        if(source.startsWith("http://127.0.0.1:")){
            media.addOption(":network-caching="+cache);
            media.addOption(":live-caching="+Math.max(750,cache-150));
            media.addOption(":http-reconnect");
        }else media.addOption(":file-caching=900");
    }
    private void recoverStream(){
        if(released||satelliteRelay==null||!satelliteRelay.running()||
           playbackUri==null||streamRetries>=2)return;
        final int attempt=++streamRetries;
        main.postDelayed(()->{
            if(released||satelliteRelay==null||!satelliteRelay.running()||
               libVLC==null||mediaPlayer==null||attempt!=streamRetries)return;
            try{
                Media reload=new Media(libVLC,Uri.parse(playbackUri));
                configure(reload,playbackUri);
                mediaPlayer.stop();
                mediaPlayer.setMedia(reload);
                reload.release();
                status.setText(tx("إعادة وصل بث DVB "+attempt+"/2","Reconnecting DVB stream "+attempt+"/2"));
                mediaPlayer.play();
            }catch(Throwable failure){
                status.setText(tx("لم يتمكن LibVLC من استعادة البث","LibVLC could not recover the stream"));
            }
        },2200L);
    }

    private void showBiss(){
        long id=getIntent().getLongExtra("dvb_channel",-1L);
        if(id<0){toast(tx("اختر قناة DVB أولاً","Select a DVB channel first"));return;}
        ReceiverCatalog.Channel selected=ReceiverCatalog.get(this).channel(id);
        if(selected!=null)BissKeyDialog.show(this,selected);
        else toast(tx("القناة غير موجودة","Channel missing"));
    }

    /** Restart LibVLC only, leaving the DVB capture and tuned satellite untouched. */
    private void choosePerformance(){
        final String[] modes={Software422Profile.SMOOTH,
            Software422Profile.BALANCED,Software422Profile.QUALITY};
        final String[] labels={
            tx("سريع · أقل تقطيعاً على X5M (تجاوز بعض الإطارات)","Smooth · less CPU, may skip frames"),
            tx("متوازن · صورة وصوت","Balanced · picture and sound"),
            tx("جودة أعلى · يحتاج CPU أقوى","Higher quality · uses more CPU")
        };
        int current=1;
        for(int n=0;n<modes.length;n++)if(modes[n].equals(Software422Profile.current(this)))current=n;
        long bitrate=prefs().getLong("software_ts_bitrate",0);
        String capture=prefs().getString("software_422_runtime","NOT_STARTED");
        String telemetry="\nTS: "+(bitrate>0?(bitrate/1000000.0)+" Mbit/s":"—")+
           " · "+capture;
        new AlertDialog.Builder(this).setTitle(tx("أداء 4:2:2","4:2:2 performance"))
          .setMessage(Software422Profile.status(this)+telemetry)
          .setSingleChoiceItems(labels,current,(dialog,which)->{
            dialog.dismiss();
            String desired=modes[which];
            if(desired.equals(Software422Profile.current(this)))return;
            Software422Profile.save(this,desired);
            if(playbackUri!=null&&!released){
                status.setText(tx("إعادة تشغيل فك الفيديو فقط…","Restarting decoder only…"));
                open(playbackUri);
            }else status.setText(Software422Profile.status(this));
          }).setNegativeButton(tx("رجوع","Close"),null).show();
    }
    private void closeDecoder(){
        if(mediaPlayer!=null){
            try{mediaPlayer.setEventListener(null);}catch(Throwable ignored){}
            try{mediaPlayer.stop();}catch(Throwable ignored){}
            try{mediaPlayer.detachViews();}catch(Throwable ignored){}
            try{mediaPlayer.release();}catch(Throwable ignored){}
            mediaPlayer=null;
        }
        if(libVLC!=null){
            try{libVLC.release();}catch(Throwable ignored){}
            libVLC=null;
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
        if(k==android.view.KeyEvent.KEYCODE_F4){
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)choosePerformance();
            return true;
        }
        if(k==android.view.KeyEvent.KEYCODE_F3){
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showBiss();
            return true;
        }
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
        prefs().edit().putBoolean("software_dvb_422_active",false)
          .putBoolean("dvb_tuning",false).putString("software_422_runtime","STOPPED").apply();
        LiveTsRelay capture=satelliteRelay;
        if(capture!=null)new Thread(capture::close,"iraqhub-capture-stop").start();
        relayThread.shutdownNow();
        main.removeCallbacksAndMessages(null);
        closeDecoder();
        super.onDestroy();
    }
}
