package com.smarthub.plus;

/** Pure, conservative auto policy; no decoder restarts or guesses about GPU usage. */
final class Auto422Policy {
 private Auto422Policy(){}
 /** CPU is process CPU time expressed as percentage of one CPU core (100 = one full core).
  * Use observed CPU capacity rather than treating four threads as a universal optimum. */
 static String choose(double processCpuPercent,int cores,boolean lowMemory,int thermalStatus){
  if(lowMemory||thermalStatus>=3)return Software422Profile.SMOOTH;
  int usable=Math.max(1,Math.min(4,cores-1));
  if(processCpuPercent>=usable*75.0)return Software422Profile.SMOOTH;
  return Software422Profile.BALANCED;
 }
 static int sampleMs(){return 8000;}
 static int decoderThreads(String effectiveMode,int cores){
  int spare=Math.max(1,cores>2?cores-1:cores);
  return Math.min(Software422Profile.SMOOTH.equals(effectiveMode)?3:4,spare);
 }
}