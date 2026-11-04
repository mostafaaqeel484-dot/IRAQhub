package com.smarthub.plus;
/** Translation may finish slowly; only the currently observed cue may reach the screen. */
final class CaptionLatch {
 private String context="",text="",key="";private long token,lastSeen;
 synchronized long offer(String identity,String value,long now){if(!context.equals(identity)){context=identity;clear();}String clean=value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC).replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","").trim().replaceAll("[^\\S\\n]+"," ").replaceAll("\\n+","\n");if(clean.isEmpty()){if(now-lastSeen>=900)clear();return token;}String normalized=clean.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{P}\\p{S}]","").replaceAll("\\s+"," ").trim();if(normalized.isEmpty())return token;if(!normalized.equals(key)){key=normalized;text=clean;token++;}lastSeen=now;return token;}
 synchronized boolean current(long value,long now){return value==token&&!text.isEmpty()&&now-lastSeen<2500;}
 synchronized String text(){return text;}
 synchronized long token(){return token;}
 synchronized void clear(){if(!text.isEmpty())token++;text="";key="";}
}
