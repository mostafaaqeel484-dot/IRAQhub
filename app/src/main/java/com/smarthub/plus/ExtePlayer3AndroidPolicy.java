package com.smarthub.plus;

/**
 * Experimental Android adaptation of upstream ExtePlayer3's MPEG-TS PTS
 * tracking core, NOT a binary copy of the Enigma2 LinuxDVB player.
 * Its upstream pts_clock.h is compiled into our FFmpeg/ANativeWindow JNI.
 */
final class ExtePlayer3AndroidPolicy {
 static final String ID="exteplayer3_android";
 static final String ORIGINAL="gpu_native";
 static final String SMOOTH="420_450";
 private ExtePlayer3AndroidPolicy(){}
 static boolean isNativeRoute(String requested){
  return ID.equals(requested)||"jni_ffmpeg".equals(requested);
 }
 static boolean prefer(boolean nativeAvailable,boolean selected,boolean safe){
  return Rx7Compat422Policy.direct(nativeAvailable,selected,safe);
 }
 static String toggleOutput(String chosen){
  return ORIGINAL.equals(chosen)?SMOOTH:ORIGINAL;
 }
 static String display(String chosen){
  return ORIGINAL.equals(chosen)?"Original source · up to 1920×1080":
    SMOOTH.equals(chosen)?"800×450 · 4:2:0 output":chosen;
 }
}
