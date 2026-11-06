package com.smarthub.plus;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Disk cache of already verified XMLTV channel events keyed by exact feed and mapping. */
final class EpgQuickCache {
 private EpgQuickCache(){}
 static String key(String feed,String channel,String physical,String id)throws Exception{
  return SportsCache.key(feed+"|"+StreamingXmlTv.channelKey(channel)+"|"+physical+"|"+id);
 }
 private static File file(Context c,String key){
  File dir=new File(c.getCacheDir(),"epg-channel-quick");
  if(!dir.exists())dir.mkdirs();
  return new File(dir,key+".json");
 }
 static List<NativeEpgEvents.Event> read(Context c,String key,File feed,long now){
  File cached=file(c,key);
  if(!cached.isFile()||cached.length()<=20||cached.length()>300*1024)return null;
  try(InputStream input=new FileInputStream(cached)){
   JSONObject record=new JSONObject(PlaylistRepository.readBounded(input));
   if(record.optLong("feedStamp",-1)!=feed.lastModified()||
      record.optLong("feedSize",-1)!=feed.length()||
      now-record.optLong("savedAt",0)>48L*3600000||
      now<record.optLong("savedAt",0))return null;
   JSONArray array=record.optJSONArray("events");if(array==null)return null;
   List<NativeEpgEvents.Event> events=new ArrayList<>();
   for(int i=0;i<Math.min(300,array.length());i++){
    JSONObject row=array.optJSONObject(i);if(row==null)continue;
    long start=row.optLong("s"),end=row.optLong("e");
    String title=row.optString("t",""),image=row.optString("i","");
    if(start>0&&end>now&&end>start&&start<now+48L*3600000
      &&ProgrammeTitle.available(title))
      events.add(new NativeEpgEvents.Event(title,start,end,image));
   }
   return events;
  }catch(Exception ignored){return null;}
 }
 static void save(Context c,String key,File feed,List<NativeEpgEvents.Event> events,long now){
  try{
   JSONArray list=new JSONArray();
   for(NativeEpgEvents.Event event:events){
    if(list.length()>=300)break;
    list.put(new JSONObject().put("t",event.title).put("s",event.start)
      .put("e",event.end).put("i",event.image));
   }
   JSONObject record=new JSONObject().put("feedStamp",feed.lastModified())
     .put("feedSize",feed.length()).put("savedAt",now).put("events",list);
   byte[] bytes=record.toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>300*1024)return;
   File dir=file(c,key).getParentFile();
   AtomicFile target=new AtomicFile(file(c,key));
   FileOutputStream out=target.startWrite();
   try{out.write(bytes);target.finishWrite(out);}
   catch(Exception fail){target.failWrite(out);throw fail;}
   File[] files=dir.listFiles();
   if(files!=null&&files.length>100){
    Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
    for(int i=100;i<files.length;i++)files[i].delete();
   }
  }catch(Exception ignored){}
 }
}
