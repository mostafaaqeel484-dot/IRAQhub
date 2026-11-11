package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class SatelliteStateTest {
 @Test public void imageMovesWithUpcomingProgrammeAndExpires(){SatelliteState s=new SatelliteState();s.title="First";s.image="first.jpg";s.endMs=200;s.nextTitle="Second";s.nextImage="second.jpg";s.nextStartMs=200;s.nextEndMs=400;s.expire(200);assertEquals("second.jpg",s.image);assertEquals("",s.nextImage);s.expire(400);assertEquals("",s.image);}
 @Test public void knownUpcomingProgrammeBecomesCurrentAtItsRealEpgBoundary(){SatelliteState s=new SatelliteState();s.title="First";s.startMs=100;s.endMs=200;s.nextTitle="Second";s.nextStartMs=200;s.nextEndMs=400;s.expire(200);assertEquals("Second",s.title);assertEquals(200,s.startMs);assertEquals(400,s.endMs);assertEquals("",s.nextTitle);}
 @Test public void aFutureGapDoesNotInventACurrentProgramme(){SatelliteState s=new SatelliteState();s.title="First";s.endMs=200;s.nextTitle="Second";s.nextStartMs=300;s.nextEndMs=400;s.expire(250);assertEquals("",s.title);assertEquals("Second",s.nextTitle);s.expire(300);assertEquals("Second",s.title);}
 @Test public void aReusedChannelNumberDoesNotReuseAnotherChannelsEpg(){SatelliteState a=new SatelliteState(),b=new SatelliteState();a.channelId=b.channelId="Aurum:3";a.channelName="Film Channel";b.channelName="Sports Channel";assertFalse(SatelliteUiParser.sameChannel(a,b));}
}
