package com.smarthub.plus;import org.junit.Test;import org.json.JSONObject;import static org.junit.Assert.*;public class ReceiverDvbBridgeTest {
@Test public void actualSidListCountsDistinctServicesWithoutGuessingProgrammeIds()throws Exception{assertArrayEquals(new int[]{2,4},ReceiverDvbBridge.counts(new JSONObject("{iAddCount:2,stEPGCHSIDList:[{tp_id:70,tp_type:0,aucSIDList:'1:2:2:3:'},{tp_id:71,tp_type:0,aucSIDList:'4'}]}")));}
@Test public void outOfRangeSidIsRejected()throws Exception{assertArrayEquals(new int[]{1,-1},ReceiverDvbBridge.counts(new JSONObject("{stEPGCHSIDList:[{tp_id:70,tp_type:0,aucSIDList:'65536'}]}")));}
@Test public void missingNativeTransponderIsRejected()throws Exception{try{ReceiverDvbBridge.counts(new JSONObject("{stEPGCHSIDList:[{aucSIDList:'12'}]}"));fail();}catch(Exception expected){}}
@Test public void noPublishedEpgIsAnEmptyRealList()throws Exception{assertArrayEquals(new int[]{0,0},ReceiverDvbBridge.counts(new JSONObject("{iAddCount:0}")));}
@Test public void opaqueNativeSidPayloadIsNeverSplitIntoGuessedIdentifiers()throws Exception{assertArrayEquals(new int[]{1,-1},ReceiverDvbBridge.counts(new JSONObject("{stEPGCHSIDList:[{tp_id:70,tp_type:0,aucSIDList:'12345678901234'}]}")));}
}
