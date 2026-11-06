package com.smarthub.plus;
import android.content.Context;
import android.graphics.*;
import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;
/** Replaceable online metadata/artwork provider; no subtitle credentials are embedded. */
final class OnlineProgramme {
 final MediaRepository.Item item;final Bitmap poster;
 static final class Candidate {final String id,type,title,year;Candidate(JSONObject j){id=j.optString("id");type=j.optString("type");title=j.optString("title");year=j.optString("year");}}
 static final class Choices extends IOException {final List<Candidate> values=new ArrayList<>();Choices(JSONArray rows){super("Several programme editions need confirmation");if(rows!=null)for(int i=0;i<Math.min(20,rows.length());i++){JSONObject j=rows.optJSONObject(i);if(j!=null&&j.optString("id").matches("(tt[0-9]{7,10}|tmdb:[0-9]+)"))values.add(new Candidate(j));}}}
 private OnlineProgramme(MediaRepository.Item i,Bitmap p){item=i;poster=p;}
 static OnlineProgramme fetch(Context context,String raw)throws Exception{return load(context,SatelliteState.read(context),raw,"","","");}
 static OnlineProgramme fetch(Context context,SatelliteState event,String raw)throws Exception{return load(context,event,raw,"","","");}
 static OnlineProgramme fetch(Context context,String raw,String imdb)throws Exception{return load(context,SatelliteState.read(context),raw,"","",imdb);}
 static OnlineProgramme selected(Context context,String raw,Candidate candidate)throws Exception{return load(context,SatelliteState.read(context),raw,candidate.id,candidate.type,"");}
 static OnlineProgramme selected(Context context,SatelliteState event,String raw,Candidate candidate)throws Exception{return load(context,event,raw,candidate.id,candidate.type,"");}
 private static OnlineProgramme load(Context context,SatelliteState event,String raw,String id,String type,String imdb)throws Exception{
  if(event==null)event=SatelliteState.read(context);
  long start=raw.equals(event.title)?event.startMs:raw.equals(event.nextTitle)?event.nextStartMs:0,
       end=raw.equals(event.title)?event.endMs:raw.equals(event.nextTitle)?event.nextEndMs:0;
  double duration=start>0&&end>start?(end-start)/60000.0:0;
  String tmdbKey="";
  try{tmdbKey=ProviderSecrets.read(context).tmdb;}catch(Exception unavailable){/* Another poster source remains usable. */}
  if(id.isEmpty()&&imdb.isEmpty()&&!tmdbKey.isEmpty()){
   JSONObject remembered=PosterSelection.read(context,event,raw);
   id=remembered.optString("id");type=remembered.optString("type");
  }
  JSONObject j=new JSONObject().put("status","not_found");
  // A failed TMDB call is not a reason to disable BetterPosters and TVmaze.
  if(!tmdbKey.isEmpty()&&(id.isEmpty()||id.startsWith("tmdb:")))try{
   j=new TmdbProvider(context,tmdbKey).resolve(raw,id,type,imdb,duration);
  }catch(Exception unavailable){/* Try independent providers. */}
  if(!"matched".equals(j.optString("status")))try{
   JSONObject other=new BetterPostersProvider(context).resolve(raw,
      id,type,imdb,duration);
   if("matched".equals(other.optString("status")))j=other;
  }catch(Exception unavailable){/* Keep any existing verified candidate. */}
  if(!"matched".equals(j.optString("status")))try{
   JSONObject other=new TvmazeProvider(context).resolve(raw,imdb);
   if("matched".equals(other.optString("status")))j=other;
  }catch(Exception unavailable){/* Preserve verified EPG artwork. */}
  if(!"matched".equals(j.optString("status")))return epg(context,event,raw);
  PosterSelection.save(context,event,raw,j);MediaRepository.Item item=new MediaRepository.Item();item.imdbId=j.optString("imdbId");if(!item.imdbId.isEmpty()&&!item.imdbId.matches("tt[0-9]{7,10}"))return null;boolean tmdb="TMDB".equals(j.optString("provider"));boolean better="BetterPosters".equals(j.optString("provider"));boolean tvmaze="TVmaze".equals(j.optString("provider"));item.id=tmdb?(item.imdbId.isEmpty()?j.optString("id"):"tmdb:"+item.imdbId):(better?"betterposters:":tvmaze?"tvmaze:":"subsource:")+(item.imdbId.isEmpty()?j.optString("id"):item.imdbId);item.title=j.optString("title");item.release=j.optString("year");item.kind=("series".equals(j.optString("type"))||"tvseries".equals(j.optString("type")))?"series":"film";item.imageKind="poster";item.imagePage=j.optString("source");item.imageLicense=(tmdb?"TMDB":better?"BetterPosters":tvmaze?"TVmaze":"Subsource")+" · حقوق الصور لأصحابها";item.imageCredit=tmdb?"TMDB":better?"BetterPosters / TMDB":tvmaze?"TVmaze":"Subsource";item.background=j.optString("background");item.people=j.optJSONArray("people")==null?new JSONArray():j.optJSONArray("people");item.summary=j.optString("description");item.runtime=j.optString("runtime");item.cast=join(j.optJSONArray("cast"));item.directors=join(j.optJSONArray("directors"));item.genres=join(j.optJSONArray("genres"));item.ratings=j.optString("rating");item.cached=j.optBoolean("cached");item.fetchedAt=j.optLong("fetchedAt",System.currentTimeMillis());
  String address=j.optString("poster");Bitmap bitmap=posterBitmap(context,address);
  if(bitmap==null&&tmdb)try{
   // Reuse verified TMDB/IMDb identity; never pick a different named movie.
   String verified=item.imdbId;
   JSONObject betterArt=new BetterPostersProvider(context).resolve(raw,
     verified.isEmpty()?j.optString("id"):"",j.optString("type"),verified,duration);
   boolean same="matched".equals(betterArt.optString("status"))&&
     (verified.matches("tt[0-9]{7,10}")?verified.equals(betterArt.optString("imdbId")):
       j.optString("id").equals(betterArt.optString("id")))&&
     j.optString("type").equals(betterArt.optString("type"));
   if(same){
    String candidate=betterArt.optString("poster");
    Bitmap alternative=posterBitmap(context,candidate);
    if(alternative!=null){bitmap=alternative;address=candidate;item.imageCredit="BetterPosters";item.imageLicense="BetterPosters · حقوق الصور لأصحابها";}
   }
  }catch(Exception unavailable){}
  if(bitmap==null&&item.imdbId.matches("tt[0-9]{7,10}")&&
      context.getSharedPreferences("smarthub",0).getBoolean("betterposters_direct_enabled",true))
   try{
    String direct=BetterPostersDirect.poster(context,item.imdbId);
    Bitmap alternative=posterBitmap(context,direct);
    if(alternative!=null){bitmap=alternative;address=direct;item.imageCredit="BetterPosters · IMDb";item.imageLicense="BetterPosters · حقوق الصور لأصحابها";}
   }catch(Exception unavailable){}
  if(bitmap==null){
   bitmap=EpgArtwork.load(context,event,raw);
   if(bitmap!=null){item.imageKind="epg";item.imageCredit="EPG";item.imageLicense="EPG · حقوق الصور لأصحابها";}
  }
  // Do not let an unusable metadata-only result block open-image fallbacks.
  if(bitmap==null)return null;
  item.image="epg".equals(item.imageKind)?"":address;
  return new OnlineProgramme(item,bitmap);
 }
 private static OnlineProgramme epg(Context context,SatelliteState event,String raw)throws Exception{Bitmap picture=EpgArtwork.load(context,event,raw);if(picture==null)return null;MediaRepository.Item item=new MediaRepository.Item();item.id="epg:"+EpgArtwork.key(event.channelName,raw);item.title=raw;item.imageKind="epg";item.imageCredit="EPG";item.imageLicense="EPG · حقوق الصور لأصحابها";item.fetchedAt=System.currentTimeMillis();return new OnlineProgramme(item,picture);}
 private static Bitmap posterBitmap(Context context,String address){
  if(address==null||address.isEmpty())return null;
  try{
   URI uri=new URI(address);
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||
      uri.getFragment()!=null||!Arrays.asList("btttr.cc","cdn.subsource.net",
        "image.tmdb.org","static.tvmaze.com").contains(uri.getHost()))return null;
   byte[] data=ArtworkCache.fetch(context,uri.toURL(),6*1024*1024);
   BitmapFactory.Options bounds=new BitmapFactory.Options();
   bounds.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(data,0,data.length,bounds);
   if(bounds.outWidth<90||bounds.outHeight<90||bounds.outWidth>10000||bounds.outHeight>10000)return null;
   BitmapFactory.Options opts=new BitmapFactory.Options();
   int limit=1280;opts.inSampleSize=1;
   while(bounds.outWidth/opts.inSampleSize>limit||bounds.outHeight/opts.inSampleSize>limit)opts.inSampleSize*=2;
   return BitmapFactory.decodeByteArray(data,0,data.length,opts);
  }catch(Exception unavailable){return null;}
 }
 private static String join(JSONArray array){if(array==null)return "";List<String> values=new ArrayList<>();for(int n=0;n<Math.min(12,array.length());n++)values.add(array.optString(n));return String.join("، ",values);}
}
