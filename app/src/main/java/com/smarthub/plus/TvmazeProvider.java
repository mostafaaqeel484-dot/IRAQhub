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

 // TVmaze search endpoint returns a JSON array. The cache wrapper is an object
 // whose "data" member contains that array.
 private JSONArray get(String title)throws Exception{
  String key=Integer.toHexString(title.toLowerCase(Locale.ROOT).hashCode());java.io.File dir=new java.io.File(context.getCacheDir(),"tvmaze");if(!dir.exists())dir.mkdirs();java.io.File cache=new java.io.File(dir,key+".json");
  try{String body=HubHttp.get("https://api.tvmaze.com/search/shows?q="+enc(title),512*1024);JSONObject wrapper=new JSONObject().put("at",System.currentTimeMillis()).put("data",new JSONArray(body));try(java.io.FileOutputStream out=new java.io.FileOutputStream(cache)){out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));}return wrapper.getJSONArray("data");}
  catch(Exception e){if(cache.isFile()&&System.currentTimeMillis()-cache.lastModified()<7L*24*3600000L)try(java.io.FileInputStream in=new java.io.FileInputStream(cache)){return new JSONObject(PlaylistRepository.readBounded(in)).getJSONArray("data");}catch(Exception ignored){}throw e;}
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
  JSONArray genres=show.optJSONArray("genres");if(genres==null)genres=new JSONArray();
  JSONObject ex=show.optJSONObject("externals");String imdb=ex==null?"":ex.optString("imdb");
  return new JSONObject().put("status","matched").put("id",show.optString("id")).put("type","series").put("imdbId",imdb).put("title",show.optString("name")).put("year",year(show.optString("premiered"))).put("poster",poster).put("background","").put("description",clean(show.optString("summary"))).put("runtime",show.optString("runtime")).put("rating",show.optJSONObject("rating")==null?"":show.optJSONObject("rating").optString("average")).put("genres",genres).put("cast",new JSONArray()).put("directors",new JSONArray()).put("source","https://www.tvmaze.com/shows/"+show.optString("id")).put("provider","TVmaze").put("cached",false).put("fetchedAt",System.currentTimeMillis());
 }
}
