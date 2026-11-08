package com.smarthub.plus;

/**
 * X5M isolated MPV render choices. The satellite MPEG-TS and its original
 * encoded 4:2:2/10-bit frames are never modified by selecting output format.
 * The optional FFmpeg filter runs only AFTER decoder output.
 */
final class Mpv422FluidPolicy {
 static final String AUTO="auto_720",P720="420_720",P540="420_540",
   P480="420_480",P360="420_360",P260="420_260",P1080="420_1080",
   Y422_1080="422_1080",Y422_720="422_720",Y422_540="422_540",
   Y422_480="422_480",Y422_360="422_360",Y422_260="422_260",
   GPU_NATIVE="gpu_native";
 static final String SW="sw",HW="hw";
 private static final String[] MODES={AUTO,P720,P540,P480,P360,P260,P1080,
   Y422_1080,Y422_720,Y422_540,Y422_480,Y422_360,Y422_260,GPU_NATIVE};
 // One public panel rather than two competing "4:2:2 engines".
 // 480p remains accepted for users upgrading from previous releases.
 private static final String[] PUBLIC_MODES={
   AUTO,P1080,P720,P540,P360,P260,
   Y422_1080,Y422_720,Y422_540,Y422_360,Y422_260,GPU_NATIVE};
 static String[] publicModes(){return PUBLIC_MODES.clone();}
 private Mpv422FluidPolicy(){}

 static String[] modes(){return MODES.clone();}
 static String normalized(String mode){
  if(mode!=null)for(String value:MODES)if(value.equals(mode))return value;
  return AUTO;
 }
 static String decoderMode(String requested){
  return HW.equals(requested)?HW:SW;
 }
 static String hwdecOption(String requested){
  // Experimental Android MediaCodec copy path. MPV may fall back to SW for
  // unsupported H.264/HEVC 4:2:2; hwdec-current is the actual result.
  return HW.equals(decoderMode(requested))?"mediacodec-copy":"no";
 }
 static String initialRenderMode(String chosen){
  String safe=normalized(chosen);
  return AUTO.equals(safe)?P720:safe;
 }
 static int targetHeight(String mode){
  String value=normalized(mode);
  if(AUTO.equals(value)||P720.equals(value)||Y422_720.equals(value))return 720;
  if(P1080.equals(value)||Y422_1080.equals(value))return 1080;
  if(P540.equals(value)||Y422_540.equals(value))return 540;
  if(P480.equals(value)||Y422_480.equals(value))return 480;
  if(P360.equals(value)||Y422_360.equals(value))return 360;
  if(P260.equals(value)||Y422_260.equals(value))return 260;
  return 0; // native GPU with no forced video filter
 }
 static boolean is422(String mode){
  String value=normalized(mode);
  return value.startsWith("422_");
 }
 static String videoFilter(String renderMode){
  int height=targetHeight(renderMode);
  if(height==0)return "";
  // Preserve 16:9 output pixels for typical broadcast HD frames.
  // 422 output keeps 10-bit; 420 output intentionally chooses 8-bit.
  int width=height==1080?1920:height==720?1280:
    height==540?960:height==480?854:height==260?462:640;
  String format=is422(renderMode)?"yuv422p10le":"yuv420p";
  return "lavfi=[scale=w="+width+":h="+height+
    ":flags=fast_bilinear,format=pix_fmts="+format+"]";
 }
 static String readable(String value){
  if(GPU_NATIVE.equals(value))return "GPU FAST / native GPU output (no forced software scaling)";
  String normalized=normalized(value);
  return targetHeight(normalized)+"p "+
    (is422(normalized)?"4:2:2 10-bit":"4:2:0 8-bit");
 }
 static String outputLabel(String chosen,String renderMode){
  return normalized(chosen)+" / "+readable(renderMode)+
   (GPU_NATIVE.equals(renderMode)?" (unfiltered)":" (FFmpeg post-decode)");
 }
 static int poorDropSamples(int previous,int current,long duration,int consecutive){
  if(previous<0||current<previous||duration<3500||duration>9000)return 0;
  return current-previous>=7?Math.min(3,consecutive+1):0;
 }
 static boolean shouldDownshift(String chosen,int consecutivePoorSamples,
      long elapsedSinceStartMs,int recentDropped,long sampleDurationMs){
  return AUTO.equals(normalized(chosen))&&consecutivePoorSamples>=2&&
    elapsedSinceStartMs>=12000&&sampleDurationMs>=3500&&
    sampleDurationMs<=9000&&recentDropped>=7;
 }
 static String nextAutoMode(String current){
  if(P720.equals(current))return P540;
  if(P540.equals(current))return P360;
  if(P360.equals(current))return P260;
  return current;
 }
 /** Exactly one GPU-native recovery after filtered loading failure. */
 static boolean recoverWithNative(String renderMode,boolean triedNative,String reason){
  if(triedNative||GPU_NATIVE.equals(renderMode)||reason==null)return false;
  return reason.startsWith("MPV_NO_FILE_LOADED")||
    reason.startsWith("MPV_END_BEFORE_FILE_LOADED")||
    reason.startsWith("MPV_SURFACE_NOT_READY")||
    reason.startsWith("MPV_SURFACE_ATTACH")||
    reason.contains("MPV_VF_REJECTED");
 }
}
