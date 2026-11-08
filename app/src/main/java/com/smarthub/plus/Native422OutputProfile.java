package com.smarthub.plus;

/**
 * Display scale/chroma policy for the direct FFmpeg JNI decoder.
 *
 * Source HEVC/H264 MPEG-TS is always decoded at its transmitted size.
 * "422" keeps decoded chroma until the final RGBA Surface conversion,
 * while "420" converts to 8-bit 420 before the RGBA screen output.
 * Neither setting represents a 10-bit/4:2:2 Android display surface.
 */
final class Native422OutputProfile {
 static final String AUTO="auto_720";
 static final String GPU_FAST="gpu_native";
 private static final int[] HEIGHTS={1080,720,540,360,260};
 static final class Target {
  final int width,height;
  final boolean convert420,gpuFast;
  final String selected;
  Target(String selected,int width,int height,boolean convert420,boolean gpuFast){
   this.selected=selected;
   this.width=width;this.height=height;
   this.convert420=convert420;this.gpuFast=gpuFast;
  }
  boolean valid(){return gpuFast||(width>0&&height>0&&width%2==0&&height%2==0);}
 }
 private Native422OutputProfile(){}
 static String[] modes(){
  String[] values=new String[12];
  values[0]=AUTO;
  int n=1;
  for(int height:HEIGHTS)values[n++]="422_"+height;
  for(int height:HEIGHTS)values[n++]="420_"+height;
  values[n]=GPU_FAST;
  return values;
 }
 static String normalize(String candidate){
  if(candidate!=null)for(String mode:modes())if(mode.equals(candidate))return mode;
  return AUTO;
 }
 static Target target(String selected){
  String mode=normalize(selected);
  if(GPU_FAST.equals(mode))return new Target(mode,0,0,false,true);
  if(AUTO.equals(mode))mode="420_720";
  boolean convert420=mode.startsWith("420_");
  int height=Integer.parseInt(mode.substring(4));
  int width=height==1080?1920:height==720?1280:
    height==540?960:height==360?640:462;
  return new Target(normalize(selected),width,height,convert420,false);
 }
 static String nextAuto(String current,int droppedInSixSeconds,int samples){
  String normalized=normalize(current);
  if(droppedInSixSeconds<7||samples<2)return normalized;
  switch(normalized){
   case AUTO:case "420_720":return "420_540";
   case "420_540":return "420_360";
   case "420_360":return "420_260";
   default:return normalized;
  }
 }
 static boolean canUseNative(boolean compiled,boolean sourceIsAuthorizedLoopback,
                             boolean userOptedIn){
  return compiled&&sourceIsAuthorizedLoopback&&userOptedIn;
 }
}