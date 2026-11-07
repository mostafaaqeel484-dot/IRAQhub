package com.smarthub.plus;

/**
 * Render-only choices for the X5M CPU-decoded 4:2:2 -> 4:2:0 experiment.
 * The source MPEG-TS remains untouched, and the decoder still MUST decode
 * the original 1920x1080 yuv422p10 bitstream before any output conversion.
 */
final class Mpv422FluidPolicy {
 static final String AUTO="auto_720",P720="420_720",P540="420_540",
     GPU_NATIVE="gpu_native";
 private Mpv422FluidPolicy(){}

 static String normalized(String name){
  return P720.equals(name)||P540.equals(name)||GPU_NATIVE.equals(name)
    ?name:AUTO;
 }
 static String initialRenderMode(String chosen){
  String safe=normalized(chosen);
  return AUTO.equals(safe)?P720:safe;
 }
 static String videoFilter(String renderMode){
  if(P540.equals(renderMode))
   return "lavfi=[scale=w=960:h=540:flags=fast_bilinear,format=pix_fmts=yuv420p]";
  if(P720.equals(renderMode))
   return "lavfi=[scale=w=1280:h=720:flags=fast_bilinear,format=pix_fmts=yuv420p]";
  return ""; // No CPU conversion if OpenGL ES is faster on this ROM.
 }
 static boolean shouldDownshift(String chosen,int consecutivePoorSamples,
    long elapsedSinceStartMs,int recentDropped,long sampleDurationMs){
  return AUTO.equals(normalized(chosen))&&
    consecutivePoorSamples>=2&&elapsedSinceStartMs>=12000&&
    sampleDurationMs>=3500&&sampleDurationMs<=9000&&recentDropped>=7;
 }
 static int poorDropSamples(int previous, int current, long sampleDurationMs,
    int consecutive){
  if(previous<0||current<previous||sampleDurationMs<3500||
     sampleDurationMs>9000)return 0;
  int delta=current-previous;
  return delta>=7?Math.min(3,consecutive+1):0;
 }
 static int targetHeight(String renderMode){
  return P540.equals(renderMode)?540:P720.equals(renderMode)?720:0;
 }
 static String outputLabel(String chosen,String renderMode){
  return normalized(chosen)+" / "+renderMode+
    (GPU_NATIVE.equals(renderMode)?" (native GPU path)":" (YUV420P 8bit after decode)");
 }
}
