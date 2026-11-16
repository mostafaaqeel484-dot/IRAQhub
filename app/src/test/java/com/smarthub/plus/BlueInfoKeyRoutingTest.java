package com.smarthub.plus;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BlueInfoKeyRoutingTest {
 @Test public void onePressOpensInfobarExactlyOnce(){
  assertTrue(BlueInfoKeyRouting.shouldShow(KeyEvent.KEYCODE_PROG_BLUE,KeyEvent.ACTION_DOWN,0));
  assertFalse(BlueInfoKeyRouting.shouldShow(KeyEvent.KEYCODE_PROG_BLUE,KeyEvent.ACTION_DOWN,1));
  assertFalse(BlueInfoKeyRouting.shouldShow(KeyEvent.KEYCODE_PROG_BLUE,KeyEvent.ACTION_DOWN,30));
  assertFalse(BlueInfoKeyRouting.shouldShow(KeyEvent.KEYCODE_PROG_BLUE,KeyEvent.ACTION_UP,0));
 }
 @Test public void otherKeysNeverTriggerBlueRoute(){
  assertFalse(BlueInfoKeyRouting.shouldShow(KeyEvent.KEYCODE_INFO,KeyEvent.ACTION_DOWN,0));
  assertFalse(BlueInfoKeyRouting.handles(KeyEvent.KEYCODE_DPAD_CENTER));
 }
}
