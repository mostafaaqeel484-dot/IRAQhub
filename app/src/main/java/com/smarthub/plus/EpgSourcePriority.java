package com.smarthub.plus;
import java.util.Locale;
/**
 * Verified receiver EIT and factory EPG beat internet. Source selection never
 * guesses channel identity from a programme title.
 */
final class EpgSourcePriority {
 static final int DVB=400,FACTORY=330,ONLINE=100;
 private EpgSourcePriority(){}
 static int rank(String source){
  if(source==null||source.trim().isEmpty())return 0;
  String value=source.toLowerCase(Locale.ROOT);
  if(value.contains("dvb eit")||value.contains("native eit")||
     value.contains("dvb native")||value.contains("native receiver"))return DVB;
  if(value.contains("epg pro")||value.contains("epgpro")||
     value.contains("factory")||value.contains("chmax")||
     value.contains("oem epg"))return FACTORY;
  if(value.contains("android tv")||value.contains("published epg"))return 300;
  if(value.contains("dvb"))return 350;
  if(value.contains("xmltv")||value.contains("mapped guide"))return 200;
  if(value.contains("online")||value.contains("internet")||value.contains("epgshare"))return ONLINE;
  if(value.contains("observed")||value.contains("receiver"))return 180;
  return 150;
 }
 static boolean prefer(String incoming,String existing,boolean currentValid){
  return !currentValid||rank(incoming)>rank(existing);
 }
 static boolean factory(String source){return rank(source)==FACTORY;}
}
