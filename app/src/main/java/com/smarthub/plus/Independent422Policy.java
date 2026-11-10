package com.smarthub.plus;

/** Pure safety and telemetry policy for the dedicated MPV/FFmpeg process. */
final class Independent422Policy {
 private Independent422Policy(){}
 static boolean eligibleDvb(int serviceType,int caFlag){
  return serviceType==0&&caFlag>=0;
 }
 static boolean clockProgressed(double oldSeconds,double newSeconds){
  return !Double.isNaN(newSeconds)&&!Double.isInfinite(newSeconds)&&
    newSeconds>=0&&(oldSeconds<0||newSeconds-oldSeconds>=0.30);
 }
 static boolean clockStalled(long lastProgressMs,long nowMs,
     boolean relayRunning){
  return relayRunning&&lastProgressMs>0&&nowMs-lastProgressMs>11000;
 }
}
