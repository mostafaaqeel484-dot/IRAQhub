package com.smarthub.plus;
import android.content.*;import android.util.AtomicFile;import java.io.*;import java.net.*;import java.util.*;import java.util.zip.GZIPInputStream;
/** Public, compressed country guides; parsed one requested channel at a time. */
final class OnlineEpgGuide {
 private static final java.util.concurrent.ConcurrentHashMap<String,Object> DOWNLOAD_LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
 private static final class Parsed{final long stamp;final List<NativeEpgEvents.Event> events;Parsed(long stamp,List<NativeEpgEvents.Event> events){this.stamp=stamp;this.events=events;}}private final LinkedHashMap<String,Parsed> parsed=new LinkedHashMap<String,Parsed>(12,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Parsed> item){return size()>8;}};
 private final Context context;OnlineEpgGuide(Context c){context=c.getApplicationContext();}
 static String feed(String channel){String key=StreamingXmlTv.channelKey(channel);if(key.startsWith("mbc")||key.startsWith("dubai")||key.equals("samadubai")||key.startsWith("abudhabi")||key.startsWith("sharjah"))return "AE1";if(key.startsWith("aljazeera")||key.startsWith("الجزيرة"))return "ALJAZEERA1";if(key.startsWith("bein"))return "BEIN1";if(key.startsWith("osn"))return "SA1";return "";}
 List<NativeEpgEvents.Event> current(String channel,long now)throws Exception{return current(channel,"",now);}
 List<NativeEpgEvents.Event> current(String channel,String physicalId,long now)throws Exception{android.content.SharedPreferences prefs=context.getSharedPreferences("smarthub",0);String mapped=physicalId==null?"":prefs.getString("online_epg_channel_feed_id:"+physicalId,"");if(mapped.isEmpty())mapped=prefs.getString("online_epg_channel_feed:"+StreamingXmlTv.channelKey(channel),"");String feed=mapped.matches("[A-Z0-9_]{1,32}")?mapped:feed(channel);if(feed.isEmpty())return Collections.emptyList();File file=feedFile(context,feed);
 if(!file.isFile()||file.length()<20){
  EpgFeedPrefetchWorker.request(context,feed);
  return Collections.emptyList();
 }
 if(System.currentTimeMillis()-file.lastModified()>4L*3600000)
  EpgFeedPrefetchWorker.request(context,feed);
 String explicit=physicalId==null?"":context.getSharedPreferences("smarthub",0)
   .getString("satellite_xmltv_id:"+physicalId,"");
 String key=EpgQuickCache.key(feed,channel,physicalId==null?"":physicalId,explicit);
 Parsed inMemory=parsed.get(key);
 if(inMemory!=null&&inMemory.stamp==file.lastModified())return inMemory.events;
 List<NativeEpgEvents.Event> quick=EpgQuickCache.read(context,key,file,now);
 if(quick!=null){
  parsed.put(key,new Parsed(file.lastModified(),quick));
  return quick;
 }
 try(InputStream in=new GZIPInputStream(new FileInputStream(file))){
  List<NativeEpgEvents.Event> events=StreamingXmlTv.read(in,channel,explicit,now);
  parsed.put(key,new Parsed(file.lastModified(),events));
  EpgQuickCache.save(context,key,file,events,now);
  context.getSharedPreferences("smarthub",0).edit()
   .putString("online_epg_last_feed",feed)
   .putString("online_epg_last_channel",StreamingXmlTv.channelKey(channel))
   .putInt("online_epg_last_matches",events.size()).apply();
  return events;
 }catch(Exception malformed){
  context.getSharedPreferences("smarthub",0).edit()
   .putString("online_epg_parse_status",malformed.getClass().getSimpleName())
   .putString("online_epg_last_feed",feed).apply();
  EpgFeedPrefetchWorker.request(context,feed);
  return Collections.emptyList();
 }}
 /** Refresh known feeds without parsing the entire guide into memory. Per-channel SAX parsing remains lazy. */
 static int syncKnownFeeds(Context context)throws Exception{
  OnlineEpgGuide guide=new OnlineEpgGuide(context);int ok=0;List<String> failed=new ArrayList<>();
  List<String> listed=EpgShareFeedDirectory.discover(context);
  List<String> requested=EpgShareFeedDirectory.parse(EpgShareFeedDirectory.selected(context));
  if(requested.isEmpty())requested=Arrays.asList("AE1","ALJAZEERA1","BEIN1","SA1");
  int checked=0;
  for(String feed:requested){
   if(++checked>10)break;
   if(!listed.isEmpty()&&!listed.contains(feed)){
    failed.add(feed+":NOT_IN_INDEX");continue;
   }
   try{File file=guide.download(feed,true);try(InputStream in=new FileInputStream(file)){int a=in.read(),b=in.read();if(a!=0x1f||b!=0x8b)throw new IOException("EPG_GZIP_HEADER_INVALID");}String result=context.getApplicationContext().getSharedPreferences("smarthub",0).getString("online_epg_result:"+feed,"");if(!"STALE_CACHE".equals(result))ok++;else failed.add(feed+":STALE_CACHE");}
   catch(Exception error){failed.add(feed+":"+error.getClass().getSimpleName());}
  }
  SharedPreferences prefs=context.getApplicationContext().getSharedPreferences("smarthub",0);
  prefs.edit().putLong("online_epg_sync_at",System.currentTimeMillis()).putInt("online_epg_sync_success_count",ok).putString("online_epg_sync_failures",android.text.TextUtils.join(",",failed)).putString("online_epg_status",failed.isEmpty()?"EPG_FEEDS_REFRESHED":"EPG_PARTIAL_REFRESH").apply();
  if(ok==0)throw new IOException("All EPGShare feeds failed: "+android.text.TextUtils.join(",",failed));
  return ok;
 }
 File download(String feed,boolean force)throws Exception{
  if(!feed.matches("[A-Z0-9_]{1,32}"))throw new IOException("INVALID_EPG_FEED");
  synchronized(DOWNLOAD_LOCKS.computeIfAbsent(feed,k->new Object())){
   return downloadLocked(feed,force);
  }
 }
 private static File feedFile(Context context,String feed){
  File directory=new File(context.getFilesDir(),"online-epg");
  if(!directory.isDirectory())directory.mkdirs();
  File dest=new File(directory,feed+".xml.gz");
  if(!dest.isFile()){
   File older=new File(new File(context.getCacheDir(),"online-epg"),feed+".xml.gz");
   if(older.isFile()&&older.length()>=20&&older.length()<=10L*1024*1024)
    try(java.io.InputStream in=new FileInputStream(older);
        java.io.FileOutputStream out=new FileOutputStream(dest)){
     byte[] buffer=new byte[16384];int read;
     while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
     dest.setLastModified(older.lastModified());
    }catch(Exception ignored){dest.delete();}
  }
  return dest;
 }
 private File downloadLocked(String feed,boolean force)throws Exception{if(!feed.matches("[A-Z0-9_]{1,32}"))throw new IOException("INVALID_EPG_FEED");File dir=new File(context.getFilesDir(),"online-epg");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("EPG_CACHE_UNAVAILABLE");File file=new File(dir,feed+".xml.gz");long now=System.currentTimeMillis();if(!force&&file.isFile()&&now-file.lastModified()>=0&&now-file.lastModified()<6*3600000L)return file;SharedPreferences prefs=context.getSharedPreferences("smarthub",0);HttpURLConnection connection=(HttpURLConnection)new URL("https://epgshare01.online/epgshare01/epg_ripper_"+feed+".xml.gz").openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(12000);connection.setRequestProperty("User-Agent","IRAQhub/3.4 EPG reader");if(file.isFile()){String tag=prefs.getString("online_epg_etag:"+feed,"");if(!tag.isEmpty())connection.setRequestProperty("If-None-Match",tag);long modified=prefs.getLong("online_epg_modified:"+feed,0);if(modified>0)connection.setIfModifiedSince(modified);}try{int code=connection.getResponseCode();if(code==304&&file.isFile()){file.setLastModified(now);prefs.edit().putString("online_epg_result:"+feed,"NOT_MODIFIED").apply();return file;}if(code!=200)throw new IOException("EPG_HTTP_"+code);if(connection.getContentLengthLong()>10*1024*1024L)throw new IOException("EPG_DOWNLOAD_LIMIT");AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[16384];int n,count=0,first=-1,second=-1;while((n=in.read(buffer))>=0){if(n==0)continue;if(count==0)first=buffer[0]&255;if(count<=1&&count+n>1)second=buffer[1-count]&255;count+=n;if(count>10*1024*1024)throw new IOException("EPG_DOWNLOAD_LIMIT");out.write(buffer,0,n);}if(count<20)throw new IOException("EPG_EMPTY_DOWNLOAD");if(first!=0x1f||second!=0x8b)throw new IOException("EPG_GZIP_HEADER_INVALID");atomic.finishWrite(out);String tag=connection.getHeaderField("ETag");prefs.edit().putString("online_epg_etag:"+feed,tag==null?"":tag).putLong("online_epg_modified:"+feed,connection.getLastModified()).putLong("online_epg_checked_at",now).putInt("online_epg_download_bytes",count).putString("online_epg_result:"+feed,"UPDATED").apply();return file;}catch(Exception error){atomic.failWrite(out);throw error;}}catch(Exception unavailable){if(file.isFile()&&now-file.lastModified()<36*3600000L){prefs.edit().putString("online_epg_status","EPG_CACHE_UPDATE_UNAVAILABLE").putString("online_epg_result:"+feed,"STALE_CACHE").apply();return file;}throw unavailable;}finally{connection.disconnect();}}
}
