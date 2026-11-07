package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZapRecoveryPolicyTest {
 @Test public void allowsOnlyEvidenceBackedUnencryptedLockedRecovery(){
  assertTrue(ZapRecoveryPolicy.allow(55000,12000,20000,1,true,true,false,false));
  assertFalse(ZapRecoveryPolicy.allow(55000,12000,20000,2,true,true,false,false));
  assertFalse(ZapRecoveryPolicy.allow(55000,12000,20000,1,false,true,false,false));
  assertFalse(ZapRecoveryPolicy.allow(55000,12000,20000,1,true,false,false,false));
  assertFalse(ZapRecoveryPolicy.allow(55000,12000,20000,1,true,true,true,false));
  assertFalse(ZapRecoveryPolicy.allow(55000,12000,20000,1,true,true,false,true));
  assertFalse(ZapRecoveryPolicy.allow(16000,12000,0,0,true,true,false,false));
 }
}