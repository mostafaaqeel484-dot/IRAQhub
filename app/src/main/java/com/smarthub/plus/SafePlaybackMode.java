package com.smarthub.plus;
import android.content.Context;

/** One-switch revert to the OEM-compatible DVB path; never changes the ROM. */
final class SafePlaybackMode {
 private SafePlaybackMode(){}
 static boolean enabled(Context c){
  return c.getSharedPreferences("smarthub",0).getBoolean("iraqhub_safe_mode",false);
 }
 static void set(Context c,boolean on){
  c.getSharedPreferences("smarthub",0).edit()
   .putBoolean("iraqhub_safe_mode",on)
   .putLong("iraqhub_safe_mode_changed",System.currentTimeMillis())
   .apply();
 }
}
