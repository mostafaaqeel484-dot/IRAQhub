package com.smarthub.plus;
/** Pure stable identities for source precedence; title matches alone must not
 * prevent TMDB from being searched when its key becomes configured. */
final class PosterArtworkPriority {
 private PosterArtworkPriority(){}
 static boolean pinnedTmdbId(String value){
  return value!=null&&value.matches("tmdb:[1-9][0-9]*");
 }
 static boolean exactImdb(String value){
  return value!=null&&value.matches("tt[0-9]{7,10}");
 }
 static boolean preferAlternativeOverBetter(String provider,String imdb){
  return "BetterPosters".equals(provider)&&exactImdb(imdb);
 }
}
