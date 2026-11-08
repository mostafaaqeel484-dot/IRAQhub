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
 private Button audioButton,renderButton,decoderButton;
 private String selectedRenderMode=Mpv422FluidPolicy.AUTO;
 private String renderMode=Mpv422FluidPolicy.P720;
 private String selectedDecoderMode=Mpv422FluidPolicy.SW;
 private String activeDecoderMode=Mpv422FluidPolicy.SW;
 private boolean hardwareRecoveryUsed;
 private int previousVoDrops=-1,poorDropSamples,autoDownshiftSteps;
 private long previousDropSampleAt,renderStartedAt;
 private boolean nativeRecoveryUsed,frameRecoveryUsed,terminalFrameFailureShown;
 private int noFrameSamples;
 private int rendererEpoch;
 private Mpv422Renderer renderer;
 private Native422Decoder directDecoder;
 private boolean nativeFallbackUsed;
 private long nativeLastFrames,nativeLastProgressAt;
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
   if(directDecoder!=null){
    long frames=directDecoder.displayedFrames();
    long now=SystemClock.elapsedRealtime();
    if(frames>nativeLastFrames){
     nativeLastFrames=frames;nativeLastProgressAt=now;
     prefs().edit().putLong("native422_rendered_frames",frames)
       .putString("decoder_422_verification","JNI_ANATIVEWINDOW_FRAMES_ADVANCING")
       .putLong("native422_frame_at",System.currentTimeMillis()).apply();
    }else if(!nativeFallbackUsed&&now-renderStartedAt>18000&&
       (frames==0||now-nativeLastProgressAt>12000)){
     nativeFallbackUsed=true;
     prefs().edit().putString("native422_runtime","STALLED_FALLBACK_MPV")
       .putLong("native422_last_rendered",frames).apply();
     display(tx("توقف عرض JNI؛ الرجوع إلى MPV احتياطياً",
       "JNI frames stalled; reverting once to MPV fallback"));
     restartRenderer();return;
    }
    main.postDelayed(this,4800);return;
   }
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
     int recentVoDrops=previousVoDrops>=0&&values.dropped>=previousVoDrops?
       values.dropped-previousVoDrops:0;
     long period=previousDropSampleAt>0?now-previousDropSampleAt:0;
     poorDropSamples=Mpv422FluidPolicy.poorDropSamples(
       previousVoDrops,values.dropped,period,poorDropSamples);
     previousVoDrops=values.dropped;
     previousDropSampleAt=now;
     noFrameSamples=Mpv422StreamHealthPolicy.missingFrameSamples(
       noFrameSamples,values.videoFormat,values.estimatedFps);
     if(Mpv422StreamHealthPolicy.shouldRecover(
        loaded,frameRecoveryUsed,playbackUrl!=null&&
          (relay==null||relay.running()),now-renderStartedAt,noFrameSamples)){
      // One recovery on the existing MPEG-TS relay, no DVB re-tune.
      frameRecoveryUsed=true;
      android.util.Log.w("IRAQhub422",
        "HEVC_NO_DECODED_FRAMES_SOFT_RETRY sw/native/videoonly"+
        " ts_cc_gaps="+prefs().getLong("software_ts_cc_gaps",-1)+
        " ts_tei="+prefs().getLong("software_ts_tei_packets",-1)+
        " pvr_rollovers="+prefs().getInt("software_ts_pvr_rotations",0));
      activeDecoderMode=Mpv422FluidPolicy.SW;
      renderMode=Mpv422FluidPolicy.GPU_NATIVE;
      videoOnly=true;
      prefs().edit().putString("independent_422_auto_action",
        "HEVC_NO_FRAMES_ONE_SHOT_SW_NATIVE_NEW_TS_CLIENT")
        .putBoolean("independent_422_video_only",true).apply();
      updateRenderButton();updateDecoderButton();updateAudioButton();
      display(tx("فشل ظهور إطارات HEVC؛ محاولة SW بدون صوت وGPU أصلي على أحدث TS",
        "No HEVC frames; one SW/video-only/native retry from latest TS"));
      restartRenderer();
      return;
     }
     if(frameRecoveryUsed&&!terminalFrameFailureShown&&
        noFrameSamples>=4&&now-renderStartedAt>=20000){
      terminalFrameFailureShown=true;
      android.util.Log.e("IRAQhub422",
        "HEVC_NO_FRAMES_AFTER_ONE_RETRY ts_cc_gaps="+
        prefs().getLong("software_ts_cc_gaps",-1));
      prefs().edit().putString("independent_422_health",
        "HEVC_NO_DECODED_FRAMES_AFTER_SINGLE_RETRY").apply();
      display(tx("لم تظهر صورة؛ افحص TS CC/TEI ومسار FFmpeg في Stats",
        "No decoded frames after retry; inspect TS CC/TEI in Stats"));
     }
     if(autoDownshiftSteps<2&&
        !renderMode.equals(Mpv422FluidPolicy.nextAutoMode(renderMode))&&
        Mpv422FluidPolicy.shouldDownshift(selectedRenderMode,poorDropSamples,
          now-renderStartedAt,recentVoDrops,period)){
      String from=renderMode;
      renderMode=Mpv422FluidPolicy.nextAutoMode(renderMode);
      autoDownshiftSteps++;
      prefs().edit().putString("independent_422_auto_action",
        from+"_TO_"+renderMode+"_SUSTAINED_VO_DROPS").apply();
      updateRenderButton();
      display(tx("إطارات متأخرة؛ خفض دقة الإخراج إلى ",
        "VO drops; lowering output to ")+Mpv422FluidPolicy.readable(renderMode));
      restartRenderer();
      return;
     }
     prefs().edit().putString("independent_422_health",state)
       .putLong("independent_422_health_at",System.currentTimeMillis())
       .putInt("independent_422_dropped",values.dropped)
       .putInt("independent_422_decoder_dropped",values.decoderDropped)
       .putString("independent_422_pixfmt",values.videoFormat)
       .putString("independent_422_output_pixfmt",values.outputFormat)
       .putInt("independent_422_recent_vo_drops",recentVoDrops)
       .putString("independent_422_render_mode",renderMode)
       .putString("independent_422_hwdec_actual",values.hardwareDecoder)
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
   selectedRenderMode=Mpv422FluidPolicy.normalized(
     prefs().getString("independent_422_fluid_mode",Mpv422FluidPolicy.AUTO));
   renderMode=Mpv422FluidPolicy.initialRenderMode(selectedRenderMode);
   selectedDecoderMode=Mpv422FluidPolicy.decoderMode(
     prefs().getString("independent_422_decoder_mode",Mpv422FluidPolicy.SW));
   activeDecoderMode=selectedDecoderMode;
   if("jni_ffmpeg".equals(getIntent().getStringExtra("decoder_engine"))){
    selectedRenderMode=Native422OutputProfile.normalize(
      prefs().getString("native422_output_mode",Native422OutputProfile.AUTO));
    renderMode=Mpv422FluidPolicy.initialRenderMode(selectedRenderMode);
    selectedDecoderMode=Mpv422FluidPolicy.SW;
    activeDecoderMode=Mpv422FluidPolicy.SW;
    videoOnly=true; // Direct FFmpeg JNI first stage is video only.
   }
   if(Native422V2Profile.enabled(getIntent().getStringExtra("decoder_engine"))){
    selectedRenderMode=Native422V2Profile.renderMode();
    renderMode=Native422V2Profile.renderMode();
    selectedDecoderMode=Native422V2Profile.decoderMode();
    activeDecoderMode=selectedDecoderMode;
    videoOnly=Native422V2Profile.videoOnly();
   }
   if(SafePlaybackMode.enabled(this)){
    // Direct developer entry can still diagnose, with SW/no-audio/native.
    // Normal channel selection is forced to DVB in safe mode.
    selectedRenderMode=Mpv422FluidPolicy.GPU_NATIVE;
    renderMode=Mpv422FluidPolicy.GPU_NATIVE;
    selectedDecoderMode=Mpv422FluidPolicy.SW;
    activeDecoderMode=selectedDecoderMode;
    videoOnly=true;
   }
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
  renderButton=small("");
   updateRenderButton();
   renderButton.setOnClickListener(v->chooseRenderMode());
   decoderButton=small("");
   updateDecoderButton();
   decoderButton.setOnClickListener(v->chooseDecoderMode());
   Button subtitles=small(tx("الترجمة","Subtitles"));
  subtitles.setOnClickListener(v->subtitlePicker());
  Button retry=small(tx("إعادة الفيديو","Retry video"));
  retry.setOnClickListener(v->{
   frameRecoveryUsed=false;terminalFrameFailureShown=false;
   if(playbackUrl!=null)restartRenderer();
  });
  Button back=small(tx("خروج","Exit"));
  back.setOnClickListener(v->finish());
  for(Button button:new Button[]{diagnostic,audioButton,renderButton,decoderButton,subtitles,retry,back})
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
  button.setTextSize(12);
  button.setMinHeight(dp(48));
  button.setPadding(dp(2),0,dp(2),0);
  button.setFocusable(true);
  // Firmware default buttons were white-on-white in field photos.
  // Explicit dark backgrounds, bright labels and visible D-pad focus.
  android.graphics.drawable.StateListDrawable bg=
    new android.graphics.drawable.StateListDrawable();
  int[][] states={
    new int[]{android.R.attr.state_pressed},
    new int[]{android.R.attr.state_focused},
    new int[]{}};
  int[] fills={0xff31546f,0xff294259,0xff1c2430};
  for(int i=0;i<states.length;i++){
   android.graphics.drawable.GradientDrawable shape=
     new android.graphics.drawable.GradientDrawable();
   shape.setColor(fills[i]);
   shape.setStroke(dp(i==1?2:1),i==1?0xff93dcff:0xff55616f);
   shape.setCornerRadius(dp(5));
   bg.addState(states[i],shape);
  }
  button.setBackground(bg);
  return button;
 }
 private void updateAudioButton(){
  if(audioButton!=null)audioButton.setText(videoOnly?
   tx("فيديو فقط","Video only"):tx("مع الصوت","Audio on"));
 }
 private void updateRenderButton(){
  if(renderButton==null)return;
  // Keep the remote button legible on 720p receivers; the dialog and
  // Stats show the full pixel format, bit depth and precise resolution.
  String label=Mpv422FluidPolicy.GPU_NATIVE.equals(renderMode)?"GPU":
    Mpv422FluidPolicy.targetHeight(renderMode)+"p "+
      (Mpv422FluidPolicy.is422(renderMode)?"422":"420");
  renderButton.setText(Mpv422FluidPolicy.AUTO.equals(selectedRenderMode)
    ?"Auto "+label:label);
 }
 private void updateDecoderButton(){
  if(decoderButton!=null)decoderButton.setText(
    Mpv422FluidPolicy.HW.equals(selectedDecoderMode)?
      tx("HW تجريبي","HW test"):tx("SW فك","SW decode"));
 }
 private void chooseDecoderMode(){
  String[] modes={Mpv422FluidPolicy.SW,Mpv422FluidPolicy.HW};
  String[] labels={
    tx("SW / FFmpeg — موصى به لفيد 4:2:2 10-bit",
      "SW / FFmpeg — recommended for 4:2:2 10-bit"),
    tx("HW / MediaCodec-copy — تجريبي؛ قد يرجع إلى SW",
      "HW / MediaCodec-copy — experimental, may fall back to SW")
  };
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("طريقة فك الفيديو · ليست صيغة إخراج الصورة",
     "Decoder backend · separate from output pixel format"))
   .setSingleChoiceItems(labels,
     Mpv422FluidPolicy.HW.equals(selectedDecoderMode)?1:0,(dialog,which)->{
    String chosen=modes[which];
    dialog.dismiss();
    if(chosen.equals(selectedDecoderMode))return;
    selectedDecoderMode=chosen;
    activeDecoderMode=chosen;
    hardwareRecoveryUsed=false;
    nativeRecoveryUsed=false;
    frameRecoveryUsed=false;terminalFrameFailureShown=false;
    prefs().edit().putString("independent_422_decoder_mode",chosen).apply();
    updateDecoderButton();
    if(playbackUrl!=null&&!stopped)restartRenderer();
   }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void chooseRenderMode(){
  final String[] modes=Mpv422FluidPolicy.publicModes();
  String[] options=new String[modes.length];
  for(int i=0;i<modes.length;i++){
   String mode=modes[i];
   if(Mpv422FluidPolicy.AUTO.equals(mode))
    options[i]=tx("تلقائي 420: 720 ← 540 ← 360 عند تكرر سقوط الإطارات",
      "Auto 420: 720 → 540 → 360 on sustained VO drops");
   else if(Mpv422FluidPolicy.GPU_NATIVE.equals(mode))
    options[i]=tx("GPU أصلي بدون فلتر (صيغة المصدر)",
      "GPU native without filter (source format)");
   else
    options[i]=Mpv422FluidPolicy.readable(mode);
  }
  int selected=0;
  for(int i=0;i<modes.length;i++)
   if(modes[i].equals(selectedRenderMode)){selected=i;break;}
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("صيغة ودقة الصورة بعد فك المصدر 4:2:2",
     "Pixel format and resolution AFTER decoding source 4:2:2"))
   .setSingleChoiceItems(options,selected,(dialog,which)->{
    selectedRenderMode=modes[which];
    renderMode=Mpv422FluidPolicy.initialRenderMode(selectedRenderMode);
    autoDownshiftSteps=0;
    frameRecoveryUsed=false;terminalFrameFailureShown=false;
    activeDecoderMode=selectedDecoderMode;
    hardwareRecoveryUsed=false;
    nativeRecoveryUsed=false;
    prefs().edit().putString("independent_422_fluid_mode",selectedRenderMode)
      .remove("independent_422_auto_action").apply();
    updateRenderButton();
    dialog.dismiss();
    if(playbackUrl!=null&&!stopped)restartRenderer();
   }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
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
  final int callbackEpoch=rendererEpoch;
  loaded=false;previousPosition=-1;previousPositionAt=0;
   previousVoDrops=-1;previousDropSampleAt=0;poorDropSamples=0;
   noFrameSamples=0;
   renderStartedAt=SystemClock.elapsedRealtime();
  nativeLastFrames=0;nativeLastProgressAt=renderStartedAt;
  if("jni_ffmpeg".equals(getIntent().getStringExtra("decoder_engine"))&&
      !nativeFallbackUsed){
   if(Native422Decoder.available()&&Native422Decoder.localAuthorizedTs(url)){
    final Native422Decoder direct=new Native422Decoder(this,videoRoot,
      new Native422Decoder.Listener(){
       @Override public void firstFrame(){
        if(stopped||destroyed||callbackEpoch!=rendererEpoch)return;
        loaded=true;
        prefs().edit().putString("independent_422_runtime",
          "DIRECT_FFMPEG_JNI_FIRST_FRAME")
         .putString("decoder_422_verification","DIRECT_JNI_FIRST_FRAME_ONLY")
         .putString("decoder_kind","NATIVE_LIBAVCODEC_SW").apply();
        display(tx("FFmpeg JNI · عُرض أول إطار؛ نراقب الحركة",
          "FFmpeg JNI first frame rendered; verifying progression"));
       }
       @Override public void failed(String reason){
        if(stopped||destroyed||callbackEpoch!=rendererEpoch)return;
        nativeFallbackUsed=true;
        prefs().edit().putString("native422_runtime","JNI_FAILED_FALLBACK")
         .putString("native422_failure",reason).apply();
        display(tx("فشل فك FFmpeg JNI؛ الرجوع إلى MPV",
          "Direct FFmpeg JNI failed; MPV fallback"));
        restartRenderer();
       }
      },selectedRenderMode);
    directDecoder=direct;
    if(direct.start(url)){
     prefs().edit().putString("independent_422_runtime","DIRECT_FFMPEG_JNI_OPENING")
       .putString("decoder_kind","NATIVE_LIBAVCODEC_SW")
       .putString("native422_selected_output",selectedRenderMode).apply();
     return;
    }
    direct.close();directDecoder=null;
   }
   nativeFallbackUsed=true;
   prefs().edit().putString("native422_runtime","NATIVE_UNAVAILABLE_FALLBACK_MPV").apply();
  }
  prefs().edit().putString("independent_422_runtime","MPV_OPENING")
    .putString("decoder_kind","LIBMPV_INDEPENDENT_ACTIVE_REQUEST_"+activeDecoderMode)
    .putString("decoder_422_verification","MPV_FILE_NOT_YET_LOADED")
    .remove("decoder_last_error").apply();
  display(tx("MPV · إخراج ", "MPV · output ")+
     Mpv422FluidPolicy.outputLabel(selectedRenderMode,renderMode)+
     " · "+activeDecoderMode.toUpperCase(Locale.ROOT));
  try{
   Mpv422Renderer next=new Mpv422Renderer(this,videoRoot,
     new Mpv422Renderer.Listener(){
      @Override public void ready(){
       if(stopped||destroyed||callbackEpoch!=rendererEpoch)return;
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
       if(stopped||destroyed||callbackEpoch!=rendererEpoch)return;
       if(Mpv422FluidPolicy.HW.equals(activeDecoderMode)&&!hardwareRecoveryUsed){
        // Hardware decode is optional. One SW retry on the SAME TS relay,
        // before trying the unfiltered GPU path; no endless restart loops.
        hardwareRecoveryUsed=true;
        activeDecoderMode=Mpv422FluidPolicy.SW;
        prefs().edit().putString("independent_422_hw_fallback_reason",reason)
          .putString("independent_422_auto_action","HW_START_FAILURE_TO_SW").apply();
        display(tx("فشل بدء HW؛ إعادة التشغيل بفك SW",
          "HW startup failed; retrying SW decoder"));
        restartRenderer();
        return;
       }
       if(Mpv422FluidPolicy.recoverWithNative(renderMode,nativeRecoveryUsed,reason)){
        // One attempt at the last known working MPV output. TS stays open.
        nativeRecoveryUsed=true;
        renderMode=Mpv422FluidPolicy.GPU_NATIVE;
        prefs().edit().putString("independent_422_auto_action",
          "FILTER_OPEN_FAILURE_FALLBACK_GPU_NATIVE")
          .putString("independent_422_filter_failure",reason).apply();
        updateRenderButton();
        display(tx("تعذّر فتح الفلتر، تجربة GPU أصلي",
          "Output filter failed; retrying native GPU"));
        restartRenderer();
        return;
       }
       loaded=false;
       prefs().edit().putString("independent_422_runtime","MPV_FAILURE")
        .putString("decoder_last_error",reason).apply();
       display(tx("محرك MPV تعطل: ","MPV engine failed: ")+reason+
        tx(" · يمكن العودة واختيار LibVLC"," · go back to choose LibVLC"));
       // Show the actual missing JNI library / class in a readable dialog,
       // not a single tiny line at the top of a 55-inch TV.
       String report="MPV: "+reason+
         "\nABI: "+java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS)+
         "\nDecoder: dev.jdtech.mpv:libmpv:1.0.0"+
         "\nDevice: "+android.os.Build.MODEL;
       new android.app.AlertDialog.Builder(Independent422Activity.this)
        .setTitle(tx("خطأ تشغيل محرك MPV المستقل",
          "Independent MPV engine failed"))
        .setMessage(report)
        .setNeutralButton(tx("نسخ الخطأ","Copy error"),(d,w)->{
          android.content.ClipboardManager clipboard=
            (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
          if(clipboard!=null)clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("IRAQhub 422 MPV",report));
        })
        .setPositiveButton(tx("موافق","OK"),null)
        .show();
      }
     },videoOnly,renderMode,activeDecoderMode);
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
  rendererEpoch++;
  Mpv422Renderer old=renderer;
  renderer=null;loaded=false;
  Native422Decoder direct=directDecoder;
  directDecoder=null;
  if(direct!=null)try{direct.close();}catch(Throwable ignored){}
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
    "\nEngine: "+(Native422V2Profile.enabled(getIntent().getStringExtra("decoder_engine"))?
      "IRAQhub Native422 V2 session (FFmpeg via libmpv)":
      "libmpv / FFmpeg (NOT LibVLC)")+
    "\nNative JNI present: "+Native422Decoder.available()+
    "\nDirect rendered frames: "+(directDecoder==null?"NOT_ACTIVE":directDecoder.displayedFrames())+
    "\nNative fallback used: "+nativeFallbackUsed+
    "\nAudio decoding: "+(videoOnly?"DISABLED (aid=no)":"ENABLED")+
    "\nProfile: "+Software422Profile.effective(this)+
     "\nOutput selection: "+selectedRenderMode+
     "\nDecoder requested: "+selectedDecoderMode+
     "\nDecoder attempted: "+activeDecoderMode+
     "\nMPV actual hwdec-current: "+(m==null?"UNAVAILABLE":m.hardwareDecoder)+
     "\nHW fallback cause: "+prefs().getString("independent_422_hw_fallback_reason","NONE")+
     "\nActual output path: "+Mpv422FluidPolicy.outputLabel(selectedRenderMode,renderMode)+
     "\nCPU filter: "+prefs().getString("mpv_422_output_filter","NOT_SET")+
     "\nAuto downshift: "+prefs().getString("independent_422_auto_action","NOT_TRIGGERED")+
     "\nFirst filter failure: "+prefs().getString("independent_422_filter_failure","NONE")+
     "\nMPV startup: "+(renderer==null?"NOT_ACTIVE":renderer.diagnosticState())+
    "\nTS: "+String.format(Locale.ROOT,"%.2f Mbit/s",bitrate/1000000.0)+
    "\nTS original CC gaps: "+prefs().getLong("software_ts_cc_gaps",-1)+
    "\nTS transport error packets: "+prefs().getLong("software_ts_tei_packets",-1)+
    "\nTS invalid sync/headers: "+prefs().getLong("software_ts_invalid_packets",-1)+
    "\nTS last gap PID: "+prefs().getInt("software_ts_last_gap_pid",-1)+
    "\nPVR rollovers: "+prefs().getInt("software_ts_pvr_rotations",0)+
    "\nNo-frame samples: "+noFrameSamples+
    "\nOne-shot HEVC recovery used: "+frameRecoveryUsed+
    "\nCapture: "+prefs().getString("software_ts_capture_error","NOT_TESTED")+
    "\nlibmpv FILE_LOADED: "+loaded+
    "\nPlayback clock: "+(m==null?"UNKNOWN":String.format(Locale.ROOT,"%.2f s",m.position))+
    "\nEstimated source FPS: "+(m==null?"UNKNOWN":String.format(Locale.ROOT,"%.2f",m.estimatedFps))+
    "\nDropped: "+(m==null?-1:m.dropped)+
    "\nDecoder dropped: "+(m==null?-1:m.decoderDropped)+
    "\nOutput: "+(m==null?"unknown":m.outputWidth+"x"+m.outputHeight)+
    "\nDecoded source pixel format: "+(m==null?"unknown":m.videoFormat)+
     "\nFilter output pixel format: "+(m==null?"unknown":m.outputFormat)+
     "\nDropped in recent interval: "+prefs().getInt("independent_422_recent_vo_drops",-1)+
    "\nClock health: "+prefs().getString("independent_422_health","NOT_SAMPLED")+
    "\nCPU process: "+prefs().getInt("auto_422_cpu_percent",-1)+"% of one core"+
    "\nVideo frames visually moving: NOT AUTOMATICALLY VERIFIED"+
    "\nFocus service: "+prefs().getString("independent_422_focus","NOT_BOUND")+
    "\nDevice supported ABIs: "+java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS)+
    "\nMPV Android AAR: dev.jdtech.mpv:libmpv:1.0.0"+
    "\nBootstrap error: "+prefs().getString("decoder_last_error","NONE");
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
