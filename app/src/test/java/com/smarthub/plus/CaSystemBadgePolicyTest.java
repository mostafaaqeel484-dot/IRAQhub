package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class CaSystemBadgePolicyTest {
 @Test public void simultaneousNagraAndBetacryptLightBothYellow(){
  boolean[] a=CaSystemBadgePolicy.active("CA · NAGRA · BETA");
  assertTrue(a[0]);assertTrue(a[4]);assertTrue(a[7]);
  assertFalse(a[6]);assertFalse(a[8]);
 }
 @Test public void unknownDoesNotGetFalseFtaOrBiss(){
  boolean[] unknown=CaSystemBadgePolicy.active("");
  for(boolean active:unknown)assertFalse(active);
  boolean[] ca=CaSystemBadgePolicy.active("CA");
  assertTrue(ca[0]);assertTrue(ca[8]);assertFalse(ca[6]);
  boolean[] fta=CaSystemBadgePolicy.active("FTA");
  assertTrue(fta[6]);assertFalse(fta[0]);
 }
 @Test public void namedConditionalAccessCoversBissVideoGuardAndIrdeto(){
  boolean[] x=CaSystemBadgePolicy.active("CA · BISS · NDS · IR");
  assertTrue(x[1]);assertTrue(x[2]);assertTrue(x[3]);
 }
}
