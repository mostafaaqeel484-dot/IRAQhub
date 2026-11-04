package com.smarthub.plus;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Idempotent scheduler for the unified EPG/poster source refresh. */
final class IraqHubSyncScheduler {
 private static final String UNIQUE="iraqhub-unified-epg-poster-sync";
 private IraqHubSyncScheduler(){}
 static void runNow(Context context){
  Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(constraints).build();
  WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(UNIQUE+"-manual",androidx.work.ExistingWorkPolicy.REPLACE,request);
 }
 static void ensure(Context context){
  Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(IraqHubSyncWorker.class,4,TimeUnit.HOURS)
   .setConstraints(constraints).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build();
  WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
  manager.enqueueUniquePeriodicWork(UNIQUE,ExistingPeriodicWorkPolicy.KEEP,request);
  OneTimeWorkRequest initial=new OneTimeWorkRequest.Builder(IraqHubSyncWorker.class).setConstraints(constraints).build();
  manager.enqueueUniqueWork(UNIQUE+"-initial",androidx.work.ExistingWorkPolicy.KEEP,initial);
 }
}
