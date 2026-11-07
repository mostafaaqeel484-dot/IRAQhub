package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers actual epg_ripper_*.xml.gz filenames from the live EPGShare index. */
final class EpgShareFeedDirectory {
 private static final String INDEX="https://epgshare01.online/epgshare01/";
 private static final Pattern NAME=Pattern.compile(
   "epg_ripper_([A-Za-z0-9_]{1,32})\\.xml\\.gz",Pattern.CASE_INSENSITIVE);
 private EpgShareFeedDirectory(){}
 static List<String> discover(Context context){
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  long now=System.currentTimeMillis();
  List<String> cache=parse(p.getString("epgshare_available",""));
  long at=p.getLong("epgshare_discovered_at",0);
  if(!cache.isEmpty()&&now>=at&&now-at<6*3600000L)return cache;
  HttpURLConnection request=null;
  try{
   request=(HttpURLConnection)new URL(INDEX).openConnection();
   request.setInstanceFollowRedirects(false);
   request.setConnectTimeout(8000);request.setReadTimeout(12000);
   request.setRequestProperty("Accept","text/html");
   if(request.getResponseCode()!=200)throw new java.io.IOException("EPGSHARE_DIRECTORY_HTTP");
   String html;
   try(InputStream in=request.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
    byte[] block=new byte[8192];int n;
    while((n=in.read(block))!=-1){
     if(buffer.size()+n>512*1024)throw new java.io.IOException("EPGSHARE_DIRECTORY_LIMIT");
     buffer.write(block,0,n);
    }
    html=new String(buffer.toByteArray(),StandardCharsets.UTF_8);
   }
   Matcher matcher=NAME.matcher(html);Set<String> names=new LinkedHashSet<>();
   while(matcher.find()&&names.size()<180)names.add(matcher.group(1).toUpperCase(java.util.Locale.ROOT));
   if(names.isEmpty())throw new java.io.IOException("EPGSHARE_NO_FEEDS");
   List<String> found=new ArrayList<>(names);Collections.sort(found);
   p.edit().putString("epgshare_available",String.join(",",found))
     .putLong("epgshare_discovered_at",now)
     .putString("epgshare_discovery_status","OK").apply();
   return found;
  }catch(Exception unavailable){
   p.edit().putString("epgshare_discovery_status",
    "INDEX_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
   return cache; // offline mode: preserve last verified selection
  }finally{if(request!=null)request.disconnect();}
 }
 static List<String> parse(String csv){
  Set<String> names=new LinkedHashSet<>();
  if(csv!=null)for(String part:csv.split(",")){
   String code=part.trim().toUpperCase(java.util.Locale.ROOT);
   if(code.matches("[A-Z0-9_]{1,32}")&&names.size()<180)names.add(code);
  }
  return new ArrayList<>(names);
 }
 static String selected(Context context){
  return context.getSharedPreferences("smarthub",0)
    .getString("epgshare_selected_feeds","AE1,ALJAZEERA1,BEIN1");
 }
 static void selectForChannel(Context context,String channel,String feed){
  selectForChannel(context,channel,"",feed);
 }
 static void selectForChannel(Context context,String channel,String physicalId,String feed){
  String code=feed==null?"":feed.toUpperCase(java.util.Locale.ROOT);
  if(!code.matches("[A-Z0-9_]{1,32}"))throw new IllegalArgumentException("INVALID_EPG_FEED");
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  Set<String> codes=new LinkedHashSet<>(parse(selected(context)));
  codes.add(code);
  SharedPreferences.Editor editor=p.edit();
  if(physicalId!=null&&!physicalId.isEmpty())editor.putString("online_epg_channel_feed_id:"+physicalId,code);
  else editor.putString("online_epg_channel_feed:"+StreamingXmlTv.channelKey(channel),code);
  editor.putString("epgshare_selected_feeds",String.join(",",codes))
    .putLong("epgshare_mapping_changed_at",System.currentTimeMillis()).apply();
 }
}
