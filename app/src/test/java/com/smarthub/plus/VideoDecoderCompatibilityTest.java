package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoDecoderCompatibilityTest {
 @Test public void hevcRangeExtensionIdDoesNotConfirm422(){
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("hvc1.4.4.L120.B0"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("hev1.6.4.L153.B0"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("hvc1.5.4.L120.B0"));
 }
 @Test public void detectsAvcHigh422Profile(){
  assertTrue(VideoDecoderCompatibility.isKnown422CodecString("avc1.7A001F"));
  assertTrue(VideoDecoderCompatibility.isKnown422CodecString("avc3.7a001f"));
 }
 @Test public void doesNotMislabelCommon420Profiles(){
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("hvc1.2.4.L120.B0"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("avc1.640028"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("avc1.4D401E"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("avc1.7A"));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("avc1.7A001FEXTRA"));
 }
 @Test public void classifiesKnownSoftwareDecoderNames(){
  assertEquals("SOFTWARE",VideoDecoderCompatibility.decoderKind("OMX.google.hevc.decoder"));
  assertEquals("SOFTWARE",VideoDecoderCompatibility.decoderKind("c2.android.avc.decoder"));
 }
 @Test public void doesNotGuessVendorDecoderIsSoftware(){
  assertEquals("HARDWARE_OR_VENDOR",VideoDecoderCompatibility.decoderKind("OMX.vendor.hevc.decoder"));
  assertEquals("UNKNOWN",VideoDecoderCompatibility.decoderKind(null));
 }
 @Test public void rejectsUnknownCodecStrings(){
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString(null));
  assertFalse(VideoDecoderCompatibility.isKnown422CodecString("mpeg2video"));
 }
}
