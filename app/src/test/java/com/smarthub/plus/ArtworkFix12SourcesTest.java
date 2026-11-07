package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fix 12: verify explicit image domains and truly free licences offline. */
public class ArtworkFix12SourcesTest {
 @Test public void tvmazeImagesMustUseExactCdnAndHttps(){
  assertEquals("https://static.tvmaze.com/uploads/images/medium_portrait/1/2.jpg",
    TvmazeProvider.image("https://static.tvmaze.com/uploads/images/medium_portrait/1/2.jpg"));
  assertEquals("",TvmazeProvider.image("http://static.tvmaze.com/a.jpg"));
  assertEquals("",TvmazeProvider.image("https://static.tvmaze.com.evil.test/a.jpg"));
  assertEquals("",TvmazeProvider.image("https://attacker@static.tvmaze.com/a.jpg"));
  assertEquals("",TvmazeProvider.image(""));
 }
 @Test public void commonsKeepsOnlyFreedomCompatibleLicences(){
  assertTrue(CommonsArtworkProvider.acceptedLicense("CC BY 4.0"));
  assertTrue(CommonsArtworkProvider.acceptedLicense("CC BY-SA 4.0"));
  assertTrue(CommonsArtworkProvider.acceptedLicense("CC0 1.0"));
  assertTrue(CommonsArtworkProvider.acceptedLicense("Public domain"));
  assertFalse(CommonsArtworkProvider.acceptedLicense("CC BY-NC 4.0"));
  assertFalse(CommonsArtworkProvider.acceptedLicense("CC BY-ND 4.0"));
  assertFalse(CommonsArtworkProvider.acceptedLicense("All rights reserved"));
  assertFalse(CommonsArtworkProvider.acceptedLicense(""));
 }
 @Test public void sportsSourceUsesOnlyDocumentedPublicV1Key(){
  assertEquals("https://www.thesportsdb.com/api/v1/json/123/",
    TheSportsDBProvider.API);
 }
}
