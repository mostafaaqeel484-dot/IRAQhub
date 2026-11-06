package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.ZoneId;
import static org.junit.Assert.*;

/** Guard against the owner's Al Sharqiya HD channel resolving to a wrong CHMAX logo. */
public class FactoryChannelNameMatchingTest {
 @Test public void satelliteQualitySuffixIsNotProgrammeName() {
  assertTrue(FactoryProtocol.compatibleChannelName("AL SHARQIYA NEWS HD","Al-Sharqiya News"));
  assertTrue(FactoryProtocol.compatibleChannelName("BBC One FHD","BBC One"));
  assertFalse(FactoryProtocol.compatibleChannelName("Al Sharqiya Sport","Al Sharqiya News"));
  assertFalse(FactoryProtocol.compatibleChannelName("Al Sharqiya","Al Sharqiya 2"));
 }
 @Test public void ambiguousHdSdVariantsAreRejected()throws Exception{
  JSONArray channels=new JSONArray()
    .put(new JSONObject().put("id","11").put("name","Example SD"))
    .put(new JSONObject().put("id","22").put("name","Example HD"));
  JSONObject response=new JSONObject().put("data",new JSONObject().put("channelList",channels));
  assertEquals("",FactoryProtocol.channelKey(response,"Example","",1793990000000L,ZoneId.of("Asia/Baghdad")));
 }
 @Test public void exactChannelPreferredOverSuffixFallback()throws Exception{
  JSONArray channels=new JSONArray()
    .put(new JSONObject().put("id","11").put("name","Example"))
    .put(new JSONObject().put("id","22").put("name","Example HD"));
  JSONObject response=new JSONObject().put("data",new JSONObject().put("channelList",channels));
  assertEquals("11",FactoryProtocol.channelKey(response,"Example","",1793990000000L,ZoneId.of("Asia/Baghdad")));
 }
}
