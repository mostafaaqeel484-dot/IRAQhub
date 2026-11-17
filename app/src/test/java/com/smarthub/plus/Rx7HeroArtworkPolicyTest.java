package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Rx7HeroArtworkPolicyTest {
 @Test public void sameProgrammeRefreshMustKeepPreviouslyShownArtwork(){
  assertFalse(Rx7HeroArtworkPolicy.eventChanged("DVB:44|Movie","DVB:44|Movie"));
  assertTrue(Rx7HeroArtworkPolicy.shouldRefresh("DVB:44|Movie","DVB:44|Movie",50000,40000));
  assertTrue(Rx7HeroArtworkPolicy.eventChanged("DVB:44|Movie","DVB:44|News"));
 }
 @Test public void neverTryToInventPosterWithoutChannelOrProgramme(){
  assertFalse(Rx7HeroArtworkPolicy.canLoadPoster("","Movie"));
  assertFalse(Rx7HeroArtworkPolicy.canLoadPoster("DVB:1",""));
  assertTrue(Rx7HeroArtworkPolicy.canLoadPoster("DVB:1","Verified programme"));
 }
}
