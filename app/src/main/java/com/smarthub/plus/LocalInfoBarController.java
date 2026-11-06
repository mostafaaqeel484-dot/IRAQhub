package com.smarthub.plus;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity-owned infobar. The same NowNextView used by the original companion,
 * now inside IRAQhub playback; no SYSTEM_ALERT_WINDOW permission or OEM Home.
 */
final class LocalInfoBarController implements AutoCloseable {
 private final Activity host;
 private final FrameLayout root;
 private final NowNextView bar;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final SharedPreferences prefs;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final FactoryProvider chmax;
 private final PublishedEpgArtwork published;
 private String logoKey="",imageKey="";
 private Bitmap channelLogo,providerLogo,currentPoster,nextPoster;
 private long logoRetry;
 private volatile boolean closed;
 private boolean visible;
 private int seq;
 private SatelliteState latest=new SatelliteState();
 private final Runnable hide=()->{visible=false;bar.setVisibility(View.GONE);
   prefs.edit().putBoolean("now_next_visible",false).apply();};
 private final Runnable tick=new Runnable(){
  @Override public void run(){
   if(closed)return;
   if(visible){
    SatelliteState observed=SatelliteState.read(host);
    SatelliteState enriched=ReceiverInfoRepository.get(host).enrich(observed);
    refresh(enriched);
    main.postDelayed(this,1450);
   }
  }
 };

 LocalInfoBarController(Activity activity,FrameLayout frame){
  host=activity;root=frame;
  prefs=activity.getSharedPreferences("smarthub",Context.MODE_PRIVATE);
  chmax=new FactoryProvider(activity);
  published=new PublishedEpgArtwork(activity);
  bar=new NowNextView(activity);
  bar.setVisibility(View.GONE);
  FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
     FrameLayout.LayoutParams.MATCH_PARENT,OsdGeometry.window(activity),Gravity.BOTTOM);
  params.bottomMargin=OsdGeometry.bottom(activity);
  root.addView(bar,params);
 }

 void show(){
  if(closed)return;
  latest=ReceiverInfoRepository.get(host).enrich(SatelliteState.read(host));
  refresh(latest);
  visible=true;
  bar.setVisibility(View.VISIBLE);
  prefs.edit().putBoolean("now_next_visible",true).apply();
  main.removeCallbacks(hide);
  int seconds=Math.max(1,Math.min(10,prefs.getInt("osd_duration_seconds",8)));
  main.postDelayed(hide,seconds*1000L);
  main.removeCallbacks(tick);
  main.postDelayed(tick,600);
 }

 private void refresh(SatelliteState state){
  if(closed||!visible&&bar.getVisibility()!=View.VISIBLE)return;
  latest=state;
  String id=ReceiverInfoRepository.channelKey(state);
  String logos=id+"|"+prefs.getLong("logo_mapping_changed_at",0)+"|"+
    prefs.getLong("native_logos_changed_at",0)+"|"+
    prefs.getLong("providers_changed_at",0);
  if(!logos.equals(logoKey)){
   logoKey=logos;
   channelLogo=null;providerLogo=null;
   logoRetry=0;
   if(!state.channelName.isEmpty()){
    final String expected=logos;
    LogoRepository.get(host).request(state.channelName,new LogoRepository.Listener(){
     @Override public void channel(Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       channelLogo=image;paint();
      }});
     }
     @Override public void provider(String name,Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       providerLogo=image;paint();
      }});
     }
    });
   }
  }
  String art=id+"|"+state.title+"|"+state.startMs+"|"+state.nextTitle+"|"+
    state.nextStartMs+"|"+state.image+"|"+state.nextImage+"|"+
    prefs.getLong("epg_art_changed_at",0);
  if(!art.equals(imageKey)){
   imageKey=art;
   currentPoster=null;nextPoster=null;
   final String check=art;
   final SatelliteState snap=state.copy();
   io.execute(()->{
    try{
     published.refresh(snap,System.currentTimeMillis());
     Bitmap now=poster(snap,snap.title,snap.image);
     Bitmap next=poster(snap,snap.nextTitle,snap.nextImage);
     main.post(()->{if(!closed&&check.equals(imageKey)){
      currentPoster=now;nextPoster=next;paint();
     }});
    }catch(Exception ignored){}
   });
  }
  if(System.currentTimeMillis()>=logoRetry&&!state.channelName.isEmpty()){
   logoRetry=System.currentTimeMillis()+60000;
   final String expected=logoKey;
   final SatelliteState snap=state.copy();
   io.execute(()->{
    chmax.enrich(snap);
    if(!prefs.getBoolean("osd_chmax_logos",true)||!prefs.getBoolean("factory_enabled",true))return;
    Bitmap logo=FactoryProvider.logo(host,snap.channelName,snap.channelId);
    if(logo==null)logo=CHMAXIntegration.logo(host,snap,false);
    if(logo!=null){
     Bitmap result=logo;
     main.post(()->{if(!closed&&expected.equals(logoKey)){
      channelLogo=result;paint();
     }});
    }
   });
  }
  paint();
 }

 private Bitmap poster(SatelliteState s,String title,String url){
  if(title==null||title.trim().isEmpty())return null;
  String href=url;
  if(href==null||href.isEmpty())href=PublishedEpgArtwork.image(host,s,title);
  // Only verified programme-associated HTTPS artwork; never use name guesses.
  if(href==null||!href.startsWith("https://")||href.length()>2048)return null;
  try{
   byte[] bytes=ArtworkCache.fetch(host,new URL(href),5*1024*1024);
   BitmapFactory.Options probe=new BitmapFactory.Options();
   probe.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,probe);
   if(probe.outWidth<50||probe.outHeight<50||
     probe.outWidth>10000||probe.outHeight>10000)return null;
   probe.inJustDecodeBounds=false;
   probe.inSampleSize=Math.max(1,Math.max(probe.outWidth/480,probe.outHeight/720));
   return BitmapFactory.decodeByteArray(bytes,0,bytes.length,probe);
  }catch(Exception unavailable){return null;}
 }

 private void paint(){
  if(closed||bar.getVisibility()!=View.VISIBLE)return;
  bar.bind(latest,currentPoster,nextPoster,channelLogo,"");
  bar.provider(providerLogo);
 }

 @Override public void close(){
  if(closed)return;
  closed=true;visible=false;
  main.removeCallbacks(hide);main.removeCallbacks(tick);
  chmax.close();io.shutdownNow();
  prefs.edit().putBoolean("now_next_visible",false).apply();
  root.removeView(bar);
 }
}
