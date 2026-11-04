package com.smarthub.plus;import org.junit.Test;import static org.junit.Assert.*;public class BroadcastBadgesTest {
@Test public void dimensionsAndFpsAreParsed(){assertEquals("SD · 720×576 · 25 fps",BroadcastBadges.resolution("720X576 25 FPS | 4:3 | 11269"));assertEquals("4K · 3840×2160",BroadcastBadges.resolution("3840x2160"));}
@Test public void playbackAndNamesDoNotImplyEncryption(){assertEquals("",BroadcastBadges.encryption("beIN Sports HD | DVB_S | $"));assertEquals("FTA",BroadcastBadges.encryption("Not encrypted"));assertEquals("ENCRYPTED",BroadcastBadges.encryption("Scrambled"));}
@Test public void providerNeedsExplicitLabel(){assertEquals("MBC",BroadcastBadges.provider("Provider: MBC"));assertEquals("",BroadcastBadges.provider("DVB_S | MBC"));}
}
