package com.smarthub.plus;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** EPG programme images, never channel logos. URL/XMLTV and observed native-guide crops. */
final class EpgArtwork {
 static String key(String channel,String title){try{String raw=PlaylistRepository.normalize(channel)+"\n"+PlaylistRepository.normalize(title);StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private static File directory(Context c){File dir=new File(c.getCacheDir(),"epg-programme-art");dir.mkdirs();return dir;}
 static synchronized void saveGuide(Context c,String channel,String title,Bitmap bitmap)throws IOException{if(channel.isEmpty()||title.isEmpty()||bitmap==null)return;File target=new File(directory(c),key(channel,title)+".png");try(FileOutputStream out=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot save EPG artwork");}File[] files=directory(c).listFiles();if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long size=0;for(int n=0;n<files.length;n++){size+=files[n].length();if(n>=32||size>16*1024*1024)files[n].delete();}}c.getSharedPreferences("smarthub",0).edit().putLong("epg_art_changed_at",System.currentTimeMillis()).putString("epg_art_observed_channel",channel).putString("epg_art_observed_title",title).apply();}
 static Bitmap load(Context c,String raw)throws Exception{
  SatelliteState state=SatelliteState.read(c);return load(c,state,raw);
 }
 /**
  * Programme-specific priority: broadcast/XMLTV, published Android TV EPG,
  * verified TMDB/BetterPosters/TVmaze cache, exact sports event, native crop.
  * A dead or corrupt URL must never prevent another trusted source displaying.
  */
 static Bitmap load(Context c,SatelliteState state,String raw)throws Exception{
  return load(c,state,raw,state!=null&&raw!=null&&!raw.equals(state.title)&&raw.equals(state.nextTitle));
 }
 static Bitmap load(Context c,SatelliteState state,String raw,boolean upcoming)throws Exception{
  return load(c,state,raw,upcoming,false);
 }
 /** Render verified already-cached programme imagery without any network/guide query.
  * Used during active 4:2:2 software decoding: video wins over poster downloads. */
 static Bitmap loadCached(Context c,SatelliteState state,String raw,boolean upcoming)throws Exception{
  return load(c,state,raw,upcoming,true);
 }
 private static Bitmap load(Context c,SatelliteState state,String raw,boolean upcoming,boolean cacheOnly)throws Exception{
  if(state==null||raw==null||raw.trim().isEmpty())return null;
  String title=raw.trim();
  boolean current=!upcoming&&title.equals(state.title),next=upcoming&&title.equals(state.nextTitle);
  if(!current&&!next)return null;
  LinkedHashSet<String> addresses=new LinkedHashSet<>();
  String broadcast=current?state.image:state.nextImage;
  if(broadcast!=null&&!broadcast.isEmpty())addresses.add(broadcast);
  if(!cacheOnly)try{
   SatelliteGuideRepository guide=new SatelliteGuideRepository(c);
   PlaylistRepository.Programme p=current?guide.currentFor(state,System.currentTimeMillis()):guide.nextFor(state,System.currentTimeMillis());
   if(p!=null&&PlaylistRepository.normalize(p.title).equals(PlaylistRepository.normalize(title))
       &&p.image!=null&&!p.image.isEmpty())addresses.add(p.image);
  }catch(Exception unavailable){}
  String published=PublishedEpgArtwork.image(c,state,title,upcoming);
  if(published!=null&&!published.isEmpty())addresses.add(published);
  String verified=UnifiedArtworkPrefetch.cachedUrl(c,state,title,upcoming);
  if(!verified.isEmpty())addresses.add(verified);
  SportsMatch match=sportsMatch(c,state,upcoming);
  if(match!=null&&SportsProgrammeArtwork.verified(match))try{
   if(match.image!=null&&!match.image.isEmpty()){
    URI u=new URI(match.image);
    if("https".equalsIgnoreCase(u.getScheme())&&u.getUserInfo()==null&&
      ("www.thesportsdb.com".equalsIgnoreCase(u.getHost())||
       "r2.thesportsdb.com".equalsIgnoreCase(u.getHost()))&&
      u.getPath()!=null&&u.getPath().startsWith("/images/"))
     addresses.add(match.image);
   }
  }catch(Exception unavailable){}
  for(String address:addresses)try{
   URI source=new URI(address);String scheme=source.getScheme();
   if(source.getUserInfo()!=null||source.getFragment()!=null)continue;
   byte[] bytes;
   if("https".equalsIgnoreCase(scheme)){
    bytes=cacheOnly?ArtworkCache.cached(c,source.toURL(),5*1024*1024):
      ArtworkCache.fetch(c,source.toURL(),5*1024*1024);
   }else if(!cacheOnly&&"http".equalsIgnoreCase(scheme)){
    HttpURLConnection connection=(HttpURLConnection)source.toURL().openConnection();
    bytes=OnlineChannelLogo.read(connection,5*1024*1024);
   }else if(!cacheOnly&&("content".equalsIgnoreCase(scheme)||"android.resource".equalsIgnoreCase(scheme))){
    try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(address))){bytes=read(in);}
   }else continue;
   if(bytes==null)continue;
   Bitmap picture=decode(bytes);
   if(picture!=null)return picture;
  }catch(Exception unavailable){/* Fall through to another verified programme source. */}
  // A sports fallback is an exact-event card, not a vaguely named stock poster.
  // Draw only after we have a single verified sports event and real team identities.
  if(match!=null&&SportsProgrammeArtwork.verified(match)){
   Bitmap card=SportsProgrammeArtwork.card(c,match,cacheOnly);
   if(card!=null)return card;
  }
  // Native-guide crops are name-scoped; never use one for a same-title NEXT event.
  if(upcoming&&state.title.equals(state.nextTitle))return null;
  return loadGuide(c,state.channelName,title);
 }
 /** NEXT must be matched using NEXT's date/time, not the current broadcast. */
 private static SportsMatch sportsMatch(Context c,SatelliteState s,boolean upcoming){
  if(s==null)return null;
  try{
   SatelliteState event=s;
   if(upcoming){
    event=s.copy();
    event.title=s.nextTitle;
    event.startMs=s.nextStartMs;
    event.endMs=s.nextEndMs;
   }
   if(event.startMs<=0||event.endMs<=event.startMs)return null;
   return MatchRepository.get(c).forProgramme(event);
  }catch(Exception unavailable){return null;}
 }
 static Bitmap loadGuide(Context c,String channel,String title)throws Exception{File file=new File(directory(c),key(channel,title)+".png");if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<12*3600000L)try(InputStream in=new FileInputStream(file)){return decode(read(in));}return null;}
 private static byte[] read(InputStream in)throws IOException{if(in==null)throw new IOException("No EPG artwork stream");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>5*1024*1024)throw new IOException("EPG artwork exceeds limit");out.write(b,0,n);}return out.toByteArray();}
 private static Bitmap decode(byte[] bytes){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>10000||o.outHeight>10000)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1920||o.outHeight/o.inSampleSize>1920)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}
}
