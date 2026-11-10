package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChannelLogoWhiteCleanerTest {
 @Test public void onlyOpaqueNearWhitePixelsQualify(){
  assertTrue(ChannelLogoWhiteCleaner.nearWhite(0xffffffff));
  assertTrue(ChannelLogoWhiteCleaner.nearWhite(0xfffaf7f7));
  assertFalse(ChannelLogoWhiteCleaner.nearWhite(0xffffffff&0x00ffffff));
  assertFalse(ChannelLogoWhiteCleaner.nearWhite(0xffdedede));
  assertFalse(ChannelLogoWhiteCleaner.nearWhite(0xffffffff&0xffff00ff));
 }
}
