package com.smarthub.plus;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

/**
 * Low-frequency measurements of the app process, system memory and exposed thermal status.
 * Updates settings for the NEXT decoder open/recovery; never disrupts a live 4:2:2 picture.
 * CPU readings are process estimates, not decoder FPS or GPU measurements.
 */
final class Auto422RuntimeMonitor {
 private final Context app;
 private final SharedPreferences prefs;
 private final Handler main=new Handler(Looper.getMainLooper());
 private long previousWall,previousCpu;
 private boolean running;
 private String candidate="";
 private int confirmations;
 private final Runnable sample=new Runnable(){
  @Override public void run(){
   if(!running)return;
   long wall=SystemClock.elapsedRealtime(),cpu=Process.getElapsedCpuTime();
   long elapsed=wall-previousWall,cpuDelta=cpu-previousCpu;
   previousWall=wall;previousCpu=cpu;
   if(elapsed>=1000&&cpuDelta>=0){
    double load=Math.min(100.0*Math.max(1,Runtime.getRuntime().availableProcessors()),
      100.0*cpuDelta/elapsed);
    ActivityManager activity=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
    if(activity!=null)activity.getMemoryInfo(memory);
    boolean pressure=memory.lowMemory||(memory.totalMem>0&&
      memory.availMem<Math.max(256L*1024*1024,memory.totalMem/12));
    int thermal=-1;
    if(Build.VERSION.SDK_INT>=29)try{
     PowerManager manager=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
     if(manager!=null)thermal=manager.getCurrentThermalStatus();
    }catch(Exception ignored){}
    String next=Auto422Policy.choose(load,Runtime.getRuntime().availableProcessors(),
      pressure,thermal);
    SharedPreferences.Editor edit=prefs.edit()
       .putInt("auto_422_cpu_percent",(int)Math.round(load))
       .putBoolean("auto_422_low_memory",pressure)
       .putInt("auto_422_thermal",thermal)
       .putLong("auto_422_sample_at",System.currentTimeMillis());
    if(Software422Profile.AUTO.equals(Software422Profile.current(app))){
     if(next.equals(candidate))confirmations++;else{candidate=next;confirmations=1;}
     if(pressure||thermal>=3||confirmations>=2){
      // The effective target is read only when creating the next decoder.
      edit.putString("auto_422_target",next);
     }
    }
    edit.apply();
   }
   if(running)main.postDelayed(this,Auto422Policy.sampleMs());
  }
 };
 Auto422RuntimeMonitor(Context context){
  app=context.getApplicationContext();
  prefs=app.getSharedPreferences("smarthub",0);
 }
 void start(){
  if(running)return;
  running=true;previousWall=SystemClock.elapsedRealtime();
  previousCpu=Process.getElapsedCpuTime();
  main.postDelayed(sample,Auto422Policy.sampleMs());
 }
 void stop(){running=false;main.removeCallbacks(sample);}
}