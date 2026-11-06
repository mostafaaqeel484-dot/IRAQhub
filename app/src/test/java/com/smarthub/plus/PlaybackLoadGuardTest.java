package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackLoadGuardTest {
 @Test public void protectsDvbAndDemandingStreams(){
  assertFalse(PlaybackLoadGuard.shouldDefer(true,false,false,false,false));
  assertTrue(PlaybackLoadGuard.shouldDefer(true,false,true,false,false));
  assertTrue(PlaybackLoadGuard.shouldDefer(false,true,true,false,false));
  assertTrue(PlaybackLoadGuard.shouldDefer(false,true,false,true,false));
  assertTrue(PlaybackLoadGuard.shouldDefer(false,true,false,false,true));
  assertFalse(PlaybackLoadGuard.shouldDefer(false,true,false,false,false));
  assertFalse(PlaybackLoadGuard.shouldDefer(false,false,true,true,true));
 }
}
