package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class FactoryEpgGuardTest {
 @Test public void manufacturerBusinessRejectionDoesNotCauseRapidRetry(){
  assertTrue(FactoryEpgGuard.delayMs("FACTORY_SERVICE_REJECTED_50000")>=15L*60000);
 }
 @Test public void timeoutsAreShorterThanAuthorizationFailures(){
  assertTrue(FactoryEpgGuard.delayMs("FACTORY_TIMEOUT")<
    FactoryEpgGuard.delayMs("FACTORY_SERVICE_REJECTED_50000"));
 }
 @Test public void sourceSpecificTimeoutAndRateLimitsDiffer(){
  assertTrue(FactoryEpgGuard.delayMs("FACTORY_HTTP_429")>
    FactoryEpgGuard.delayMs("FACTORY_TIMEOUT"));
 }
}
