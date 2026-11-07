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
   LogoRepository.get(app).pauseForVideo();
  }
 }
 static void leave(Context context){
  synchronized(LOCK){
   if(viewers==0)return;
   if(--viewers!=0)return;
   Context app=context.getApplicationContext();
   LogoRepository.get(app).resumeAfterVideo();
   app.getSharedPreferences("smarthub",0).edit()
      .putBoolean("video_priority_active",false).apply();
  }
 }
 /** OnTrimMemory uses bounded LRU eviction, without forcing Java GC. */
 static void memoryPressure(Context context,int level){
  if(level<android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)return;
  LogoRepository.get(context.getApplicationContext()).trimRamCache();
 }
 static int sessionCount(){synchronized(LOCK){return viewers;}}
}
