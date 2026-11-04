package com.smarthub.plus;
/** EPG clock only. Subtitle delay must never change programme progress or remaining time. */
final class ProgrammeClock {
 final boolean known;final long remainingMs,elapsedMs;final int progress;
 ProgrammeClock(long start,long end,long now){known=start>0&&end>start;elapsedMs=known?Math.max(0,Math.min(end-start,now-start)):0;remainingMs=known?Math.max(0,end-Math.max(start,now)):0;progress=known?(int)Math.max(0,Math.min(1000,elapsedMs*1000/(end-start))):0;}
 static String format(long ms,boolean millis){long value=Math.max(0,ms);long h=value/3600000,m=value/60000%60,s=value/1000%60;return String.format(java.util.Locale.ROOT,millis?"%02d:%02d:%02d.%03d":"%02d:%02d:%02d",h,m,s,value%1000);}
}
