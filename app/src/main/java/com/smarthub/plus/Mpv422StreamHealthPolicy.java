package com.smarthub.plus;

/**
 * Conservative X5M HEVC no-frame recovery policy.
 * MPEG-TS carrying bytes and FILE_LOADED do not prove decoded video frames.
 * Retry ONCE on the newest existing live TS segment with SW/no audio/native
 * output. A retry never re-tunes DVB, replaces the PVR capture, or loops.
 */
final class Mpv422StreamHealthPolicy {
 private Mpv422StreamHealthPolicy(){}
 static boolean noDecodedFrames(String decodedPixelFormat,double estimatedFps){
  return (decodedPixelFormat==null||
    decodedPixelFormat.isEmpty()||
    "unknown".equalsIgnoreCase(decodedPixelFormat))&&estimatedFps<=0;
 }
 static int missingFrameSamples(int previous,String decodedPixelFormat,double estimatedFps){
  return noDecodedFrames(decodedPixelFormat,estimatedFps)?
    Math.min(20,previous+1):0;
 }
 static boolean shouldRecover(boolean loaded,boolean alreadyRetried,
     boolean relayOrNetworkReady,long millisecondsSinceRendererStarted,
     int successiveNoFrameSamples){
  return loaded&&!alreadyRetried&&relayOrNetworkReady&&
    millisecondsSinceRendererStarted>=18000&&successiveNoFrameSamples>=3;
 }
}
