package com.smarthub.plus;

import android.view.KeyEvent;

/** Deliberately separates channel selection from page scrolling. */
final class ReceiverRemoteKeyPolicy {
    private ReceiverRemoteKeyPolicy() {}

    /** Standard Android digit keys plus numerical keypad codes. */
    static int numericDigit(int keycode){
      if(keycode>=KeyEvent.KEYCODE_0&&keycode<=KeyEvent.KEYCODE_9)
        return keycode-KeyEvent.KEYCODE_0;
      if(keycode>=KeyEvent.KEYCODE_NUMPAD_0&&keycode<=KeyEvent.KEYCODE_NUMPAD_9)
        return keycode-KeyEvent.KEYCODE_NUMPAD_0;
      return -1;
    }
    static int channelStep(int keycode){
        if(keycode==KeyEvent.KEYCODE_CHANNEL_UP)return 1;
        if(keycode==KeyEvent.KEYCODE_CHANNEL_DOWN)return -1;
        return 0;
    }
    static int pageStep(int keycode){
        if(keycode==KeyEvent.KEYCODE_PAGE_DOWN)return 1;
        if(keycode==KeyEvent.KEYCODE_PAGE_UP)return -1;
        return 0;
    }
}
