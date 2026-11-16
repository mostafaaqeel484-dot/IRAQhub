package com.smarthub.plus;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ProviderCatalogueSyncWorker extends Worker {
 public ProviderCatalogueSyncWorker(@NonNull Context context,@NonNull WorkerParameters params){
  super(context,params);
 }
 @NonNull @Override public Result doWork(){
  Context context=getApplicationContext();
  if(PlaybackResourceMode.active()||context.getSharedPreferences("smarthub",0)
      .getBoolean("software_dvb_422_active",false))return Result.retry();
  ChlLogoAddon.get(context).syncProviders();
  return Result.success();
 }
}