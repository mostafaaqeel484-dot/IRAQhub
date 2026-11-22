package com.smarthub.plus;

/** RX7's single user-facing 4:2:2 compatibility choice.
 * Do not auto-launch software decoders on ordinary 4:2:0 channels.
 * Direct libavcodec is attempted only on explicit 4:2:2 playback actions,
 * when the signed ARMv7 JNI library was really loaded, and Safe Mode is off.
 * Existing MPV fallback remains available without retuning the tuner.
 */
final class Rx7Compat422Policy {
 static final String ENABLED="rx7_direct422_enabled";
 static final String DEFAULT_OUTPUT="420_450";
 private Rx7Compat422Policy(){}
 static boolean direct(boolean compiled,boolean enabled,boolean safeMode){
  return compiled&&enabled&&!safeMode;
 }
 static boolean isCaptureMissing(String error){
  return "CAPTURE_NO_MPEG_TS".equals(error)||
    "CAPTURE_NO_TS_DATA".equals(error)||
    "CAPTURE_STORAGE_UNAVAILABLE".equals(error);
 }
 static int fpsX100(long previous,long current,long elapsedMs){
  if(previous<0||current<previous||elapsedMs<250)return 0;
  return (int)Math.min(60000,Math.max(0,((current-previous)*100000L)/elapsedMs));
 }
 static boolean moving(long previous,long current,long elapsedMs){
  return elapsedMs>=250&&previous>=0&&current>previous;
 }
}
