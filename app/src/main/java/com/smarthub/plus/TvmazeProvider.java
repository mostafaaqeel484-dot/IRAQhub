package com.smarthub.plus;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** TVmaze fallback for series only. Ambiguous or weak title matches are rejected. */
final class TvmazeProvider {
 private final Context context;
 TvmazeProvider(Context c){context=c.getApplicationContext();}
 private static String norm(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ");}
 private static int year(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:19|20)[0-9]{2}").matcher(s==null?"":s);return m.find()?Integer.parseInt(m.group()):0;}
 private static String clean(String s){return s==null?"":s.replaceAll("(?i)<br\\s*/?>","\\n").replaceAll("<[^>]+>"," ").replace("&amp;","&").replace("&quot;","\\\"").replace("&#39;","'").replaceAll("\\s+"," ").trim();}
 private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
 private JSONArray get(String title)throws Exception{
  String key=SportsCache.key("tvmaze:"+title.toLowerCase(Locale.ROOT));
  java.io.File dir=new java.io.File(context.getCacheDir(),"tvmaze");dir.mkdirs();
  java.io.File cache=new java.io.File(dir,key+".json");
  JSONObject saved=null;
  if(cache.isFile())try(java.io.FileInputStream in=new java.io.FileInputStream(cache)){
   saved=new JSONObject(PlaylistRepository.readBounded(in));
  }catch(Exception ignored){}
  long now=System.currentTimeMillis();
  // Search names do not change every time INFO is pressed.
  if(saved!=null&&now-saved.optLong("at")<12L*3600000L)return saved.getJSONArray("data");
  boolean defer=PlaybackResourceMode.active()||
   context.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false)||
   now<context.getSharedPreferences("smarthub",0).getLong("tvmaze_retry_at",0);
  if(defer){
   if(saved!=null&&now-saved.optLong("at")<7L*86400000L)return saved.getJSONArray("data");
   throw new IOException("TVMAZE_DEFERRED_OR_RATE_LIMITED");
  }
  try{
   String body=HubHttp.get("https://api.tvmaze.com/search/shows?q="+enc(title),512*1024);
   JSONObject result=new JSONObject().put("at",now).put("data",new JSONArray(body));
   android.util.AtomicFile safe=new android.util.AtomicFile(cache);
   java.io.FileOutputStream out=safe.startWrite();
   try{out.write(result.toString().getBytes(StandardCharsets.UTF_8));safe.finishWrite(out);}
   catch(Exception e){safe.failWrite(out);throw e;}
   return result.getJSONArray("data");
  }catch(Exception e){
   if(e.getMessage()!=null&&e.getMessage().contains("429"))
    context.getSharedPreferences("smarthub",0).edit()
     .putLong("tvmaze_retry_at",System.currentTimeMillis()+60000L).apply();
   if(saved!=null&&now-saved.optLong("at")<7L*86400000L)return saved.getJSONArray("data");
   throw e;
  }
 }
 /** Only numbered SxxExx can select episode-specific landscape art. */
 private String episodePoster(JSONObject show,BroadcastTitle wanted){
  if(wanted.season<=0||wanted.episode<=0)return "";
  String id=show.optString("id");
  if(!id.matches("[1-9][0-9]{0,9}"))return "";
  try{
   java.io.File dir=new java.io.File(context.getCacheDir(),"tvmaze-episodes");dir.mkdirs();
   java.io.File file=new java.io.File(dir,id+"-"+wanted.season+"-"+wanted.episode+".json");
   JSONObject episode=null;
   if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<86400000L)
    try(java.io.FileInputStream in=new java.io.FileInputStream(file)){
     episode=new JSONObject(PlaylistRepository.readBounded(in));
    }catch(Exception ignored){}
   if(episode==null){
    if(PlaybackResourceMode.active()||
       context.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false)||
       System.currentTimeMillis()<context.getSharedPreferences("smarthub",0)
        .getLong("tvmaze_retry_at",0))return "";
    String url="https://api.tvmaze.com/shows/"+id+"/episodebynumber?season="+
      wanted.season+"&number="+wanted.episode;
    episode=new JSONObject(HubHttp.get(url,256*1024));
    if(episode.optInt("season")!=wanted.season||
       episode.optInt("number")!=wanted.episode)return "";
    android.util.AtomicFile safe=new android.util.AtomicFile(file);
    java.io.FileOutputStream out=safe.startWrite();
    try{out.write(episode.toString().getBytes(StandardCharsets.UTF_8));safe.finishWrite(out);}
    catch(Exception e){safe.failWrite(out);throw e;}
   }
   JSONObject picture=episode.optJSONObject("image");
   return image(picture==null?"":picture.optString("medium",picture.optString("original")));
  }catch(Exception e){
   if(e.getMessage()!=null&&e.getMessage().contains("429"))
    context.getSharedPreferences("smarthub",0).edit()
     .putLong("tvmaze_retry_at",System.currentTimeMillis()+60000L).apply();
   return "";
  }
 }
 static String image(String source){
  try{
   java.net.URI uri=new java.net.URI(source);
   return "https".equals(uri.getScheme())&&"static.tvmaze.com".equals(uri.getHost())&&
    uri.getRawUserInfo()==null&&uri.getRawFragment()==null?source:"";
  }catch(Exception ignored){return "";}
 }

 JSONObject resolve(String raw,String imdbId)throws Exception{
  if(!context.getSharedPreferences("smarthub",0).getBoolean("artwork_tvmaze_enabled",true))
   throw new IOException("TVMAZE_DISABLED");
  BroadcastTitle wanted=BroadcastTitle.parse(raw);JSONArray rows=get(wanted.title);List<JSONObject> exact=new ArrayList<>();
  for(int i=0;i<Math.min(20,rows.length());i++){
   JSONObject hit=rows.optJSONObject(i);if(hit==null)continue;JSONObject show=hit.optJSONObject("show");if(show==null)continue;
   String name=show.optString("name");if(!norm(name).equals(norm(wanted.title)))continue;
   int y=year(show.optString("premiered"));if(wanted.year>0&&y>0&&wanted.year!=y)continue;
   JSONObject ex=show.optJSONObject("externals");String ext=ex==null?"":ex.optString("imdb");
   if(!imdbId.isEmpty()&&!imdbId.equals(ext))continue;
   exact.add(show);
  }
  if(exact.size()!=1)return new JSONObject().put("status",exact.isEmpty()?"not_found":"ambiguous");
  JSONObject show=exact.get(0),image=show.optJSONObject("image");String poster=image==null?"":image.optString("original");if(poster.isEmpty()&&image!=null)poster=image.optString("medium");
  poster=image(poster);
  String episode=episodePoster(show,wanted);
  if(!episode.isEmpty())poster=episode;
  JSONArray genres=show.optJSONArray("genres");if(genres==null)genres=new JSONArray();
  JSONObject ex=show.optJSONObject("externals");String imdb=ex==null?"":ex.optString("imdb");
  return new JSONObject().put("status","matched").put("id",show.optString("id")).put("type","series").put("imdbId",imdb).put("title",show.optString("name")).put("year",year(show.optString("premiered"))).put("poster",poster).put("background","").put("description",clean(show.optString("summary"))).put("runtime",show.optString("runtime")).put("rating",show.optJSONObject("rating")==null?"":show.optJSONObject("rating").optString("average")).put("genres",genres).put("cast",new JSONArray()).put("directors",new JSONArray()).put("source","https://www.tvmaze.com/shows/"+show.optString("id")).put("provider","TVmaze").put("cached",false).put("fetchedAt",System.currentTimeMillis());
 }
}
