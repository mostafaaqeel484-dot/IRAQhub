package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** No live network or receiver is needed for the identity and privacy gates. */
public class Rx6NewProvidersTest {
 @Test public void animeResultsRequireExactNames(){
  assertTrue(AnimeMetadataProvider.exact("One Piece","ONE PIECE"));
  assertFalse(AnimeMetadataProvider.exact("One Piece","One Piece Film: Red"));
  assertFalse(AnimeMetadataProvider.exact("OSN","OSN Movies"));
  assertFalse(AnimeMetadataProvider.exact("","Anything"));
 }
 @Test public void posterUrlsAreHttpsAndPinnedToKnownHosts(){
  assertTrue(AnimeMetadataProvider.safeArt("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21.jpg","s4.anilist.co"));
  assertTrue(AnimeMetadataProvider.safeArt("https://media.kitsu.app/anime/poster.jpg","media.kitsu.app"));
  assertFalse(AnimeMetadataProvider.safeArt("http://s4.anilist.co/cover.jpg","s4.anilist.co"));
  assertFalse(AnimeMetadataProvider.safeArt("https://s4.anilist.co.evil.test/cover.jpg","s4.anilist.co"));
  assertFalse(AnimeMetadataProvider.safeArt("https://user@s4.anilist.co/file.jpg","s4.anilist.co"));
 }
 @Test public void importedReceiverKeyCannotBeProviderId(){
  assertEquals("-70|11042|27500|10101",ReceiverDbCrosswalk.key(-70,11042,27500,10101));
  assertEquals("130|11269|27500|6",ReceiverDbCrosswalk.key(130,11269,27500,6));
 }
 @Test public void animeSourcesAreIndependentFromFactoryEpg(){
  assertNotEquals(PosterSourceSwitches.ANILIST,PosterSourceSwitches.KITSU);
  assertNotEquals(PosterSourceSwitches.ANIDB,PosterSourceSwitches.TMDB);
 }
}
