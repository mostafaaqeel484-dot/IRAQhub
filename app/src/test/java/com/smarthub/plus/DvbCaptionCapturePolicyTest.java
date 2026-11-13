package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

/** Do not allow a screen-projection session to read other apps or protected TV. */
public final class DvbCaptionCapturePolicyTest {
 @Test public void acceptsOnlyVisibleForegroundOwnFreeToAirService(){
  String physical="130:H:11954:V:27500:100";
  String id="DVB:"+physical;
  assertTrue(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     true,true,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed("DVB:other",physical,physical,
     true,true,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,"other",
     true,true,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     false,true,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     true,false,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     true,true,false,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     true,true,true,true,false));
  assertFalse(DvbCaptionCapturePolicy.allowed(id,physical,physical,
     true,true,true,false,true));
 }
 @Test public void neverCapturesStreamingOrNonDvbContent(){
  String physical="130:H:11954:V:27500:100";
  assertFalse(DvbCaptionCapturePolicy.allowed("IPTV:"+physical,physical,physical,
     true,true,true,false,false));
  assertFalse(DvbCaptionCapturePolicy.allowed("",physical,physical,
     true,true,true,false,false));
 }
}
