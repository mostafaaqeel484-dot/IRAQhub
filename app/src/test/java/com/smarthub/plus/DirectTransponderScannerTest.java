package com.smarthub.plus;

import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

/** Manual search must request real full-TP transport packets, never fake channel metadata. */
public class DirectTransponderScannerTest {
 @Test public void fullTpRequestUsesLockedTunerAndNoMadeUpService()throws Exception{
  ReceiverCatalog.Sat hotbird=new ReceiverCatalog.Sat(7,"Hotbird",130,"KU");
  JSONObject request=DirectTransponderScanner.wholeTransponderRequest(
   hotbird,11994,"V",29900,12345,1_101_000_111,new File("/data/scan"),
   new File("/data/scan/tp.ts"));
  assertEquals(1,request.getInt("tp_record"));
  assertEquals(12345,request.getInt("tp_id"));
  assertEquals(11994,request.getInt("tp_freq"));
  assertEquals(1,request.getInt("tp_pol"));
  assertEquals(29900,request.getInt("tp_symbolrate"));
  assertEquals(130,request.getInt("sat_degree"));
  assertEquals(0,request.getInt("iServiceId"));
  assertEquals(8191,request.getInt("iVideoPid"));
  assertEquals(0,request.getInt("uiSIDCount"));
  assertEquals("",request.getString("pucSids"));
  assertTrue(request.getString("path").endsWith("/tp.ts"));
  assertFalse(request.has("channel_name"));
 }
 @Test public void rejectInvalidHardwareContext(){
  try{
   DirectTransponderScanner.wholeTransponderRequest(
    new ReceiverCatalog.Sat(1,"Hotbird",130,"KU"),
    11994,"V",29900,-1,1_101_000_111,new File("a"),new File("b"));
   fail("Negative tuner lock cannot be used");
  }catch(Exception expected){}
  try{
   DirectTransponderScanner.wholeTransponderRequest(
    new ReceiverCatalog.Sat(1,"Hotbird",130,"KU"),
    11994,"X",29900,100,1_101_000_111,new File("a"),new File("b"));
   fail("Bad polarization cannot be used");
  }catch(Exception expected){}
 }
 @Test public void changingSidOrPidResetsManualScanStability()throws Exception{
  org.json.JSONArray first=new org.json.JSONArray()
    .put(new JSONObject().put("sid",1).put("pmt",100)
       .put("videoPid",256).put("name","Channel A"))
    .put(new JSONObject().put("sid",2).put("pmt",200)
       .put("videoPid",512).put("name","Channel B"));
  org.json.JSONArray reordered=new org.json.JSONArray()
    .put(new JSONObject().put("sid",2).put("pmt",200)
       .put("videoPid",512).put("name","Channel B"))
    .put(new JSONObject().put("sid",1).put("pmt",100)
       .put("videoPid",256).put("name","Channel A"));
  org.json.JSONArray changed=new org.json.JSONArray()
    .put(new JSONObject().put("sid",2).put("pmt",200)
       .put("videoPid",512).put("name","Channel B"))
    .put(new JSONObject().put("sid",1).put("pmt",100)
       .put("videoPid",257).put("name","Channel A"));
  String verified=DirectTransponderScanner.verifiedSignature(
    new DvbPsiScan.Result(1000,6,7,8,0,first));
  assertEquals(verified,DirectTransponderScanner.verifiedSignature(
    new DvbPsiScan.Result(2000,12,7,8,0,reordered)));
  assertNotEquals(verified,DirectTransponderScanner.verifiedSignature(
    new DvbPsiScan.Result(2000,12,7,8,0,changed)));
  assertNotEquals(verified,DirectTransponderScanner.verifiedSignature(
    new DvbPsiScan.Result(2000,12,7,8,1,first)));
 }

}
