package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public class AurumDvbLogoBridgeTest {
 @Test public void knownSkySportCaptureMapsOnlyExactPhysicalService(){
  assertEquals("Aurum:1|skysport4k",AurumDvbLogoBridge.verifiedLegacySource(
    "DVB:130:KU:11994:V:29900:11628","Sky Sport 4k"));
  assertEquals("",AurumDvbLogoBridge.verifiedLegacySource(
    "DVB:130:KU:11994:V:29900:11629","Sky Sport 4k"));
  assertEquals("",AurumDvbLogoBridge.verifiedLegacySource(
    "DVB:130:KU:11994:V:29900:11628","Another Channel"));
  assertEquals("",AurumDvbLogoBridge.verifiedLegacySource(
    "DVB:130:KU:10796:V:27500:15722","Sky Sport 4k"));
  assertEquals("",AurumDvbLogoBridge.verifiedLegacySource(
    "DVB:190:KU:11994:V:29900:11628","Sky Sport 4k"));
 }
 @Test public void physicalBridgeNamesMustExactlyMatchCapturedService(){
  assertTrue(AurumDvbLogoBridge.validSourceFor("Aurum:1|skysport4k","Sky Sport 4K"));
  assertFalse(AurumDvbLogoBridge.validSourceFor("Aurum:1|skysport4k","Eleven Sports 2 HD"));
  assertFalse(AurumDvbLogoBridge.validSourceFor("Aurum:1|skysport4k",""));
  assertFalse(AurumDvbLogoBridge.validSourceFor("Aurum:1|skysport4k;other","Sky Sport 4k"));
  assertFalse(AurumDvbLogoBridge.validSourceFor("DVB:1|skysport4k","Sky Sport 4k"));
 }
 @Test public void nativeInfobarTransponderIsParsedWithoutGuessing(){
  AurumDvbLogoBridge.Transponder tp=AurumDvbLogoBridge.parseTp("TP 11994 / V / 29900");
  assertNotNull(tp);
  assertEquals(11994,tp.frequency);
  assertEquals("V",tp.polarity);
  assertEquals(29900,tp.symbolRate);
  assertNotNull(AurumDvbLogoBridge.parseTp("11994V29900"));
  assertNull(AurumDvbLogoBridge.parseTp("11994"));
  assertNull(AurumDvbLogoBridge.parseTp("Sky Sport 4k"));
  assertNull(AurumDvbLogoBridge.parseTp("99999/V/29900"));
 }
}
