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
 static OnlineProgramme fetch(Context context,SatelliteState event,String raw,boolean upcoming)throws Exception{return load(context,event,raw,"","","",upcoming);}
 static OnlineProgramme fetch(Context context,String raw,String imdb)throws Exception{return load(context,SatelliteState.read(context),raw,"","",imdb);}
 static OnlineProgramme selected(Context context,String raw,Candidate candidate)throws Exception{return load(context,SatelliteState.read(context),raw,candidate.id,candidate.type,"");}
 static OnlineProgramme selected(Context context,SatelliteState event,String raw,Candidate candidate)throws Exception{return load(context,event,raw,candidate.id,candidate.type,"");}
 static OnlineProgramme selected(Context context,SatelliteState event,String raw,Candidate candidate,boolean upcoming)throws Exception{return load(context,event,raw,candidate.id,candidate.type,"",upcoming);}
 private static OnlineProgramme load(Context context,SatelliteState event,String raw,String id,String type,String imdb)throws Exception{
  if(event==null)event=SatelliteState.read(context);
  return load(context,event,raw,id,type,imdb,!raw.equals(event.title)&&raw.equals(event.nextTitle));
 }
 private static OnlineProgramme load(Context context,SatelliteState event,String raw,String id,String type,String imdb,boolean upcoming)throws Exception{
  if(event==null)event=SatelliteState.read(context);
  boolean same=raw.equals(upcoming?event.nextTitle:event.title);
  long start=same?(upcoming?event.nextStartMs:event.startMs):0,
       end=same?(upcoming?event.nextEndMs:event.endMs):0;
  double duration=start>0&&end>start?(end-start)/60000.0:0;
  String tmdbKey="";
  try{tmdbKey=ProviderSecrets.read(context).tmdb;}catch(Exception unavailable){/* Another poster source remains usable. */}
  if(id.isEmpty()&&imdb.isEmpty()&&!tmdbKey.isEmpty()){
   JSONObject remembered=PosterSelection.read(context,event,raw,upcoming);
   // Old BetterPosters/TVmaze IDs must NEVER lock out TMDB after its key
   // is configured. Only reuse a pinned TMDB identity; use IMDb separately.
   String oldId=remembered.optString("id");
   if(PosterArtworkPriority.pinnedTmdbId(oldId)){
    id=oldId;type=remembered.optString("type");
   }else if(PosterArtworkPriority.exactImdb(remembered.optString("imdbId"))){
    imdb=remembered.optString("imdbId");
   }
  }
  JSONObject j=new JSONObject().put("status","not_found");
  JSONObject approximateCandidates=null;
  boolean similarPoster=false;
  // BetterPosters is the first automatic movie/series provider.
  // A failed or ambiguous addon lookup must not block TMDB or TVmaze.
  if(PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER))try{
   JSONObject better=new BetterPostersProvider(context).resolve(raw,
      id,type,imdb,duration);
   if("matched".equals(better.optString("status")))j=better;
  }catch(Exception unavailable){/* Try independent sources. */}
  if(!"matched".equals(j.optString("status"))&&
     PosterSourceSwitches.enabled(context,PosterSourceSwitches.TMDB)&&
     !tmdbKey.isEmpty()&&(id.isEmpty()||id.startsWith("tmdb:")))try{
   j=new TmdbProvider(context,tmdbKey).resolve(raw,id,type,imdb,duration);
   if("ambiguous".equals(j.optString("status"))&&!j.isNull("candidates"))
    approximateCandidates=j;
  }catch(Exception unavailable){/* Keep other verified sources usable. */}
  // Some BetterPosters addons expose /meta but not /catalog search.
  // Use a verified TMDB identity to retrieve Better artwork BEFORE showing
  // the TMDB image. TMDB remains the metadata and image fallback.
  if("TMDB".equals(j.optString("provider"))&&
     "matched".equals(j.optString("status"))&&
     PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER))try{
   String tmdbImdb=j.optString("imdbId","");
   String verified=PosterArtworkPriority.exactImdb(tmdbImdb)?tmdbImdb:j.optString("id","");
   String kind=j.optString("type","");
   if(!verified.isEmpty()&&("movie".equals(kind)||"series".equals(kind))){
    JSONObject better=new BetterPostersProvider(context).resolve(raw,
      verified,kind,tmdbImdb,duration);
    if("matched".equals(better.optString("status"))&&
       verified.equals(better.optString("id"))&&
       kind.equals(better.optString("type"))&&
       !better.optString("poster","").isEmpty())j=better;
   }
  }catch(Exception unavailable){/* Preserve a valid TMDB fallback. */}
  if(!"matched".equals(j.optString("status"))&&
     PosterSourceSwitches.enabled(context,PosterSourceSwitches.TVMAZE))try{
   JSONObject other=new TvmazeProvider(context).resolve(raw,imdb);
   if("matched".equals(other.optString("status")))j=other;
  }catch(Exception unavailable){/* Preserve verified EPG artwork. */}
  if(!"matched".equals(j.optString("status")))try{
   JSONObject anime=AnimeMetadataProvider.resolve(context,raw);
   if(anime!=null&&"matched".equals(anime.optString("status")))j=anime;
  }catch(Exception unavailable){/* Preserve genuine EPG artwork. */}
  if(!"matched".equals(j.optString("status"))&&approximateCandidates!=null
      &&id.isEmpty()&&imdb.isEmpty()&&!tmdbKey.isEmpty()
      &&PosterSourceSwitches.enabled(context,PosterSourceSwitches.TMDB)
      &&context.getSharedPreferences("smarthub",0)
        .getBoolean("osd_similar_posters",true)){
   JSONObject guess=SimilarPosterChoice.choose(
     approximateCandidates.optJSONArray("candidates"),raw);
   if(guess!=null)try{
    JSONObject detail=new TmdbProvider(context,tmdbKey).resolve(raw,
      guess.optString("id"),guess.optString("type"),"",duration);
    if("matched".equals(detail.optString("status"))){
     j=detail;
     similarPoster=true;
    }
   }catch(Exception unavailable){/* Preserve the normal EPG fallback. */}
  }
  if(!"matched".equals(j.optString("status"))){
   // RPDB/OPDB can still work if TMDB is disabled, provided an exact IMDb
   // identity was explicitly supplied or verified in a saved selection.
   String verified=imdb;
   if(verified==null||!verified.matches("tt[0-9]{7,10}"))try{
    verified=PosterSelection.read(context,event,raw,upcoming).optString("imdbId","");
   }catch(Exception ignored){verified="";}
   if(verified!=null&&verified.matches("tt[0-9]{7,10}"))try{
    String link=VerifiedPosterFallback.find(context,verified,raw,"");
    Bitmap picture=posterBitmap(context,link);
    if(picture!=null){
     MediaRepository.Item standalone=new MediaRepository.Item();
     standalone.id="imdb:"+verified;standalone.imdbId=verified;
     standalone.title=raw;standalone.image=link;standalone.imageKind="poster";
     standalone.imagePage=link;standalone.imageCredit=posterSourceCredit(link);
     standalone.imageLicense="Poster rights remain with the respective copyright owners";
     standalone.fetchedAt=System.currentTimeMillis();
     return new OnlineProgramme(standalone,picture);
    }
   }catch(Exception ignored){}
   return epg(context,event,raw,upcoming);
  }
  if(!similarPoster)PosterSelection.save(context,event,raw,upcoming,j);MediaRepository.Item item=new MediaRepository.Item();item.imdbId=j.optString("imdbId");if(!item.imdbId.isEmpty()&&!item.imdbId.matches("tt[0-9]{7,10}"))return null;boolean tmdb="TMDB".equals(j.optString("provider"));boolean better="BetterPosters".equals(j.optString("provider"));boolean tvmaze="TVmaze".equals(j.optString("provider"));boolean anime="AniList".equals(j.optString("provider"))||"Kitsu".equals(j.optString("provider"));item.id=anime?j.optString("id"):tmdb?j.optString("id"):(better?"betterposters:":tvmaze?"tvmaze:":"subsource:")+(item.imdbId.isEmpty()?j.optString("id"):item.imdbId);item.title=j.optString("title");item.release=j.optString("year");item.kind=("series".equals(j.optString("type"))||"tvseries".equals(j.optString("type")))?"series":"film";item.imageKind="poster";item.imagePage=j.optString("source");item.imageLicense=(anime?j.optString("provider"):tmdb?"TMDB":better?"BetterPosters":tvmaze?"TVmaze":"Subsource")+" · artwork rights retained by the owners";item.imageCredit=anime?j.optString("provider"):tmdb?"TMDB":better?"BetterPosters / TMDB":tvmaze?"TVmaze":"Subsource";item.background=j.optString("background");item.people=j.optJSONArray("people")==null?new JSONArray():j.optJSONArray("people");item.summary=j.optString("description");item.runtime=j.optString("runtime");item.cast=join(j.optJSONArray("cast"));item.directors=join(j.optJSONArray("directors"));item.genres=join(j.optJSONArray("genres"));item.ratings=j.optString("rating");item.cached=j.optBoolean("cached");item.fetchedAt=j.optLong("fetchedAt",System.currentTimeMillis());
  String address=j.optString("poster");
  Bitmap bitmap=null;
  // A verified BetterPosters image is preferred first. Other IMDb poster
  // providers are fallback only if this image cannot be downloaded/decoded.
  if(bitmap==null)bitmap=posterBitmap(context,address);
  // Exact IMDb alternatives are used only when the selected poster fails.
  // Identity-bound RPDB / OpenPosterDB / licensed archive fallbacks.
  // The free RPDB key is public; an OMDb Poster API requires a separate
  // patron key and is intentionally NOT called with an RPDB key.
  if(bitmap==null&&item.imdbId.matches("tt[0-9]{7,10}")&&!similarPoster){
   String external=VerifiedPosterFallback.find(context,item.imdbId,item.title,item.release);
   Bitmap alternative=posterBitmap(context,external);
   if(alternative!=null){
    bitmap=alternative;address=external;
    item.imageCredit=posterSourceCredit(external);
    item.imageLicense="External verified-source poster · rights retained by creators";
   }
  }
  if(bitmap==null&&tmdb)try{
   // Reuse verified TMDB/IMDb identity; never pick a different named movie.
   String verified=item.imdbId;
   JSONObject betterArt=new BetterPostersProvider(context).resolve(raw,
     verified.isEmpty()?j.optString("id"):"",j.optString("type"),verified,duration);
   boolean sameIdentity="matched".equals(betterArt.optString("status"))&&
     (verified.matches("tt[0-9]{7,10}")?verified.equals(betterArt.optString("imdbId")):
       j.optString("id").equals(betterArt.optString("id")))&&
     j.optString("type").equals(betterArt.optString("type"));
   if(sameIdentity){
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
   bitmap=EpgArtwork.load(context,event,raw,upcoming);
   if(bitmap!=null){item.imageKind="epg";item.imageCredit="EPG";item.imageLicense="EPG · حقوق الصور لأصحابها";}
  }
  // Do not let an unusable metadata-only result block open-image fallbacks.
  if(bitmap==null)return null;
  if(similarPoster){
   // A real, exact EPG photo always outranks a guessed movie association.
   if("epg".equals(item.imageKind))return epg(context,event,raw,upcoming);
   item.imageCredit+=" · similar-title candidate (unverified)";
   if(!item.summary.isEmpty())item.summary=
     (context.getSharedPreferences("smarthub",0).getBoolean("arabic",true)?
       "≈ نبذة لعنوان مشابه وليست تأكيداً لنفس العمل: ":
       "≈ Similar-title synopsis, not confirmed for this programme: ")+
     item.summary;
  }
  item.image="epg".equals(item.imageKind)?"":address;
  return new OnlineProgramme(item,bitmap);
 }
 private static OnlineProgramme epg(Context context,SatelliteState event,String raw,boolean upcoming)throws Exception{Bitmap picture=EpgArtwork.load(context,event,raw,upcoming);if(picture==null)return null;MediaRepository.Item item=new MediaRepository.Item();item.id="epg:"+EpgArtwork.key(event.channelName,raw);item.title=raw;item.imageKind="epg";item.imageCredit="EPG";item.imageLicense="EPG · حقوق الصور لأصحابها";item.fetchedAt=System.currentTimeMillis();return new OnlineProgramme(item,picture);}
 private static Bitmap posterBitmap(Context context,String address){
  if(address==null||address.isEmpty())return null;
  try{
   if(PrivatePosterCache.token(address))return PrivatePosterCache.read(context,address);
   URI uri=new URI(address);
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||
      uri.getFragment()!=null||!Arrays.asList("btttr.cc","cdn.subsource.net",
        "image.tmdb.org","static.tvmaze.com","openposterdb.com",
        "api.ratingposterdb.com","archive.org","s4.anilist.co","s3.anilist.co",
        "media.kitsu.app","media.kitsu.io","kitsu.io").contains(uri.getHost()))return null;
   byte[] data=ArtworkCache.fetch(context,uri.toURL(),6*1024*1024);
   BitmapFactory.Options bounds=new BitmapFactory.Options();
   bounds.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(data,0,data.length,bounds);
   if(bounds.outWidth<90||bounds.outHeight<90||bounds.outWidth>10000||bounds.outHeight>10000)return null;
   BitmapFactory.Options opts=new BitmapFactory.Options();
   // Limit decoding for the 4 GB X5M launcher; InfoBar downsamples to 512.
   int limit=768;opts.inSampleSize=1;
   while(bounds.outWidth/opts.inSampleSize>limit||bounds.outHeight/opts.inSampleSize>limit)opts.inSampleSize*=2;
   return BitmapFactory.decodeByteArray(data,0,data.length,opts);
  }catch(Exception unavailable){return null;}
 }
 private static String posterSourceCredit(String link){
  if(link==null)return "Verified IMDb artwork";
  if(link.startsWith("privateposter:opdb:"))return "OpenPosterDB (local)";
  if(link.startsWith("privateposter:rpdb:"))return "RatingPosterDB (local)";
  if(link.startsWith("privateposter:omdb:"))return "OMDb Poster API (local)";
  if(link.contains("openposterdb.com"))return "OpenPosterDB";
  if(link.contains("ratingposterdb.com"))return "RatingPosterDB";
  if(link.contains("archive.org"))return "Internet Archive";
  return "Verified IMDb artwork";
 }
 private static String join(JSONArray array){if(array==null)return "";List<String> values=new ArrayList<>();for(int n=0;n<Math.min(12,array.length());n++)values.add(array.optString(n));return String.join("، ",values);}
}
