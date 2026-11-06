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

/**
 * Three independent, persistent 240-minute refresh loops.
 * XMLTV and BetterPosters require connectivity; native EIT does not.
 * WorkManager keeps schedules across ordinary app/device restarts.
 */
final class IraqHubSyncScheduler {
 private static final String XMLTV="iraqhub-unified-epg-poster-sync"; // retain old unique work identity
 private static final String POSTERS="iraqhub-native-posters-sync";
 private static final String DVB="iraqhub-native-dvb-epg-sync";
 private IraqHubSyncScheduler(){}
 static void runNow(Context context){
  WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
  Constraints internet=online();
  manager.enqueueUniqueWork(XMLTV+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(POSTERS+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(PosterRefreshWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(DVB+"-manual",ExistingWorkPolicy.REPLACE,
    new OneTimeWorkRequest.Builder(DvbEpgRefreshWorker.class).build());
 }
 static void ensure(Context context){
  WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
  Constraints internet=online();
  manager.enqueueUniquePeriodicWork(XMLTV,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(IraqHubSyncWorker.class,internet));
  manager.enqueueUniquePeriodicWork(POSTERS,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(PosterRefreshWorker.class,internet));
  manager.enqueueUniquePeriodicWork(DVB,ExistingPeriodicWorkPolicy.KEEP,
    everyFourHours(DvbEpgRefreshWorker.class,null));
  manager.enqueueUniqueWork(XMLTV+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(POSTERS+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(PosterRefreshWorker.class).setConstraints(internet).build());
  manager.enqueueUniqueWork(DVB+"-initial",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(DvbEpgRefreshWorker.class).build());
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
