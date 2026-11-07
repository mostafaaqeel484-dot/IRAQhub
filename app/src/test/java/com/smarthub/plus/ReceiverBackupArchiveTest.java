package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.HashSet;

/** Protect BISS/credentials against accidental future backup expansion. */
public class ReceiverBackupArchiveTest {
 @Test public void onlyHarmlessPreferencesMayBeExported(){
  HashSet<String> allowed=new HashSet<>(Arrays.asList(ReceiverBackupArchive.PREFS));
  assertTrue(allowed.contains("rx_wallpaper"));
  assertTrue(allowed.contains("channel_list_wide_mode"));
  assertTrue(allowed.contains("software_422_preferred_engine"));
  for(String risky:new String[]{"biss_key","own_biss","provider_api_key",
      "tmdb_api_key","auth_token","password","secret",
      "own_usals_location","native_device_serial"}){
   assertFalse(risky+" leaked",allowed.contains(risky));
  }
 }
}