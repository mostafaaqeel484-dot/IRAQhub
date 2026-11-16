package com.smarthub.plus;

import android.view.KeyEvent;

/** One deterministic Blue button action across live DVB, channel list and
 * authorised OEM receiver shortcut. Holding Blue must not open companion,
 * a signal rectangle or a programme-details dialog. */
final class BlueInfoKeyRouting {
 private BlueInfoKeyRouting(){}
 static boolean handles(int keyCode){
  return keyCode==KeyEvent.KEYCODE_PROG_BLUE;
 }
 static boolean shouldShow(int keyCode,int action,int repeats){
  return handles(keyCode)&&action==KeyEvent.ACTION_DOWN&&repeats==0;
 }
}
