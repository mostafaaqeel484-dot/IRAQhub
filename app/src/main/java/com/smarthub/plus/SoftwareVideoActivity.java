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
import org.videolan.libvlc.interfaces.IMedia;

import java.util.ArrayList;

/**
 * Explicit software-decoding fallback for IPTV/file streams.
 * Hardware decoding is disabled intentionally. This activity does not claim
 * hardware 4:2:2 support and does not control the OEM DVB tuner.
 */
public final class SoftwareVideoActivity extends HubActivity {
    private boolean hasResourceMode;
    private void enterResourceMode(){
        if(!hasResourceMode){PlaybackResourceMode.enter(this);hasResourceMode=true;}
    }
    private void leaveResourceMode(){
        if(hasResourceMode){PlaybackResourceMode.leave(this);hasResourceMode=false;}
    }
    private Auto422RuntimeMonitor autoMonitor;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private FrameLayout playerRoot;
    private Mpv422Renderer mpvRenderer;
    private boolean mpvSelected;
    private boolean mpvFallbackUsed;
    private TextView status;
    private boolean released;
    private volatile boolean backgroundStopped;
    private String playbackUri;
    private int streamRetries;
    private long lastBufferStatus;
    private boolean videoOutputSeen;
    private long decoderOpenedAt;
    private boolean decoderBuffering;
    private boolean preferredSubtitleChecked;
    private int selectedSubtitlePid(){
      long id=getIntent().getLongExtra("dvb_channel",-1);
      if(id<0)return -1;
      ReceiverCatalog.Channel channel=ReceiverCatalog.get(this).channel(id);
      if(channel==null)return -1;
      return prefs().getInt("subtitle_preferred_pid:"+channel.physical(),-1);
    }
    /** Apply only when a REAL, listed player track matches the DB transport PID.
     * An OEM "Sub" metadata entry is NOT proof the firmware can render it. */
    private void applyPreferredLibVlcSubtitle(){
      if(preferredSubtitleChecked||mediaPlayer==null)return;
      int pid=selectedSubtitlePid();
      if(pid<0)return;
      try{
        MediaPlayer.TrackDescription[] subtitles=mediaPlayer.getSpuTracks();
        if(subtitles==null||subtitles.length==0)return;
        preferredSubtitleChecked=true;
        for(MediaPlayer.TrackDescription track:subtitles){
          if(track.id!=pid)continue;
          boolean ok=mediaPlayer.setSpuTrack(track.id);
          prefs().edit().putString("native_subtitle_status",
            ok?"LIBVLC_MATCHED_TS_PID_"+pid:"LIBVLC_PID_SELECT_FAILED_"+pid).apply();
          return;
        }
        prefs().edit().putString("native_subtitle_status",
          "LIBVLC_PID_NOT_PRESENT_"+pid).apply();
      }catch(Throwable unsupported){
        prefs().edit().putString("native_subtitle_status",
          "LIBVLC_SUBTITLE_TRACKS_UNAVAILABLE").apply();
      }
    }
    private long lastPictureProgressAt,videoStatsAt;
    private long previousDisplayed=-1,previousDecoded=-1,previousAudio=-1,previousDemux=-1;
    private long audioSincePicture,demuxSincePicture,decodedSincePicture;
    private boolean videoStallEngineSwitchUsed;
    private final Runnable frameHealth=new Runnable(){
      @Override public void run(){
        if(released||backgroundStopped)return;
        if(mediaPlayer!=null&&satelliteRelay!=null&&satelliteRelay.running())
          sampleDecoderFrames();
        main.postDelayed(this,2600);
      }
    };
    /** Stats are measured by LibVLC, not inferred from Vout, TS bitrate or audio.
     * A real renderer stall is repaired without retuning the satellite. */
    private void sampleDecoderFrames(){
      IMedia live=null;
      try{
        if(!mediaPlayer.isPlaying())return;
        live=mediaPlayer.getMedia();
        if(live==null)return;
        IMedia.Stats stats=live.getStats();
        if(stats==null)return;
        long now=android.os.SystemClock.elapsedRealtime();
        long display=stats.displayedPictures,decoded=stats.decodedVideo,
          audio=stats.decodedAudio,demux=stats.demuxReadBytes;
        if(previousDisplayed<0||display<previousDisplayed||decoded<previousDecoded){
          lastPictureProgressAt=now;videoStatsAt=now;
          audioSincePicture=0;demuxSincePicture=0;decodedSincePicture=0;
        }else{
          audioSincePicture+=Math.max(0,audio-previousAudio);
          demuxSincePicture+=Math.max(0,demux-previousDemux);
          decodedSincePicture+=Math.max(0,decoded-previousDecoded);
          if(display>previousDisplayed){
            lastPictureProgressAt=now;
            audioSincePicture=0;demuxSincePicture=0;decodedSincePicture=0;
          }
        }
        previousDisplayed=display;previousDecoded=decoded;
        previousAudio=audio;previousDemux=demux;videoStatsAt=now;
        prefs().edit().putLong("libvlc_displayed_pictures",display)
          .putLong("libvlc_decoded_video",decoded)
          .putLong("libvlc_decoded_audio",audio)
          .putLong("libvlc_demux_read_bytes",demux)
          .putLong("libvlc_last_picture_advanced_ms",lastPictureProgressAt)
          .apply();
        boolean stuck=VideoFrameStallPolicy.stalled(lastPictureProgressAt,now,
          demuxSincePicture,audioSincePicture,0,decodedSincePicture,true,decoderBuffering,
          videoOutputSeen);
        boolean render=VideoFrameStallPolicy.rendererStalled(lastPictureProgressAt,now,
          demuxSincePicture,audioSincePicture,0,decodedSincePicture,true,
          decoderBuffering,videoOutputSeen);
        if(stuck||render){
          String reason=render?"LIBVLC_DISPLAY_STALL_AUDIO_ACTIVE":
            "LIBVLC_DECODE_STALL_AUDIO_ACTIVE";
          prefs().edit().putString("decoder_video_health",reason)
            .putString("decoder_last_error",reason)
            .putString("decoder_422_verification","VIDEO_STALLED_AFTER_FIRST_PICTURE")
            .apply();
          status.setText(tx("توقف تحديث صورة 4:2:2 والصوت مستمر · محاولة استعادة الفيديو",
            "4:2:2 video stalled while audio continued · recovering video"));
          lastPictureProgressAt=now;audioSincePicture=0;
          demuxSincePicture=0;decodedSincePicture=0;
          if(streamRetries<2)recoverStream();
          else if(!videoStallEngineSwitchUsed&&!mpvFallbackUsed){
            videoStallEngineSwitchUsed=true;
            mpvSelected=true;
            open(playbackUri); // Switch engine once, retaining tuned TS relay.
          }
        }
      }catch(Throwable unavailable){
        prefs().edit().putString("libvlc_stats_status",
          unavailable.getClass().getSimpleName()).apply();
      }finally{
        if(live!=null)try{live.release();}catch(Throwable ignored){}
      }
    }
    private void resetVideoStats(){
      previousDisplayed=-1;previousDecoded=-1;previousAudio=-1;previousDemux=-1;
      lastPictureProgressAt=0;videoStatsAt=0;audioSincePicture=0;
      demuxSincePicture=0;decodedSincePicture=0;decoderBuffering=false;
    }
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private LiveTsRelay satelliteRelay;
    private long nativeAudioToken;
    private LocalInfoBarController localInfo;
    private final Runnable voutWatchdog=()->{
       if(released||backgroundStopped||videoOutputSeen||mediaPlayer==null)return;
       if(satelliteRelay==null||!satelliteRelay.running())return;
       prefs().edit().putString("decoder_422_verification","NO_VIDEO_OUTPUT_AFTER_14S")
         .putString("software_422_runtime","PLAYING_BUT_NO_VIDEO_VOUT").apply();
       if(streamRetries<2)recoverStream();
    };
    private final java.util.concurrent.ExecutorService relayThread=java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        enterResourceMode();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        playerRoot=root;
        mpvSelected="mpv".equals(getIntent().getStringExtra("decoder_engine"));
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
        setContentView(root);
        localInfo=new LocalInfoBarController(this,root);

