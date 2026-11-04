package com.smarthub.plus;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
public final class PrayerData {
 public static final String[] PRAYERS={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
 public static JSONObject find(JSONArray days,LocalDate date){if(days==null)return null;String wanted=date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy",Locale.ROOT));for(int i=0;i<days.length();i++){JSONObject d=days.optJSONObject(i);JSONObject x=d==null?null:d.optJSONObject("date");JSONObject g=x==null?null:x.optJSONObject("gregorian");if(g!=null&&wanted.equals(g.optString("date")))return d;}return null;}
 public static LocalDate date(JSONObject day){return LocalDate.parse(day.optJSONObject("date").optJSONObject("gregorian").optString("date"),DateTimeFormatter.ofPattern("dd-MM-yyyy",Locale.ROOT));}
 public static ZoneId zone(JSONObject day){return ZoneId.of(day.optJSONObject("meta").optString("timezone"));}
 public static LocalTime time(String raw){Matcher m=Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(raw);if(!m.find())throw new IllegalArgumentException("Invalid prayer time");return LocalTime.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)));}
 public static long instant(JSONObject day,String prayer){return date(day).atTime(time(day.optJSONObject("timings").optString(prayer))).atZone(zone(day)).toInstant().toEpochMilli();}
 public static Map<String,Long> reminders(JSONObject day,long now){Map<String,Long> result=new LinkedHashMap<>();for(String name:PRAYERS){long at=instant(day,name)-5*60_000L;if(at>now)result.put(name,at);}return result;}
 private PrayerData(){}
}
