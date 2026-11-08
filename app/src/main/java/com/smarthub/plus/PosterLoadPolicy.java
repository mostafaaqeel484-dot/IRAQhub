package com.smarthub.plus;

/**
 * Poster load budget. Existing cached artwork may be decoded into tiny OSD
 * bitmaps during software 4:2:2; remote provider/network work is deferred.
 * Never infer a poster from a bare or mismatched programme title.
 */
final class PosterLoadPolicy {
 private PosterLoadPolicy(){}
 static boolean cacheAllowed(boolean enabled,String title){
  return enabled&&title!=null&&!title.trim().isEmpty();
 }
 static boolean networkAllowed(boolean enabled,boolean decoderFocus,
       boolean software422,boolean tuning,String title){
  return cacheAllowed(enabled,title)&&!decoderFocus&&!software422&&!tuning;
 }
}
