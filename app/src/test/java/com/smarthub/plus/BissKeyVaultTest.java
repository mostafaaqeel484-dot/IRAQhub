package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

/** Verify BISS key format input without exposing real keys or needing Android Keystore. */
public final class BissKeyVaultTest {
 @Test public void acceptsBissOne12Or16Hex(){
  assertEquals("0123456789AB",BissKeyVault.normalize("01 23 45 67 89 ab"));
  assertEquals("0123456789ABCDEF",BissKeyVault.normalize("01:23:45:67:89:AB:CD:EF"));
 }
 @Test public void rejectsWrongLengthsAndNonHex(){
  for(String sample:new String[]{"0011","0123456789ABCDE","G123456789ABCDEF","0123456789ABCDEF00"}){
   try{BissKeyVault.normalize(sample);fail("Accepted invalid key length/alphabet");}
   catch(IllegalArgumentException expected){}
  }
 }
}
