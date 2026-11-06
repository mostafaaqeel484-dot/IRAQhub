package com.smarthub.plus;

import android.view.KeyEvent;

/** Deliberately separates channel selection from page scrolling. */
final class ReceiverRemoteKeyPolicy {
    private ReceiverRemoteKeyPolicy() {}

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
