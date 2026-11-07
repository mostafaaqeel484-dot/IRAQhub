package com.smarthub.plus;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import dev.jdtech.mpv.MPVLib;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process libmpv 1.0.0 (FFmpeg) renderer for 4:2:2 on Android 14.
 *
 * Uses EXACTLY the same app-owned LiveTsRelay URL as LibVLC, therefore it
 * neither retunes the DVB receiver nor bypasses OEM CA/BISS. Software
 * decoding is explicitly selected; success requires device video inspection.
 *
 * One instance at a time. Never open MPV and VLC concurrently.
 */
final class MpvPlaybackController implements SurfaceHolder.Callback,MPVLib.EventObserver {
 interface Listener { void status(String code); void failure(String code); }
 private final Activity activity;
 private final FrameLayout host;
 private final Listener listener;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final AtomicBoolean stopped=new AtomicBoolean();
 private MPVLib lib;
 private SurfaceView surface;
 private String url;
 private boolean attached,loaded;
 private long openedAt;
 private final Runnable missingVideo=()->{
  if(!stopped.get()&&!loaded)listener.failure("MPV_NO_VIDEO_CONFIG_16S");
 };

 MpvPlaybackController(Activity activity,FrameLayout host,Listener listener){
  this.activity=activity;this.host=host;this.listener=listener;
 }
 static boolean available(){
  try{Class.forName("dev.jdtech.mpv.MPVLib");return true;}
  catch(Throwable missing){return false;}
 }
 boolean open(String source){
  if(source==null||source.isEmpty())return false;
  if(!source.startsWith("http://127.0.0.1:")&&
     !source.startsWith("https://")&&!source.startsWith("content://"))
   return false;
  try{
   url=source;
   openedAt=SystemClock.elapsedRealtime();
   lib=MPVLib.create(activity.getApplicationContext());
   if(lib==null)return false;
   option("config","no");
   option("hwdec","no");               // Never assume X5M HW 4:2:2 decode
   option("vo","gpu");
   option("gpu-api","opengl");          // OpenGL ES output via Android Surface
   option("video-sync","audio");        // Avoid speed-changing video
   option("interpolation","no");       // Lower GPU pressure on X5M
   option("scale","bilinear");
   option("dscale","bilinear");
   option("cscale","bilinear");
   option("cache","yes");
   option("cache-secs","1.2");
   option("demuxer-max-bytes","16777216");
   option("demuxer-readahead-secs","1");
   option("audio-display","no");
   option("force-window","no");
   option("idle","yes");
   lib.init();
   lib.addObserver(this);
   lib.observeProperty("vo-configured",MPVLib.MpvFormat.MPV_FORMAT_FLAG);
   surface=new SurfaceView(activity);
   surface.setKeepScreenOn(true);
   surface.setBackgroundColor(0xff000000);
   host.addView(surface,0,new FrameLayout.LayoutParams(-1,-1));
   surface.getHolder().addCallback(this);
   if(surface.getHolder().getSurface().isValid())
    surfaceCreated(surface.getHolder());
   main.postDelayed(missingVideo,16000L);
   listener.status("MPV_INIT_READY_WAITING_SURFACE");
   return true;
  }catch(Throwable unsupported){
   listener.status("MPV_INIT_FAILED_"+unsupported.getClass().getSimpleName());
   close();
   return false;
  }
 }
 private void option(String name,String value){
  int result=lib.setOptionString(name,value);
  // Some options are deprecated by libmpv; keep a safe default rather
  // than aborting the entire decoder for optional tuning flags.
  if(result<0&&("vo".equals(name)||"hwdec".equals(name)))
   throw new IllegalArgumentException("MPV_REQUIRED_OPTION_"+name);
 }
 @Override public void surfaceCreated(SurfaceHolder holder){
  if(stopped.get()||attached||lib==null)return;
  try{
   attached=true;
   lib.attachSurface(holder.getSurface());
   lib.setOptionString("force-window","yes");
   lib.command(new String[]{"loadfile",url,"replace"});
   listener.status("MPV_LOADING_TS");
  }catch(Throwable failure){
   listener.failure("MPV_SURFACE_"+failure.getClass().getSimpleName());
  }
 }
 @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){
  if(lib!=null&&!stopped.get())
   try{lib.setPropertyString("android-surface-size",w+"x"+h);}catch(Throwable ignored){}
 }
 @Override public void surfaceDestroyed(SurfaceHolder holder){
  if(lib!=null&&attached){
   try{lib.setPropertyString("vo","null");}catch(Throwable ignored){}
   try{lib.detachSurface();}catch(Throwable ignored){}
   attached=false;
  }
 }
 @Override public void event(int eventId){
  if(stopped.get())return;
  if(eventId==MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG ||
     eventId==MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED){
   main.post(()->{
    if(stopped.get())return;
    if(eventId==MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG){
     loaded=true;main.removeCallbacks(missingVideo);
     listener.status("MPV_VIDEO_CONFIGURED_VISUAL_NOT_VERIFIED");
    }else listener.status("MPV_FILE_LOADED");
   });
  }else if(eventId==MPVLib.MpvEvent.MPV_EVENT_END_FILE||
           eventId==MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN){
   main.post(()->{if(!stopped.get())listener.failure("MPV_END_OR_ERROR");});
  }
 }
 @Override public void eventProperty(String property){
  /* No content or user identifiers leave the device. */
 }
 @Override public void eventProperty(String property,long value){}
 @Override public void eventProperty(String property,double value){}
 @Override public void eventProperty(String property,boolean value){
  if("vo-configured".equals(property)&&value)main.post(()->{
   if(stopped.get())return;
   loaded=true;main.removeCallbacks(missingVideo);
   listener.status("MPV_VO_CONFIGURED_COLOR_NOT_VERIFIED");
  });
 }
 @Override public void eventProperty(String property,String value){}

 void cycleAudio(){
  if(!stopped.get()&&lib!=null)try{lib.command(new String[]{"cycle","aid"});}
  catch(Throwable ignored){}
 }
 void cycleSubtitle(){
  if(!stopped.get()&&lib!=null)try{lib.command(new String[]{"cycle","sid"});}
  catch(Throwable ignored){}
 }
 void close(){
  if(!stopped.compareAndSet(false,true))return;
  main.removeCallbacks(missingVideo);
  MPVLib old=lib;lib=null;
  if(old!=null){
   try{old.removeObserver(this);}catch(Throwable ignored){}
   if(attached){
    try{old.setPropertyString("vo","null");}catch(Throwable ignored){}
    try{old.detachSurface();}catch(Throwable ignored){}
    attached=false;
   }
   try{old.command(new String[]{"stop"});}catch(Throwable ignored){}
   try{old.destroy();}catch(Throwable ignored){}
  }
  if(surface!=null){
   surface.getHolder().removeCallback(this);
   host.removeView(surface);
   surface=null;
  }
 }
}
