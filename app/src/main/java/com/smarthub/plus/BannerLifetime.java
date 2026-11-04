package com.smarthub.plus;
/** Monotonic ten-second window. Repainting images never restarts the timer. */
final class BannerLifetime {
 private long duration;private String context="";private long until;
 BannerLifetime(long duration){this.duration=duration;}
 void duration(long value){long next=Math.max(1000,Math.min(10000,value));if(duration!=next){if(until>0)until=until-duration+next;duration=next;}}
 void context(String value,long now){if(!value.equals(context)){context=value;show(now);}}
 void show(long now){until=now+duration;}
 boolean visible(long now){return now<until;}
 long remaining(long now){return Math.max(0,until-now);}
}
