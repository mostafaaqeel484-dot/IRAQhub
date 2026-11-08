package com.smarthub.plus;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class SportsDataTest {
 private JSONObject event()throws Exception{return new JSONObject().put("idEvent","1").put("idLeague","4328").put("strSport","Soccer").put("idHomeTeam","10").put("idAwayTeam","20").put("strHomeTeam","Arsenal").put("strAwayTeam","Liverpool").put("strTimestamp","2026-11-01T16:30:00").put("strStatus","NS");}
 @Test public void englishLeagueCannotAcceptEgyptianLeagueResults()throws Exception{JSONObject e=event().put("idLeague","4615");assertNull(TheSportsDBProvider.parse(e,LeagueRepository.byId("4328"),1,false));}
 @Test public void unknownScoreIsNotInventedZeroZero()throws Exception{SportsMatch m=TheSportsDBProvider.parse(event(),LeagueRepository.byId("4328"),1,false);assertEquals("— : —",m.score());assertFalse(m.live());assertTrue(m.time().contains("19:30"));}
 @Test public void pastKickoffDoesNotImplyLive()throws Exception{SportsMatch m=TheSportsDBProvider.parse(event().put("strTimestamp","2020-01-01T00:00:00").put("strStatus","UNKNOWN"),LeagueRepository.byId("4328"),1,false);assertFalse(m.live());}
 @Test public void offlineLiveSnapshotIsClearlyUnconfirmed()throws Exception{SportsMatch m=TheSportsDBProvider.parse(event().put("strStatus","1H"),LeagueRepository.byId("4328"),System.currentTimeMillis(),true);assertTrue(m.state().contains("unconfirmed"));}
 @Test public void epgMatchingNeedsBothTeamsAndWholeWords(){assertTrue(MatchRepository.programmeMatches("Liverpool vs. Arsenal","Liverpool","Arsenal"));assertFalse(MatchRepository.programmeMatches("Sports live: Liverpool","Liverpool","Arsenal"));assertFalse(MatchRepository.programmeMatches("Arsenal Women v Chelsea","Arsenal","Chelsea Men"));}
 @Test public void iraqiCupDoesNotHaveAnInventedProviderId(){assertTrue(LeagueRepository.ALL.stream().anyMatch(l->l.name.equals("Iraqi FA Cup")&&l.id.isEmpty()));}
 @Test public void iraqiStarsLeagueAcceptsVerifiedHistoricalSourceName(){assertTrue(LeagueRepository.nameMatches(LeagueRepository.byId("5056"),"Iraqi Premier League"));assertFalse(LeagueRepository.nameMatches(LeagueRepository.byId("4328"),"Egyptian Premier League"));}
 @Test public void arbitraryImageHostsAndHugeEmbeddedImagesAreRejected(){assertFalse(TeamLogoManager.trusted("https://192.168.0.1/private"));assertFalse(TeamLogoManager.trusted("javascript:alert(1)"));assertTrue(TeamLogoManager.trusted("https://r2.thesportsdb.com/images/badge.png"));assertTrue(TeamLogoManager.trusted("data:image/png;base64,aGVsbG8="));}
 @Test public void matchStoragePreservesSourceIdentityAndTimestamp()throws Exception{SportsMatch a=TheSportsDBProvider.parse(event(),LeagueRepository.byId("4328"),123456,false),b=SportsMatch.from(a.json());assertEquals(a.id,b.id);assertEquals(a.homeId,b.homeId);assertEquals(a.kickoff,b.kickoff);assertEquals(123456,b.updatedAt);}
 @Test public void tunerValuesKeepUnitsAndScientificBer(){assertEquals("2.3E-7",ReceptionData.ber("BER: 2.3E-7"));assertEquals("7.25 Mbps",ReceptionData.bitrate("7.25 Mbps"));assertEquals("",ReceptionData.bitrate("1080p"));}
 @Test public void similarAbbreviationsMatchBothRealTeams(){
  assertTrue(MatchRepository.programmeMatches("Man Utd v Liverpool",
    "Manchester United","Liverpool FC"));
  assertFalse(MatchRepository.programmeMatches("Man Utd v Liverpool",
    "Manchester United","Manchester City"));
 }
 @Test public void duplicateSportProvidersPreferActualPoster(){
  long now=1700000000000L,at=now+3600000L;
  SportsMatch a=new SportsMatch();
  a.id="openliga:124";a.kickoff=at;a.home="Manchester United";
  a.away="Liverpool FC";
  SportsMatch b=new SportsMatch();
  b.id="tsdb:300";b.kickoff=at+120000L;b.home="Man Utd";
  b.away="Liverpool";b.image="https://r2.thesportsdb.com/images/fixture.jpg";
  assertSame(b,MatchRepository.selectForProgramme(
    java.util.Arrays.asList(a,b),"Man Utd vs Liverpool",at,at+7200000L,now));
  assertNull(MatchRepository.selectForProgramme(
    java.util.Arrays.asList(a,b),"Man Utd vs Liverpool",at,at+7200000L,at+7300000L));
 }
 @Test public void distinctKickoffsRemainAmbiguous(){
  long now=1700000000000L,at=now+3600000L;
  SportsMatch a=new SportsMatch();a.id="tsdb:1";a.kickoff=at;
  a.home="Arsenal";a.away="Liverpool";
  SportsMatch b=new SportsMatch();b.id="tsdb:2";b.kickoff=at+7200000L;
  b.home="Arsenal";b.away="Liverpool";
  assertNull(MatchRepository.selectForProgramme(java.util.Arrays.asList(a,b),
    "Arsenal v Liverpool",at,at+10000000L,now));
 }
}
