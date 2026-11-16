package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SavedLogoSourcePolicyTest {
 @Test public void officialAndLegacyLogosNeedChmaxEnabled(){
  assertTrue(SavedLogoSourcePolicy.allow("CHMAX_AUTHORISED",true,false));
  assertTrue(SavedLogoSourcePolicy.allow("receiver visible EPG / CHMAX branding",true,false));
  assertTrue(SavedLogoSourcePolicy.allow("",true,false));
  assertFalse(SavedLogoSourcePolicy.allow("CHMAX_AUTHORISED",false,true));
  assertFalse(SavedLogoSourcePolicy.allow("",false,true));
 }
 @Test public void manualLogosNeedExplicitExternalSwitch(){
  assertTrue(SavedLogoSourcePolicy.allow("MANUAL_CHANNEL_IMAGE",false,true));
  assertFalse(SavedLogoSourcePolicy.allow("MANUAL_CHANNEL_IMAGE",true,false));
 }
 @Test public void unknownUntrustedMarkerCannotOverrideChannelLogo(){
  assertFalse(SavedLogoSourcePolicy.allow("UNVERIFIED",true,true));
  assertFalse(SavedLogoSourcePolicy.allow("CHMAX_AUTHORISED_BACKUP",true,true));
 }
}
