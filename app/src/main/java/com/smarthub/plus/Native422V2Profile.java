package com.smarthub.plus;

/**
 * 4.0.1 Native422 pipeline V2, intentionally experimental.
 * Real software decoding remains FFmpeg INSIDE packaged libmpv 1.0.0.
 * This is a new safe TS/session orchestration profile, NOT a new codec JNI
 * implementation and NOT proven capable of 50fps 10-bit 4:2:2 on X5M.
 */
final class Native422V2Profile {
 static final String ID="native_v2";
 private Native422V2Profile(){}
 static boolean enabled(String selection){return ID.equals(selection);}
 static String renderMode(){return Mpv422FluidPolicy.GPU_NATIVE;}
 static String decoderMode(){return Mpv422FluidPolicy.SW;}
 static boolean videoOnly(){return true;}
 static boolean allowsHardware(boolean actualVerified){
  // Hardware 4:2:2 support on ARMv7 X5M is not verified.
  return actualVerified;
 }
}
