package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Identity guard only; image decoding and actual display need device tests. */
public class SportsProgrammeArtworkTest {
 private SportsMatch complete(){
  SportsMatch m=new SportsMatch();
  m.id="tsdb:2494097";
  m.leagueId="4328";
  m.homeId="133635";
  m.awayId="133616";
  m.home="Leeds United";m.away="Tottenham Hotspur";
  m.kickoff=1794054600000L;
  return m;
 }
 @Test public void sourceEventAndBothTeamIdsAreMandatory(){
  SportsMatch m=complete();
  assertTrue(SportsProgrammeArtwork.verified(m));
  m.id="guessed:2494097";assertFalse(SportsProgrammeArtwork.verified(m));
  m=complete();m.awayId="";assertFalse(SportsProgrammeArtwork.verified(m));
  m=complete();m.leagueId="";assertFalse(SportsProgrammeArtwork.verified(m));
  m=complete();m.kickoff=0;assertFalse(SportsProgrammeArtwork.verified(m));
 }
}
