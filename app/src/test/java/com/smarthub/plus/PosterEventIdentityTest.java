package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real Aurum trace: current and next can both be named "Eastern News Channel". */
public class PosterEventIdentityTest {
 @Test public void sameTitleDifferentAirtimesNeverShareAPosterKey(){
  SatelliteState s=new SatelliteState();
  s.channelId="Aurum:35";
  s.channelName="AL SHARQIYA NEWS HD";
  s.title="Eastern News Channel";
  s.nextTitle="Eastern News Channel";
  s.startMs=1793980800000L;
  s.nextStartMs=1793984400000L;
  String current=PosterSelection.key(s,s.title,false);
  String upcoming=PosterSelection.key(s,s.nextTitle,true);
  assertFalse(current.isEmpty());assertFalse(upcoming.isEmpty());
  assertNotEquals(current,upcoming);
 }
 @Test public void satelliteAndChannelScopePreventCrossChannelLeakage(){
  SatelliteState first=new SatelliteState();
  first.channelId="Aurum:35";first.title="Movie Night";first.startMs=1793980800000L;
  SatelliteState second=first.copy();second.channelId="Aurum:36";
  assertNotEquals(PosterSelection.key(first,first.title,false),PosterSelection.key(second,second.title,false));
 }
 @Test public void noEventOrWrongTitleCannotBindAPoster(){
  SatelliteState s=new SatelliteState();s.channelId="Aurum:35";s.title="Movie";s.startMs=0;
  assertEquals("",PosterSelection.key(s,"Movie",false));
  s.startMs=100000L;
  assertEquals("",PosterSelection.key(s,"Another movie",false));
  assertEquals("",PosterSelection.key(s,"Movie",true));
 }
}
