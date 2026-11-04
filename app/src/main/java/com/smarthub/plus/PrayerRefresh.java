package com.smarthub.plus;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
/** Short bounded refresh used only when an enabled reminder calendar expires. */
final class PrayerRefresh {
 static synchronized void ifNeeded(Context context){SharedPreferences p=context.getSharedPreferences("smarthub",0);if(!p.getBoolean("prayer_alerts",false))return;JSONObject saved=HubFiles.read(context,"prayers.json");if(saved==null)return;try{JSONArray rows=saved.getJSONArray("data");ZoneId zone=PrayerData.zone(rows.getJSONObject(0));LocalDate date=LocalDate.now(zone);if(PrayerData.find(rows,date)!=null){p.edit().remove("prayer_refresh_error").apply();return;}if(System.currentTimeMillis()-p.getLong("prayer_last_attempt",0)<30*60_000L)return;p.edit().putLong("prayer_last_attempt",System.currentTimeMillis()).apply();String url="https://api.aladhan.com/v1/calendarByCity/"+date.getYear()+"/"+date.getMonthValue()+"?city="+URLEncoder.encode(saved.getString("city"),"UTF-8")+"&country="+URLEncoder.encode(saved.getString("country"),"UTF-8")+"&method="+saved.optInt("method",3);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2500);c.setReadTimeout(3500);c.setRequestProperty("User-Agent","SmartHubPlus/1.0 prayer calendar");JSONObject fresh;try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("Calendar too large");out.write(b,0,n);}fresh=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}finally{c.disconnect();}if(fresh.optInt("code")!=200||PrayerData.find(fresh.optJSONArray("data"),date)==null)throw new IOException("Current calendar not available");fresh.put("city",saved.getString("city")).put("country",saved.getString("country")).put("method",saved.optInt("method",3)).put("fetchedAt",System.currentTimeMillis());HubFiles.save(context,"prayers.json",fresh);p.edit().remove("prayer_refresh_error").apply();}catch(Exception failure){p.edit().putString("prayer_refresh_error","جدول الصلاة يحتاج تحديثاً / Prayer calendar needs refresh: "+failure.getClass().getSimpleName()).apply();}}
 private PrayerRefresh(){}
}
