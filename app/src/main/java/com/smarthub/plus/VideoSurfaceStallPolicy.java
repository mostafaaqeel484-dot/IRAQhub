package com.smarthub.plus;

/** Distinguishes a moving playback clock from a moving output Surface.
 * Multiple identical tiny PixelCopy samples are only a freeze *suspicion*:
 * static menus, slides and station identifications can legitimately be still.
 * Limit automatic recovery to one attempt in a live DVB session.
 */
final class VideoSurfaceStallPolicy {
 private VideoSurfaceStallPolicy(){}
 static final int SAMPLE_INTERVAL_MS=4800;
 static int identicalSamples(int previous,long previousHash,long currentHash,
                             boolean captured){
  if(!captured)return 0;
  return previousHash==currentHash?Math.min(12,previous+1):0;
 }
 static boolean probableStall(int unchangedSamples,long elapsedMs,
       long sincePlaybackClockProgressMs,boolean relayRunning,
       boolean alreadyRecovered){
  return !alreadyRecovered&&relayRunning&&unchangedSamples>=4&&
     elapsedMs>=19000&&sincePlaybackClockProgressMs<=9500;
 }
}
