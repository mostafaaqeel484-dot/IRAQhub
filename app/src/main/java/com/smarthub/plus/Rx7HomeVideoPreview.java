package com.smarthub.plus;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

/** Muted, focus-only Home preview for an explicit HTTP(S) stream bound by
 * the user to the current channel. Does NOT create a DVB tuner session.
 * Keeps the artwork visible until the first decoded frame; errors fail back
 * to the artwork instead of showing a black video rectangle. */
final class Rx7HomeVideoPreview implements TextureView.SurfaceTextureListener {
 interface Listener {void firstFrame(boolean visible);}
 private final Activity host;
 private final TextureView texture;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Listener listener;
 private MediaPlayer player;
 private String channelId="";
 private boolean focus,foreground,destroyed;
 private boolean visible;
 private int generation;
 private final Runnable deferredStart=this::tryStart;

 Rx7HomeVideoPreview(Activity context,FrameLayout tile,Listener callback){
  host=context;listener=callback;
  texture=new TextureView(context);
  texture.setOpaque(false);
  texture.setAlpha(0f);
  texture.setFocusable(false);
  texture.setClickable(false);
  tile.addView(texture,new FrameLayout.LayoutParams(-1,-1));
  texture.setSurfaceTextureListener(this);
 }
 void bind(String id){
  String next=id==null?"":id;
  if(!next.equals(channelId)){
   stop();
   channelId=next;
  }
  schedule();
 }
 void focus(boolean focused){
  if(focus==focused)return;
  focus=focused;
  if(!focus)stop();else schedule();
 }
 void foreground(boolean active){
  if(foreground==active)return;
  foreground=active;
  if(!foreground)stop();else schedule();
 }
 private void schedule(){
  main.removeCallbacks(deferredStart);
  if(!destroyed&&focus&&foreground)
   main.postDelayed(deferredStart,550);
 }
 private void tryStart(){
  if(destroyed||!texture.isAvailable())return;
  SharedPreferences p=host.getSharedPreferences("smarthub",0);
  String url=p.getString(Rx7PreviewPolicy.STREAM,"");
  String expected=p.getString(Rx7PreviewPolicy.BOUND,"");
  if(!Rx7PreviewPolicy.allowed(p.getBoolean(Rx7PreviewPolicy.ENABLED,false),
      focus,foreground,PlaybackResourceMode.active(),expected,channelId,url)){
   stop();return;
  }
  if(player!=null)return;
  final int token=++generation;
  try{
   MediaPlayer m=new MediaPlayer();
   player=m;
   m.setAudioAttributes(new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
   m.setVolume(0f,0f);
   Surface surface=new Surface(texture.getSurfaceTexture());
   try{m.setSurface(surface);}finally{surface.release();}
   m.setOnPreparedListener(prepared->{
    if(token!=generation||destroyed||player!=prepared)return;
    try{prepared.setVolume(0f,0f);prepared.start();}
    catch(RuntimeException error){stop();}
   });
   m.setOnInfoListener((source,what,extra)->{
    if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&
       token==generation&&player==source&&!destroyed){
     visible=true;texture.setAlpha(1f);listener.firstFrame(true);
    }
    return false;
   });
   m.setOnErrorListener((source,what,extra)->{
    if(token==generation)stop();
    return true;
   });
   m.setOnCompletionListener(source->{if(token==generation)stop();});
   m.setDataSource(host,Uri.parse(url));
   m.prepareAsync();
   main.postDelayed(()->{if(!destroyed&&token==generation&&!visible)stop();},9000);
  }catch(Exception error){stop();}
 }
 void stop(){
  generation++;
  main.removeCallbacks(deferredStart);
  if(visible){visible=false;listener.firstFrame(false);}
  texture.setAlpha(0f);
  MediaPlayer old=player;
  player=null;
  if(old!=null)try{old.reset();old.release();}catch(RuntimeException ignore){}
 }
 void destroy(){
  destroyed=true;stop();main.removeCallbacksAndMessages(null);
  texture.setSurfaceTextureListener(null);
 }
 @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height){schedule();}
 @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height){}
 @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){stop();return true;}
 @Override public void onSurfaceTextureUpdated(SurfaceTexture surface){}
}
