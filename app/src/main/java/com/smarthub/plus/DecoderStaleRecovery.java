package com.smarthub.plus;

/**
 * Process death destroys all active Java decoder leases but may leave
 * SharedPreferences flags set; recover only if the main process has no lease.
 */
final class DecoderStaleRecovery {
 private DecoderStaleRecovery(){}
 static boolean shouldClear(boolean hasInProcessViewer,
                             boolean software422Flag,boolean videoPriorityFlag){
  return !hasInProcessViewer&&(software422Flag||videoPriorityFlag);
 }
}
