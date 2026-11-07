package com.smarthub.plus;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
final class Mpv422Renderer implements SurfaceHolder.Callback, MPVLib.EventObserver {
 interface Listener { void ready(); void failed(String reason); }
 private final Context context;
 private final SurfaceView view;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Listener listener;
 private MPVLib mpv;
 private boolean destroyed,loaded;
 private long started;
 private final Runnable timeout=()->{
  if(!destroyed&&!loaded)failure("MPV_NO_FILE_LOADED_15S");
 };
 Mpv422Renderer(Context context,FrameLayout root,Listener listener){
  this.context=context;
  this.listener=listener;
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
   mpv=MPVLib.create(context);
   if(mpv==null){failure("MPV_CREATE_RETURNED_NULL");return false;}
   mpv.addObserver(this);
   mpv.setOptionString("hwdec","no");
   mpv.setOptionString("vo","gpu");
   mpv.setOptionString("profile","fast");
   mpv.setOptionString("cache","yes");
   mpv.setOptionString("cache-secs","2");
   mpv.setOptionString("demuxer-max-bytes","16777216");
   mpv.setOptionString("video-sync","audio");
   mpv.setOptionString("vd-lavc-threads","4");
   mpv.setOptionString("audio-display","no");
   mpv.init();
   if(view.getHolder().getSurface()!=null&&view.getHolder().getSurface().isValid())
    mpv.attachSurface(view.getHolder().getSurface());
   view.setVisibility(android.view.View.VISIBLE);
   mpv.command(new String[]{"loadfile",url,"replace"});
   main.postDelayed(timeout,15000);
   return true;
  }catch(Throwable t){
   failure("MPV_START_"+t.getClass().getSimpleName());
   return false;
  }
 }
 void close(){
  destroyed=true;main.removeCallbacksAndMessages(null);
  try{view.getHolder().removeCallback(this);}catch(Throwable ignored){}
  try{if(mpv!=null){mpv.removeObserver(this);mpv.detachSurface();mpv.destroy();}}
  catch(Throwable ignored){}finally{mpv=null;}
  view.setVisibility(android.view.View.GONE);
 }
 private void failure(String error){
  if(destroyed)return;
  context.getSharedPreferences("smarthub",0).edit()
   .putString("software_422_runtime","MPV_ERROR")
   .putString("decoder_last_error",error).apply();
  main.post(()->{if(!destroyed)listener.failed(error);});
 }
 @Override public void surfaceCreated(SurfaceHolder holder){
  try{if(mpv!=null&&holder.getSurface().isValid())mpv.attachSurface(holder.getSurface());}
  catch(Throwable t){failure("MPV_SURFACE_"+t.getClass().getSimpleName());}
 }
 @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
 @Override public void surfaceDestroyed(SurfaceHolder holder){
  try{if(mpv!=null)mpv.detachSurface();}catch(Throwable ignored){}
 }
 @Override public void event(int eventId){
  if(eventId==MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED){
   loaded=true;main.removeCallbacks(timeout);
   context.getSharedPreferences("smarthub",0).edit()
    .putString("software_422_runtime","MPV_FILE_LOADED")
    .putString("decoder_422_verification","MPV_FILE_LOADED_PICTURE_NOT_VERIFIED")
    .putLong("mpv_load_latency_ms",SystemClock.elapsedRealtime()-started).apply();
   main.post(()->{if(!destroyed)listener.ready();});
  }
 }
 @Override public void endFile(int reason,int error){
  if(error<0)failure("MPV_END_FILE_ERROR_"+error);
 }
 @Override public void eventProperty(String property){}
 @Override public void eventProperty(String property,long value){}
 @Override public void eventProperty(String property,double value){}
 @Override public void eventProperty(String property,boolean value){}
 @Override public void eventProperty(String property,String value){}
}
