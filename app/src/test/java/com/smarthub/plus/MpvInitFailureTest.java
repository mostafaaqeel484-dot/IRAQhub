package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public class MpvInitFailureTest {
 @Test public void includesMissingClassNameAndRoot(){
  NoClassDefFoundError missing=new NoClassDefFoundError("Failed resolution of Ldev/jdtech/mpv/MPVLib;");
  missing.initCause(new ClassNotFoundException("dev.jdtech.mpv.MPVLib"));
  String detail=MpvInitFailure.describe(missing);
  assertTrue(detail.contains("NoClassDefFoundError"));
  assertTrue(detail.contains("dev.jdtech.mpv.MPVLib"));
  assertTrue(detail.contains("ClassNotFoundException"));
 }
 @Test public void capturesNativeDlopenInsteadOfHidingError(){
  String detail=MpvInitFailure.describe(
   new UnsatisfiedLinkError("dlopen failed: library libplayer.so not found"));
  assertTrue(detail.contains("UnsatisfiedLinkError"));
  assertTrue(detail.contains("libplayer.so"));
 }
 @Test public void limitsLogMessageAndEliminatesNewlines(){
  String result=MpvInitFailure.describe(new Error("X\n".repeat(900)));
  assertTrue(result.length()<=640);
  assertFalse(result.contains("\n"));
 }
}