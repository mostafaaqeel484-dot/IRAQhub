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
 private NowNextView bar;
 private final Handler main=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final FactoryProvider chmax;
 private final PublishedEpgArtwork published;
 private String logoKey="",imageKey="";
 private Bitmap channelLogo,providerLogo,currentPoster,nextPoster;
 private long logoRetry,posterRetryAt;
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
  visible=true;
  bar.setVisibility(View.VISIBLE);
  latest=ReceiverInfoRepository.get(host).enrich(SatelliteState.read(host));
  refresh(latest);
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
    prefs.getLong("providers_changed_at",0)+"|"+prefs.getLong("osd_changed_at",0);
  if(!logos.equals(logoKey)){
   logoKey=logos;
   channelLogo=CHMAXIntegration.logo(host,state,false);providerLogo=CHMAXIntegration.logo(host,state,true);
   logoRetry=0;
   if(!state.channelName.isEmpty()){
    final String expected=logos;
    LogoRepository repository=LogoRepository.get(host);
    LogoRepository.Listener images=new LogoRepository.Listener(){
     @Override public void channel(Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       if(image!=null)channelLogo=image;paint();
      }});
     }
     @Override public void provider(String name,Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       if(image!=null)providerLogo=image;paint();
      }});
     }
    };
    if(prefs.getBoolean("software_dvb_422_active",false))
      repository.requestCached(state.channelName,state.channelId,images);
    else repository.request(state.channelName,state.channelId,images);
   }
  }
  String art=id+"|"+state.title+"|"+state.startMs+"|"+state.nextTitle+"|"+
    state.nextStartMs+"|"+state.image+"|"+state.nextImage+"|"+
    prefs.getLong("epg_art_changed_at",0);
  boolean unresolved=(!state.title.isEmpty()&&currentPoster==null)||
    (!state.nextTitle.isEmpty()&&nextPoster==null);
  if(!art.equals(imageKey)||unresolved&&System.currentTimeMillis()>posterRetryAt){
   imageKey=art;posterRetryAt=System.currentTimeMillis()+45000;
   currentPoster=null;nextPoster=null;
   final String expected=art;
   final SatelliteState snapshot=state.copy();
   // Two independent pipelines: a slow TMDB movie lookup must not delay NEXT.
   io.execute(()->{
    try{published.refresh(snapshot,System.currentTimeMillis());}
    catch(Exception ignored){}
    loadPoster(snapshot,false,expected);
   });
   io.execute(()->loadPoster(snapshot,true,expected));
  }
  if(System.currentTimeMillis()>=logoRetry&&!state.channelName.isEmpty()){
   logoRetry=System.currentTimeMillis()+60000;
   final String expected=logoKey;
   final SatelliteState snap=state.copy();
   io.execute(()->{
    if(prefs.getBoolean("software_dvb_422_active",false))return;
    chmax.enrich(snap);
    if(!prefs.getBoolean("osd_chmax_logos",true)||!prefs.getBoolean("factory_enabled",true))return;
    Bitmap logo=prefs.getBoolean("software_dvb_422_active",false)?null:FactoryProvider.logo(host,snap.channelName,snap.channelId);
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

 private void loadPoster(SatelliteState snapshot,boolean upcoming,String expected){
  if(prefs.getBoolean("software_dvb_422_active",false)){
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
      "VIDEO_PRIORITY_POSTERS_SUSPENDED").apply();
   return;
  }
  String title=upcoming?snapshot.nextTitle:snapshot.title;
  if(title==null||title.trim().isEmpty())return;
  Bitmap image=poster(snapshot,upcoming);
  if(image!=null)publishPoster(expected,upcoming,image);
  if(image==null&&!prefs.getBoolean("software_dvb_422_active",false)&&
     !prefs.getBoolean("dvb_tuning",false)){
    image=onlinePoster(snapshot,upcoming);
    if(image!=null)publishPoster(expected,upcoming,image);
  }
 }
 private void publishPoster(String expected,boolean upcoming,Bitmap image){
  main.post(()->{
   if(closed||!expected.equals(imageKey))return;
   if(upcoming)nextPoster=image;else currentPoster=image;
   paint();
  });
 }
 private Bitmap poster(SatelliteState state,boolean upcoming){
  String title=upcoming?state.nextTitle:state.title;
  if(title==null||title.trim().isEmpty())return null;
  try{
   Bitmap image=EpgArtwork.load(host,state,title,upcoming);
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     image==null?"NO_VERIFIED_EPG_ART":"EPG_POSTER_OK").apply();
   return image;
  }catch(Exception failed){
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     "EPG_"+failed.getClass().getSimpleName()).apply();
   return null;
  }
 }
 private Bitmap onlinePoster(SatelliteState state,boolean upcoming){
  String title=upcoming?state.nextTitle:state.title;
  if(title==null||title.trim().length()<3)return null;
  try{
   OnlineProgramme programme=OnlineProgramme.fetch(host,state,title,upcoming);
   Bitmap image=programme==null?null:programme.poster;
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
      image==null?"NO_VERIFIED_TMDB_BETTER_TVMAZE_MATCH":"ONLINE_POSTER_OK").apply();
   return image;
  }catch(Exception error){
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     "ONLINE_"+error.getClass().getSimpleName()).apply();
   return null;
  }
 }

 private void paint(){
  if(closed||bar.getVisibility()!=View.VISIBLE)return;
  bar.bind(latest,currentPoster,nextPoster,channelLogo,"");
  bar.provider(providerLogo);
 }

 void dismiss(){main.removeCallbacks(hide);main.removeCallbacks(tick);hide.run();}

 @Override public void close(){
  if(closed)return;
  closed=true;visible=false;
  main.removeCallbacks(hide);main.removeCallbacks(tick);
  chmax.close();io.shutdownNow();
  prefs.edit().putBoolean("now_next_visible",false).apply();
  root.removeView(bar);
 }
}
