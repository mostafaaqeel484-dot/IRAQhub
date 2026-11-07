package com.smarthub.plus;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Bound from the separate :decoder422 process. All resource suspension happens
 * here, in IRAQhub's MAIN process, where WorkManager and logo RAM live.
 * The binder lifecycle reclaims the budget if the decoder process crashes.
 *
 * It never terminates Android services, tuner, TS recording or other apps.
 */
public final class Isolated422FocusService extends Service {
 private final IBinder binder=new Binder();
 private boolean leased;
 @Override public synchronized IBinder onBind(Intent intent){
  if(!leased){leased=true;PlaybackResourceMode.enter(this);}
  return binder;
 }
 @Override public synchronized boolean onUnbind(Intent intent){
  releaseFocus();
  return false;
 }
 @Override public synchronized void onDestroy(){
  releaseFocus();
  super.onDestroy();
 }
 private void releaseFocus(){
  if(!leased)return;
  leased=false;
  PlaybackResourceMode.leave(this);
 }
}
