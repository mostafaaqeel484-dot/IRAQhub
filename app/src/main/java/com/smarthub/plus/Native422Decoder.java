package com.smarthub.plus;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * True direct JNI libavformat/libavcodec/libswscale decoder.
 * Compiled ONLY by the separate FFmpeg/NDK build profile. The ordinary
 * IRAQhub APK intentionally does not contain this untested native library.
 *
 * One FFmpeg decoding worker, one Android Surface; no LibVLC or libmpv
 * object exists inside this renderer. Audio/PVR remain outside it.
 */
final class Native422Decoder implements SurfaceHolder.Callback,AutoCloseable {
 interface Listener {void firstFrame();void failed(String code);}
 private static final boolean LIB_AVAILABLE;
 static {
  boolean loaded=false;
  try{System.loadLibrary("iraqhub_native422");loaded=true;}
  catch(Throwable missing){Log.i("IRAQhubNative422","Direct FFmpeg JNI not bundled: "+missing);}
  LIB_AVAILABLE=loaded;
 }
 static boolean available(){return LIB_AVAILABLE;}
 static boolean localAuthorizedTs(String value){
  try{
   URI uri=new URI(value);
   return "http".equalsIgnoreCase(uri.getScheme())&&
     "127.0.0.1".equals(uri.getHost())&&
     uri.getPort()>0&&uri.getPort()<=65535&&
     uri.getUserInfo()==null&&uri.getFragment()==null&&
     uri.getPath()!=null&&uri.getPath().endsWith(".ts");
  }catch(Exception invalid){return false;}
 }
 private static native long nativeCreate();
 private native int nativeRun(long handle,String url,Surface surface,
                                    int width,int height,boolean convert420,
                                    boolean audioEnabled);
 private static native void nativeStop(long handle);
 private static native void nativeDestroy(long handle);
 private static native long nativeFrames(long handle);
 private static native long nativeDropped(long handle);
 private static native void nativeSelectAudio(long handle,int streamIndex);
 private final Object handleLock=new Object();
 private final ExecutorService decodeThread=Executors.newSingleThreadExecutor(r->{
  Thread worker=new Thread(r,"iraqhub-direct-ffmpeg-avcodec");
  worker.setPriority(Thread.NORM_PRIORITY+1);
  return worker;
 });
 private final Handler main=new Handler(Looper.getMainLooper());
 private final SurfaceView view;
 private final Listener listener;
 private final Native422OutputProfile.Target target;
 private final Context app;
 private final boolean audioEnabled;
 private final Object audioLock=new Object();
 private final ArrayList<AudioOption> tracks=new ArrayList<>();
 private AudioTrack audioOutput;
 static final class AudioOption {
  final int streamIndex;
  final String language,codec;
  AudioOption(int streamIndex,String language,String codec){
   this.streamIndex=streamIndex;this.language=language;this.codec=codec;
  }
  String label(){return language+" · "+codec+" · stream "+streamIndex;}
 }
 List<AudioOption> audioTracks(){
  synchronized(audioLock){return new ArrayList<>(tracks);}
 }
 boolean selectAudio(int index){
  synchronized(handleLock){
   if(handle==0||closed)return false;
   nativeSelectAudio(handle,index);
   return true;
  }
 }
 private String url;
 private long handle;
 private boolean started,closed,reported;
 private int resultCode;
 Native422Decoder(Context c,FrameLayout container,Listener listener,String mode,
                  boolean audioEnabled){
  this.listener=listener;this.app=c.getApplicationContext();
  this.audioEnabled=audioEnabled;
  target=Native422OutputProfile.target(mode);
  view=new SurfaceView(c);
  view.setKeepScreenOn(true);
  container.addView(view,0,new FrameLayout.LayoutParams(-1,-1));
  view.getHolder().addCallback(this);
 }
 boolean start(String source){
  if(closed||!available()||!localAuthorizedTs(source))return false;
  url=source;
  startIfReady();
  return true;
 }
 private void startIfReady(){
  if(started||closed||url==null||!view.getHolder().getSurface().isValid())return;
  synchronized(handleLock){
   if(started||closed)return;
   handle=nativeCreate();
   if(handle==0)return;
   started=true;
  }
  final long id=handle;
  final Surface surface=view.getHolder().getSurface();
  decodeThread.execute(()->{
   try{
    resultCode=nativeRun(id,url,surface,target.width,target.height,target.convert420,
                             audioEnabled);
   }catch(Throwable error){
    resultCode=-101;
    Log.e("IRAQhubNative422","Native AVCodec bridge failed",error);
   }finally{
    releaseAudio();
    synchronized(handleLock){
     if(handle==id){nativeDestroy(id);handle=0;}
    }
    if(!closed)main.post(()->{
     if(!closed)listener.failed(resultCode==-101?
       "JNI_THROWABLE":"FFMPEG_NATIVE_RUN_"+resultCode);
    });
   }
  });
 }
 /** Track IDs are real PMT stream indices, never user-defined audio names. */
 @SuppressWarnings("unused")
 private void onNativeAudioTrack(int stream,String language,String codec){
  synchronized(audioLock){
   for(AudioOption item:tracks)if(item.streamIndex==stream)return;
   tracks.add(new AudioOption(stream,language,codec));
  }
 }
 private void releaseAudioLocked(){
  if(audioOutput!=null){
   try{audioOutput.pause();audioOutput.flush();audioOutput.release();}
   catch(Throwable ignored){}
   audioOutput=null;
  }
 }
 private void releaseAudio(){synchronized(audioLock){releaseAudioLocked();}}
 @SuppressWarnings("unused")
 private void onNativeAudioReset(){releaseAudio();}
 @SuppressWarnings("unused")
 private void onNativePcm(byte[] pcm,int length){
  if(closed||!audioEnabled||pcm==null||length<=0||length>pcm.length)return;
  synchronized(audioLock){
   if(closed)return;
   try{
    if(audioOutput==null){
     final int min=AudioTrack.getMinBufferSize(48000,
       AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
     if(min<=0)return;
     AudioTrack fresh=new AudioTrack.Builder()
       .setAudioAttributes(new AudioAttributes.Builder()
         .setUsage(AudioAttributes.USAGE_MEDIA)
         .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
       .setAudioFormat(new AudioFormat.Builder()
         .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
         .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
         .build())
       .setBufferSizeInBytes(Math.max(min*2,48000*4/2))
       .setTransferMode(AudioTrack.MODE_STREAM).build();
     if(fresh.getState()!=AudioTrack.STATE_INITIALIZED){
      fresh.release();return;
     }
     audioOutput=fresh;
     fresh.setVolume(Math.max(0f,Math.min(1f,
       app.getSharedPreferences("smarthub",0).getInt("dvb_volume",35)/100f)));
     fresh.play();
    }
    audioOutput.write(pcm,0,length,AudioTrack.WRITE_BLOCKING);
   }catch(Throwable failure){
    Log.w("IRAQhubEP3","Android PCM output unavailable",failure);
    releaseAudioLocked();
   }
  }
 }
 /** This callback is invoked by native FFmpeg only after an RGBA frame was
  * successfully presented via ANativeWindow_unlockAndPost. */
 @SuppressWarnings("unused")
 private void onNativeFrame(int frameCount){
  if(frameCount!=1||reported)return;
  reported=true;
  main.post(()->{if(!closed)listener.firstFrame();});
 }
 long displayedFrames(){
  synchronized(handleLock){
   return handle==0?0:nativeFrames(handle);
  }
 }
 long droppedFrames(){
  synchronized(handleLock){
   return handle==0?0:nativeDropped(handle);
  }
 }
 @Override public void surfaceCreated(SurfaceHolder holder){startIfReady();}
 @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){startIfReady();}
 @Override public void surfaceDestroyed(SurfaceHolder holder){stop();}
 void stop(){
  synchronized(handleLock){
   if(handle!=0)nativeStop(handle);
  }
 }
 @Override public void close(){
  if(closed)return;
  closed=true;
  stop();
  releaseAudio();
  view.getHolder().removeCallback(this);
  decodeThread.shutdown();
  android.view.ViewParent parent=view.getParent();
  if(parent instanceof FrameLayout)((FrameLayout)parent).removeView(view);
 }
}
