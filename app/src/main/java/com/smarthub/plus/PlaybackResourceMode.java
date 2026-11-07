package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-owned decoder resource budget, not a RAM cleaner.
 * Never kills the OEM DVB service, never calls System.gc() and never
 * clears the player's media buffer while a channel is active.
 */
final class PlaybackResourceMode {
 private static final Object LOCK=new Object();
 private static int viewers;
 private PlaybackResourceMode(){}

 static void enter(Context context){
  synchronized(LOCK){
   if(++viewers!=1)return;
   Context app=context.getApplicationContext();
   app.getSharedPreferences("smarthub",0).edit()
      .putBoolean("video_priority_active",true)
      .putLong("video_priority_since",System.currentTimeMillis()).apply();
   // 4:2:2 focus: clear app-owned decoded logo bitmaps before the player
   // begins decoding. Preserve cached files so logos/posters still work later.
   LogoRepository.get(app).pauseForVideo();
   IraqHubSyncScheduler.suspendForVideo(app);
   app.getSharedPreferences("smarthub",0).edit()
     .putLong("video_priority_ram_trim_at",System.currentTimeMillis())
     .putString("video_priority_mode","SOFTWARE_DECODER_FOCUS").apply();
  }
 }
 static void leave(Context context){
  synchronized(LOCK){
   if(viewers==0)return;
   if(--viewers!=0)return;
   Context app=context.getApplicationContext();
   LogoRepository.get(app).resumeAfterVideo();
   app.getSharedPreferences("smarthub",0).edit()
      .putBoolean("video_priority_active",false)
      .putString("video_priority_mode","NORMAL").apply();
   IraqHubSyncScheduler.resumeAfterVideo(app);
  }
 }
 /** OnTrimMemory uses bounded LRU eviction, without forcing Java GC. */
 static void memoryPressure(Context context,int level){
  if(level<android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)return;
  LogoRepository.get(context.getApplicationContext()).trimRamCache();
 }
 /** Process-local lease; if Android kills and recreates this process, stale
  * SharedPreferences must not keep background EPG work suspended forever. */
 static boolean active(){synchronized(LOCK){return viewers>0;}}
 /** Restrict simplified Tiger visuals to live DVB software decoding, not ordinary DVB/IPTV. */
 static boolean lite(Context context){
  return active()&&context.getSharedPreferences("smarthub",0)
    .getBoolean("software_dvb_422_active",false);
 }
 static void clearStaleOnLauncherStart(Context context){
  synchronized(LOCK){
   if(viewers==0)context.getApplicationContext()
    .getSharedPreferences("smarthub",0).edit()
    .putBoolean("video_priority_active",false).apply();
  }
 }
 static int sessionCount(){synchronized(LOCK){return viewers;}}
}
