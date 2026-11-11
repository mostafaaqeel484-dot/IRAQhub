package com.smarthub.plus;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Manually imported guide: exact DVB identity only, never overwrites manufacturer EPG.
 * This class performs NO scheduled network requests. Only the explicit UI preview
 * invokes fetch(). The accepted, bounded event list persists in app filesDir. */
final class ManualEpgStore {
 static final class Preview {
  final String physical,channel,source;
  final List<NativeEpgEvents.Event> events;
  Preview(String id,String name,String label,List<NativeEpgEvents.Event> rows){
   physical=id;channel=name;source=label;events=Collections.unmodifiableList(new ArrayList<>(rows));
  }
 }
 private ManualEpgStore(){}
 static boolean validChannel(String id,String name){
  return id!=null&&id.startsWith("DVB:")&&id.length()<=160&&
   name!=null&&!StreamingXmlTv.channelKey(name).isEmpty();
 }
 static String key(String id){return SportsCache.key("manual-epg-v1|"+id);}
 private static File file(Context c,String id){
  File folder=new File(c.getFilesDir(),"manual-epg");if(!folder.exists())folder.mkdirs();
  return new File(folder,key(id)+".json");
 }
 static Preview fetch(Context context,SatelliteState channel,String provider,String feed,
                      String url,String xmltvId)throws Exception {
  if(channel==null||!validChannel(channel.channelId,channel.channelName))
   throw new IOException("DVB_CHANNEL_REQUIRED");
  if(xmltvId==null)xmltvId="";
  if(xmltvId.length()>200)throw new IOException("XMLTV_ID_TOO_LONG");
  String label;
  InputStream input;
  if("EPGShare".equals(provider)){
   if(feed==null||!feed.matches("[A-Z0-9_]{1,32}"))throw new IOException("INVALID_EPGSHARE_FEED");
   File download=new OnlineEpgGuide(context).download(feed,true);
   label="EPGShare "+feed;
   input=new GZIPInputStream(new FileInputStream(download));
  }else if("XMLTV".equals(provider)){
   label="Manual XMLTV";
   byte[] bytes=fetchHttps(url);
   input=new ByteArrayInputStream(bytes);
   if(bytes.length>=2&&(bytes[0]&255)==0x1f&&(bytes[1]&255)==0x8b)
    input=new GZIPInputStream(input);
  }else throw new IOException("UNKNOWN_EPG_PROVIDER");
  List<NativeEpgEvents.Event> rows;
  try(InputStream bounded=new LimitStream(input,48L*1024*1024)){
   rows=StreamingXmlTv.read(bounded,channel.channelName,xmltvId,System.currentTimeMillis());
  }
  if(rows.isEmpty())throw new IOException("EPG_NO_MATCHING_CHANNEL_EVENTS");
  rows.sort(Comparator.comparingLong(a->a.start));
  return new Preview(channel.channelId,channel.channelName,label,rows);
 }
 private static byte[] fetchHttps(String value)throws Exception {
  if(value==null||value.length()>2048)throw new IOException("INVALID_HTTPS_XMLTV_URL");
  URI parsed=new URI(value.trim());
  String host=parsed.getHost();
  if(!"https".equalsIgnoreCase(parsed.getScheme())||host==null||
     host.equalsIgnoreCase("localhost")||host.endsWith(".local")||
     parsed.getUserInfo()!=null||parsed.getPort()!=-1&&parsed.getPort()!=443||
     parsed.getFragment()!=null||host.matches("[0-9.:]+"))
   throw new IOException("PUBLIC_HTTPS_URL_REQUIRED");
  // Prevent direct requests to private/link-local IP ranges via DNS.
  for(InetAddress addr:InetAddress.getAllByName(host)){
   if(addr.isAnyLocalAddress()||addr.isLoopbackAddress()||
      addr.isLinkLocalAddress()||addr.isSiteLocalAddress()||
      addr.isMulticastAddress())throw new IOException("PRIVATE_HOST_REJECTED");
  }
  HttpURLConnection connection=(HttpURLConnection)parsed.toURL().openConnection();
  connection.setInstanceFollowRedirects(false);
  connection.setConnectTimeout(8000);connection.setReadTimeout(15000);
  connection.setRequestProperty("Accept","application/xml,application/gzip,application/octet-stream");
  try{
   if(connection.getResponseCode()!=200)throw new IOException("XMLTV_HTTP_"+connection.getResponseCode());
   if(connection.getContentLengthLong()>12L*1024*1024)throw new IOException("XMLTV_FILE_TOO_LARGE");
   try(InputStream in=connection.getInputStream();
       ByteArrayOutputStream output=new ByteArrayOutputStream()){
    byte[] buf=new byte[16384];int n;
    while((n=in.read(buf))!=-1){
     if(output.size()+n>12L*1024*1024)throw new IOException("XMLTV_FILE_TOO_LARGE");
     output.write(buf,0,n);
    }
    if(output.size()<24)throw new IOException("XMLTV_EMPTY");
    return output.toByteArray();
   }
  }finally{connection.disconnect();}
 }
 static void save(Context c,Preview selected)throws Exception {
  if(selected==null||!validChannel(selected.physical,selected.channel)||
     selected.events.isEmpty())throw new IOException("EPG_NO_PREVIEW");
  long now=System.currentTimeMillis();
  JSONArray list=new JSONArray();
  for(NativeEpgEvents.Event event:selected.events){
   if(list.length()>=300)break;
   if(event.end<=now||event.start>now+72L*3600000)continue;
   list.put(new JSONObject().put("t",event.title).put("s",event.start)
       .put("e",event.end).put("i",event.image));
  }
  if(list.length()==0)throw new IOException("EPG_NO_CURRENT_OR_FUTURE_EVENTS");
  JSONObject obj=new JSONObject().put("v",1).put("physical",selected.physical)
   .put("channel",StreamingXmlTv.channelKey(selected.channel))
   .put("source",selected.source).put("saved",now).put("events",list);
  byte[] bytes=obj.toString().getBytes(StandardCharsets.UTF_8);
  if(bytes.length>300000)throw new IOException("EPG_PREVIEW_TOO_LARGE");
  AtomicFile atomic=new AtomicFile(file(c,selected.physical));
  FileOutputStream out=atomic.startWrite();
  try{out.write(bytes);atomic.finishWrite(out);}
  catch(Exception failed){atomic.failWrite(out);throw failed;}
  c.getSharedPreferences("smarthub",0).edit()
    .putLong("manual_epg_changed_at",now).putLong("epg_art_changed_at",now)
    .putString("manual_epg_last_import",selected.source).apply();
 }
 static List<NativeEpgEvents.Event> read(Context c,String id,String name,long now){
  if(!validChannel(id,name))return Collections.emptyList();
  File local=file(c,id);
  if(!local.isFile()||local.length()<30||local.length()>300000)return Collections.emptyList();
  try(InputStream stream=new FileInputStream(local)){
   JSONObject record=new JSONObject(PlaylistRepository.readBounded(stream));
   if(record.optInt("v")!=1||!id.equals(record.optString("physical"))||
      !StreamingXmlTv.channelKey(name).equals(record.optString("channel")))return Collections.emptyList();
   JSONArray rows=record.optJSONArray("events");if(rows==null)return Collections.emptyList();
   ArrayList<NativeEpgEvents.Event> result=new ArrayList<>();
   for(int i=0;i<Math.min(300,rows.length());i++){
    JSONObject row=rows.optJSONObject(i);if(row==null)continue;
    long start=row.optLong("s"),end=row.optLong("e");
    String title=row.optString("t");
    if(start>0&&start<now+72L*3600000&&end>now&&end>start&&
       ProgrammeTitle.available(title))
     result.add(new NativeEpgEvents.Event(title,start,end,row.optString("i","")));
   }
   return result;
  }catch(Exception ignored){return Collections.emptyList();}
 }
 static String source(Context c,String id){
  try(InputStream in=new FileInputStream(file(c,id))){
   JSONObject json=new JSONObject(PlaylistRepository.readBounded(in));
   return json.optString("source","");
  }catch(Exception ignored){return "";}
 }
 static void clear(Context c,String id){
  if(id==null||!id.startsWith("DVB:"))return;
  file(c,id).delete();
  c.getSharedPreferences("smarthub",0).edit()
   .putLong("manual_epg_changed_at",System.currentTimeMillis())
   .putLong("epg_art_changed_at",System.currentTimeMillis()).apply();
 }
 static NativeEpgEvents.Event now(List<NativeEpgEvents.Event> events,long when){
  return BroadcastEpgProvider.current(events,when);
 }
 static NativeEpgEvents.Event next(List<NativeEpgEvents.Event> events,long when){
  return BroadcastEpgProvider.next(events,when);
 }
 private static final class LimitStream extends FilterInputStream{
  private long left;
  LimitStream(InputStream in,long max){super(in);left=max;}
  public int read()throws IOException{
   if(left<=0)throw new IOException("XMLTV_UNPACK_LIMIT");
   int next=super.read();if(next>=0)left--;return next;
  }
  public int read(byte[] bytes,int off,int len)throws IOException{
   if(left<=0)throw new IOException("XMLTV_UNPACK_LIMIT");
   int n=super.read(bytes,off,(int)Math.min(len,left));if(n>0)left-=n;return n;
  }
 }
}
