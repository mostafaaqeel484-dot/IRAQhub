package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvbVideoHealthTest {
 @Test public void unavailableTelemetryNeverMeansStall(){
  DvbVideoHealth h=new DvbVideoHealth();
  assertEquals(DvbVideoHealth.State.NO_TELEMETRY,h.sample(0,-1));
  assertEquals(DvbVideoHealth.State.NO_TELEMETRY,h.sample(20000,-1));
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(21000,100));
 }
 @Test public void detectsTwelveSecondsWithoutProgressAndRecovers(){
  DvbVideoHealth h=new DvbVideoHealth();
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(1000,20));
  assertEquals(DvbVideoHealth.State.ADVANCING,h.sample(2000,21));
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(13999,21));
  assertEquals(DvbVideoHealth.State.STALLED,h.sample(14000,21));
  assertEquals(DvbVideoHealth.State.ADVANCING,h.sample(15000,22));
 }
 @Test public void resetOrFrameCounterRollbackStartsNewGracePeriod(){
  DvbVideoHealth h=new DvbVideoHealth();
  h.sample(0,1000);
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(15000,10));
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(20000,10));
  h.reset();
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(25000,12));
 }
 @Test public void negativeOrRewoundClockDoesNotCreateFalseStall(){
  DvbVideoHealth h=new DvbVideoHealth();
  h.sample(12000,10);
  assertEquals(DvbVideoHealth.State.WAITING,h.sample(10000,10));
 }
}
