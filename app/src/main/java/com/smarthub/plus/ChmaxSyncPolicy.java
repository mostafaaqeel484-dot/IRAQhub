package com.smarthub.plus;

/** Package checkpoint and bounded network backoff; pure logic for JVM tests. */
final class ChmaxSyncPolicy {
 private ChmaxSyncPolicy(){}
 static boolean skip(int prior,String oldHash,String nowHash,String priorChannels,
                     String nowChannels,long checkedAt,long now){
  if(prior<0||oldHash==null||oldHash.isEmpty()||!oldHash.equals(nowHash)||
      nowChannels==null||nowChannels.isEmpty()||
      !nowChannels.equals(priorChannels)||checkedAt<=0||now<checkedAt)return false;
  long age=now-checkedAt;
  // Even zero-match packages are verified; do not download them on every sync.
  // Recheck missing artwork after 12h, populated packages after 7d.
  return age<(prior==0?12L*3600000L:7L*86400000L);
 }
 static boolean serverBackoff(String code){
  if(code==null)return false;
  return code.contains("429")||code.contains("403")||
    code.contains("AUTH_REQUIRED")||code.contains("50000");
 }
}
