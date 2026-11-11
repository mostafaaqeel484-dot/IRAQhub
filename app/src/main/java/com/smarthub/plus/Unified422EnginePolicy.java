package com.smarthub.plus;

/** Single front-end policy; FFmpeg-backed internal backends are not concurrent. */
final class Unified422EnginePolicy {
 private Unified422EnginePolicy(){}
 static boolean preferMpv(String engine,String outputMode){
  if("mpv".equals(engine))return true;
  return "unified".equals(engine)&&
    !Mpv422FluidPolicy.AUTO.equals(Mpv422FluidPolicy.normalized(outputMode));
 }
 static String backendName(String engine,String outputMode){
  return preferMpv(engine,outputMode)?"MPV_FFMPEG":"LIBVLC_FFMPEG";
 }
}
