package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class VerifiedPosterFallbackTest {
 @Test public void ccLicensesAccepted(){
  assertTrue(VerifiedPosterFallback.licensed("https://creativecommons.org/licenses/by/4.0/"));
  assertTrue(VerifiedPosterFallback.licensed("http://creativecommons.org/licenses/by-sa/4.0/"));
  assertTrue(VerifiedPosterFallback.licensed("https://creativecommons.org/publicdomain/zero/1.0/"));
 }
 @Test public void missingOrRestrictedLicenseRejected(){
  assertFalse(VerifiedPosterFallback.licensed(""));
  assertFalse(VerifiedPosterFallback.licensed("all rights reserved"));
  assertFalse(VerifiedPosterFallback.licensed("https://creativecommons.org/licenses/by-nc/4.0/"));
  assertFalse(VerifiedPosterFallback.licensed("https://notarchive.example/cc-by"));
 }
}
