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
import android.widget.TextView;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Activity-owned infobar. The same NowNextView used by the original companion,
 * now inside IRAQhub playback; no SYSTEM_ALERT_WINDOW permission or OEM Home.
 */
final class LocalInfoBarController implements AutoCloseable {
 private final Activity host;
 private final FrameLayout root;
 private NowNextView bar;
 private final TextView signalNotice;
 private final Handler main=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final ExecutorService sportsIo=Executors.newSingleThreadExecutor();
 private Future<?> sportsWork;
 private final FactoryProvider chmax;
 private final PublishedEpgArtwork published;
 private String logoKey="",imageKey="",lastPrefetchEvent="";
 private String logoService="";
 private long lastFullTick;
 private String observedRevisions="";
 private Bitmap channelLogo,providerLogo,currentPoster,nextPoster;
 private String currentSynopsis="",nextSynopsis="";
 private String paintedCurrentSynopsis="",paintedNextSynopsis="";
 private long logoRetry,posterRetryAt,lastPaintAt;
 private String lastPaintState="";
 private Bitmap paintedChannel,paintedProvider,paintedNow,paintedNext;
 private volatile boolean closed;
 private boolean visible,expanded;
 private int seq;
 private SatelliteState latest=new SatelliteState();
 private final Runnable hide=()->{hideBar();refreshSignalNotice();};
 private final Runnable checkSignal=this::refreshSignalNotice;
 private final Runnable tick=new Runnable(){
  @Override public void run(){
   if(closed)return;
   if(visible){
    long now=android.os.SystemClock.elapsedRealtime();
    String revisions=prefs.getLong("native_logos_changed_at",0)+"|"+
      prefs.getLong("logo_mapping_changed_at",0)+"|"+
      prefs.getLong("providers_changed_at",0);
    boolean changed=!revisions.equals(observedRevisions);
    observedRevisions=revisions;
    // Every second inspect only local OEM/CHMAX revision. Avoid heavy
    // EPG/SQLite requests at 1 Hz while software 4:2:2 needs the CPU.
    boolean lite=PlaybackResourceMode.lite(host);
    if(changed||now-lastFullTick>=(lite?4200:1500)){
     SatelliteState observed=SatelliteState.read(host);
     SatelliteState enriched=ReceiverInfoRepository.get(host).enrich(observed);
     refresh(enriched);
     lastFullTick=now;
    }
    main.postDelayed(this,500);
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

  // A confirmed lock loss survives the InfoBar timer without keeping its
  // posters, programme rows or tuner meters on screen.
  signalNotice=new TextView(activity);
  signalNotice.setText(prefs.getBoolean("arabic",true)?"لا توجد إشارة":"No signal");
  signalNotice.setTextColor(0xffffb1b1);
  signalNotice.setBackgroundColor(0xe8000000);
  float density=activity.getResources().getDisplayMetrics().density;
  int horizontal=Math.round(16*density),vertical=Math.round(6*density);
  signalNotice.setPadding(horizontal,vertical,horizontal,vertical);
  FrameLayout.LayoutParams noticePosition=new FrameLayout.LayoutParams(-2,-2,
    Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
  noticePosition.bottomMargin=vertical;
  signalNotice.setVisibility(View.GONE);
  root.addView(signalNotice,noticePosition);
 }

 void show(){
  if(closed)return;
  main.removeCallbacks(checkSignal);
  signalNotice.setVisibility(View.GONE);
  visible=true;
  lastPaintState="";lastPaintAt=0;
  expanded=false;bar.setExpanded(false);
  bar.setVisibility(View.VISIBLE);
  latest=ReceiverInfoRepository.get(host).enrich(SatelliteState.read(host));
  refresh(latest);
  // The immediate read uses warmed memory; a secondary short refresh lets
  // a newly tuned service display EPG ALL without blocking INFO rendering.
  main.postDelayed(()->{if(!closed&&visible){
   SatelliteState fresh=ReceiverInfoRepository.get(host).enrich(SatelliteState.read(host));
   refresh(fresh);
  }},80);
  prefs.edit().putBoolean("now_next_visible",true).apply();
  restartHideTimer();
  main.removeCallbacks(tick);
  main.postDelayed(tick,600);
 }

 /** First INFO opens Tiger ribbon; repeated INFO expands current/next posters. */
 void showDetails(){
  if(closed)return;
  if(!visible){show();return;}
  expanded=!expanded;bar.setExpanded(expanded);
  lastPaintState="";
  restartHideTimer();
  paint();
 }

 private void restartHideTimer(){
  main.removeCallbacks(hide);
  main.postDelayed(hide,InfoBarDuration.millis(prefs.getInt(
    InfoBarDuration.KEY,InfoBarDuration.DEFAULT_SECONDS)));
 }

 private void refreshSignalNotice(){
  if(closed||visible)return;
  SatelliteState state=SatelliteState.read(host);
  ReceiverSignalReading signal=DirectReceiverSignal.reading(state);
  boolean noSignal=state.channelId.startsWith("DVB:")&&signal!=null&&signal.locked==0&&
    !prefs.getBoolean("dvb_tuning",false)&&
    state.channelId.equals("DVB:"+prefs.getString("own_dvb_physical",""));
  signalNotice.setVisibility(noSignal?View.VISIBLE:View.GONE);
  if(noSignal)main.postDelayed(checkSignal,1300);
 }

 private void hideBar(){
  visible=false;
  if(sportsWork!=null)sportsWork.cancel(true);
  bar.setVisibility(View.GONE);
  main.removeCallbacks(tick);
  releaseHiddenArtwork();
  prefs.edit().putBoolean("now_next_visible",false).apply();
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
   // A cache/mapping revision is not a channel change. Keep verified
   // channel/provider art on screen while a replacement loads.
   if(!id.equals(logoService)){
    channelLogo=null;providerLogo=null;
    logoService=id;
    // Queue factory CHMAX independently of the manual logo screen.
    FactoryProvider.requestAutomaticLogo(host,state);
   }
   logoRetry=0;
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
  String broadcastKey=id+"|"+state.title+"|"+state.startMs+"|"+
   state.nextTitle+"|"+state.nextStartMs;
  boolean eventChanged=!broadcastKey.equals(lastPrefetchEvent);
  boolean unresolved=(!state.title.isEmpty()&&currentPoster==null)||
    (!state.nextTitle.isEmpty()&&nextPoster==null);
  if(!prefs.getBoolean("osd_posters",true)){
   imageKey=art;currentPoster=null;nextPoster=null;
   currentSynopsis="";nextSynopsis="";
  }else if(!art.equals(imageKey)||unresolved&&System.currentTimeMillis()>posterRetryAt){
   imageKey=art;posterRetryAt=System.currentTimeMillis()+45000;
   // A cache timestamp update does not change the NOW/NEXT event.
   // Keep the displayed poster and synopsis until a new image arrives.
   if(eventChanged){
    currentPoster=null;nextPoster=null;
    currentSynopsis="";nextSynopsis="";
   }
   final String expected=art;
   final SatelliteState snapshot=state.copy();
   if(eventChanged){
    lastPrefetchEvent=broadcastKey;
    if(!prefs.getBoolean("software_dvb_422_active",false)&&
       !prefs.getBoolean("dvb_tuning",false)&&snapshot.startMs>0)
     IraqHubSyncScheduler.programmeChanged(host);
    // The 4-hour general worker was too late for the CURRENT match.
    // Search only the selected club immediately on a dedicated bounded IO
    // thread, independent of movie/metadata lookups; never on the UI thread.
    if(sportsWork!=null)sportsWork.cancel(true);
    if(SportsLatestTeamArtwork.fixtureTeams(snapshot.title).length==2||
       SportsLatestTeamArtwork.fixtureTeams(snapshot.nextTitle).length==2){
     sportsWork=sportsIo.submit(()->{
      boolean updated=false;
      if(!Thread.currentThread().isInterrupted()&&
         SportsLatestTeamArtwork.fixtureTeams(snapshot.title).length==2)
       updated=SportsLatestTeamArtwork.prefetch(host,snapshot,snapshot.title,false);
      if(!Thread.currentThread().isInterrupted()&&
         SportsLatestTeamArtwork.fixtureTeams(snapshot.nextTitle).length==2)
       updated=SportsLatestTeamArtwork.prefetch(host,snapshot,snapshot.nextTitle,true)||updated;
      if(updated&&!Thread.currentThread().isInterrupted())
       main.post(()->{if(!closed&&visible&&broadcastKey.equals(lastPrefetchEvent))
        refresh(ReceiverInfoRepository.get(host).enrich(SatelliteState.read(host)));
       });
     });
    }
   }
   // Show NOW and NEXT independently before the slower published-EPG scan.
   // A later provider cache update is handled by the next tick.
   io.execute(()->loadPoster(snapshot,false,expected));
   io.execute(()->loadPoster(snapshot,true,expected));
   if(!prefs.getBoolean("software_dvb_422_active",false))
    io.execute(()->{
     try{published.refresh(snapshot,System.currentTimeMillis());}
     catch(Exception ignored){}
    });
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
  String local=ProgrammeSynopsisSource.local(host,snapshot,upcoming);
  if(!local.isEmpty())publishSynopsis(expected,upcoming,local);
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
  if(image==null&&SportsLatestTeamArtwork.fixtureTeams(title).length==2)return;
  if(image==null&&PosterLoadPolicy.networkAllowed(enabled,focus,sw422,tuning,title)){
   image=onlinePoster(snapshot,upcoming,expected);
   if(image!=null)publishPoster(expected,upcoming,compactPoster(image));
  }
 }
 private void publishSynopsis(String expected,boolean upcoming,String description){
  final String shortText=ProgrammeSynopsisText.clean(description,160);
  if(shortText.isEmpty())return;
  main.post(()->{
   if(closed||!visible||!expected.equals(imageKey))return;
   if(upcoming)nextSynopsis=shortText;else currentSynopsis=shortText;
   paint();
  });
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
   Bitmap image=EpgArtwork.loadInfobarCached(host,state,title,upcoming);
   if(image==null)image=EpgArtwork.loadInfobarBroadcast(host,state,title,upcoming);
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     image==null?"NO_VERIFIED_EPG_ART":"EPG_POSTER_OK").apply();
   return compactPoster(image);
  }catch(Exception failed){
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
     "EPG_"+failed.getClass().getSimpleName()).apply();
   return null;
  }
 }
 private Bitmap onlinePoster(SatelliteState state,boolean upcoming,String expected){
  String title=upcoming?state.nextTitle:state.title;
  if(title==null||title.trim().length()<3)return null;
  try{
   OnlineProgramme programme=OnlineProgramme.fetch(host,state,title,upcoming);
   if(programme!=null&&programme.item!=null&&
       programme.item.summary!=null&&!programme.item.summary.trim().isEmpty()){
    // Metadata belongs to this event only; never leak a prior-channel synopsis.
    publishSynopsis(expected,upcoming,programme.item.summary);
   }
   Bitmap image=programme==null?null:programme.poster;
   String credit=programme!=null&&programme.item!=null&&programme.item.imageCredit!=null
     ?programme.item.imageCredit:"NONE";
   String label=credit.replaceAll("[^A-Za-z0-9]","_");
   if(label.length()>48)label=label.substring(0,48);
   prefs.edit().putString(upcoming?"osd_next_poster_status":"osd_now_poster_status",
      image==null?"NO_VERIFIED_METADATA_POSTER":"ONLINE_POSTER_"+label).apply();
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
     paintedProvider!=providerLogo||paintedNow!=currentPoster||paintedNext!=nextPoster||
     !paintedCurrentSynopsis.equals(currentSynopsis)||
     !paintedNextSynopsis.equals(nextSynopsis);
  // Always repaint immediately on event/artwork/INFO changes. Throttle only
  // unchanged live status while the CPU is busy with software 4:2:2.
  if(PlaybackResourceMode.lite(host)&&!changed&&now-lastPaintAt<4000)return;
  lastPaintState=state;lastPaintAt=now;
  paintedChannel=channelLogo;paintedProvider=providerLogo;
  paintedNow=currentPoster;paintedNext=nextPoster;
  paintedCurrentSynopsis=currentSynopsis;paintedNextSynopsis=nextSynopsis;
  bar.bind(latest,currentPoster,nextPoster,channelLogo,"");
  bar.synopses(currentSynopsis,nextSynopsis);
  bar.provider(providerLogo);
 }
 private void releaseHiddenArtwork(){
  // Free references held by both controller and ImageViews on dismissal.
  channelLogo=null;providerLogo=null;currentPoster=null;nextPoster=null;
  currentSynopsis="";nextSynopsis="";
  paintedCurrentSynopsis="";paintedNextSynopsis="";
  paintedChannel=null;paintedProvider=null;paintedNow=null;paintedNext=null;
  logoKey="";logoService="";observedRevisions="";imageKey="";lastPaintState="";lastPaintAt=0;
  posterRetryAt=0;logoRetry=0;
  bar.clearArtwork();
 }

 boolean isVisible(){return visible;}

 void dismiss(){
  main.removeCallbacks(hide);
  main.removeCallbacks(checkSignal);
  signalNotice.setVisibility(View.GONE);
  hideBar();
 }

 @Override public void close(){
  if(closed)return;
  closed=true;visible=false;
  main.removeCallbacks(hide);main.removeCallbacks(tick);main.removeCallbacks(checkSignal);
  chmax.close();if(sportsWork!=null)sportsWork.cancel(true);
  sportsIo.shutdownNow();io.shutdownNow();
  prefs.edit().putBoolean("now_next_visible",false).apply();
  releaseHiddenArtwork();
  root.removeView(bar);
  root.removeView(signalNotice);
 }
}
