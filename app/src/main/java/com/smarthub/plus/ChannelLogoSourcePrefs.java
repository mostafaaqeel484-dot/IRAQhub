package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;

/** CHMAX and independent channel-logo sources can both be enabled.
 * Provider artwork is an entirely different opt-in; no cross-source caching. */
final class ChannelLogoSourcePrefs {
 static final String CHMAX="rx6_channel_art_chmax";
 static final String EXTERNAL="rx6_channel_art_external";
 static final String PROVIDERS="rx6_provider_art_enabled";
 static final String REVISION="rx6_art_source_settings_changed";
 private ChannelLogoSourcePrefs(){}
 static boolean chmax(Context c){
  return c.getSharedPreferences("smarthub",0).getBoolean(CHMAX,
    c.getSharedPreferences("smarthub",0).getBoolean("osd_chmax_logos",true));
 }
 static boolean external(Context c){
  return c.getSharedPreferences("smarthub",0).getBoolean(EXTERNAL,true);
 }
 static boolean providers(Context c){
  return c.getSharedPreferences("smarthub",0).getBoolean(PROVIDERS,true);
 }
 static String sourceId(Context c){return sourceId(chmax(c),external(c));}
 static String sourceId(boolean chmax,boolean external){
  return (chmax?"c":"-")+(external?"e":"-");
 }
 static void set(Context c,String key,boolean enabled){
  if(!CHMAX.equals(key)&&!EXTERNAL.equals(key)&&!PROVIDERS.equals(key))
   throw new IllegalArgumentException("INVALID_LOGO_SOURCE");
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  p.edit().putBoolean(key,enabled)
   .putLong(REVISION,System.currentTimeMillis())
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
  LogoRepository.get(c).invalidate();
 }
}
