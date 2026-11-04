package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class BroadcastTitleTest {
 @Test public void episodeWithoutSeasonCanFindArtworkWithoutInventingASeason(){BroadcastTitle t=BroadcastTitle.parse("Iyanu:Episode 10");assertEquals("Iyanu",t.title);assertEquals(10,t.episode);assertEquals(0,t.season);}
 @Test public void cleansExplicitLabelsQualityYearAndEpisodeWithoutChangingTitleWords(){BroadcastTitle t=BroadcastTitle.parse("Movie: Inception (2010) HD");assertEquals("Inception",t.title);assertEquals(2010,t.year);t=BroadcastTitle.parse("مسلسل: Dark S٠٢E٠٣ FHD");assertEquals("Dark",t.title);assertEquals(2,t.season);assertEquals(3,t.episode);assertEquals("The Movie",BroadcastTitle.parse("The Movie").title);}
 @Test public void exactAliasAndYearAreRequiredForAutomaticMatch(){MediaRepository.Item i=new MediaRepository.Item();i.title="Dune";i.kind="film";i.release="2021-09-01";i.matchTitles.add("كثيب");assertTrue(BroadcastTitle.parse("فيلم: كثيب (2021)").matches(i));assertFalse(BroadcastTitle.parse("Dune (1984)").matches(i));assertFalse(BroadcastTitle.parse("Dune Part Two").matches(i));}
}
