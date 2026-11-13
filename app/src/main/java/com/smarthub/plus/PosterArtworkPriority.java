package com.smarthub.plus;
/** First automatic poster provider is BetterPosters. A manual user selection
 * always has priority, and title similarity alone never invents an IMDb ID. */
final class PosterArtworkPriority {
 private PosterArtworkPriority(){}
 static boolean pinnedTmdbId(String value){
  return value!=null&&value.matches("tmdb:[1-9][0-9]*");
 }
 static boolean exactImdb(String value){
  return value!=null&&value.matches("tt[0-9]{7,10}");
 }
 static boolean betterSource(String value){
  return value!=null&&value.startsWith("BetterPosters");
 }
 static boolean attemptBetterFirst(boolean enabled,boolean manualChoice){
  return enabled&&!manualChoice;
 }
}
