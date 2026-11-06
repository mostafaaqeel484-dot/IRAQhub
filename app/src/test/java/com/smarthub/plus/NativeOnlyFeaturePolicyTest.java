package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeOnlyFeaturePolicyTest {
 @Test public void noOemMenuIsMistakenForNativeFeature(){
  int enabled=0,blocked=0;
  for(OemFeatureMenuIndex.Item item:OemFeatureMenuIndex.all()){
   boolean permitted=NativeOnlyFeaturePolicy.isOwned(item.route);
   if(permitted){
    enabled++;
    assertTrue(item.route.startsWith("local_")||item.route.equals("osd")||item.route.equals("remote"));
   }else{
    blocked++;
    assertFalse(item.route.startsWith("local_"));
   }
  }
  assertTrue(enabled>=7);
  assertTrue(blocked>35);
 }
 @Test public void originalLauncherNeverQualifiesAsNative(){
  for(String route:new String[]{"home","tv","epg","timer","xml:installation","xml:channel","xml:other","patch","server","private_pvr"}){
   assertFalse(route,NativeOnlyFeaturePolicy.isOwned(route));
  }
 }
}
