package com.smarthub.plus;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SatelliteGuideRepositoryTest {
    private static final long NOW=1_800_000_000_000L;
    private static SatelliteGuideRepository.Event event(String id,String xml,String title,long start,long end){SatelliteGuideRepository.Event e=new SatelliteGuideRepository.Event();e.id=id;e.xmlId=xml;e.title=title;e.channelName="Cinema HD";e.startMs=start;e.endMs=end;return e;}
    private static SatelliteState state(){SatelliteState s=new SatelliteState();s.channelId="native-42";s.channelName="Cinema HD";s.title="Current film";s.startMs=NOW-1000;s.endMs=NOW+10000;s.observedAt=NOW;s.source="Receiver UI";return s;}
    @Test public void unknownTimesStaleObservationsAndWrongChannelNeverBecomeLiveEvents(){
        SatelliteState s=state();assertEquals(1,SatelliteGuideRepository.observedEvents(s,NOW).size());
        s.startMs=0;assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());s=state();s.endMs=NOW;assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());
        s=state();s.observedAt=NOW-120001;assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());s=state();s.observedAt=NOW+61000;assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());
        s=state();s.channelId="";assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());s=state();s.title="";assertTrue(SatelliteGuideRepository.observedEvents(s,NOW).isEmpty());
    }
    @Test public void nextProgrammeNeedsRealFutureTimesAndRetainsItsNativeChannelBinding(){
        SatelliteState s=state();s.nextTitle="Next film";s.nextStartMs=NOW+10000;s.nextEndMs=NOW+20000;
        List<SatelliteGuideRepository.Event> found=SatelliteGuideRepository.observedEvents(s,NOW);assertEquals(2,found.size());assertEquals("native-42",found.get(1).channelId);assertEquals(NOW+10000,found.get(1).startMs);
        s.nextStartMs=0;assertEquals(1,SatelliteGuideRepository.observedEvents(s,NOW).size());
    }
    @Test public void exactUniqueAliasesResolveButAmbiguousOrPartialNamesDoNotGuess()throws Exception{
        Map<String,List<String>> aliases=SatelliteGuideRepository.parseAliases("<tv><channel id='cinema.uk'><display-name>Cinema HD</display-name><display-name>سينما</display-name></channel><channel id='sport'><display-name>Sport HD</display-name></channel></tv>");
        SatelliteState s=state();assertEquals("cinema.uk",SatelliteGuideRepository.resolveXmlId(s,"",aliases));s.channelName="سينما";assertEquals("cinema.uk",SatelliteGuideRepository.resolveXmlId(s,"",aliases));s.channelName="Cinema";assertEquals("",SatelliteGuideRepository.resolveXmlId(s,"",aliases));
        s.channelName="Cinema HD";aliases.put("other.cinema",Arrays.asList("Cinema HD"));assertEquals("",SatelliteGuideRepository.resolveXmlId(s,"",aliases));assertEquals("other.cinema",SatelliteGuideRepository.resolveXmlId(s,"other.cinema",aliases));
    }
    @Test public void xmlAliasReaderRefusesExternalEntitiesAndNonTvRoot()throws Exception{
        for(String xml:new String[]{"<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///secret'>]><tv><channel id='a'><display-name>&x;</display-name></channel></tv>","<other><channel id='a'/></other>"}){
            try{SatelliteGuideRepository.parseAliases(xml);fail("Unsafe XML accepted");}catch(Exception expected){}
        }
    }
    @Test public void currentScheduleUsesExactChannelAndEndExclusiveTimesWithoutRevivingOldRows(){
        List<SatelliteGuideRepository.Event> entries=Arrays.asList(event("old","a","Old",NOW-20000,NOW-10000),event("current","a","Now",NOW-1000,NOW+1000),event("other","b","Wrong channel",NOW-1000,NOW+1000));
        assertEquals("Now",SatelliteGuideRepository.current(entries,"a",NOW).title);assertNull(SatelliteGuideRepository.current(entries,"a",NOW+1000));assertNull(SatelliteGuideRepository.current(entries,"unknown",NOW));assertNull(SatelliteGuideRepository.current(entries,"",NOW));
    }
    @Test public void smartSearchNormalizesArabicAcceptsT9AndOnlyFlagsSourceMarkedRepeats(){
        SatelliteGuideRepository.Event e=event("a","a","الأخبار العربية",NOW,NOW+10000);assertTrue(SatelliteGuideRepository.matches(e,"الاخبار",NOW,false));assertTrue(SatelliteGuideRepository.matches(e,PlaylistRepository.t9("العربية"),NOW,false));assertFalse(SatelliteGuideRepository.matches(e,"repeat",NOW,false));e.repeat=true;assertTrue(SatelliteGuideRepository.matches(e,"إعادة",NOW,false));
        e.endMs=NOW-1;e.startMs=NOW-10000;assertFalse(SatelliteGuideRepository.matches(e,"",NOW,false));assertTrue(SatelliteGuideRepository.matches(e,"",NOW,true));
    }
    @Test public void mergingUpdatesTheSameEventWithoutLosingOtherChannelsOrMutatingInputs(){
        SatelliteGuideRepository.Event a=event("same","a","Original",NOW,NOW+1000),b=event("other","b","Keep",NOW,NOW+2000),update=event("same","a","Corrected",NOW,NOW+3000);
        List<SatelliteGuideRepository.Event> merged=SatelliteGuideRepository.mergeEvents(Arrays.asList(a,b),Arrays.asList(update));assertEquals(2,merged.size());assertEquals("Original",a.title);assertTrue(merged.stream().anyMatch(e->e.title.equals("Corrected")&&e.endMs==NOW+3000));assertTrue(merged.stream().anyMatch(e->e.title.equals("Keep")));
    }
    @Test public void reminderChecksRejectEarlyExpiredChangedAndWrongPhaseAlarms(){
        SatelliteGuideRepository.Event e=event("a","a","Film",NOW,NOW+3600000);
        assertTrue(SatelliteGuideRepository.reminderDue(e,"start",NOW,NOW));assertFalse(SatelliteGuideRepository.reminderDue(e,"start",NOW,NOW-2000));assertFalse(SatelliteGuideRepository.reminderDue(e,"start",NOW,NOW+11*60000));assertFalse(SatelliteGuideRepository.reminderDue(e,"start",NOW+1,NOW));assertFalse(SatelliteGuideRepository.reminderDue(e,"wrong",NOW,NOW));
        assertTrue(SatelliteGuideRepository.reminderDue(e,"end",e.endMs,e.endMs));assertFalse(SatelliteGuideRepository.reminderDue(e,"end",e.endMs,e.endMs-2000));assertFalse(SatelliteGuideRepository.reminderDue(e,"start",NOW,e.endMs));
    }
}
