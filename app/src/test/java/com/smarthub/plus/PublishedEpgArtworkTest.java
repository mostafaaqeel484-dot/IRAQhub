package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PublishedEpgArtworkTest {
 @Test public void sameTitleAndSlotMaySupplyImage(){assertTrue(PublishedEpgArtwork.matchesEvent("Gold (2022)",1000000,2000000,"Gold (2022)",1000000,2000000));}
 @Test public void sameNameFromAnotherBroadcastIsRejected(){assertFalse(PublishedEpgArtwork.matchesEvent("Gold",1000000,2000000,"Gold",3000000,4000000));}
 @Test public void remakesAndMissingDatesAreRejected(){assertFalse(PublishedEpgArtwork.matchesEvent("Gold (2022)",1000000,2000000,"Gold (2016)",1000000,2000000));assertFalse(PublishedEpgArtwork.matchesEvent("Gold",0,0,"Gold",1000000,2000000));}
 @Test public void channelsMustHaveExactCanonicalNames(){assertTrue(PublishedEpgArtwork.matchesChannel("HBO2 HD","HBO2 HD"));assertFalse(PublishedEpgArtwork.matchesChannel("HBO2 HD","HBO HD"));assertFalse(PublishedEpgArtwork.matchesChannel("",""));}
}
