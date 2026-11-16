package com.smarthub.plus;
import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RemoteDirectionPolicyTest {
 @Test public void directionsNavigateOnlyWhenChannelListVisible(){
  assertEquals(RemoteDirectionPolicy.LIST_PREVIOUS,
   RemoteDirectionPolicy.action(KeyEvent.KEYCODE_DPAD_LEFT,true));
  assertEquals(RemoteDirectionPolicy.LIST_NEXT,
   RemoteDirectionPolicy.action(KeyEvent.KEYCODE_DPAD_RIGHT,true));
 }
 @Test public void directionsChangeVolumeDuringFullscreenDvbOr422Video(){
  assertEquals(RemoteDirectionPolicy.VOLUME_DOWN,
   RemoteDirectionPolicy.action(KeyEvent.KEYCODE_DPAD_LEFT,false));
  assertEquals(RemoteDirectionPolicy.VOLUME_UP,
   RemoteDirectionPolicy.action(KeyEvent.KEYCODE_DPAD_RIGHT,false));
  assertEquals(RemoteDirectionPolicy.NONE,
   RemoteDirectionPolicy.action(KeyEvent.KEYCODE_DPAD_CENTER,false));
 }
}
