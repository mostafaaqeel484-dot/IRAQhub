package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Guard rails for the codec selection policy. A detected HEVC Main10 profile
 * without chroma evidence cannot be promoted to a 4:2:2 hardware decoder.
 */
public class X5MDecoderSelectorTest {
 @Test public void onlyPreviouslyProvenCodecCanBePromoted(){
  assertTrue(X5MDecoderSelector.permitted(
    "c2.amlogic.hevc.decoder","video/hevc",
    "c2.amlogic.hevc.decoder","video/hevc"));
  assertFalse(X5MDecoderSelector.permitted(
    "c2.amlogic.hevc.decoder","video/hevc","","video/hevc"));
  assertFalse(X5MDecoderSelector.permitted(
    "c2.amlogic.hevc.decoder","video/hevc",
    "c2.amlogic.hevc.decoder","video/avc"));
  assertFalse(X5MDecoderSelector.permitted(
    "c2.amlogic.hevc.decoder","video/avc",
    "c2.amlogic.hevc.decoder","video/hevc"));
  assertFalse(X5MDecoderSelector.permitted(
    "c2.amlogic.hevc.decoder","audio/mp4a-latm",
    "c2.amlogic.hevc.decoder","audio/mp4a-latm"));
 }
}
