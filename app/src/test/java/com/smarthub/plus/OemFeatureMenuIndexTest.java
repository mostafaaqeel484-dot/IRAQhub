package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public final class OemFeatureMenuIndexTest {
 @Test public void completeCatalogueHasEssentialFamilies(){
  assertTrue(OemFeatureMenuIndex.count("SCAN")>=10);
  assertTrue(OemFeatureMenuIndex.count("DISH")>=8);
  assertTrue(OemFeatureMenuIndex.count("CHANNEL")>=10);
  assertTrue(OemFeatureMenuIndex.count("CLOUD")>=4);
  assertTrue(OemFeatureMenuIndex.count("RECORD")>=5);
  assertTrue(OemFeatureMenuIndex.count("OTHER")>=10);
 }
 @Test public void everyRouteIsBoundedAndFeaturesAreNotPrivateActivities(){
  Set<String> valid=new HashSet<>(Arrays.asList(
   "xml:installation","xml:channel","xml:other","xml:network",
   "local_lnb","local_channels","local_sats","local_signal","local_chmax","local_logos",
   "timer","tv","epg","private_pvr","usb_files","usb_media","osd","remote",
   "server","ca","patch","homeshare","net_update","file_update",
   "android_settings","all_apps","wallpaper","home"));
  int count=0;
  for(OemFeatureMenuIndex.Item item:OemFeatureMenuIndex.all()){
   assertFalse(item.ar.isEmpty());
   assertFalse(item.en.isEmpty());
   assertTrue("Unsupported route: "+item.route,valid.contains(item.route));
   assertFalse(item.route.startsWith("com.tosmart."));
   if(item.graph.isEmpty())assertTrue(item.fragment.isEmpty());
   if(item.route.equals("private_pvr"))assertEquals("RECORD",item.group);
   count++;
  }
  assertTrue(count>=55);
 }
 @Test public void uncertaintyIsExplicit(){
  boolean blindUnconfirmed=false,unicastUnconfirmed=false,privatePvr=false;
  for(OemFeatureMenuIndex.Item item:OemFeatureMenuIndex.all()){
   if(item.en.contains("Blind scan"))blindUnconfirmed=!item.confirmed;
   if(item.en.contains("Unicable"))unicastUnconfirmed=!item.confirmed;
   if(item.route.equals("private_pvr"))privatePvr=true;
  }
  assertTrue(blindUnconfirmed);
  assertTrue(unicastUnconfirmed);
  assertTrue(privatePvr);
 }
}
