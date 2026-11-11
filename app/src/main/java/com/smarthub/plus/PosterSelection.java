package com.smarthub.plus;
import android.content.*;
import org.json.*;
import java.util.*;
/** A verified poster belongs to one precise channel AND broadcast event. */
final class PosterSelection {
 static String key(SatelliteState state,String title){
  return key(state,title,state!=null&&title!=null&&!title.equals(state.title)&&title.equals(state.nextTitle));
 }
 static String key(SatelliteState state,String title,boolean upcoming){
  if(state==null||title==null||title.trim().isEmpty()||state.channelId.isEmpty())return "";
  String expected=upcoming?state.nextTitle:state.title;
  long start=upcoming?state.nextStartMs:state.startMs;
  if(!title.equals(expected)||start<=0)return "";
  try{return SportsCache.key(state.channelId+"|"+PlaylistRepository.normalize(title)+"|"+start);}
  catch(Exception e){return "";}
 }
 static JSONObject read(Context context,SatelliteState state,String title){return read(context,state,title,false);}
 static JSONObject read(Context context,SatelliteState state,String title,boolean upcoming){
  try{
   String key=key(state,title,upcoming);if(key.isEmpty())return new JSONObject();
   JSONObject value=new JSONObject(context.getSharedPreferences("poster_choices",0).getString(key,"{}"));
   if(System.currentTimeMillis()-value.optLong("at")>7L*86400000L)return new JSONObject();
   return value;
  }catch(Exception e){return new JSONObject();}
 }
 static void save(Context context,SatelliteState state,String title,JSONObject match){save(context,state,title,false,match);}
 static void save(Context context,SatelliteState state,String title,boolean upcoming,JSONObject match){
  if(match==null||!"TMDB".equals(match.optString("provider"))||
     !match.optString("id").matches("tmdb:[1-9][0-9]*")||
     !Arrays.asList("movie","series").contains(match.optString("type")))return;
  try{
   String key=key(state,title,upcoming);if(key.isEmpty())return;
   SharedPreferences prefs=context.getSharedPreferences("poster_choices",0);
   SharedPreferences.Editor edit=prefs.edit();
   Map<String,?> rows=prefs.getAll();
   if(rows.size()>64)for(String old:rows.keySet())edit.remove(old);
   edit.putString(key,new JSONObject().put("id",match.optString("id"))
     .put("type",match.optString("type")).put("at",System.currentTimeMillis()).toString()).apply();
  }catch(Exception ignored){}
 }
}
