package com.smarthub.plus;

import android.content.Context;
import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only anime metadata. No account credentials, guessed cross-title images,
 * or scheduled lookups. AniDB identity is obtained through Kitsu mappings:
 * this does NOT impersonate a registered AniDB UDP/HTTP client. */
final class AnimeMetadataProvider {
 private static final ConcurrentHashMap<String,Long> retry=new ConcurrentHashMap<>();
 private AnimeMetadataProvider(){}
 static String normal(String text){
  if(text==null)return "";
  return ChannelAliasDatabase.normalize(text).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
 }
 static boolean exact(String requested,String candidate){
  String a=normal(requested),b=normal(candidate);
  return a.length()>=3&&a.equals(b);
 }
 private static boolean allowed(Context c,String id){
  return PosterSourceSwitches.enabled(c,id)&&!PlaybackResourceMode.active()&&
   !c.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false);
 }
 private static boolean reasonable(String title){
  return title!=null&&title.length()>=3&&title.length()<=100&&
   !title.matches("(?i)(?:test|service|program|no information|unknown)[ 0-9-]*");
 }
 static JSONObject resolve(Context c,String title){
  if(!reasonable(title))return null;
  if(!allowed(c,PosterSourceSwitches.ANILIST)&&!allowed(c,PosterSourceSwitches.KITSU))return null;
  String key=normal(title);
  long now=System.currentTimeMillis();
  Long recent=retry.get(key);
  if(recent!=null&&now<recent)return null;
  JSONObject result=null;
  if(allowed(c,PosterSourceSwitches.ANILIST))try{result=aniList(title);}catch(Exception ignored){}
  if(result==null&&allowed(c,PosterSourceSwitches.KITSU))try{result=kitsu(title,
     PosterSourceSwitches.enabled(c,PosterSourceSwitches.ANIDB));}catch(Exception ignored){}
  if(result==null)retry.put(key,now+60L*60*1000);
  else retry.remove(key);
  return result;
 }
 private static JSONObject json(DirectHttp.Reply result,int limit)throws Exception{
  if(result.code!=200||result.bytes.length<2||result.bytes.length>limit)throw new java.io.IOException("ANIME_SOURCE_UNAVAILABLE_"+result.code);
  return new JSONObject(new String(result.bytes,StandardCharsets.UTF_8));
 }
 static JSONObject aniList(String title)throws Exception{
  if(!reasonable(title))return null;
  JSONObject variables=new JSONObject().put("search",title);
  String query="query($search:String){Page(page:1,perPage:6){media(search:$search,type:ANIME){id title{english romaji native}synonyms description(asHtml:false)startDate{year}coverImage{large extraLarge}genres}}}";
  JSONObject body=new JSONObject().put("query",query).put("variables",variables);
  Map<String,String> headers=new LinkedHashMap<>();
  headers.put("Accept","application/json");
  DirectHttp.Reply reply=DirectHttp.request("https://graphql.anilist.co",headers,body.toString(),260000,null);
  JSONObject root=json(reply,260000);
  JSONObject data=root.optJSONObject("data");
  JSONObject page=data==null?null:data.optJSONObject("Page");
  JSONArray rows=page==null?null:page.optJSONArray("media");
  if(rows==null)return null;
  for(int i=0;i<rows.length();i++){
   JSONObject entry=rows.optJSONObject(i);if(entry==null)continue;
   JSONObject names=entry.optJSONObject("title");
   String matched=match(title,names,"english","romaji","native");
   JSONArray aliases=entry.optJSONArray("synonyms");
   if(matched.isEmpty()&&aliases!=null)for(int a=0;a<Math.min(aliases.length(),20);a++)
     if(exact(title,aliases.optString(a))){matched=aliases.optString(a);break;}
   if(matched.isEmpty())continue;
   JSONObject images=entry.optJSONObject("coverImage");
   String image=images==null?"":images.optString("extraLarge",images.optString("large",""));
   if(!safeArt(image,"s4.anilist.co","s3.anilist.co"))continue;
   JSONObject date=entry.optJSONObject("startDate");
   return result(entry.optInt("id"),"AniList",matched,
      date==null?"":Integer.toString(date.optInt("year",0)),
      entry.optString("description"),image,entry.optJSONArray("genres"));
  }
  return null;
 }
 static JSONObject kitsu(String title,boolean withAniDb)throws Exception{
  if(!reasonable(title))return null;
  String url="https://kitsu.io/api/edge/anime?filter%5Btext%5D="+
    URLEncoder.encode(title,"UTF-8")+"&page%5Blimit%5D=8";
  Map<String,String> h=Collections.singletonMap("Accept","application/vnd.api+json");
  JSONObject root=json(DirectHttp.request(url,h,null,260000,null),260000);
  JSONArray entries=root.optJSONArray("data");if(entries==null)return null;
  for(int i=0;i<entries.length();i++){
   JSONObject e=entries.optJSONObject(i),attrs=e==null?null:e.optJSONObject("attributes");
   if(attrs==null)continue;
   JSONObject titles=attrs.optJSONObject("titles");
   String matched=match(title,titles,"en","en_jp","ja_jp");
   if(matched.isEmpty()&&exact(title,attrs.optString("canonicalTitle")))
    matched=attrs.optString("canonicalTitle");
   if(matched.isEmpty())continue;
   JSONObject poster=attrs.optJSONObject("posterImage");
   String image=poster==null?"":poster.optString("large",poster.optString("medium",""));
   if(!safeArt(image,"media.kitsu.app","media.kitsu.io","kitsu.io"))continue;
   int id=-1;try{id=Integer.parseInt(e.optString("id",""));}catch(Exception ignored){}
   JSONObject out=result(id,"Kitsu",matched,attrs.optString("startDate").replaceAll("^(\\d{4}).*","$1"),
     attrs.optString("synopsis"),image,null);
   if(withAniDb&&id>0)try{
    String aniDb=aniDbId(id);
    if(!aniDb.isEmpty())out.put("anidbId",aniDb);
   }catch(Exception ignored){}
   return out;
  }
  return null;
 }
 static String aniDbId(int kitsuId)throws Exception{
  if(kitsuId<=0)return "";
  JSONObject root=json(DirectHttp.request("https://kitsu.io/api/edge/anime/"+
    kitsuId+"/mappings",Collections.singletonMap("Accept","application/vnd.api+json"),
    null,150000,null),150000);
  JSONArray entries=root.optJSONArray("data");if(entries==null)return "";
  for(int i=0;i<entries.length();i++){
   JSONObject row=entries.optJSONObject(i),attr=row==null?null:row.optJSONObject("attributes");
   if(attr!=null&&"anidb".equalsIgnoreCase(attr.optString("externalSite"))){
    String id=attr.optString("externalId");
    if(id.matches("[1-9][0-9]{0,8}"))return id;
   }
  }
  return "";
 }
 private static String match(String request,JSONObject names,String...keys){
  if(names==null)return "";
  for(String key:keys)if(exact(request,names.optString(key)))return names.optString(key);
  return "";
 }
 static boolean safeArt(String url,String...hosts){
  if(url==null||!url.startsWith("https://"))return false;
  try{
   java.net.URI u=new java.net.URI(url);
   if(u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1)return false;
   for(String host:hosts)if(host.equalsIgnoreCase(u.getHost()))return true;
  }catch(Exception ignored){}
  return false;
 }
 private static JSONObject result(int id,String source,String title,String year,String description,
                                  String poster,JSONArray genres)throws Exception{
  JSONObject out=new JSONObject().put("status","matched")
   .put("provider",source).put("id",source.toLowerCase(Locale.ROOT)+":"+id)
   .put("title",title).put("type","series").put("poster",poster)
   .put("year",year!=null&&year.matches("[12][0-9]{3}")?year:"")
   .put("description",description==null?"":description.replaceAll("<[^>]*>","").trim())
   .put("source",source+" · exact title");
  if(genres!=null)out.put("genres",genres);
  return out;
 }
}
