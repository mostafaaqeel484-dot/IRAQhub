package com.smarthub.plus;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.json.*;
/** Optional exact IMDb poster sources. No name-derived unverified posters. */
final class VerifiedPosterFallback {
 private static final ConcurrentHashMap<String,Long> retry=new ConcurrentHashMap<>();
 private VerifiedPosterFallback(){}
 static String find(Context context,String imdb,String exactTitle,String releaseYear){
  if(imdb==null||!imdb.matches("tt[0-9]{7,10}")||
      PlaybackResourceMode.active()||
      context.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false))return "";
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  long now=System.currentTimeMillis();
  boolean useOpdb=PosterSourceSwitches.enabled(context,PosterSourceSwitches.OPDB);
  boolean useRpdb=PosterSourceSwitches.enabled(context,PosterSourceSwitches.RPDB);
  boolean useArchive=PosterSourceSwitches.enabled(context,PosterSourceSwitches.ARCHIVE);
  boolean useOmdb=PosterSourceSwitches.enabled(context,PosterSourceSwitches.OMDB);
  if(!useOpdb&&!useRpdb&&!useArchive&&!useOmdb)return "";
  // Re-enabling a source must bypass a failure cooldown for a different source set.
  String retryKey=imdb+"|"+useOpdb+"|"+useRpdb+"|"+useArchive+"|"+useOmdb+"|"+p.getLong("poster_sources_changed_at",0);
  Long next=retry.get(retryKey);if(next!=null&&now<next)return "";
  // The documented public demo keys have shared limits: failures back off.
  for(String id:new String[]{"opdb","rpdb","omdb"}){
   if(("opdb".equals(id)&&!useOpdb)||("rpdb".equals(id)&&!useRpdb)||("omdb".equals(id)&&!useOmdb))continue;
   String secret=PosterApiSecrets.get(context,id);
   if(!secret.isEmpty()){
    String local=PrivatePosterCache.fetch(context,id,imdb,secret);
    if(!local.isEmpty())return local;
   }
  }
  String opdb=PosterApiSecrets.configured(context,"opdb")?"":"t0-free-rpdb";
  if(!opdb.matches("[A-Za-z0-9_-]{3,64}"))opdb="";
  String[] urls={
   (!useOpdb||opdb.isEmpty())?"":"https://openposterdb.com/"+opdb+"/imdb/poster-default/"+imdb+".jpg",
   useRpdb?"https://api.ratingposterdb.com/t0-free-rpdb/imdb/poster-default/"+imdb+".jpg?fallback=true":"",
   useRpdb?"https://api.ratingposterdb.com/t0-free-rpdb-boxes/imdb/poster-default/"+imdb+".jpg?fallback=true":""
  };
  for(String url:urls)if(!url.isEmpty())try{
   if(valid(ArtworkCache.fetch(context,new URL(url),4*1024*1024)))return url;
  }catch(Exception ignored){}
  // Archive is licensed-image-only, exact-title and optionally exact-year.
  // Archive is not a movie-poster database: incorrect matches are rejected.
  if(useArchive){
   String archive=archive(context,exactTitle,releaseYear);
   if(!archive.isEmpty())return archive;
  }
  retry.put(retryKey,now+60L*60L*1000L);
  return "";
 }
 static boolean valid(byte[] data){
  if(!ArtworkCache.validImage(data))return false;
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(data,0,data.length,bounds);
  return bounds.outWidth>=120&&bounds.outHeight>=120&&
    bounds.outWidth<=8000&&bounds.outHeight<=8000;
 }
 static boolean licensed(String value){
  if(value==null)return false;
  String text=value.toLowerCase(Locale.ROOT).replace("https://","http://");
  return text.startsWith("http://creativecommons.org/licenses/by/")||
    text.startsWith("http://creativecommons.org/licenses/by-sa/")||
    text.startsWith("http://creativecommons.org/publicdomain/zero/");
 }
 static String archive(Context c,String title,String year){
  if(title==null||title.trim().length()<5||title.length()>90||
     !title.matches("[\\p{L}\\p{N} .,'&!:-]+"))return "";
  try{
   String q="title:\""+title.trim().replace("\"","")+"\" AND mediatype:image";
   String addr="https://archive.org/advancedsearch.php?q="+
     URLEncoder.encode(q,"UTF-8")+
     "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year"+
     "&fl%5B%5D=licenseurl&rows=5&page=1&output=json";
   DirectHttp.Reply response=DirectHttp.request(addr,Collections.emptyMap(),null,160000,null);
   if(response.code!=200)return "";
   JSONObject object=new JSONObject(new String(response.bytes,StandardCharsets.UTF_8));
   JSONArray docs=object.optJSONObject("response")==null?null:
     object.optJSONObject("response").optJSONArray("docs");
   if(docs==null)return "";
   for(int i=0;i<Math.min(docs.length(),5);i++){
    JSONObject item=docs.optJSONObject(i);if(item==null)continue;
    String id=item.optString("identifier","");
    String candidate=item.optString("title","");
    String when=item.optString("year","");
    String license=item.optString("licenseurl","");
    if(!id.matches("[A-Za-z0-9_.-]{4,120}")||
       !PlaylistRepository.normalize(candidate).equals(
         PlaylistRepository.normalize(title))||!licensed(license))continue;
    if(year!=null&&year.matches("[12][0-9]{3}")&&
       !year.equals(when))continue;
    String url="https://archive.org/services/img/"+id;
    if(valid(ArtworkCache.fetch(c,new URL(url),4*1024*1024)))return url;
   }
  }catch(Exception ignored){}
  return "";
 }
}
