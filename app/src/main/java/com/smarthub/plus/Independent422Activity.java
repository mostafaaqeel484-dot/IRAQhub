package com.smarthub.plus;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IRAQhub 4:2:2 independent software decoder (android:process=:decoder422).
 *
 * - libmpv + FFmpeg, NEVER instantiates LibVLC or its MediaPlayer;
 * - MPEG-TS comes from the real app-owned LiveTsRelay and OEM DVB tuner;
 * - no channel posters, EPG, WebView, Accessibility or launcher UI loop;
 * - binds a main-process service to pause optional WorkManager/bitmap work;
 * - video-only uses aid=no and system clock (default, user can enable audio);
 * - close only the app-owned TS relay, not tuner/OEM CA/PVR processes.
 *
 * A successful MPV FILE_LOADED or a moving clock does not prove the TV
 * SurfaceView is displaying distinct frames. Device testing is essential.
 */
public final class Independent422Activity extends HubActivity {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService captureIo=Executors.newSingleThreadExecutor();
 private final AtomicBoolean captureStarted=new AtomicBoolean(false);
 private FrameLayout videoRoot;
 private TextView status;
 private Button audioButton;
 private Mpv422Renderer renderer;
 private LiveTsRelay relay;
 private Auto422RuntimeMonitor monitor;
 private long audioToken;
 private long channelId=-1;
 private volatile String playbackUrl;
 private volatile int generation;
 private boolean videoOnly=true,bound,loaded;
 private volatile boolean stopped=true,destroyed;
 private long previousPositionAt;
 private double previousPosition=-1;
 private final ServiceConnection focus=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName component,IBinder binder){
   if(destroyed||stopped)return;
   prefs().edit().putString("independent_422_focus","BOUND_MAIN_PROCESS").apply();
   begin();
  }
  @Override public void onServiceDisconnected(ComponentName component){
   if(!stopped&&!destroyed)
    display(tx("انقطع ربط إيقاف أعمال الخلفية؛ فك الفيديو مستمر",
      "Background-focus service disconnected; decoder continues"));
  }
 };

 private final Runnable health=new Runnable(){
  @Override public void run(){
   if(stopped||destroyed)return;
   Mpv422Renderer engine=renderer;
   if(engine!=null&&loaded){
    Mpv422Renderer.Metrics values=engine.metrics();
    if(values!=null){
     long now=SystemClock.elapsedRealtime();
     boolean progressed=Independent422Policy.clockProgressed(
       previousPosition,values.position);
     if(progressed){previousPositionAt=now;previousPosition=values.position;}
     String state="MPV_POSITION_UNAVAILABLE";
     if(values.position>=0){
      state=Independent422Policy.clockStalled(previousPositionAt,now,
        relay!=null&&relay.running())?"MPV_PLAYBACK_CLOCK_STALLED":
          "MPV_CLOCK_ADVANCING_PICTURE_UNVERIFIED";
     }
     prefs().edit().putString("independent_422_health",state)
       .putLong("independent_422_health_at",System.currentTimeMillis())
       .putInt("independent_422_dropped",values.dropped)
       .putInt("independent_422_decoder_dropped",values.decoderDropped)
       .putString("independent_422_pixfmt",values.videoFormat)
       .apply();
     if("MPV_PLAYBACK_CLOCK_STALLED".equals(state))
      display(tx("توقف تقدّم ساعة MPV؛ افحص TS وأعد فتح المحرك من زر الإعادة",
        "MPV playback clock stopped; check TS and use Retry"));
    }
   }
   main.postDelayed(this,4800);
  }
 };

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  channelId=getIntent().getLongExtra("dvb_channel",-1L);
  videoOnly=getIntent().getBooleanExtra("video_only",
    prefs().getBoolean("independent_422_video_only",true));
  if(channelId<0){
   String url=getIntent().getStringExtra("media_uri");
   if(!Software422Source.validNetwork(url)){
    // The real local TS URL is minted only by LiveTsRelay; external input
    // must be an explicitly supported network source, not a private file.
    Toast.makeText(this,tx("مصدر الفيديو غير صالح","Invalid video source"),
      Toast.LENGTH_LONG).show();
    finish();return;
   }
   playbackUrl=url;
  }

  videoRoot=new FrameLayout(this);
  videoRoot.setBackgroundColor(0xff000000);
  status=new TextView(this);
  status.setTextColor(0xfff4f4f4);
  status.setTextSize(14);
  status.setBackgroundColor(0xbb050505);
  status.setPadding(dp(16),dp(9),dp(16),dp(9));
  status.setText(tx("تهيئة محرك 4:2:2 المستقل · libmpv","Preparing isolated 4:2:2 libmpv engine"));
  FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
  videoRoot.addView(status,top);

  LinearLayout controls=new LinearLayout(this);
  controls.setOrientation(LinearLayout.HORIZONTAL);
  controls.setGravity(Gravity.CENTER_VERTICAL);
  controls.setBackgroundColor(0xad030303);
  controls.setPadding(dp(7),dp(3),dp(7),dp(3));
  Button diagnostic=small(tx("الفحص","Stats"));
  diagnostic.setOnClickListener(v->diagnostics());
  audioButton=small("");
  updateAudioButton();
  audioButton.setOnClickListener(v->{
   videoOnly=!videoOnly;
   prefs().edit().putBoolean("independent_422_video_only",videoOnly).apply();
   updateAudioButton();
   if(loaded&&playbackUrl!=null)restartRenderer();
  });
  Button subtitles=small(tx("الترجمة","Subtitles"));
  subtitles.setOnClickListener(v->subtitlePicker());
  Button retry=small(tx("إعادة الفيديو","Retry video"));
  retry.setOnClickListener(v->{if(playbackUrl!=null)restartRenderer();});
  Button back=small(tx("خروج","Exit"));
  back.setOnClickListener(v->finish());
  for(Button button:new Button[]{diagnostic,audioButton,subtitles,retry,back})
   controls.addView(button,new LinearLayout.LayoutParams(0,dp(52),1));
  FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
  videoRoot.addView(controls,bottom);
  setContentView(videoRoot);
  prefs().edit().putString("independent_422_runtime","CREATED")
    .putString("decoder_kind","SOFTWARE_LIBMPV_INDEPENDENT_PROCESS").apply();
 }

 private Button small(String text){
  Button button=new Button(this);
  button.setText(text);
  button.setAllCaps(false);
  button.setTextColor(0xffffffff);
  button.setTextSize(13);
  button.setMinHeight(dp(48));
  button.setPadding(dp(2),0,dp(2),0);
  button.setFocusable(true);
  return button;
 }
 private void updateAudioButton(){
  if(audioButton!=null)audioButton.setText(videoOnly?
   tx("فيديو فقط","Video only"):tx("مع الصوت","Audio on"));
 }
 @Override protected void onStart(){
  super.onStart();
  if(destroyed||videoRoot==null)return;
  stopped=false;
  try{
   bound=bindService(new Intent(this,Isolated422FocusService.class),
     focus,Context.BIND_AUTO_CREATE);
  }catch(Exception e){bound=false;}
  if(!bound){
   display(tx("تعذر عزل أعمال الخلفية؛ بدء محرك الفيديو بدون Focus",
     "Main-process focus unavailable; starting isolated decoder"));
   begin();
  }else{
   // The callback normally arrives quickly. Do not keep the receiver hung
   // forever if the focus service is unavailable on a particular firmware.
   main.postDelayed(()->{
    if(!stopped&&!destroyed&&!captureStarted.get()){
     display(tx("انتهت مهلة Focus؛ تشغيل الفيديو المستقل",
       "Focus timed out; starting independent video"));
     begin();
    }
   },5000);
  }
  main.postDelayed(health,4800);
 }

 private void begin(){
  if(stopped||destroyed||!captureStarted.compareAndSet(false,true))return;
  monitor=new Auto422RuntimeMonitor(this);
  monitor.start(); // reports CPU of the independent process, not total launcher
  if(channelId>=0){
   audioToken=NativeSatelliteAudioGate.suppress(this);
   startDvb(channelId,++generation);
  }else{
   if(playbackUrl==null)
    playbackUrl=getIntent().getStringExtra("media_uri");
   if(playbackUrl!=null)openRenderer(playbackUrl);
  }
 }

 private void startDvb(long id,int session){
  display(tx("فك DVB مستقل؛ جارٍ الحصول على MPEG-TS من التونر…",
    "Independent DVB decode: requesting MPEG-TS from tuner…"));
  captureIo.execute(()->{
   try{
    ReceiverCatalog catalog=ReceiverCatalog.get(this);
    catalog.ensure();
    ReceiverCatalog.Channel channel=catalog.channel(id);
    if(channel==null)throw new IOException("CHANNEL_NOT_FOUND");
    if(!Independent422Policy.eligibleDvb(channel.tv,channel.ca))
     throw new IOException("SOFTWARE_DVB_TV_METADATA_REQUIRED");
    if(channel.ca!=0)runOnUiThread(()->getWindow()
      .addFlags(WindowManager.LayoutParams.FLAG_SECURE));
    ReceiverSignalReading tuner=DvbTuner.ensure(this,channel);
    if(stopped||destroyed||session!=generation)return;
    JSONObject request=DvbPlayRequest.create(channel.data,(int)channel.id,
      channel.sid,channel.angle,channel.frequency,channel.pol,
      channel.symbolRate,tuner.tp,0,false);
    if(channel.ca!=0&&channel.data.optJSONArray("CaSystemIdList")!=null&&
       channel.data.optJSONArray("CaSystemIdList").length()>0)
      FirmwareCaRequest.apply(request,channel.ca,
        channel.data.optJSONArray("CaSystemIdList"),
        catalog.transportServices(channel));
    request.put("tp_id",tuner.tp);
    LiveTsRelay newRelay=new LiveTsRelay(this,channel,request,
      new LiveTsRelay.Listener(){
       @Override public void ready(String url){
        if(stopped||destroyed||session!=generation)return;
        playbackUrl=url;
        prefs().edit().putString("independent_422_runtime","TS_READY")
          .putString("independent_422_channel","DVB:"+channel.physical())
          .putBoolean("software_dvb_422_active",true).apply();
        NativeSatelliteAudioGate.enforce(Independent422Activity.this,audioToken);
        openRenderer(url);
       }
       @Override public void error(String cause){
        if(stopped||destroyed||session!=generation)return;
        prefs().edit().putString("independent_422_runtime","TS_ERROR")
         .putString("decoder_last_error",cause).apply();
        display(tx("فشل تدفق DVB: ","DVB stream failure: ")+cause);
       }
      });
    if(stopped||destroyed||session!=generation)newRelay.close();
    else{relay=newRelay;newRelay.start();}
   }catch(Exception failure){
    if(stopped||destroyed||session!=generation)return;
    String message=failure.getMessage()==null?
      failure.getClass().getSimpleName():failure.getMessage();
    prefs().edit().putString("independent_422_runtime","DVB_START_FAILED")
      .putString("decoder_last_error",message).apply();
    display(tx("تعذر تشغيل المحرك المستقل: ",
      "Independent decoder failed to start: ")+message);
   }
  });
 }

 private void openRenderer(String url){
  if(stopped||destroyed||videoRoot==null)return;
  closeRenderer();
  loaded=false;previousPosition=-1;previousPositionAt=0;
  prefs().edit().putString("independent_422_runtime","MPV_OPENING")
    .putString("decoder_kind","SOFTWARE_LIBMPV_INDEPENDENT_PROCESS")
    .putString("decoder_422_verification","MPV_FILE_NOT_YET_LOADED")
    .remove("decoder_last_error").apply();
  display(tx("MPV مستقل · جاري فتح الفيديو", "Isolated MPV · opening video"));
  try{
   Mpv422Renderer next=new Mpv422Renderer(this,videoRoot,
     new Mpv422Renderer.Listener(){
      @Override public void ready(){
       if(stopped||destroyed)return;
       loaded=true;previousPositionAt=SystemClock.elapsedRealtime();
       prefs().edit().putString("independent_422_runtime","MPV_FILE_LOADED")
         .putString("decoder_422_verification",
           "MPV_FILE_LOADED_VISUAL_MOVEMENT_NOT_PROVEN").apply();
       display(tx("محرك MPV مستقل يعمل · اختبر حركة الصورة",
         "Independent MPV loaded · verify picture movement"));
       if(channelId>=0){
        ReceiverCatalog.Channel c=ReceiverCatalog.get(Independent422Activity.this)
          .channel(channelId);
        if(c!=null){
         int pid=prefs().getInt("subtitle_preferred_pid:"+c.physical(),-1);
         if(pid>0&&renderer!=null)renderer.selectPreferredPid(pid);
        }
       }
       main.postDelayed(()->{if(!stopped&&loaded&&status!=null)
          status.setVisibility(View.GONE);},4600);
      }
      @Override public void failed(String reason){
       if(stopped||destroyed)return;
       loaded=false;
       prefs().edit().putString("independent_422_runtime","MPV_FAILURE")
        .putString("decoder_last_error",reason).apply();
       display(tx("محرك MPV تعطل: ","MPV engine failed: ")+reason+
        tx(" · يمكن العودة واختيار LibVLC"," · go back to choose LibVLC"));
      }
     },videoOnly);
   renderer=next;
   if(!next.start(url))display(tx("تعذر تحميل libmpv على هذه الروم",
     "Unable to start libmpv on this firmware"));
  }catch(Throwable unavailable){
   prefs().edit().putString("independent_422_runtime","MPV_INIT_FAILED")
     .putString("decoder_last_error",unavailable.getClass().getSimpleName()).apply();
   display(tx("مكتبة محرك MPV غير متاحة: ",
     "MPV native engine unavailable: ")+unavailable.getClass().getSimpleName());
  }
 }
 private void restartRenderer(){
  if(stopped||destroyed||playbackUrl==null)return;
  openRenderer(playbackUrl); // Reuse same TS relay; no tuner retune.
 }
 private void closeRenderer(){
  Mpv422Renderer old=renderer;
  renderer=null;loaded=false;
  if(old!=null)try{old.close();}catch(Throwable ignored){}
 }
 private void display(String value){
  main.post(()->{if(!destroyed&&status!=null){
   status.setVisibility(View.VISIBLE);status.setText(value);
  }});
 }
 private void subtitlePicker(){
  Mpv422Renderer engine=renderer;
  if(engine==null||!loaded){
   display(tx("الترجمة تحتاج فتح بث MPV أولاً",
     "Load MPV stream before selecting subtitles"));return;
  }
  List<Mpv422Renderer.Sub> tracks=engine.subtitleTracks();
  String[] labels=new String[tracks.size()+1];
  labels[0]=tx("إيقاف الترجمة","Subtitles off");
  for(int i=0;i<tracks.size();i++)labels[i+1]=tracks.get(i).label;
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("ترجمة البث داخل محرك MPV","MPV embedded subtitle tracks"))
   .setItems(labels,(dialog,index)->{
    int track=index==0?-1:tracks.get(index-1).id;
    if(renderer!=null)renderer.selectSubtitle(track);
   }).show();
 }
 private void diagnostics(){
  Mpv422Renderer.Metrics m=renderer==null?null:renderer.metrics();
  long bitrate=prefs().getLong("software_ts_bitrate",0);
  String report=tx("محرك منفصل: ","Separate process: ")+
    android.os.Process.myPid()+
    "\nEngine: libmpv / FFmpeg (NOT LibVLC)"+
    "\nAudio decoding: "+(videoOnly?"DISABLED (aid=no)":"ENABLED")+
    "\nProfile: "+Software422Profile.effective(this)+
    "\nTS: "+String.format(Locale.ROOT,"%.2f Mbit/s",bitrate/1000000.0)+
    "\nCapture: "+prefs().getString("software_ts_capture_error","NOT_TESTED")+
    "\nlibmpv FILE_LOADED: "+loaded+
    "\nPlayback clock: "+(m==null?"UNKNOWN":String.format(Locale.ROOT,"%.2f s",m.position))+
    "\nEstimated source FPS: "+(m==null?"UNKNOWN":String.format(Locale.ROOT,"%.2f",m.estimatedFps))+
    "\nDropped: "+(m==null?-1:m.dropped)+
    "\nDecoder dropped: "+(m==null?-1:m.decoderDropped)+
    "\nOutput: "+(m==null?"unknown":m.outputWidth+"x"+m.outputHeight)+
    "\nPixel format: "+(m==null?"unknown":m.videoFormat)+
    "\nClock health: "+prefs().getString("independent_422_health","NOT_SAMPLED")+
    "\nCPU process: "+prefs().getInt("auto_422_cpu_percent",-1)+"% of one core"+
    "\nVideo frames visually moving: NOT AUTOMATICALLY VERIFIED"+
    "\nFocus service: "+prefs().getString("independent_422_focus","NOT_BOUND");
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("تشخيص محرك 4:2:2 المستقل","Isolated 4:2:2 engine diagnostics"))
   .setMessage(report).setPositiveButton("OK",null).show();
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
   if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
    finish();return true;
   }
   if(event.getKeyCode()==KeyEvent.KEYCODE_INFO){
    diagnostics();return true;
   }
  }
  return super.dispatchKeyEvent(event);
 }
 @Override protected void onStop(){
  stopped=true;generation++;main.removeCallbacks(health);
  closeRenderer();
  LiveTsRelay active=relay;relay=null;
  // Retain external URI so onStart can restore playback after interruption.
  if(channelId>=0)playbackUrl=null;
  if(active!=null)new Thread(active::close,"iraqhub-independent-ts-close").start();
  if(monitor!=null){monitor.stop();monitor=null;}
  NativeSatelliteAudioGate.restore(this,audioToken);audioToken=0;
  prefs().edit().putBoolean("software_dvb_422_active",false)
    .putString("independent_422_runtime","STOPPED").apply();
  if(bound){
   bound=false;
   try{unbindService(focus);}catch(Exception ignored){}
  }
  captureStarted.set(false);
  super.onStop();
 }
 @Override protected void onDestroy(){
  destroyed=true;main.removeCallbacksAndMessages(null);
  captureIo.shutdownNow();closeRenderer();
  super.onDestroy();
 }
 @Override public void onTrimMemory(int level){
  super.onTrimMemory(level);
  PlaybackResourceMode.memoryPressure(this,level);
 }
}
