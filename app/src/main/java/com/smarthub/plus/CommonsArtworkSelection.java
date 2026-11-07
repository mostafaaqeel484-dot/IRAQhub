package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.URI;
import java.net.URL;

/**
 * Explicitly selected Commons artwork, never guessed from a programme title.
 * Keep source/author/license and exact event identity together.
 */
final class CommonsArtworkSelection {
 private static final String EVENT_PREFIX="event:";
 private CommonsArtworkSelection(){}

 static String checkedImage(JSONObject verified)throws Exception{
  if(verified==null||!"verified".equals(verified.optString("status"))
      ||!"Wikimedia Commons".equals(verified.optString("provider"))
      ||verified.optString("license").isEmpty()
      ||verified.optString("artist").isEmpty()
      ||verified.optString("source").isEmpty())
   throw new java.io.IOException("COMMONS_ATTRIBUTION_REQUIRED");
  String image=verified.optString("poster");
  URI uri=new URI(image);
  if(!"https".equalsIgnoreCase(uri.getScheme())||
    !"upload.wikimedia.org".equalsIgnoreCase(uri.getHost())||
    uri.getUserInfo()!=null||uri.getFragment()!=null)
   throw new java.io.IOException("COMMONS_IMAGE_HOST_INVALID");
  return image;
 }
 static boolean assignProgramme(Context context,SatelliteState state,
      boolean next,JSONObject verified)throws Exception{
  String title=next?state.nextTitle:state.title;
  String key=PosterSelection.key(state,title,next);
  if(key.isEmpty())return false;
  checkedImage(verified);
  long end=next?state.nextEndMs:state.endMs;
  // Some OEM guides expose a start time but no end time. Keep the selection
  // event-scoped instead of expiring it immediately.
  if(end<=System.currentTimeMillis())end=System.currentTimeMillis()+6L*3600000;
  JSONObject saved=new JSONObject(verified.toString())
    .put("eventKey",key).put("selectedAt",System.currentTimeMillis())
    .put("end",end);
  SharedPreferences settings=context.getSharedPreferences("commons_artwork",0);
  SharedPreferences.Editor edit=settings.edit();
  if(settings.getAll().size()>60)for(String prior:settings.getAll().keySet())
   edit.remove(prior);
  edit.putString(EVENT_PREFIX+key,saved.toString()).apply();
  context.getSharedPreferences("smarthub",0).edit()
    .putLong("epg_art_changed_at",System.currentTimeMillis()).apply();
  return true;
 }
 static JSONObject event(Context context,SatelliteState state,
      String title,boolean next){
  try{
   String key=PosterSelection.key(state,title,next);
   if(key.isEmpty())return null;
   JSONObject saved=new JSONObject(context.getSharedPreferences("commons_artwork",0)
     .getString(EVENT_PREFIX+key,"{}"));
   if(!key.equals(saved.optString("eventKey")))return null;
   if(System.currentTimeMillis()>saved.optLong("end",0)+60000L)return null;
   checkedImage(saved);
   return saved;
  }catch(Exception ignored){return null;}
 }
 static boolean assignChannelLogo(Context context,SatelliteState state,
      JSONObject verified)throws Exception{
  if(state==null||!state.channelId.startsWith("DVB:")||
       state.channelName==null||state.channelName.isEmpty())return false;
  String image=checkedImage(verified);
  byte[] bytes=ArtworkCache.fetch(context,new URL(image),5*1024*1024);
  BitmapFactory.Options bounds=new BitmapFactory.Options();
  bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
  if(bounds.outWidth<24||bounds.outHeight<24||
     bounds.outWidth>8192||bounds.outHeight>8192)return false;
  BitmapFactory.Options options=new BitmapFactory.Options();
  options.inSampleSize=1;
  while(bounds.outWidth/options.inSampleSize>512||
       bounds.outHeight/options.inSampleSize>512)options.inSampleSize*=2;
  Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  if(decoded==null)return false;
  try{
   new ChannelLogoCache(context).save("channel:physical:"+state.channelId,decoded);
  }finally{decoded.recycle();}
  context.getSharedPreferences("smarthub",0).edit()
    .putString("commons_logo_selected:"+state.channelId,
       verified.toString()).putLong("logo_mapping_changed_at",
       System.currentTimeMillis()).apply();
  LogoRepository.get(context).trimRamCache();
  LogoRepository.get(context).invalidate();
  return true;
 }
}
