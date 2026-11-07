package com.smarthub.plus;
import android.content.Context;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.json.*;
/** Bounded on-demand cache. Stale responses retain their real fetch time and are marked. */
final class SportsCache {
 private final File directory;private static long lastCall;private static final Map<String,Long> blocked=new HashMap<>();
 private static final ArrayDeque<Long> minuteRequests=new ArrayDeque<>();
 SportsCache(Context c){directory=new File(c.getCacheDir(),"sports-data");directory.mkdirs();}
 static String key(String raw)throws Exception{StringBuilder b=new StringBuilder();for(byte n:MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)))b.append(String.format(Locale.ROOT,"%02x",n&255));return b.toString();}
 synchronized JSONObject get(String url,long ttl)throws Exception{File f=new File(directory,key(url)+".json");JSONObject saved=null;if(f.isFile())try(InputStream in=new FileInputStream(f)){saved=new JSONObject(PlaylistRepository.readBounded(in));}catch(Exception ignored){}long now=System.currentTimeMillis();if(saved!=null&&now-saved.optLong("at")<ttl)return saved.put("stale",false);try{pace(url);DirectHttp.Reply reply=DirectHttp.request(url,Collections.emptyMap(),null,8*1024*1024,null);if(reply.code==429){synchronized(SportsCache.class){blocked.put(new java.net.URI(url).getHost(),System.currentTimeMillis()+60000);}throw new IOException("Source rate limit (429)");}if(reply.code!=200)throw new IOException("Source HTTP "+reply.code);Object parsed=new JSONTokener(new String(reply.bytes,StandardCharsets.UTF_8)).nextValue();JSONObject data=parsed instanceof JSONArray?new JSONObject().put("rows",parsed):(JSONObject)parsed;JSONObject out=new JSONObject().put("at",System.currentTimeMillis()).put("data",data).put("stale",false);try(FileOutputStream stream=new FileOutputStream(f)){stream.write(out.toString().getBytes(StandardCharsets.UTF_8));}trim();return out;}catch(Exception e){if(saved!=null)return saved.put("stale",true);throw e;}}
 private static synchronized void pace(String url)throws Exception{
  long now=System.currentTimeMillis();
  if(now<blocked.getOrDefault(new java.net.URI(url).getHost(),0L))
    throw new IOException("Source requested backoff; retry later");
  // Public v1/123: at most 30 calls in any rolling 60 seconds from this app process.
  while(!minuteRequests.isEmpty()&&now-minuteRequests.peekFirst()>=60000)
   minuteRequests.removeFirst();
  long wait=Math.max(0,2200-(now-lastCall));
  if(minuteRequests.size()>=30)
   wait=Math.max(wait,60000-(now-minuteRequests.peekFirst()));
  if(wait>0)Thread.sleep(wait);
  now=System.currentTimeMillis();
  while(!minuteRequests.isEmpty()&&now-minuteRequests.peekFirst()>=60000)
   minuteRequests.removeFirst();
  if(minuteRequests.size()>=30)throw new IOException("SPORTSDB_FREE_30_PER_MIN");
  minuteRequests.addLast(now);
  lastCall=now;
 }
 private void trim(){File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long size=0;for(int i=0;i<files.length;i++){size+=files[i].length();if(i>=160||size>24*1024*1024)files[i].delete();}}
}
