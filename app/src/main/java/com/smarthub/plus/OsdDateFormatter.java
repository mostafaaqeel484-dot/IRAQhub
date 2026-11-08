package com.smarthub.plus;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Compact 4.0.1 channel OSD date: small year above day/date/time. */
final class OsdDateFormatter {
 private OsdDateFormatter(){}
 static String year(long timestamp,TimeZone zone){
  SimpleDateFormat format=new SimpleDateFormat("yyyy",Locale.ROOT);
  format.setTimeZone(zone);return format.format(new Date(timestamp));
 }
 static String dateLine(long timestamp,TimeZone zone,boolean arabic){
  Locale locale=arabic?new Locale("ar"):Locale.ENGLISH;
  SimpleDateFormat date=new SimpleDateFormat("EEEE d MMMM",locale);
  SimpleDateFormat clock=new SimpleDateFormat("H:mm",Locale.ROOT);
  date.setTimeZone(zone);clock.setTimeZone(zone);
  return date.format(new Date(timestamp))+"  "+clock.format(new Date(timestamp));
 }
}
