package com.smarthub.plus;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Runtime-only CPU-sensitive LibVLC tuning. This does not assert that the
 * Amlogic VPU supports DVB 4:2:2 or that 4GB RAM guarantees realtime 1080i.
 */
final class Software422Profile {
 static final String BALANCED="balanced",SMOOTH="smooth",QUALITY="quality";
 private Software422Profile(){}
 static String current(Context context){
  String saved=context.getSharedPreferences("smarthub",0).getString("software_422_mode","");
  if(BALANCED.equals(saved)||SMOOTH.equals(saved)||QUALITY.equals(saved))return saved;
  String hw=((Build.HARDWARE==null?"":Build.HARDWARE)+" "+
    (Build.BOARD==null?"":Build.BOARD)+" "+(Build.MODEL==null?"":Build.MODEL))
    .toLowerCase(Locale.ROOT);
  return hw.contains("x5m")||hw.contains("s905x5m")?SMOOTH:BALANCED;
 }
 static void save(Context context,String mode){
  if(!BALANCED.equals(mode)&&!SMOOTH.equals(mode)&&!QUALITY.equals(mode))
   throw new IllegalArgumentException("INVALID_422_PROFILE");
  context.getSharedPreferences("smarthub",0).edit().putString("software_422_mode",mode).apply();
 }
 static int threads(){
  int cores=Runtime.getRuntime().availableProcessors();
  // Leave some CPU time for live TS capture, audio, remote and the UI.
  return Math.min(4,Math.max(1,cores>2?cores-1:cores));
 }
 static int networkCacheMs(String mode){return QUALITY.equals(mode)?1500:SMOOTH.equals(mode)?850:1050;}
 static int liveCacheMs(String mode){return QUALITY.equals(mode)?1150:SMOOTH.equals(mode)?700:850;}
 static ArrayList<String> options(Context context){return buildOptions(current(context));}
 static ArrayList<String> buildOptions(String mode){
  if(!BALANCED.equals(mode)&&!SMOOTH.equals(mode)&&!QUALITY.equals(mode))
   throw new IllegalArgumentException("INVALID_422_PROFILE");
  ArrayList<String> options=new ArrayList<>();
  options.add("--avcodec-hw=none");
  options.add("--codec=avcodec");
  options.add("--no-video-title-show");
  options.add("--no-audio-time-stretch");
  options.add("--aout=audiotrack");
  options.add("--avcodec-threads="+threads());
  // Values 0,1,2 are VLC's documented default/non-ref/B-frame discard levels.
  options.add("--avcodec-skiploopfilter="+(QUALITY.equals(mode)?0:SMOOTH.equals(mode)?2:1));
  // Skipping reference-dependent/B frames produces jerky motion and A/V drift.
  // Decode all frames; only deblocking is relaxed in Smooth.
  options.add("--avcodec-skip-frame=0");
  options.add("--avcodec-skip-idct=0");
  options.add("--drop-late-frames");
  // Do not use VLC --skip-frames: it causes choppy picture during sports feeds.
  // Bound live latency without forcing LibVLC's file demuxer on an MPEG-TS HTTP stream.
  options.add("--network-caching="+networkCacheMs(mode));
  options.add("--live-caching="+liveCacheMs(mode));
  return options;
 }
 static String status(Context context){
  long ram=0;
  try{
   ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
   ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
   if(manager!=null){manager.getMemoryInfo(info);ram=info.totalMem/1048576L;}
  }catch(Exception ignored){}
  String soc=Build.HARDWARE==null?"":Build.HARDWARE;
  if(Build.VERSION.SDK_INT>=31){
   try{if(Build.SOC_MODEL!=null&&!Build.SOC_MODEL.isEmpty())soc=Build.SOC_MODEL;}
   catch(Throwable ignored){}
  }
  return (Build.MODEL==null?"Android DVB":Build.MODEL)+" · "+
   soc+" · "+Runtime.getRuntime().availableProcessors()+" CPU cores · "+
   (ram>0?ram+" MiB RAM · ":"")+
   "LibVLC software 4:2:2 · "+current(context);
 }
}
