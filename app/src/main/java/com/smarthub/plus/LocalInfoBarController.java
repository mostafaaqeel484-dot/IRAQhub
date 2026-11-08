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
 private String logoKey="",imageKey="",lastPrefetchEvent="";
 private Bitmap channelLogo,providerLogo,currentPoster,nextPoster;
 private long logoRetry,posterRetryAt,lastPaintAt;
 private String lastPaintState="";
 private Bitmap paintedChannel,paintedProvider,paintedNow,paintedNext;
 private volatile boolean closed;
 private boolean visible,expanded;
 private int seq;
 private SatelliteState latest=new SatelliteState();
 private final Runnable hide=new Runnable(){
  @Override public void run(){
   SatelliteState state=SatelliteState.read(host);
   ReceiverSignalReading signal=DirectReceiverSignal.reading(state);
   boolean noSignal=state.channelId.startsWith("DVB:")&&signal!=null&&
     signal.locked==0&&!prefs.getBoolean("dvb_tuning",false)&&
     state.channelId.equals("DVB:"+prefs.getString("own_dvb_physical",""));
   if(!closed&&noSignal){
    visible=true;bar.setVisibility(View.VISIBLE);main.postDelayed(this,1300);
    return;
   }
   visible=false;bar.setVisibility(View.GONE);
   releaseHiddenArtwork();
   prefs.edit().putBoolean("now_next_visible",false).apply();
  }
 };
 private final Runnable tick=new Runnable(){
  @Override public void run(){
   if(closed)return;
   if(visible){
    SatelliteState observed=SatelliteState.read(host);
    SatelliteState enriched=ReceiverInfoRepository.get(host).enrich(observed);
    refresh(enriched);
    main.postDelayed(this,PlaybackResourceMode.lite(host)?4200:1450);
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
  // 4.0.2: anchor the OSD to the physical bottom edge (no floating gap).
  // Keep legacy offset only when explicitly requested by the user.
  params.bottomMargin=prefs.getBoolean("osd_dock_to_bottom",true)?0:OsdGeometry.bottom(activity);
  root.addView(bar,params);
 }

 void show(){
  if(closed)return;
  visible=true;
  lastPaintState="";lastPaintAt=0;
  expanded=false;bar.setExpanded(false);
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

 /** First INFO opens Tiger ribbon; repeated INFO expands current/next posters. */
 void showDetails(){
  if(closed)return;
  if(!visible){show();return;}
  expanded=!expanded;bar.setExpanded(expanded);
  lastPaintState="";
  main.removeCallbacks(hide);
  main.postDelayed(hide,Math.max(1,Math.min(10,prefs.getInt("osd_duration_seconds",8)))*1000L);
  paint();
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
   // Never decode CHMAX/PNG/SVG synchronously on INFO or channel zap.
   // Keep cached logos available even during software 4:2:2 focus; all
   // disk/receiver work stays on LogoRepository's bounded IO worker.
   channelLogo=null;providerLogo=null;logoRetry=0;
   if(!state.channelName.isEmpty()){
    final String expected=logos;
    LogoRepository repository=LogoRepository.get(host);
    LogoRepository.Listener images=new LogoRepository.Listener(){
     @Override public void channel(Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       if(image!=null)channelLogo=image;
       paint();
      }});
     }
     @Override public void provider(String name,Bitmap image){
      main.post(()->{if(!closed&&expected.equals(logoKey)){
       if(image!=null)providerLogo=image;
       paint();
      }});
     }
    };
    if(PlaybackResourceMode.active()||
       prefs.getBoolean("software_dvb_422_active",false))
      repository.requestCached(state.channelName,state.channelId,images);
    else repository.request(state.channelName,state.channelId,images);
   }
  }
  String art=id+"|"+state.title+"|"+state.startMs+"|"+state.nextTitle+"|"+
    state.nextStartMs+"|"+state.image+"|"+state.nextImage+"|"+
    prefs.getLong("epg_art_changed_at",0);
  boolean unresolved=(!state.title.isEmpty()&&currentPoster==null)||
    (!state.nextTitle.isEmpty()&&nextPoster==null);
  if(!prefs.getBoolean("osd_posters",true)){
   // Explicitly disabled by viewer; keep disk cache for later use.
   imageKey=art;currentPoster=null;nextPoster=null;
  }else if(!art.equals(imageKey)||unresolved&&System.currentTimeMillis()>posterRetryAt){
   imageKey=art;posterRetryAt=System.currentTimeMillis()+45000;
   currentPoster=null;nextPoster=null;
   final String expected=art;
   final SatelliteState snapshot=state.copy();
   // Two independent pipelines: a slow TMDB movie lookup must not delay NEXT.
   // The artwork cache's update timestamp can change repeatedly; only schedule
   // once per *broadcast event*, not once per cache invalidation or UI repaint.
   String broadcastKey=id+"|"+snapshot.title+"|"+snapshot.startMs+"|"+
     snapshot.nextTitle+"|"+snapshot.nextStartMs;
   if(!broadcastKey.equals(lastPrefetchEvent)){
    lastPrefetchEvent=broadcastKey;
    if(!prefs.getBoolean("software_dvb_422_active",false)&&
       !prefs.getBoolean("dvb_tuning",false)&&snapshot.startMs>0)
     IraqHubSyncScheduler.programmeChanged(host);
   }
   io.execute(()->{
    try{if(!prefs.getBoolean("software_dvb_422_active",false))
      published.refresh(snapshot,System.currentTimeMillis());}
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
  String title=upcoming?snapshot.nextTitle:snapshot.title;
  boolean enabled=prefs.getBoolean("osd_posters",true);
  if(!PosterLoadPolicy.cacheAllowed(enabled,title))return;
  boolean focus=PlaybackResourceMode.active();
  boolean sw422=prefs.getBoolean("software_dvb_422_active",false);
  boolean tuning=prefs.getBoolean("dvb_tuning",false);
  if(focus||sw422){
   // Real-time 4:2:2: render only images already verified and on disk.
   // Never run TMDB/CHMAX/TVmaze HTTP or heavy full-res image allocation here.
   try{
    Bitmap cached=EpgArtwork.loadInfobarCached(host,snapshot,title,upcoming);
    if(cached!=null)publishPoster(expected,upcoming,compactPoster(cached));
    prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
      cached==null?"VIDEO_FOCUS_CACHE_MISS":"VIDEO_FOCUS_CACHED_IMAGE").apply();
   }catch(Exception problem){
    prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
      "VIDEO_FOCUS_CACHE_"+problem.getClass().getSimpleName()).apply();
   }
   return;
  }
  Bitmap image=poster(snapshot,upcoming);
  if(image!=null)publishPoster(expected,upcoming,compactPoster(image));
  if(image==null&&PosterLoadPolicy.networkAllowed(enabled,focus,sw422,tuning,title)){
   image=onlinePoster(snapshot,upcoming);
   if(image!=null)publishPoster(expected,upcoming,compactPoster(image));
  }
 }
 private void publishPoster(String expected,boolean upcoming,Bitmap image){
  main.post(()->{
   if(closed||!expected.equals(imageKey))return;
   if(!visible)return;
   if(upcoming)nextPoster=image;else currentPoster=image;
   paint();
  });
 }
 private Bitmap poster(SatelliteState state,boolean upcoming){
  String title=upcoming?state.nextTitle:state.title;
  if(title==null||title.trim().isEmpty())return null;
  try{
   Bitmap image=EpgArtwork.loadInfobar(host,state,title,upcoming);
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     image==null?"NO_VERIFIED_EPG_ART":"EPG_POSTER_OK").apply();
   return compactPoster(image);
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

 private static Bitmap compactPoster(Bitmap image){
  if(image==null||image.getWidth()<=512&&image.getHeight()<=512)return image;
  float ratio=512f/Math.max(image.getWidth(),image.getHeight());
  return Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*ratio)),
    Math.max(1,Math.round(image.getHeight()*ratio)),true);
 }
 private void paint(){
  if(closed||bar.getVisibility()!=View.VISIBLE)return;
  long now=android.os.SystemClock.elapsedRealtime();
  String state=latest.channelId+"|"+latest.channelName+"|"+latest.title+"|"+
    latest.startMs+"|"+latest.endMs+"|"+latest.nextTitle+"|"+
    latest.nextStartMs+"|"+latest.nextEndMs+"|"+expanded;
  boolean changed=!state.equals(lastPaintState)||paintedChannel!=channelLogo||
     paintedProvider!=providerLogo||paintedNow!=currentPoster||paintedNext!=nextPoster;
  // Always repaint immediately on event/artwork/INFO changes. Throttle only
  // unchanged live status while the CPU is busy with software 4:2:2.
  if(PlaybackResourceMode.lite(host)&&!changed&&now-lastPaintAt<4000)return;
  lastPaintState=state;lastPaintAt=now;
  paintedChannel=channelLogo;paintedProvider=providerLogo;
  paintedNow=currentPoster;paintedNext=nextPoster;
  bar.bind(latest,currentPoster,nextPoster,channelLogo,"");
  bar.provider(providerLogo);
 }
 private void releaseHiddenArtwork(){
  // Free references held by both controller and ImageViews on dismissal.
  channelLogo=null;providerLogo=null;currentPoster=null;nextPoster=null;
  paintedChannel=null;paintedProvider=null;paintedNow=null;paintedNext=null;
  logoKey="";imageKey="";lastPaintState="";lastPaintAt=0;
  posterRetryAt=0;logoRetry=0;
  bar.clearArtwork();
 }

 void dismiss(){main.removeCallbacks(hide);main.removeCallbacks(tick);hide.run();}

 @Override public void close(){
  if(closed)return;
  closed=true;visible=false;
  main.removeCallbacks(hide);main.removeCallbacks(tick);
  chmax.close();io.shutdownNow();
  prefs.edit().putBoolean("now_next_visible",false).apply();
  releaseHiddenArtwork();
  root.removeView(bar);
 }
}
