package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source policy is orthogonal to provider branding, and neither source can
 * leak a cached icon into the other's enabled combination. */
public final class Rx6IndependentLogoSourcesTest {
 @Test public void allFourChannelSourceStatesHaveDistinctCacheKeys(){
  assertEquals("ce",ChannelLogoSourcePrefs.sourceId(true,true));
  assertEquals("c-",ChannelLogoSourcePrefs.sourceId(true,false));
  assertEquals("-e",ChannelLogoSourcePrefs.sourceId(false,true));
  assertEquals("--",ChannelLogoSourcePrefs.sourceId(false,false));
 }
 @Test public void providerFlagIsNotPartOfChannelSourceId(){
  String channelKey=ChannelLogoSourcePrefs.sourceId(true,false);
  assertEquals("c-",channelKey);
  assertNotEquals(channelKey,ChannelLogoSourcePrefs.sourceId(false,true));
 }
}
