package com.smarthub.plus;

/**
 * EPG ALL is an app-private, read-only combined schedule for DVB services.
 * It is NOT a guessed firmware command. Only a verified exact DVB physical
 * channel identity may enter this schedule.
 */
final class DvbEpgAllPolicy {
 private DvbEpgAllPolicy(){}
 static boolean physical(String channel){
  return channel!=null&&channel.startsWith("DVB:")&&channel.length()>5&&channel.length()<=250;
 }
 static int rank(String source){
  String s=source==null?"":source.toLowerCase(java.util.Locale.ROOT);
  if(s.contains("factory epg pro")||s.contains("factory chmax / epg")||
      s.contains("epg pro · verified"))return 400;
  if(s.contains("dvb eit")||s.contains("dvb native"))return 300;
  if(s.contains("android tv")||s.contains("published epg"))return 250;
  if(s.contains("xmltv")||s.contains("mapped guide")||s.contains("epgshare"))return 200;
  if(s.contains("observed")||s.contains("receiver"))return 150;
  return 100;
 }
 static boolean receiverGrade(String source){
  return rank(source)>=250;
 }
 static boolean current(long start,long end,long now,String title){
  return ProgrammeTitle.available(title)&&start>0&&start<=now&&end>now;
 }
 static boolean next(long start,long end,long now,String title){
  return ProgrammeTitle.available(title)&&start>now&&end>start&&
    start-now<=7L*86400000;
 }
}
