package com.smarthub.plus;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Fetch only the missing/expired EPGShare feed without blocking channel switching. */
public final class EpgFeedPrefetchWorker extends Worker {
 private static final ConcurrentHashMap<String,Long> requested=new ConcurrentHashMap<>();
 public EpgFeedPrefetchWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 static void request(Context context,String feed){
  if(feed==null||!feed.matches("[A-Z0-9_]{1,32}"))return;
  long now=System.currentTimeMillis();
  Long last=requested.get(feed);
  if(last!=null&&now-last<90_000)return;
  requested.put(feed,now);
  Constraints online=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(EpgFeedPrefetchWorker.class)
    .setInputData(new Data.Builder().putString("feed",feed).build())
    .setConstraints(online).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
  WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
    "iraqhub-epg-on-demand-"+feed,ExistingWorkPolicy.KEEP,work);
 }
 @NonNull @Override public Result doWork(){
  String feed=getInputData().getString("feed");
  if(feed==null||!feed.matches("[A-Z0-9_]{1,32}"))return Result.failure();
  Context context=getApplicationContext();
  if(context.getSharedPreferences("smarthub",0).getBoolean("video_priority_active",false)
    ||context.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false)
    ||context.getSharedPreferences("smarthub",0).getBoolean("dvb_tuning",false))
   return Result.retry();
  try{
   File saved=new OnlineEpgGuide(context).download(feed,true);
   if(!saved.isFile()||saved.length()<20)return Result.retry();
   context.getSharedPreferences("smarthub",0).edit()
     .putLong("online_epg_feed_ready:"+feed,System.currentTimeMillis()).apply();
   return Result.success();
  }catch(Exception unavailable){
   context.getSharedPreferences("smarthub",0).edit().putString("online_epg_feed_error:"+feed,
     unavailable.getClass().getSimpleName()).apply();
   return Result.retry();
  }
 }
}
