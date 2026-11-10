package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class FactoryRetryPolicyTest {
 @Test public void transientNetworkFailureRetriesAfterFiveSeconds(){
  assertEquals(5000L,FactoryRetryPolicy.retryMillis("FACTORY_DNS_FAILED"));
  assertEquals(5000L,FactoryRetryPolicy.retryMillis("FACTORY_TIMEOUT"));
 }
 @Test public void invalidCredentialsAndRateLimitAreBackedOff(){
  assertEquals(300000L,FactoryRetryPolicy.retryMillis("FACTORY_AUTH_REQUIRED"));
  assertEquals(300000L,FactoryRetryPolicy.retryMillis("FACTORY_SERVICE_REJECTED_50000"));
  assertEquals(300000L,FactoryRetryPolicy.retryMillis("FACTORY_HTTP_429"));
 }
 @Test public void noPublishedEpgIsNotRetriedEveryFiveSeconds(){
  assertEquals(60000L,FactoryRetryPolicy.retryMillis("FACTORY_EPG_NOT_PUBLISHED"));
  assertEquals(60000L,FactoryRetryPolicy.SUCCESS_REFRESH_MS);
 }
}
