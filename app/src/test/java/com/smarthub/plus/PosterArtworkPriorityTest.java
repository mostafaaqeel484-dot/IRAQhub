package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterArtworkPriorityTest {
 @Test public void previousBetterMatchMustNeverPinOutTmdb(){
  assertFalse(PosterArtworkPriority.pinnedTmdbId("betterposters:tt0133093"));
  assertFalse(PosterArtworkPriority.pinnedTmdbId("tvmaze:3106"));
  assertFalse(PosterArtworkPriority.pinnedTmdbId("tt0133093"));
  assertTrue(PosterArtworkPriority.pinnedTmdbId("tmdb:603"));
  assertFalse(PosterArtworkPriority.pinnedTmdbId("tmdb:0"));
 }
 @Test public void exactImdbIsRequiredBeforeRatedImageLookups(){
  assertTrue(PosterArtworkPriority.exactImdb("tt0133093"));
  assertFalse(PosterArtworkPriority.exactImdb("The Matrix"));
  assertFalse(PosterArtworkPriority.exactImdb(""));
  assertTrue(PosterArtworkPriority.betterSource("BetterPosters"));
  assertFalse(PosterArtworkPriority.betterSource("TMDB"));
  assertTrue(PosterArtworkPriority.betterSource("BetterPosters · IMDb"));
   assertTrue(PosterArtworkPriority.attemptBetterFirst(true,false));
   assertFalse(PosterArtworkPriority.attemptBetterFirst(false,false));
   assertFalse(PosterArtworkPriority.attemptBetterFirst(true,true));
 }
}
