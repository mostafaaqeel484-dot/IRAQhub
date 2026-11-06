package com.smarthub.plus;
import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ReceiverRemoteKeyPolicyTest {
 @Test public void channelKeysSelectSingleRowsNotPages(){
    assertEquals(1,ReceiverRemoteKeyPolicy.channelStep(KeyEvent.KEYCODE_CHANNEL_UP));
    assertEquals(-1,ReceiverRemoteKeyPolicy.channelStep(KeyEvent.KEYCODE_CHANNEL_DOWN));
    assertEquals(0,ReceiverRemoteKeyPolicy.pageStep(KeyEvent.KEYCODE_CHANNEL_UP));
    assertEquals(0,ReceiverRemoteKeyPolicy.pageStep(KeyEvent.KEYCODE_CHANNEL_DOWN));
 }
 @Test public void standardNumberKeysAreRecognised(){
    assertEquals(0,ReceiverRemoteKeyPolicy.numericDigit(KeyEvent.KEYCODE_0));
    assertEquals(9,ReceiverRemoteKeyPolicy.numericDigit(KeyEvent.KEYCODE_9));
    assertEquals(5,ReceiverRemoteKeyPolicy.numericDigit(KeyEvent.KEYCODE_NUMPAD_5));
    assertEquals(-1,ReceiverRemoteKeyPolicy.numericDigit(KeyEvent.KEYCODE_GUIDE));
 }
 @Test public void pageKeysScrollByPageNotChannel(){
    assertEquals(1,ReceiverRemoteKeyPolicy.pageStep(KeyEvent.KEYCODE_PAGE_DOWN));
    assertEquals(-1,ReceiverRemoteKeyPolicy.pageStep(KeyEvent.KEYCODE_PAGE_UP));
    assertEquals(0,ReceiverRemoteKeyPolicy.channelStep(KeyEvent.KEYCODE_DPAD_UP));
 }
}
