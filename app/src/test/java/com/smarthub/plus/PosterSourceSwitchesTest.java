package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterSourceSwitchesTest {
 @Test public void independentKeysForEveryPosterProvider(){
  String[] keys={PosterSourceSwitches.TMDB,PosterSourceSwitches.OPDB,
   PosterSourceSwitches.RPDB,PosterSourceSwitches.BETTER,
   PosterSourceSwitches.TVMAZE,PosterSourceSwitches.SPORTS,
   PosterSourceSwitches.ARCHIVE,PosterSourceSwitches.COMMONS};
  java.util.HashSet<String> unique=new java.util.HashSet<>();
  for(String key:keys){assertTrue(PosterSourceSwitches.known(key));assertTrue(unique.add(key));}
  assertEquals(8,unique.size());
  assertFalse(PosterSourceSwitches.known("art_source_unverified_enabled"));
 }
}
