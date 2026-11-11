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
 static final String AUTO="auto",BALANCED="balanced",SMOOTH="smooth",QUALITY="quality",STABLE="stable";
 private Software422Profile(){}
 static String current(Context context){
  String saved=context.getSharedPreferences("smarthub",0).getString("software_422_mode","");
  if(AUTO.equals(saved)||BALANCED.equals(saved)||SMOOTH.equals(saved)||QUALITY.equals(saved)||STABLE.equals(saved))return saved;
  String hw=((Build.HARDWARE==null?"":Build.HARDWARE)+" "+
    (Build.BOARD==null?"":Build.BOARD)+" "+(Build.MODEL==null?"":Build.MODEL))
    .toLowerCase(Locale.ROOT);
  return AUTO; // Auto starts conservatively and learns from receiver measurements.
 }
 static void save(Context context,String mode){
  if(!AUTO.equals(mode)&&!BALANCED.equals(mode)&&!SMOOTH.equals(mode)&&!QUALITY.equals(mode)&&!STABLE.equals(mode))
   throw new IllegalArgumentException("INVALID_422_PROFILE");
  context.getSharedPreferences("smarthub",0).edit().putString("software_422_mode",mode).apply();
 }
 static int threads(){
  int cores=Runtime.getRuntime().availableProcessors();
  // Leave some CPU time for live TS capture, audio, remote and the UI.
  return Math.min(4,Math.max(1,cores>2?cores-1:cores));
 }
 static String effective(Context context){
  String chosen=current(context);
  if(!AUTO.equals(chosen))return chosen;
  String saved=context.getSharedPreferences("smarthub",0).getString("auto_422_target",SMOOTH);
  return BALANCED.equals(saved)?BALANCED:SMOOTH;
 }
 static int networkCacheMs(String mode){return STABLE.equals(mode)?1650:QUALITY.equals(mode)?1400:SMOOTH.equals(mode)?1050:1250;}
 static int liveCacheMs(String mode){return STABLE.equals(mode)?1400:QUALITY.equals(mode)?1150:SMOOTH.equals(mode)?850:1050;}
 static ArrayList<String> options(Context context){
  String mode=effective(context);
  int cores=Runtime.getRuntime().availableProcessors();
  return buildOptions(mode,AUTO.equals(current(context))
    ?Auto422Policy.decoderThreads(mode,cores):threads());
 }
 static ArrayList<String> buildOptions(String mode){
  return buildOptions(mode,threads());
 }
 static ArrayList<String> buildOptions(String mode,int threadCount){
  if(AUTO.equals(mode))mode=SMOOTH; // Offline callers have no measured device data.
  if(!BALANCED.equals(mode)&&!SMOOTH.equals(mode)&&!QUALITY.equals(mode)&&!STABLE.equals(mode))
   throw new IllegalArgumentException("INVALID_422_PROFILE");
  ArrayList<String> options=new ArrayList<>();
  options.add("--avcodec-hw=none");
  options.add("--codec=avcodec");
  options.add("--no-video-title-show");
  options.add("--no-audio-time-stretch");
  options.add("--aout=audiotrack");
  options.add("--avcodec-threads="+Math.max(1,Math.min(4,threadCount)));
  // Values 0,1,2 are VLC's documented default/non-ref/B-frame discard levels.
  // Profile 2 skips H.264 B-frame deblocking and can visibly increase
   // edge/pixelation artefacts. Keep default quality for Balanced/Quality/Stable;
   // Smooth skips only non-reference frames, never reference/B-frame content.
   options.add("--avcodec-skiploopfilter="+(SMOOTH.equals(mode)?1:0));
  // Skipping reference-dependent/B frames produces jerky motion and A/V drift.
  // Decode all frames; only deblocking is relaxed in Smooth.
  options.add("--avcodec-skip-frame=0");
  options.add("--avcodec-skip-idct=0");
  // The user's 3.7.0 APK contained --no-avcodec-hurry-up. Retain it
  // ONLY for an explicit Stable diagnostic comparison: disabling VLC's
  // hurry-up can preserve frames but costs more CPU, so Auto/Smooth omit it.
  if(STABLE.equals(mode))options.add("--no-avcodec-hurry-up");
  // Allow frame dropping only in Smooth; Stable/Quality retain frames
   // for deinterlaced sports feeds when the CPU can keep up.
   options.add(SMOOTH.equals(mode)?"--drop-late-frames":"--no-drop-late-frames");
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
   "LibVLC software 4:2:2 · "+current(context)+
   (AUTO.equals(current(context))?" → "+effective(context):"");
 }
}
