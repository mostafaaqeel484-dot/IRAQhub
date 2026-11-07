package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public class UsbAdbSetupTest {
 @Test public void offersStartupSetupWhenDebuggingIsOff(){
  assertTrue(UsbAdbSetup.shouldPrompt(false,true,false));
 }
 @Test public void neverPromptsWhenAlreadyEnabled(){
  assertFalse(UsbAdbSetup.shouldPrompt(true,true,false));
 }
 @Test public void respectsUserDismissalForSessionAndOptOut(){
  assertFalse(UsbAdbSetup.shouldPrompt(false,true,true));
  assertFalse(UsbAdbSetup.shouldPrompt(false,false,false));
 }
}
