package com.smarthub.plus;

import android.view.KeyEvent;

/** Directional remote keys are contextual: list navigation or active playback volume. */
final class RemoteDirectionPolicy {
 static final int NONE=0, LIST_PREVIOUS=1, LIST_NEXT=2, VOLUME_DOWN=3, VOLUME_UP=4;
 private RemoteDirectionPolicy(){}
 static int action(int keyCode,boolean channelListVisible){
  if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT)
   return channelListVisible?LIST_PREVIOUS:VOLUME_DOWN;
  if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)
   return channelListVisible?LIST_NEXT:VOLUME_UP;
  return NONE;
 }
}
