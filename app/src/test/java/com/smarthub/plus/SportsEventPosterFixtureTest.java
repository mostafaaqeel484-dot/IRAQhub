package com.smarthub.plus;

import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

/**
 * Real, source-observed TheSportsDB v1 sample returned on 2026-11-07.
 * This tests parsing and exact-ID linkage, NOT image download or Android rendering.
 */
public final class SportsEventPosterFixtureTest {
 @Test public void eventPosterKeepsExactMatchAndTeamIdentity()throws Exception{
  LeagueRepository.League premier=LeagueRepository.byId("4328");
  assertNotNull(premier);
  JSONObject source=new JSONObject()
    .put("idEvent","2494097")
    .put("idLeague","4328")
    .put("strSport","Soccer")
    .put("strTimestamp","2026-11-07T12:30:00")
    .put("strHomeTeam","Leeds United")
    .put("strAwayTeam","Tottenham Hotspur")
    .put("idHomeTeam","133635")
    .put("idAwayTeam","133616")
    .put("strPoster","https://r2.thesportsdb.com/images/media/event/poster/g6c1qv1784549663.jpg")
    .put("strHomeTeamBadge","https://r2.thesportsdb.com/images/media/team/badge/jcgrml1756649030.png")
    .put("strAwayTeamBadge","https://r2.thesportsdb.com/images/media/team/badge/dfyfhl1604094109.png");
  SportsMatch match=TheSportsDBProvider.parse(source,premier,1700000000000L,false);
  assertNotNull(match);
  assertEquals("tsdb:2494097",match.id);
  assertEquals("133635",match.homeId);
  assertEquals("133616",match.awayId);
  assertEquals("4328",match.leagueId);
  assertEquals(Instant.parse("2026-11-07T12:30:00Z").toEpochMilli(),match.kickoff);
  assertEquals(source.getString("strPoster"),match.image);
  assertTrue(TeamLogoManager.trusted(match.image));
  assertTrue(TeamLogoManager.trusted(match.homeLogo));
  assertTrue(TeamLogoManager.trusted(match.awayLogo));
 }
 @Test public void mismatchedLeagueCannotSupplyMatchPoster()throws Exception{
  JSONObject wrong=new JSONObject()
    .put("idEvent","2494097").put("idLeague","9999")
    .put("strSport","Soccer").put("strHomeTeam","Leeds United")
    .put("strAwayTeam","Tottenham Hotspur")
    .put("strPoster","https://r2.thesportsdb.com/images/media/event/poster/g6c1qv1784549663.jpg");
  assertNull(TheSportsDBProvider.parse(wrong,LeagueRepository.byId("4328"),1700000000000L,false));
 }
}
