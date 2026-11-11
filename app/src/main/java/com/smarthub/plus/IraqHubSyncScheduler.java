package com.smarthub.plus;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Three independent, persistent 240-minute refresh loops.
 * XMLTV and BetterPosters require connectivity; native EIT does not.
 * WorkManager keeps schedules across ordinary app/device restarts.
 */
final class IraqHubSyncScheduler {
 private static final String XMLTV="iraqhub-unified-epg-poster-sync"; // retain old unique work identity
 private static final String POSTERS="iraqhub-native-posters-sync";
 private static final String DVB="iraqhub-native-dvb-epg-sync";
 private static final String CHMAX="iraqhub-official-chmax-logo-sync";
 private static final ExecutorService PRIORITY_IO=Executors.newSingleThreadExecutor();
 private IraqHubSyncScheduler(){}
 private static boolean videoPriority(Context context){
  return PlaybackResourceMode.active()||context.getSharedPreferences("smarthub",0)
    .getBoolean("software_dvb_422_active",false);
 }
 static String[] optionalNames(){
  return new String[]{XMLTV,POSTERS,DVB,CHMAX,
   XMLTV+"-initial",POSTERS+"-initial",DVB+"-initial",CHMAX+"-initial",
   XMLTV+"-manual",POSTERS+"-manual",DVB+"-manual",CHMAX+"-manual",
   POSTERS+"-programme"};
 }
 /** Cancel only IRAQhub-owned optional WorkManager jobs. Never kill the OEM
  * tuner, TS/PVR capture, AudioTrack or any process outside the launcher. */
 static void suspendForVideo(Context context){
  Context app=context.getApplicationContext();
  PRIORITY_IO.execute(()->{
   WorkManager manager=WorkManager.getInstance(app);
   // Wait in the dedicated IO queue for WorkManager cancellation to settle.
   // Otherwise resume(KEEP) can race an outstanding cancel and lose schedules.
   java.util.ArrayList<java.util.concurrent.Future<?>> cancellations=new java.util.ArrayList<>();
   for(String name:optionalNames())
    cancellations.add(manager.cancelUniqueWork(name).getResult());
   for(java.util.concurrent.Future<?> cancellation:cancellations)
    try{cancellation.get(3,TimeUnit.SECONDS);}
    catch(Exception ignored){/* The worker also exits on video-priority flag. */}
   app.getSharedPreferences("smarthub",0).edit()
     .putString("video_priority_jobs","PAUSED_APP_WORKMANAGER")
     .putLong("video_priority_jobs_paused_at",System.currentTimeMillis()).apply();
  });
 }
 /** Restore normal EPG/poster schedules only after the software video exits. */
 static void resumeAfterVideo(Context context){
  Context app=context.getApplicationContext();
  PRIORITY_IO.execute(()->{
   if(videoPriority(app))return;
   ensure(app);
   app.getSharedPreferences("smarthub",0).edit()
     .putString("video_priority_jobs","RESTORED")
     .putLong("video_priority_jobs_restored_at",System.currentTimeMillis()).apply();
  });
 }
 static void runNow(Context context){
  if(videoPriority(context))return;
  WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
  Constraints internet=online();
  manager.enqueueUniqueWork(XMLTV+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(POSTERS+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(PosterRefreshWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(DVB+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(DvbEpgRefreshWorker.class).build());
  manager.enqueueUniqueWork(CHMAX+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(ChmaxLogoSyncWorker.class).setConstraints(internet)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build());
 }
 static void programmeChanged(Context context){
  if(videoPriority(context))return;
  // Debounce channel switches; the worker checks the currently observed event.
  Constraints internet=online();
  WorkManager.getInstance(context.getApplicationContext())
    .enqueueUniqueWork(POSTERS+"-programme",ExistingWorkPolicy.REPLACE,
      new OneTimeWorkRequest.Builder(PosterRefreshWorker.class)
        .setInitialDelay(4,TimeUnit.SECONDS)
        .setConstraints(internet).build());
 }
 static void ensure(Context context){
  if(videoPriority(context))return;
  WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
  Constraints internet=online();
  manager.enqueueUniquePeriodicWork(XMLTV,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(IraqHubSyncWorker.class,internet));
  manager.enqueueUniquePeriodicWork(POSTERS,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(PosterRefreshWorker.class,internet));
  manager.enqueueUniquePeriodicWork(DVB,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(DvbEpgRefreshWorker.class,null));
  manager.enqueueUniquePeriodicWork(CHMAX,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(ChmaxLogoSyncWorker.class,internet));
  manager.enqueueUniqueWork(XMLTV+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(POSTERS+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(PosterRefreshWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(DVB+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(DvbEpgRefreshWorker.class).build());
  manager.enqueueUniqueWork(CHMAX+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(ChmaxLogoSyncWorker.class).setConstraints(internet)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build());
 }
 private static Constraints online(){
  return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
 }
 private static PeriodicWorkRequest everyFourHours(
      Class<? extends androidx.work.ListenableWorker> type,Constraints constraints){
  PeriodicWorkRequest.Builder builder=new PeriodicWorkRequest.Builder(type,4,TimeUnit.HOURS)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES);
  if(constraints!=null)builder.setConstraints(constraints);
  return builder.build();
 }
}
