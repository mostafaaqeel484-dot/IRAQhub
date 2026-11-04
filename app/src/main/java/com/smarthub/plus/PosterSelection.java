package com.smarthub.plus;
import android.content.*;import org.json.*;import java.util.*;
/** A verified selection is stable for one EPG event, including revisits to its channel. */
final class PosterSelection {
 static String key(SatelliteState s,String title){try{return SportsCache.key(s.channelId+"|"+PlaylistRepository.normalize(title)+"|"+(title.equals(s.title)?s.startMs:s.nextStartMs));}catch(Exception e){return "";}}
 static JSONObject read(Context c,SatelliteState state,String title){try{JSONObject j=new JSONObject(c.getSharedPreferences("poster_choices",0).getString(key(state,title),"{}"));if(System.currentTimeMillis()-j.optLong("at")>7*86400000L)return new JSONObject();return j;}catch(Exception e){return new JSONObject();}}
 static void save(Context c,SatelliteState state,String title,JSONObject match){if(!"TMDB".equals(match.optString("provider"))||!match.optString("id").matches("tmdb:[0-9]+"))return;try{SharedPreferences p=c.getSharedPreferences("poster_choices",0);SharedPreferences.Editor e=p.edit();Map<String,?> entries=p.getAll();if(entries.size()>64)for(String key:entries.keySet())e.remove(key);e.putString(key(state,title),new JSONObject().put("id",match.optString("id")).put("type",match.optString("type")).put("at",System.currentTimeMillis()).toString()).apply();}catch(Exception ignored){}}
}
