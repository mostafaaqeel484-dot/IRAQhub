package com.smarthub.plus;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
/** Old scheduled XMLTV job: disabled to enforce explicit manual guide selection. */
public final class IraqHubSyncWorker extends Worker {
 public IraqHubSyncWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 @NonNull @Override public Result doWork(){
  getApplicationContext().getSharedPreferences("smarthub",0).edit()
   .putString("iraqhub_unified_sync_state","XMLTV_MANUAL_ONLY").apply();
  return Result.success();
 }
}
