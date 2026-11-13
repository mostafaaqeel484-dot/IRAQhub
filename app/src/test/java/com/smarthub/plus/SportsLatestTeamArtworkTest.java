package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public final class SportsLatestTeamArtworkTest {
 @Test public void oneTeamCanDriveLatestEventLookup(){
  assertArrayEquals(new String[]{"Parma","Bologna"},
   SportsLatestTeamArtwork.fixtureTeams("Parma - Bologna"));
  assertArrayEquals(new String[]{"Manchester United","Hull City"},
   SportsLatestTeamArtwork.fixtureTeams("Man United - Hull City"));
  assertArrayEquals(new String[]{"Manchester United","Hull City"},
   SportsLatestTeamArtwork.fixtureTeams("LIVE: Man Utd vs Hull City"));
  assertEquals(0,SportsLatestTeamArtwork.fixtureTeams("The Dark Knight").length);
  assertEquals(0,SportsLatestTeamArtwork.fixtureTeams("Parma").length);
  assertEquals(0,SportsLatestTeamArtwork.fixtureTeams("Man Utd - Hull City - Leeds").length);
 }
 @Test public void newestPublishedTeamEventWinsNotDifferentClub()throws Exception{
  long now=Instant.parse("2026-11-13T18:00:00Z").toEpochMilli();
  String first="https://r2.thesportsdb.com/images/media/event/poster/old111.jpg";
  String newest="https://r2.thesportsdb.com/images/media/event/poster/new999.jpg";
  JSONObject older=new JSONObject()
    .put("idHomeTeam","100").put("idAwayTeam","200")
    .put("strSport","Soccer").put("strTimestamp","2026-11-02T11:00:00Z")
    .put("strPoster",first);
  JSONObject recent=new JSONObject()
    .put("idHomeTeam","100").put("idAwayTeam","400")
    .put("strSport","Soccer").put("strTimestamp","2026-11-10T11:00:00Z")
    .put("strPoster",newest);
  JSONObject other=new JSONObject()
    .put("idHomeTeam","700").put("idAwayTeam","800")
    .put("strSport","Soccer").put("strTimestamp","2026-11-12T11:00:00Z")
    .put("strPoster","https://r2.thesportsdb.com/images/media/event/poster/wrong.jpg");
  JSONObject future=new JSONObject()
    .put("idHomeTeam","100").put("idAwayTeam","800")
    .put("strSport","Soccer").put("strTimestamp","2026-11-16T11:00:00Z")
    .put("strPoster","https://r2.thesportsdb.com/images/media/event/poster/future.jpg");
  JSONObject response=new JSONObject().put("results",
     new JSONArray().put(older).put(recent).put(other).put(future));
  assertEquals(newest,SportsLatestTeamArtwork.latestImage(response,"100",now));
  assertEquals("",SportsLatestTeamArtwork.latestImage(response,"999",now));
 }
 @Test public void teamSearchMustBeExactNotSimilarOrUnrelated()throws Exception{
  JSONObject man=new JSONObject().put("strTeam","Manchester United")
    .put("idTeam","133612");
  assertTrue(SportsLatestTeamArtwork.sameTeam("Man United",man));
  assertFalse(SportsLatestTeamArtwork.sameTeam("Manchester City",man));
  assertTrue(SportsLatestTeamArtwork.sameTeam("Parma",
    new JSONObject().put("strTeam","Parma")));
 }
 @Test public void acceptsSourceTeamAliasesAndRealEventFanart()throws Exception{
  JSONObject provider=new JSONObject().put("strTeam","Manchester United")
    .put("strTeamAlternate","Man Utd, Man United")
    .put("strTeamShort","Man Utd");
  assertTrue(SportsLatestTeamArtwork.sameTeam("Man Utd",provider));
  assertFalse(SportsLatestTeamArtwork.sameTeam("Manchester City",provider));
  long now=Instant.parse("2026-11-13T18:00:00Z").toEpochMilli();
  JSONObject latest=new JSONObject().put("idEvent","2494999")
    .put("idHomeTeam","133612").put("idAwayTeam","133617")
    .put("idLeague","4328").put("strLeague","Premier League")
    .put("strHomeTeam","Manchester United").put("strAwayTeam","Hull City")
    .put("strSport","Soccer").put("strTimestamp","2026-11-12T15:30:00Z")
    .put("strFanart","https://r2.thesportsdb.com/images/media/event/fanart/sample.jpg");
  JSONObject response=new JSONObject().put("results",new JSONArray().put(latest));
  assertEquals(latest.optString("strFanart"),
      SportsLatestTeamArtwork.latestImage(response,"133612",now));
  assertNotNull(SportsLatestTeamArtwork.eventCard(latest,now));
 }
 @Test public void sportsEventKeyWorksWithoutEpgTimeButNeverBorrowsChannel()throws Exception{
  SatelliteState a=new SatelliteState();
  a.channelId="DVB:190:11954:V:27500:100";
  a.channelName="Arena Sport";
  a.title="Parma - Bologna";
  String key=SportsLatestTeamArtwork.eventKey(a,a.title,false);
  assertFalse(key.isEmpty());
  SatelliteState b=a.copy();b.channelId="DVB:190:11954:V:27500:101";
  assertNotEquals(key,SportsLatestTeamArtwork.eventKey(b,b.title,false));
  assertEquals("",SportsLatestTeamArtwork.eventKey(a,"Manchester United - Hull City",false));
  assertEquals("",SportsLatestTeamArtwork.eventKey(a,"Football highlight",false));
  assertEquals("",SportsLatestTeamArtwork.eventKey(a,a.title,true));
 }
 @Test public void rejectUntrustedLatestImage()throws Exception{
  long now=Instant.parse("2026-11-13T18:00:00Z").toEpochMilli();
  JSONObject wrong=new JSONObject().put("idHomeTeam","100")
    .put("idAwayTeam","200").put("strSport","Soccer")
    .put("strTimestamp","2026-11-11T11:00:00Z")
    .put("strPoster","https://other.invalid/images/irrelevant.png");
  assertEquals("",SportsLatestTeamArtwork.latestImage(
    new JSONObject().put("results",new JSONArray().put(wrong)),"100",now));
 }
}
