package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class EpgSourcePriorityTest {
 @Test public void receiverFirstThenFactoryThenInternet(){
  assertTrue(EpgSourcePriority.rank("DVB EIT · native receiver")>
    EpgSourcePriority.rank("Factory CHMAX / EPG"));
  assertTrue(EpgSourcePriority.rank("EPG Pro · OEM")>
    EpgSourcePriority.rank("XMLTV · mapped guide"));
  assertTrue(EpgSourcePriority.rank("XMLTV")>
    EpgSourcePriority.rank("Online EPG · EPGShare01"));
 }
 @Test public void factoryNeverReplacesValidNativeEit(){
  assertFalse(EpgSourcePriority.prefer("Factory CHMAX / EPG",
    "DVB EIT · native receiver",true));
  assertTrue(EpgSourcePriority.prefer("DVB EIT · native receiver",
    "Online EPG",true));
  assertTrue(EpgSourcePriority.prefer("Factory CHMAX / EPG",
    "Online EPG",true));
 }
}
