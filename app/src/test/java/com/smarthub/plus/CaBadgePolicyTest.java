package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class CaBadgePolicyTest {
 @Test public void multipleCASIDsIlluminateBoth(){
  boolean[] flags=CaBadgePolicy.active("CA · NAGRA · BETA");
  assertTrue(flags[0]);assertTrue(flags[4]);assertTrue(flags[7]);
  assertFalse(flags[6]);assertFalse(flags[3]);
 }
 @Test public void ftaOnlyWhenExplicit(){
  assertTrue(CaBadgePolicy.active("FTA")[6]);
  assertFalse(CaBadgePolicy.active("")[6]);
  assertFalse(CaBadgePolicy.active("CA")[6]);
 }
 @Test public void familyNamesUseConservativeMapping(){
  assertTrue(CaBadgePolicy.active("NDS / VIACCESS")[3]);
  assertTrue(CaBadgePolicy.active("NDS / VIACCESS")[5]);
  assertTrue(CaBadgePolicy.active("CA")[8]);
  assertTrue(CaBadgePolicy.active("BISS")[2]);
  assertTrue(CaBadgePolicy.active("IR")[1]);
 }
}
