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
  if(!PosterSourceSwitches.enabled(context,PosterSourceSwitches.TVMAZE))
   throw new IOException("TVMAZE_DISABLED");
  String key=Integer.toHexString(title.toLowerCase(Locale.ROOT).hashCode());java.io.File dir=new java.io.File(context.getCacheDir(),"tvmaze");if(!dir.exists())dir.mkdirs();java.io.File cache=new java.io.File(dir,key+".json");
  // Fix 12: cache first. Do not issue a new show search for every InfoBar
  // open, and never fetch on the software 4:2:2 decode path.
  JSONObject remembered=null;
  if(cache.isFile()&&cache.length()>0&&cache.length()<512*1024)
   try(java.io.FileInputStream in=new java.io.FileInputStream(cache)){
    remembered=new JSONObject(PlaylistRepository.readBounded(in));
    if(System.currentTimeMillis()-remembered.optLong("at")<12L*3600000L)
     return remembered.getJSONArray("data");
   }catch(Exception invalid){remembered=null;}
  if(PlaybackResourceMode.active()||context.getSharedPreferences("smarthub",0)
     .getBoolean("software_dvb_422_active",false)){
   if(remembered!=null)return remembered.getJSONArray("data");
   throw new IOException("TVMAZE_DEFERRED_VIDEO_PRIORITY");
  }
  try{String body=HubHttp.get("https://api.tvmaze.com/search/shows?q="+enc(title),512*1024);JSONObject wrapper=new JSONObject().put("at",System.currentTimeMillis()).put("data",new JSONArray(body));try(java.io.FileOutputStream out=new java.io.FileOutputStream(cache)){out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));}return wrapper.getJSONArray("data");}
  catch(Exception e){if(cache.isFile()&&System.currentTimeMillis()-cache.lastModified()<7L*24*3600000L)try(java.io.FileInputStream in=new java.io.FileInputStream(cache)){return new JSONObject(PlaylistRepository.readBounded(in)).getJSONArray("data");}catch(Exception ignored){}throw e;}
 }
 private String episodePoster(int showId,int season,int episode)throws Exception{
  if(!PosterSourceSwitches.enabled(context,PosterSourceSwitches.TVMAZE))
   throw new IOException("TVMAZE_DISABLED");
  if(showId<=0||season<=0||episode<=0||season>99||episode>999)return "";
  java.io.File dir=new java.io.File(context.getCacheDir(),"tvmaze-episode");
  dir.mkdirs();
  java.io.File file=new java.io.File(dir,showId+"-"+season+"-"+episode+".json");
  JSONObject data=null;long now=System.currentTimeMillis();
  if(file.isFile()&&file.length()>=2&&file.length()<256*1024
      &&now-file.lastModified()<24L*3600000L)
   try(java.io.FileInputStream in=new java.io.FileInputStream(file)){
    data=new JSONObject(PlaylistRepository.readBounded(in));
   }catch(Exception ignored){}
  if(data==null){
   if(PlaybackResourceMode.active()||context.getSharedPreferences("smarthub",0)
     .getBoolean("software_dvb_422_active",false))return "";
   String uri="https://api.tvmaze.com/shows/"+showId+
     "/episodebynumber?season="+season+"&number="+episode;
   data=new JSONObject(HubHttp.get(uri,256*1024));
   try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)){
    output.write(data.toString().getBytes(StandardCharsets.UTF_8));
   }
  }
  JSONObject image=data.optJSONObject("image");
  String address=image==null?"":image.optString("original",
    image.optString("medium",""));
  try{
   java.net.URI uri=new java.net.URI(address);
   return "https".equalsIgnoreCase(uri.getScheme())&&
     "static.tvmaze.com".equalsIgnoreCase(uri.getHost())&&
     uri.getUserInfo()==null&&uri.getFragment()==null?address:"";
  }catch(Exception invalid){return "";}
 }
 JSONObject resolve(String raw,String imdbId)throws Exception{
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
  String host="";try{host=new java.net.URI(poster).getHost();}catch(Exception ignored){}
  if(!"static.tvmaze.com".equals(host)||!poster.startsWith("https://"))poster="";
  // Exact SxxExx episode artwork when the programme title specifies both.
  // Never guess the episode from air date or substitute an unrelated show.
  if(wanted.season>0&&wanted.episode>0&&show.optInt("id")>0){
   try{
    String episodeImage=episodePoster(show.optInt("id"),wanted.season,wanted.episode);
    if(!episodeImage.isEmpty())poster=episodeImage;
   }catch(Exception unavailable){/* The verified series poster remains valid. */}
  }
  JSONArray genres=show.optJSONArray("genres");if(genres==null)genres=new JSONArray();
  JSONObject ex=show.optJSONObject("externals");String imdb=ex==null?"":ex.optString("imdb");
  return new JSONObject().put("status","matched").put("id",show.optString("id")).put("type","series").put("imdbId",imdb).put("title",show.optString("name")).put("year",year(show.optString("premiered"))).put("poster",poster).put("background","").put("description",clean(show.optString("summary"))).put("runtime",show.optString("runtime")).put("rating",show.optJSONObject("rating")==null?"":show.optJSONObject("rating").optString("average")).put("genres",genres).put("cast",new JSONArray()).put("directors",new JSONArray()).put("source","https://www.tvmaze.com/shows/"+show.optString("id")).put("provider","TVmaze").put("cached",false).put("fetchedAt",System.currentTimeMillis());
 }
}
