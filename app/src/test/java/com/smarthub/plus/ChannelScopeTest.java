package com.smarthub.plus;
import org.junit.Test;import static org.junit.Assert.*;
public class ChannelScopeTest {
 @Test public void restoresExactListIncludingGroupOrder(){ChannelScope s=ChannelScope.parse(new ChannelScope("MBC 2",32,0,true,true,1000002).json());assertEquals("MBC 2",s.query);assertEquals(32,s.satellite);assertEquals(1000002,s.group);assertTrue(s.favorites);assertTrue(s.alphabetical);}
 @Test public void tvRadioSwitchClearsSearchButKeepsSatelliteAndFavourites(){ChannelScope s=new ChannelScope("sport",32,0,true,false,5).kind(1);assertEquals(1,s.type);assertEquals("",s.query);assertEquals(32,s.satellite);assertTrue(s.favorites);assertEquals(5,s.group);}
 @Test public void invalidStateDoesNotBecomeUnboundedOrUnknownType(){ChannelScope s=ChannelScope.parse("broken");assertEquals(-1,s.satellite);assertEquals(0,s.type);assertEquals("",s.query);ChannelScope bounded=new ChannelScope("x",-20,100,false,false,-200);assertEquals(-1,bounded.group);assertEquals(0,bounded.type);}
}
