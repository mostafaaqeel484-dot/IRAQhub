package com.smarthub.plus;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import dev.jdtech.mpv.MPVLib;

/**
 * Embedded libmpv Android AAR renderer. The DVB TS relay is owned by the
 * calling Activity, so switching engines does NOT retune the satellite.
 * Any JNI/ABI/codec failure is reported to the caller and may fall back
 * to the existing LibVLC engine.
 */
final class Mpv422Renderer implements SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver {
 interface Listener { void ready(); void failed(String reason); }
 private final Context context;
 private final SurfaceView view;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Listener listener;
 private final boolean videoOnly;
 private final String fluidMode; // null for the unchanged legacy embedded engine
 private final String decoderMode; // SW default; HW is experimental MediaCodec
 private MPVLib mpv;
 private volatile boolean destroyed,loaded,initialized,attached,playbackRequested,failed;
 private volatile String pendingUrl,lastNativeWarning="NONE";
 private volatile int lastEvent=-1,lastVfOptionResult=0;
 private long started;
 private final Runnable timeout=this::onFileLoadTimeout;
 private void onFileLoadTimeout(){
  // The callback only executes after the constructor has initialized
  // final fluidMode, unlike field-initializer lambda capture on javac.
  if(!destroyed&&!loaded){
   String cause="MPV_NO_FILE_LOADED_"+(fluidMode==null?"15":"22")+
    "S_"+diagnosticState();
   failure(cause);
  }
 }
 private final Runnable surfaceTimeout=()->{
  if(!destroyed&&!loaded&&!playbackRequested)
   failure("MPV_SURFACE_NOT_READY_10S_"+diagnosticState());
 };
 Mpv422Renderer(Context context,FrameLayout root,Listener listener){
  this(context,root,listener,false);
 }
 Mpv422Renderer(Context context,FrameLayout root,Listener listener,boolean videoOnly){
  this(context,root,listener,videoOnly,null);
 }
 Mpv422Renderer(Context context,FrameLayout root,Listener listener,
     boolean videoOnly,String fluidMode){
  this(context,root,listener,videoOnly,fluidMode,Mpv422FluidPolicy.SW);
 }
 Mpv422Renderer(Context context,FrameLayout root,Listener listener,
     boolean videoOnly,String fluidMode,String decoderMode){
  this.context=context;
  this.listener=listener;
  this.videoOnly=videoOnly;
  this.fluidMode=fluidMode;
  this.decoderMode=Mpv422FluidPolicy.decoderMode(decoderMode);
  view=new SurfaceView(context);
  view.setKeepScreenOn(true);
  view.setVisibility(android.view.View.GONE);
  root.addView(view,0,new FrameLayout.LayoutParams(-1,-1));
  view.getHolder().addCallback(this);
 }
 boolean start(String url){
  if(destroyed||url==null||url.isEmpty())return false;
  try{
   started=SystemClock.elapsedRealtime();
   // The libmpv Android AAR's MPVLib companion loads native libraries
   // 'mpv' and 'player' on first access. Preload them explicitly so X5M
   // reports a concrete dlopen failure instead of an opaque
   // MPV_START_NoClassDefFoundError on a subsequent class initialization.
   System.loadLibrary("mpv");
   System.loadLibrary("player");
   Class.forName("dev.jdtech.mpv.MPVLib",true,context.getClassLoader());
   mpv=MPVLib.create(context);
   if(mpv==null){failure("MPV_CREATE_RETURNED_NULL");return false;}
   mpv.addObserver(this);
   mpv.addLogObserver(this);
   mpv.setOptionString("msg-level","all=warn");
   // Wait for Android's SurfaceHolder before loadfile. Upstream MPV
   // Android launches a file only once the SurfaceView is attached.
   mpv.setOptionString("idle","once");
   mpv.setOptionString("force-window","no");
   // Leave legacy MPV untouched. Only the isolated player exposes HW/SW.
   // MediaCodec-copy can decline unsupported 4:2:2 and decode in software:
   // query hwdec-current instead of claiming HW from the selected option.
   String requestedHwdec=fluidMode==null?"no":
      Mpv422FluidPolicy.hwdecOption(decoderMode);
   int hwResult=mpv.setOptionString("hwdec",requestedHwdec);
   if(hwResult<0&&!"no".equals(requestedHwdec)){
    Log.w("IRAQhub422","HW decode option rejected; using software");
    mpv.setOptionString("hwdec","no");
   }
   context.getSharedPreferences("smarthub",0).edit()
     .putString("mpv_422_hwdec_requested",requestedHwdec)
     .putString("mpv_422_hwdec_option_status",Integer.toString(hwResult))
     .apply();
   mpv.setOptionString("vo","gpu");
   mpv.setOptionString("profile","fast");
   if(fluidMode!=null){
    // The old independent MPV build worked with Android's own GPU context.
    // Do NOT force gpu-api/opengl-es/fbo-format during initialisation:
    // those additional overrides preceded the X5M FILE_LOADED timeout.
    // Keep cheap bilinear scaling and the optional FFmpeg output filter.
    mpv.setOptionString("scale","bilinear");
    mpv.setOptionString("dscale","bilinear");
    mpv.setOptionString("cscale","bilinear");
    mpv.setOptionString("deband","no");
    mpv.setOptionString("dither","no");
    mpv.setOptionString("linear-downscaling","no");
    // Only post-decode scaling/pixel conversion: 422 retains 10-bit,
   // 420 uses 8-bit; neither changes the original MPEG-TS decoder work.
   // Keep GPU-native mode for comparison on the vendor X5M ROM.
    String filter=Mpv422FluidPolicy.videoFilter(fluidMode);
    if(!filter.isEmpty()){
     lastVfOptionResult=mpv.setOptionString("vf",filter);
     if(lastVfOptionResult<0)throw new IllegalArgumentException(
       "MPV_VF_REJECTED_"+lastVfOptionResult+"_"+
        fluidMode);
    }
    mpv.setOptionString("vd-lavc-fast","no");
    context.getSharedPreferences("smarthub",0).edit()
      .putString("mpv_422_render_mode",fluidMode)
      .putString("mpv_422_output_filter",filter.isEmpty()?"GPU_NATIVE_NO_FILTER":filter)
      .apply();
   }
   mpv.setOptionString("cache","yes");
   String profile=Software422Profile.effective(context);
   // X5M CPU-focus: no frame interpolation. Smooth trades ONLY deblocking
   // of non-key frames for throughput; never skip reference frames/IDCT.
   mpv.setOptionString("interpolation","no");
   boolean smooth=Software422Profile.SMOOTH.equals(profile);
   if(smooth){
    // MPV's vd-lavc-fast may assume 4:2:0 even when input is 4:2:2.
    // Keep old LibVLC-comparison behaviour only in the legacy embedded path;
    // on the isolated engine choose correctness of 422 decode first.
    if(fluidMode==null)mpv.setOptionString("vd-lavc-fast","yes");
    mpv.setOptionString("vd-lavc-skiploopfilter",
      fluidMode==null?"nonkey":"nonref");
    mpv.setOptionString("framedrop","vo");
   }
   context.getSharedPreferences("smarthub",0).edit()
     .putString("mpv_422_cpu_profile",smooth?
       (fluidMode==null?"SMOOTH_NONKEY_DEBLOCK":"SMOOTH_SAFE_422_NONREF"):
       "NORMAL").apply();
   boolean pressure=context.getSharedPreferences("smarthub",0)
     .getBoolean("auto_422_low_memory",false);
   mpv.setOptionString("cache-secs",pressure?"1":"2");
   mpv.setOptionString("demuxer-max-bytes",pressure?"8388608":"16777216");
   // When audio is disabled, mpv uses its system clock for video-sync=audio.
   // This stops audio decoding and avoids audio clock catch-up frame drops;
   // it is an explicit performance experiment, not an alleged HW 4:2:2 path.
   if(videoOnly)mpv.setOptionString("aid","no");
   mpv.setOptionString("video-sync","audio");
   mpv.setOptionString("vd-lavc-threads",Integer.toString(Auto422Policy.decoderThreads(profile,Runtime.getRuntime().availableProcessors())));
   mpv.setOptionString("audio-display","no");
   pendingUrl=url;
   mpv.init();
   initialized=true;
   view.setVisibility(android.view.View.VISIBLE);
   // Some X5M versions create SurfaceHolder before/after mpv.init.
   // Either order is handled, but attach/loadfile happen exactly once.
   attachAndPlay(view.getHolder());
   main.postDelayed(surfaceTimeout,10000);
   return true;
  }catch(Throwable t){
   String detail=MpvInitFailure.describe(t);
   Log.e("IRAQhub422","MPV bootstrap failed: "+detail,t);
   failure("MPV_START_"+detail);
   return false;
  }
 }
 /** libmpv returns real playable track IDs; demux-id identifies DVB PIDs. */
 static final class Sub {
  final int id,demuxId;final String label;
  Sub(int id,int demuxId,String label){this.id=id;this.demuxId=demuxId;this.label=label;}
 }
 java.util.List<Sub> subtitleTracks(){
  java.util.ArrayList<Sub> result=new java.util.ArrayList<>();
  if(destroyed||mpv==null||!loaded)return result;
  try{
   Integer size=mpv.getPropertyInt("track-list/count");
   int count=size==null?0:Math.min(64,Math.max(0,size));
   for(int i=0;i<count;i++){
    if(!"sub".equals(mpv.getPropertyString("track-list/"+i+"/type")))continue;
    Integer id=mpv.getPropertyInt("track-list/"+i+"/id");
    Integer demux=mpv.getPropertyInt("track-list/"+i+"/demux-id");
    if(id==null||id<=0)continue;
    String language=mpv.getPropertyString("track-list/"+i+"/lang");
    String title=mpv.getPropertyString("track-list/"+i+"/title");
    result.add(new Sub(id,demux==null?-1:demux,
      (language==null?"und":language)+" · "+
      (title==null?"subtitle":title)+" · PID "+(demux==null?"?":demux)));
   }
  }catch(Throwable ignored){}
  return result;
 }
 boolean selectSubtitle(int id){
  if(mpv==null||destroyed||!loaded)return false;
  try{
   if(id>=0){
    boolean found=false;
    for(Sub track:subtitleTracks())if(track.id==id){found=true;break;}
    if(!found)return false;
   }
   mpv.setPropertyString("sid",id<0?"no":Integer.toString(id));
   return true;
  }catch(Throwable error){return false;}
 }
 void selectPreferredPid(int pid){
  if(pid<0)return;
  for(Sub track:subtitleTracks())if(track.demuxId==pid){
   if(selectSubtitle(track.id)){
    context.getSharedPreferences("smarthub",0).edit()
      .putString("native_subtitle_status","MPV_MATCHED_DEMUX_PID_"+pid).apply();
   }
   return;
  }
  context.getSharedPreferences("smarthub",0).edit()
    .putString("native_subtitle_status","MPV_SUBTITLE_PID_NOT_IN_STREAM_"+pid).apply();
 }
 /** Separate-process diagnostics. Playback time is NOT proof of visual
  * frames; frame-drop and decode properties are only libmpv counters. */
 static final class Metrics {
  final double position,estimatedFps;
  final int dropped,decoderDropped,outputWidth,outputHeight;
  final String videoFormat,outputFormat,hardwareDecoder;
  Metrics(double position,double estimatedFps,int dropped,int decoderDropped,
      int outputWidth,int outputHeight,String videoFormat,String outputFormat,
      String hardwareDecoder){
   this.position=position;this.estimatedFps=estimatedFps;
   this.dropped=dropped;this.decoderDropped=decoderDropped;
   this.outputWidth=outputWidth;this.outputHeight=outputHeight;
   this.videoFormat=videoFormat;this.outputFormat=outputFormat;
   this.hardwareDecoder=hardwareDecoder;
  }
 }
 Metrics metrics(){
  if(mpv==null||destroyed||!loaded)return null;
  try{
   Double pos=mpv.getPropertyDouble("time-pos");
   Double fps=mpv.getPropertyDouble("estimated-vf-fps");
   Integer drop=mpv.getPropertyInt("frame-drop-count");
   Integer decoderDrop=mpv.getPropertyInt("decoder-frame-drop-count");
   Integer width=mpv.getPropertyInt("video-out-params/w");
   Integer height=mpv.getPropertyInt("video-out-params/h");
   String format=mpv.getPropertyString("video-params/pixelformat");
   String outputFormat=mpv.getPropertyString("video-out-params/pixelformat");
   String actualHwdec="UNAVAILABLE";
   // Some vendor builds omit this property; never discard frame counters
   // simply because the optional hwdec-current query is unsupported.
   try{
    String value=mpv.getPropertyString("hwdec-current");
    if(value!=null)actualHwdec=value;
   }catch(Throwable ignored){}
   return new Metrics(pos==null?-1:pos,fps==null?-1:fps,
      drop==null?-1:drop,decoderDrop==null?-1:decoderDrop,
      width==null?-1:width,height==null?-1:height,
      format==null?"unknown":format,
      outputFormat==null?"unknown":outputFormat,
      actualHwdec==null?"UNAVAILABLE":actualHwdec);
  }catch(Throwable unavailable){return null;}
 }
 String diagnosticState(){
  return "event="+lastEvent+"_surface="+attached+
    "_requested_decoder="+decoderMode+
    "_command="+playbackRequested+"_vf="+lastVfOptionResult+
    "_native="+safeMessage(lastNativeWarning);
 }
 private String safeMessage(String msg){
  if(msg==null)return "NONE";
  String clean=msg.replace('\n',' ').replace('\r',' ').replace(' ','_');
  return clean.length()>130?clean.substring(0,130):clean;
 }
 private void attachAndPlay(SurfaceHolder holder){
  if(destroyed||failed||!initialized||mpv==null||attached)return;
  if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
  try{
   mpv.attachSurface(holder.getSurface());
   attached=true;
   android.graphics.Rect rect=holder.getSurfaceFrame();
   if(rect!=null&&rect.width()>0&&rect.height()>0)
    mpv.setPropertyString("android-surface-size",
      rect.width()+"x"+rect.height());
   mpv.setOptionString("force-window","yes");
   if(!playbackRequested&&pendingUrl!=null){
    playbackRequested=true;
    mpv.command(new String[]{"loadfile",pendingUrl,"replace"});
    main.postDelayed(timeout,fluidMode==null?15000:22000);
   }
  }catch(Throwable problem){
   String detail=MpvInitFailure.describe(problem);
   Log.e("IRAQhub422","Surface/playback attach failed: "+detail,problem);
   failure("MPV_SURFACE_ATTACH_"+detail);
  }
 }
 void close(){
  destroyed=true;main.removeCallbacksAndMessages(null);
  try{view.getHolder().removeCallback(this);}catch(Throwable ignored){}
  try{if(mpv!=null){
   mpv.removeObserver(this);mpv.removeLogObserver(this);
   if(attached)mpv.detachSurface();
   mpv.destroy();
  }}
  catch(Throwable error){
   Log.w("IRAQhub422","MPV shutdown failed",error);
  }finally{mpv=null;}
  view.setVisibility(android.view.View.GONE);
  android.view.ViewParent parent=view.getParent();
  if(parent instanceof FrameLayout)((FrameLayout)parent).removeView(view);
 }
 private void failure(String error){
  if(destroyed||failed)return;
  failed=true;
  main.removeCallbacks(timeout);
  main.removeCallbacks(surfaceTimeout);
  Log.e("IRAQhub422","MPV failed: "+error);
  context.getSharedPreferences("smarthub",0).edit()
   .putString("software_422_runtime","MPV_ERROR")
   .putString("decoder_last_error",error).apply();
  main.post(()->{if(!destroyed)listener.failed(error);});
 }
 @Override public void surfaceCreated(SurfaceHolder holder){
  attachAndPlay(holder);
 }
 @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
  try{
   if(initialized&&mpv!=null&&width>0&&height>0)
    mpv.setPropertyString("android-surface-size",width+"x"+height);
  }catch(Throwable e){Log.w("IRAQhub422","surface size",e);}
  attachAndPlay(holder);
 }
 @Override public void surfaceDestroyed(SurfaceHolder holder){
  try{if(attached&&mpv!=null){
   mpv.setPropertyString("vo","null");
   mpv.setPropertyString("force-window","no");
   mpv.detachSurface();
   attached=false;
  }}catch(Throwable ignored){}
 }
 @Override public void logMessage(String prefix,int level,String message){
  if(level<=30){
   String msg=(prefix==null?"mpv":prefix)+": "+(message==null?"":message);
   lastNativeWarning=msg;
   Log.w("IRAQhub422",msg);
  }
 }
 @Override public void event(int eventId){
  lastEvent=eventId;
  if(eventId==MPVLib.MpvEvent.MPV_EVENT_END_FILE){
   if(!destroyed&&!failed)failure((loaded?
      "MPV_EVENT_END_FILE":"MPV_END_BEFORE_FILE_LOADED")+
      "_"+diagnosticState());
   return;
  }
  if(eventId==MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED){
   loaded=true;
   main.removeCallbacks(timeout);
   main.removeCallbacks(surfaceTimeout);
   context.getSharedPreferences("smarthub",0).edit()
    .putString("software_422_runtime","MPV_FILE_LOADED")
    .putString("decoder_422_verification","MPV_FILE_LOADED_PICTURE_NOT_VERIFIED")
    .putLong("mpv_load_latency_ms",SystemClock.elapsedRealtime()-started).apply();
   main.post(()->{if(!destroyed)listener.ready();});
  }
 }
 @Override public void eventProperty(String property){}
 @Override public void eventProperty(String property,long value){}
 @Override public void eventProperty(String property,double value){}
 @Override public void eventProperty(String property,boolean value){}
 @Override public void eventProperty(String property,String value){}
}
