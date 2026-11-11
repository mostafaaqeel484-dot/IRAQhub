package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class DecoderStaleRecoveryTest {
 @Test public void staleDecoderFlagAfterProcessDeathMustReleaseEPGPosterScheduler(){
  assertTrue(DecoderStaleRecovery.shouldClear(false,true,false));
  assertTrue(DecoderStaleRecovery.shouldClear(false,false,true));
  assertTrue(DecoderStaleRecovery.shouldClear(false,true,true));
 }
 @Test public void activeDecoderLeaseCannotBeClearedByLauncherOpening(){
  assertFalse(DecoderStaleRecovery.shouldClear(true,true,true));
  assertFalse(DecoderStaleRecovery.shouldClear(true,false,true));
 }
 @Test public void normalLauncherNeedsNoRecovery(){
  assertFalse(DecoderStaleRecovery.shouldClear(false,false,false));
 }
}
