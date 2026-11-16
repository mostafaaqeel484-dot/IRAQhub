package com.smarthub.plus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parser for PUBLIC live-feed.net satellite metadata.
 *
 * The site does not advertise a stable public API. Never parse or persist
 * encryption keys, image URLs or scripts. This parser accepts text extracted
 * from HTML and fails closed if the website's structure stops matching.
 */
final class LiveFeedTextParser {
 private static final Pattern RF=Pattern.compile("(?<![0-9])([0-9]{4,5})\\s+([HVLRC])\\s+([0-9]{3,5})(?![0-9])");
 private static final Pattern ORBIT=Pattern.compile("@\\s*([0-9]{1,3}(?:\\.[0-9]+)?)\\s*°?\\s*([EW])",Pattern.CASE_INSENSITIVE);
 private static final Pattern CODEC=Pattern.compile("(?i)(H\\.26[45]|HEVC|AVC|MPEG-2)");
 private static final Pattern CHROMA=Pattern.compile("4:2:[02]|4:4:4");
 private static final Pattern NAME=Pattern.compile("🆔\\s*([^\\n\\r\\uD83D\\uDD11\\uD83D\\uDCE1\\uD83D\\uDCF6\\uD83C\\uDFAC\\uD83D\\uDCCA]{1,90})");
 private static final Pattern STATUS=Pattern.compile("(?<![A-Za-z])(ON|OFF)(?![A-Za-z])",Pattern.CASE_INSENSITIVE);
 private static final Pattern FEED=Pattern.compile("(?i)\\bFEED\\s*[0-9]{1,3}\\b");
 private static final int MAX_RECORDS=320;
 private LiveFeedTextParser(){}

 static final class Entry {
  String satellite="",orbital="",name="",frequency="",polarity="",symbolRate="";
  String codec="",sampling="",status="";
  String key(){return orbital+"|"+frequency+"|"+polarity+"|"+symbolRate;}
  boolean isLive(){return "ON".equals(status);}
  boolean is422(){return "4:2:2".equals(sampling);}
  String frequencyLabel(){return frequency+" "+polarity+" "+symbolRate;}
 }
 static List<Entry> parse(String body){
  LinkedHashMap<String,Entry> feeds=new LinkedHashMap<>();
  if(body==null||body.isEmpty())return new ArrayList<>();
  // Bounded scanner; HTML is fetched with a network size limit upstream.
  String text=body.length()>2_200_000?body.substring(0,2_200_000):body;
  Matcher matcher=RF.matcher(text);
  ArrayList<int[]> positions=new ArrayList<>();
  ArrayList<String[]> parts=new ArrayList<>();
  while(matcher.find()&&positions.size()<MAX_RECORDS*4){
   positions.add(new int[]{matcher.start(),matcher.end()});
   parts.add(new String[]{matcher.group(1),matcher.group(2),matcher.group(3)});
  }
  for(int i=0;i<positions.size();i++){
   int start=positions.get(i)[0],end=positions.get(i)[1];
   String[] rf=parts.get(i);
   String before=text.substring(Math.max(0,start-330),start);
   int segEnd=i+1<positions.size()?positions.get(i+1)[0]:Math.min(text.length(),end+550);
   String after=text.substring(end,Math.min(segEnd,end+680));
   Entry result=new Entry();
   result.frequency=rf[0];result.polarity=rf[1];result.symbolRate=rf[2];
   Matcher orbit=ORBIT.matcher(before);
   int last=-1;
   while(orbit.find()) {
    last=orbit.start();
    result.orbital=orbit.group(1)+orbit.group(2).toUpperCase(Locale.ROOT);
   }
   // Newer cards may label the satellite after their first frequency.
   if(result.orbital.isEmpty()){
    orbit=ORBIT.matcher(after);
    if(orbit.find()){result.orbital=orbit.group(1)+orbit.group(2).toUpperCase(Locale.ROOT);}
   }
   if(last>=0){
    String label=before.substring(Math.max(0,before.lastIndexOf('\n',last)+1),last)
      .replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+"," ").trim();
    int image=label.lastIndexOf("Image ");
    if(image>=0)label=label.substring(image+6);
    if(label.length()>78)label=label.substring(label.length()-78);
    result.satellite=label;
   }
   String piece=after;
   Matcher status=STATUS.matcher(piece);
   if(status.find())result.status=status.group(1).toUpperCase(Locale.ROOT);
   Matcher c=CODEC.matcher(piece);if(c.find())result.codec=c.group(1);
   Matcher m=CHROMA.matcher(piece);if(m.find())result.sampling=m.group();
   Matcher name=NAME.matcher(piece);
   if(name.find())result.name=clean(name.group(1));
   if(result.name.isEmpty()){
    Matcher code=FEED.matcher(piece);
    if(code.find())result.name=clean(code.group());
   }
   if(result.orbital.isEmpty()&&result.satellite.isEmpty()&&
      result.name.isEmpty()&&result.codec.isEmpty()&&result.sampling.isEmpty())
    continue; // unrelated RF-looking body text
   Entry previous=feeds.get(result.key());
   if(previous==null){feeds.put(result.key(),result);}
   else{
    if(previous.name.isEmpty()||previous.name.toUpperCase(Locale.ROOT).startsWith("FEED "))
     if(!result.name.isEmpty())previous.name=result.name;
    if(previous.status.isEmpty())previous.status=result.status;
    if(previous.codec.isEmpty())previous.codec=result.codec;
    if(previous.sampling.isEmpty())previous.sampling=result.sampling;
    if(previous.satellite.isEmpty())previous.satellite=result.satellite;
   }
  }
  return new ArrayList<>(feeds.values());
 }
 private static String clean(String raw){
  if(raw==null)return "";
  String s=raw.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
  // No free-form key-like metadata is ever retained in the native feed model.
  int stop=s.indexOf("🔑");if(stop>=0)s=s.substring(0,stop).trim();
  stop=s.indexOf("📡");if(stop>=0)s=s.substring(0,stop).trim();
  return s.length()>70?s.substring(0,70):s;
 }
}
