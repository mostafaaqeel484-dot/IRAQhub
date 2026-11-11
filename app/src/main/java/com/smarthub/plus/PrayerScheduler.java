package com.smarthub.plus;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.Map;
public final class PrayerScheduler {
 public static void schedule(Context c){cancel(c);if(!c.getSharedPreferences("smarthub",0).getBoolean("prayer_alerts",false))return;JSONObject saved=HubFiles.read(c,"prayers.json");if(saved==null)return;JSONArray days=saved.optJSONArray("data");if(days==null||days.length()==0)return;
  try{ZoneId zone=PrayerData.zone(days.getJSONObject(0));LocalDate today=LocalDate.now(zone);JSONObject day=PrayerData.find(days,today);AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
   if(day!=null)for(Map.Entry<String,Long> e:PrayerData.reminders(day,System.currentTimeMillis()).entrySet()){int index=java.util.Arrays.asList(PrayerData.PRAYERS).indexOf(e.getKey());Intent i=new Intent(c,PrayerReceiver.class).setAction("com.smarthub.plus.PRAYER").putExtra("prayer",e.getKey()).putExtra("prayer_at",PrayerData.instant(day,e.getKey())).putExtra("city",saved.optString("city"));place(am,e.getValue(),PendingIntent.getBroadcast(c,400+index,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}
   if(day==null)c.getSharedPreferences("smarthub",0).edit().putString("prayer_refresh_error","جدول الصلاة يحتاج تحديثاً / Prayer calendar needs refresh").apply();long next=day==null?System.currentTimeMillis()+60*60_000L:today.plusDays(1).atStartOfDay(zone).plusMinutes(2).toInstant().toEpochMilli();place(am,next,PendingIntent.getBroadcast(c,499,new Intent(c,PrayerReceiver.class).setAction("com.smarthub.plus.RESCHEDULE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
  }catch(Exception ignored){}
 }
 private static void place(AlarmManager am,long when,PendingIntent pending){if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);}
 public static void cancel(Context c){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);for(int code=400;code<=404;code++)am.cancel(PendingIntent.getBroadcast(c,code,new Intent(c,PrayerReceiver.class).setAction("com.smarthub.plus.PRAYER"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));am.cancel(PendingIntent.getBroadcast(c,499,new Intent(c,PrayerReceiver.class).setAction("com.smarthub.plus.RESCHEDULE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}
 private PrayerScheduler(){}
}
