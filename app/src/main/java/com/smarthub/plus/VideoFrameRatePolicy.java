package com.smarthub.plus;

/** Frame-progress telemetry for 4:2:2. 100 = 1.00 fps.
 * Unknown and counter resets return -1, never a fabricated zero FPS.
 */
final class VideoFrameRatePolicy {
 private VideoFrameRatePolicy(){}
 static int fpsX100(long previous,long current,long sampleMs){
  if(previous<0||current<previous||sampleMs<1000||sampleMs>20000)
   return -1;
  long frames=Math.min(1000000L,current-previous);
  return (int)Math.min(99999L,frames*100000L/sampleMs);
 }
 static boolean advancing(long previous,long current){
  return previous>=0&&current-previous>=2;
 }
}