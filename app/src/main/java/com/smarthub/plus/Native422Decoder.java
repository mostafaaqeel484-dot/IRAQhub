package com.smarthub.plus;

import android.content.Context;
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
 private static native int nativeRun(long handle,String url,Surface surface,
                                    int width,int height,boolean convert420);
 private static native void nativeStop(long handle);
 private static native void nativeDestroy(long handle);
 private static native long nativeFrames(long handle);
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
 private String url;
 private long handle;
 private boolean started,closed,reported;
 private int resultCode;
 Native422Decoder(Context c,FrameLayout container,Listener listener,String mode){
  this.listener=listener;
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
    resultCode=nativeRun(id,url,surface,target.width,target.height,target.convert420);
   }catch(Throwable error){
    resultCode=-101;
    Log.e("IRAQhubNative422","Native AVCodec bridge failed",error);
   }finally{
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
  view.getHolder().removeCallback(this);
  decodeThread.shutdown();
  android.view.ViewParent parent=view.getParent();
  if(parent instanceof FrameLayout)((FrameLayout)parent).removeView(view);
 }
}
