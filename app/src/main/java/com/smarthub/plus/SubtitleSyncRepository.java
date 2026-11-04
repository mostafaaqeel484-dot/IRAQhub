package com.smarthub.plus;
import android.content.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
/** Millisecond timing is keyed by film AND provider/file; a film default handles new tracks. */
final class SubtitleSyncRepository {
 static String identity(String movie,String file,String source){return digest(movie+"\n"+source+"\n"+file);}
 static String digest(String value){try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 static long clamp(long value){return Math.max(-3600000,Math.min(3600000,value));}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("smarthub",0);}
 static synchronized void bind(Context c,String movie,String file,String source,String name){SharedPreferences p=prefs(c);String key=identity(movie,file,source);long value=p.getLong("subtitle_sync:"+key,p.getLong("subtitle_offset:"+movie,0));p.edit().putString("subtitle_profile",movie).putString("subtitle_file_identity",file).putString("subtitle_provider",source).putString("subtitle_display_name",name).putString("subtitle_sync_key",key).putLong("subtitle_offset",clamp(value)).apply();record(c,clamp(value));}
 static long offset(Context c){return prefs(c).getLong("subtitle_offset",0);}
 static synchronized long set(Context c,long requested){SharedPreferences p=prefs(c);long value=clamp(requested);String movie=p.getString("subtitle_profile","default"),key=p.getString("subtitle_sync_key","");SharedPreferences.Editor e=p.edit().putLong("subtitle_offset",value).putLong("subtitle_offset:"+movie,value);if(!key.isEmpty())e.putLong("subtitle_sync:"+key,value);e.apply();record(c,value);return value;}
 static long adjust(Context c,long delta){return set(c,offset(c)+delta);}
 private static void record(Context c,long value){SharedPreferences p=prefs(c);String key=p.getString("subtitle_sync_key","");if(key.isEmpty())return;try{JSONObject j=exportCurrent(c).put("offset",value);p.edit().putString("sync_record:"+key,j.toString()).apply();}catch(Exception ignored){}}
 static JSONObject exportCurrent(Context c)throws JSONException{SharedPreferences p=prefs(c);return new JSONObject().put("movie",p.getString("subtitle_profile","")).put("file",p.getString("subtitle_file_identity","")).put("source",p.getString("subtitle_provider","")).put("name",p.getString("subtitle_display_name","")).put("offset",offset(c));}
 static org.json.JSONArray exportSaved(Context c)throws JSONException{org.json.JSONArray out=new org.json.JSONArray();for(Map.Entry<String,?> e:prefs(c).getAll().entrySet())if(e.getKey().startsWith("sync_record:")&&e.getValue() instanceof String&&out.length()<100)try{out.put(new JSONObject((String)e.getValue()));}catch(Exception ignored){}return out;}
 static void importSaved(Context c,org.json.JSONArray rows)throws JSONException{if(rows==null)return;SharedPreferences.Editor e=prefs(c).edit();for(int n=0;n<rows.length();n++){JSONObject r=rows.getJSONObject(n);String key=identity(r.getString("movie"),r.getString("file"),r.getString("source"));e.putLong("subtitle_sync:"+key,clamp(r.getLong("offset"))).putString("sync_record:"+key,r.toString());}e.apply();}
 static boolean isArabic(String text){int arabic=0,other=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(!Character.isLetter(c))continue;if(c>='\u0600'&&c<='\u06ff'||c>='\u0750'&&c<='\u077f'||c>='\u08a0'&&c<='\u08ff')arabic++;else other++;}return arabic>0&&arabic>=other/2;}
}
