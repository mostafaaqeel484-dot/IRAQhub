package com.smarthub.plus;

/**
 * Conservative DVB video watchdog. The firmware-wide VDEC counter alone is
 * not proof of a visible 4:2:2 frame. Never recover during PVR, encrypted
 * services, tuner changes, PIP, or after exhausting the retry budget.
 */
final class ZapRecoveryPolicy {
 private ZapRecoveryPolicy(){}
 static boolean allow(long now,long accepted,long lastRetry,int attempts,
                      boolean clearService,boolean signalLocked,
                      boolean pipRunning,boolean busy){
  return now>accepted && accepted>0
   && now-accepted>=7000
   && lastRetry>=0&&now-lastRetry>=20000
   && attempts>=0&&attempts<2
   && clearService&&signalLocked&&!pipRunning&&!busy;
 }
}
