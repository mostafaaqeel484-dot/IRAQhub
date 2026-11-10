package com.smarthub.plus;

/**
 * Foreground factory connection check runs every five seconds. This class
 * bounds ACTUAL network retries to avoid repeatedly sending credentials.
 * No background 5s alarm or WorkManager schedule is requested.
 */
final class FactoryRetryPolicy {
 static final long CHECK_MS=5_000L;
 static final long SUCCESS_REFRESH_MS=60_000L;
 static final long UNPUBLISHED_RETRY_MS=60_000L;
 static final long AUTH_BACKOFF_MS=5*60_000L;
 private FactoryRetryPolicy(){}
 static long retryMillis(String error){
  if(error==null)return CHECK_MS;
  if(error.equals("FACTORY_AUTH_REQUIRED")||error.equals("FACTORY_IDENTITY_REQUIRED")||
     error.equals("FACTORY_CHMAX_PROFILE_REQUIRED")||error.startsWith("FACTORY_LOGIN_REJECTED_")||
     error.startsWith("FACTORY_SERVICE_REJECTED_")||error.equals("FACTORY_HTTP_429")||
     error.equals("FACTORY_HTTP_401")||error.equals("FACTORY_HTTP_403"))
   return AUTH_BACKOFF_MS;
  if(error.contains("CHANNEL")||error.contains("PROGRAMME")||
     error.contains("NOT_PUBLISHED")||error.contains("REFERENCE"))
   return UNPUBLISHED_RETRY_MS;
  return CHECK_MS;
 }
}