        long dvbChannel=getIntent().getLongExtra("dvb_channel",-1L);
        if(dvbChannel>=0){
            autoMonitor=new Auto422RuntimeMonitor(this);
            autoMonitor.start();
            // Already reserved our app's background-resource budget onCreate.
            // Silence OEM Play audio only; never stop the tuner or TS capture.
            nativeAudioToken=NativeSatelliteAudioGate.suppress(this);
            startSatellite(dvbChannel);return;
        }
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
          if(released||backgroundStopped)return;
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
              if(released||backgroundStopped)return;
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
              NativeSatelliteAudioGate.enforce(SoftwareVideoActivity.this,nativeAudioToken);
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
        if(released||backgroundStopped)return;
        playbackUri=source;
        streamRetries=0;
        resetVideoStats();preferredSubtitleChecked=false;
        if(mpvSelected&&!mpvFallbackUsed){
          videoLayout.setVisibility(android.view.View.GONE);
          if(openMpv(source))return;
          // mpv native library, surface or ABI failed: retain the live TS relay
          // and automatically use the proven LibVLC fallback.
          mpvFallbackUsed=true;
        }
        videoLayout.setVisibility(android.view.View.VISIBLE);
        videoOutputSeen=false;
        decoderOpenedAt=android.os.SystemClock.elapsedRealtime();
        main.removeCallbacks(voutWatchdog);
        prefs().edit().putString("software_422_runtime","LIBVLC_OPENING")
          .putString("decoder_422_verification","WAITING_FOR_VIDEO_OUTPUT")
          .putString("decoder_kind","SOFTWARE_LIBVLC_FFMPEG")
          .putString("decoder_name","LibVLC avcodec")
          .putLong("decoder_first_frame_at",0)
          .putInt("libvlc_vout_count",0)
          .putString("decoder_last_error","NONE").apply();
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
                        decoderBuffering=event.getBuffering()<96f;
                        if(android.os.SystemClock.elapsedRealtime()-lastBufferStatus>650){
                            lastBufferStatus=android.os.SystemClock.elapsedRealtime();
                            status.setText(tx("مزامنة بيانات الفيديو والصوت…","Synchronising video and audio…"));
                        }
                        break;
                    case MediaPlayer.Event.Vout:
                        int outputs=event.getVoutCount();
                        prefs().edit().putInt("libvlc_vout_count",outputs).apply();
                        if(outputs>0&&!videoOutputSeen){
                           videoOutputSeen=true;
                           main.removeCallbacks(voutWatchdog);
                           prefs().edit().putString("decoder_422_verification",
                             "LIBVLC_VIDEO_OUTPUT_PRESENT_COLOR_NOT_TESTED")
                             .putString("software_422_runtime","LIBVLC_VOUT_READY")
                             .putLong("libvlc_first_vout_at",System.currentTimeMillis())
                             .putLong("libvlc_vout_latency_ms",
                               android.os.SystemClock.elapsedRealtime()-decoderOpenedAt).apply();
                        }
                        break;
                    case MediaPlayer.Event.Playing:
                        decoderBuffering=false;
                        main.postDelayed(this::applyPreferredLibVlcSubtitle,1500);
                        NativeSatelliteAudioGate.enforce(this,nativeAudioToken);
                        if(!videoOutputSeen)main.postDelayed(voutWatchdog,14000L);
                        prefs().edit().putString("software_422_runtime",
                           videoOutputSeen?"LIBVLC_VOUT_READY":"LIBVLC_PLAYING_WAIT_VOUT").apply();
                        if(localInfo!=null&&getIntent().getLongExtra("dvb_channel",-1L)>=0)localInfo.show();
                        status.setText(tx("LibVLC يعمل برمجياً · تحقق من ظهور صورة فعلية · "+Software422Profile.current(this),
                           "Software decoder running · verify visible picture · "+Software422Profile.current(this)));
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        main.removeCallbacks(voutWatchdog);
                        prefs().edit().putString("software_422_runtime","LIBVLC_ERROR")
                          .putString("decoder_last_error","LIBVLC_ENCOUNTERED_ERROR").apply();
                        status.setText(tx("انقطاع بث TS أو خطأ فك الصورة، فحص إعادة الاتصال…",
                                          "TS stream or video decoding error; checking reconnect…"));
                        recoverStream();
                        break;
                    case MediaPlayer.Event.EndReached:
                        main.removeCallbacks(voutWatchdog);
                        prefs().edit().putString("software_422_runtime","LIBVLC_STREAM_ENDED").apply();
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
            main.removeCallbacks(frameHealth);
            main.postDelayed(frameHealth,2600);
        } catch (Throwable failure) {
            main.removeCallbacks(voutWatchdog);
            prefs().edit().putString("software_422_runtime","LIBVLC_STARTUP_FAILED")
              .putString("decoder_last_error",failure.getClass().getSimpleName()).apply();
            if (status != null) {
                String detail = failure.getMessage();
                status.setText(tx("تعذر تشغيل LibVLC", "LibVLC startup failed")
                        + (detail == null ? "" : " · " + detail));
            }
        }
    }

    private boolean openMpv(String source){
      try{
        closeDecoder();
        prefs().edit().putString("software_422_runtime","MPV_OPENING")
          .putString("decoder_kind","SOFTWARE_LIBMPV_FFMPEG")
          .putString("decoder_name","libmpv Android").apply();
        mpvRenderer=new Mpv422Renderer(this,playerRoot,new Mpv422Renderer.Listener(){
          @Override public void ready(){
            if(released||backgroundStopped)return;
            NativeSatelliteAudioGate.enforce(SoftwareVideoActivity.this,nativeAudioToken);
            status.setText(tx("mpv يعمل · افحص الصورة والألوان","mpv loaded · verify picture and colours"));
            if(localInfo!=null&&getIntent().getLongExtra("dvb_channel",-1L)>=0)localInfo.show();
            int pid=selectedSubtitlePid();
            if(pid>=0&&mpvRenderer!=null)mpvRenderer.selectPreferredPid(pid);
          }
          @Override public void failed(String reason){
            if(released||backgroundStopped||mpvFallbackUsed)return;
            mpvFallbackUsed=true;
            status.setText(tx("تعذر تشغيل mpv · التحويل إلى VLC","mpv failed · switching to VLC"));
            open(playbackUri);
          }
        });
        return mpvRenderer.start(source);
      }catch(Throwable t){
        prefs().edit().putString("decoder_last_error","MPV_INIT_"+t.getClass().getSimpleName()).apply();
        return false;
      }
    }
    private void configure(Media media,String source){
        media.setHWDecoderEnabled(false,false);
        media.addOption(":avcodec-hw=none");
        media.addOption(":codec=avcodec");
        String profile=Software422Profile.effective(this);
        int cache=Software422Profile.networkCacheMs(profile);
        if(source.startsWith("http://127.0.0.1:")){
            media.addOption(":network-caching="+cache);
            media.addOption(":live-caching="+Software422Profile.liveCacheMs(profile));
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
                resetVideoStats();videoOutputSeen=false;
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
        final String[] modes={Software422Profile.AUTO,Software422Profile.SMOOTH,
            Software422Profile.BALANCED,Software422Profile.QUALITY,
            Software422Profile.STABLE};
        final String[] labels={
            tx("تلقائي · يختار من قياسات CPU والحرارة والذاكرة",
               "Auto · measured CPU, thermal and memory pressure"),
            tx("سريع · يقلل كلفة الفلترة دون حذف إطارات أساسية","Smooth · lower deblocking load, no frame skipping"),
            tx("متوازن · صورة وصوت","Balanced · picture and sound"),
            tx("جودة أعلى · يحتاج CPU أقوى","Higher quality · uses more CPU"),
            tx("ثابت · تخزين مؤقت أكبر وحفظ الإطارات","Stable · larger buffer and preserve frames"),
            tx("تشخيص الجهاز وسرعة بث TS","Device and live TS diagnostics")
        };
        int current=1;
        for(int n=0;n<modes.length;n++)if(modes[n].equals(Software422Profile.current(this)))current=n;
        long bitrate=prefs().getLong("software_ts_bitrate",0);
        String capture=prefs().getString("software_422_runtime","NOT_STARTED");
        String telemetry="\nTS: "+(bitrate>0?String.format(java.util.Locale.ROOT,"%.2f",bitrate/1000000.0)+" Mbit/s":"—")+
           " · "+capture+"\nOEM audio: "+prefs().getString("software_422_native_audio","NOT_CHECKED")+
           "\nOEM audio commands are sent without firmware acknowledgement.";
        final String diagnostics=Software422Profile.status(this)+telemetry+
           "\nAuto CPU: "+prefs().getInt("auto_422_cpu_percent",-1)+"% of one core"+
           " · Memory pressure: "+prefs().getBoolean("auto_422_low_memory",false)+
           " · Thermal status: "+prefs().getInt("auto_422_thermal",-1);
        new AlertDialog.Builder(this).setTitle(tx("أداء 4:2:2","4:2:2 performance"))
          .setSingleChoiceItems(labels,current,(dialog,which)->{
            dialog.dismiss();
            if(which==modes.length){
                new AlertDialog.Builder(this).setTitle(tx("فحص X5M وTS","X5M / TS diagnostics"))
                    .setMessage(diagnostics).setPositiveButton("OK",null).show();
                return;
            }
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
        main.removeCallbacks(frameHealth);
        resetVideoStats();
        main.removeCallbacks(voutWatchdog);
        if(mpvRenderer!=null){
          try{mpvRenderer.close();}catch(Throwable ignored){}
          mpvRenderer=null;
        }
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
        if(mpvRenderer!=null&&!mpvFallbackUsed){
           showMpvSubtitleTracks();
           return;
        }
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

    private void showMpvSubtitleTracks(){
      if(mpvRenderer==null)return;
      java.util.List<Mpv422Renderer.Sub> tracks=mpvRenderer.subtitleTracks();
      if(tracks.isEmpty()){
        status.setText(tx("لم ينشر mpv مسارات DVB subtitle قابلة للاختيار",
            "mpv reported no selectable embedded subtitle track"));
        return;
      }
      String[] labels=new String[tracks.size()+1];
      labels[0]=tx("إيقاف الترجمة","Disable subtitles");
      for(int i=0;i<tracks.size();i++)labels[i+1]=tracks.get(i).label;
      new android.app.AlertDialog.Builder(this)
        .setTitle(tx("مسارات الترجمة من البث الفعلي","Actual stream subtitles"))
        .setItems(labels,(dialog,index)->{
          int id=index==0?-1:tracks.get(index-1).id;
          boolean ok=mpvRenderer!=null&&mpvRenderer.selectSubtitle(id);
          prefs().edit().putString("native_subtitle_status",
            ok?"MPV_USER_SELECTED_"+id:"MPV_SUBTITLE_SELECTION_UNAVAILABLE").apply();
          status.setText(ok?tx("تم اختيار الترجمة في mpv","mpv subtitle track selected"):
            tx("لم يتمكن mpv من اختيار المسار","mpv subtitle selection unavailable"));
        }).show();
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
        if(k==android.view.KeyEvent.KEYCODE_PAGE_DOWN||k==android.view.KeyEvent.KEYCODE_F3){
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showBiss();
            return true;
        }
        if(k==android.view.KeyEvent.KEYCODE_PAGE_UP){
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)
                startActivity(new android.content.Intent(this,ReceiverChannelsActivity.class)
                    .putExtra("encrypted_only",true).putExtra("type",0));
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
        if(mediaPlayer!=null&&mediaPlayer.isPlaying())mediaPlayer.pause();
        // mpv is released onStop; never leave a hidden native decoder running.
        // Returning to normal DVB must not leave the OEM decoder audio muted.
        NativeSatelliteAudioGate.restore(this,nativeAudioToken);
        nativeAudioToken=0;
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if(getIntent().getLongExtra("dvb_channel",-1)>=0&&nativeAudioToken==0)
          nativeAudioToken=NativeSatelliteAudioGate.suppress(this);
        NativeSatelliteAudioGate.enforce(this,nativeAudioToken);
        if(mediaPlayer!=null&&!released)mediaPlayer.play();
    }

    @Override protected void onStop(){
        // Suspend only our own 4:2:2 decoder and PVR/TS relay while off-screen.
        // Never force-stop Aurum or the DVB tuner service.
        backgroundStopped=true;
        if(autoMonitor!=null)autoMonitor.stop();
        if(localInfo!=null)localInfo.dismiss();
        closeDecoder();
        LiveTsRelay active=satelliteRelay;
        satelliteRelay=null;
        if(active!=null)new Thread(active::close,"iraqhub-background-ts-stop").start();
        prefs().edit().putBoolean("software_dvb_422_active",false)
          .putString("software_422_runtime","BACKGROUND_STOPPED").apply();
        leaveResourceMode();
        super.onStop();
    }
    @Override protected void onRestart(){
        super.onRestart();
        backgroundStopped=false;
        if(released)return;
        if(autoMonitor!=null)autoMonitor.start();
        enterResourceMode();
        long channel=getIntent().getLongExtra("dvb_channel",-1L);
        if(channel>=0){
            enterResourceMode();
            startSatellite(channel);
        }else if(playbackUri!=null)open(playbackUri);
    }
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        PlaybackResourceMode.memoryPressure(this,level);
    }
    @Override protected void onDestroy() {
        released = true;
        if(autoMonitor!=null)autoMonitor.stop();
        if(localInfo!=null)localInfo.close();
        leaveResourceMode();
        prefs().edit().putBoolean("software_dvb_422_active",false)
          .putBoolean("dvb_tuning",false).putString("software_422_runtime","STOPPED").apply();
        // A periodic poster job can be deferred for the entire 4:2:2 session.
        // Resume once after the decoder is destroyed, not during live playback.
        IraqHubSyncScheduler.programmeChanged(this);
        LiveTsRelay capture=satelliteRelay;
        if(capture!=null)new Thread(capture::close,"iraqhub-capture-stop").start();
        relayThread.shutdownNow();
        main.removeCallbacksAndMessages(null);
        closeDecoder();
        NativeSatelliteAudioGate.restore(this,nativeAudioToken);
        super.onDestroy();
    }
}
