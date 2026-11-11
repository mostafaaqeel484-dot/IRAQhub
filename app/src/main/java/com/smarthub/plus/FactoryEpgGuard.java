package com.smarthub.plus;
import android.content.SharedPreferences;

/** Strictly EPG-only server-error cooldown. Never blocks the authorised CHMAX
 * package/icon endpoints or the native DVB / XMLTV fallback path. */
final class FactoryEpgGuard {
 private FactoryEpgGuard(){}
 static final String UNTIL="factory_epg_backoff_until";
 static long delayMs(String status){
  if(status==null)return 60000L;
  if(status.contains("50000")||status.contains("AUTH_REQUIRED")||
     status.contains("LOGIN_REJECTED")||status.contains("HTTP_403"))return 20L*60000L;
  if(status.contains("429"))return 5L*60000L;
  if(status.contains("TIMEOUT")||status.contains("DNS_FAILED"))return 120000L;
  if(status.contains("CHANNEL_NOT_MATCHED")||status.contains("EPG_NOT_PUBLISHED")||
     status.contains("CHANNEL_REFERENCE_UNAVAILABLE"))return 5L*60000L;
  return 60000L;
 }
 static boolean canRequest(SharedPreferences prefs,long now){
  return prefs.getLong(UNTIL,0)<=now;
 }
 static void failure(SharedPreferences prefs,String status,long now){
  prefs.edit().putLong(UNTIL,now+delayMs(status))
   .putString("factory_epg_last_failure",status==null?"UNKNOWN":status).apply();
 }
 static void success(SharedPreferences prefs){
  prefs.edit().remove(UNTIL).putString("factory_epg_last_failure","").apply();
 }
}
