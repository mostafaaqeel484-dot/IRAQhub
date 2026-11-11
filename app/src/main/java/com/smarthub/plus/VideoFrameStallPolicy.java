package com.smarthub.plus;

/**
 * Monotonic LibVLC media stats are authoritative evidence that the decoder
 * AUDIO is progressing while VIDEO display/decode counters have stopped.
 * MediaPlayer Vout and Playing are not frame-progress measurements.
 */
final class VideoFrameStallPolicy {
 private VideoFrameStallPolicy(){}
 static boolean stalled(long lastVideoMs,long nowMs,long demuxBytesDelta,
   long audioDelta,long displayedDelta,long decodedDelta,
   boolean playing,boolean buffering,boolean surfaceReady){
  return playing&&!buffering&&surfaceReady&&lastVideoMs>0&&
    nowMs-lastVideoMs>=8500&&demuxBytesDelta>188L*100&&
    audioDelta>=6&&displayedDelta==0&&decodedDelta==0;
 }
 static boolean rendererStalled(long lastDisplayMs,long nowMs,long demuxDelta,
   long audioDelta,long displayDelta,long videoDecodedDelta,
   boolean playing,boolean buffering,boolean surfaceReady){
  return playing&&!buffering&&surfaceReady&&lastDisplayMs>0&&
    nowMs-lastDisplayMs>=8500&&demuxDelta>188L*100&&
    audioDelta>=6&&displayDelta==0&&videoDecodedDelta>6;
 }
}