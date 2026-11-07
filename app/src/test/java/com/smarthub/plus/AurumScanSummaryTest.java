package com.smarthub.plus;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AurumScanSummaryTest {
 private static AurumScanSummary.Field field(String id,String text){
  return new AurumScanSummary.Field("com.tosmart.aurum:id/"+id,text);
 }
 private static List<AurumScanSummary.Field> observed(){
  return new ArrayList<>(Arrays.asList(
   field("tv_detail_title","التلفزيون"),
   field("tv_search_result_title","الكل"),field("tv_search_result_info","7"),
   field("tv_search_result_title","فرى"),field("tv_search_result_info","2"),
   field("tv_search_result_title","أتش دى"),field("tv_search_result_info","7"),
   field("tv_search_result_title","مشفر"),field("tv_search_result_info","5"),
   field("channel_all_total_count","Total: 8"),
   field("tv_radio_title","الراديو"),
   field("tv_search_result_title","الكل"),field("tv_search_result_info","1"),
   field("tv_search_result_title","فرى"),field("tv_search_result_info","1"),
   field("tv_search_result_title","أتش دى"),field("tv_search_result_info","0"),
   field("tv_search_result_title","مشفر"),field("tv_search_result_info","0"),
   field("tv_tips","انتهاء المسح وحفظ القنوات"),field("header_title","بحث")
  ));
 }
 @Test public void manualScanSavedRealTvAndRadioCounts(){
  AurumScanSummary.Result r=AurumScanSummary.parse(observed(),1793990000000L);
  assertNotNull(r);assertTrue(r.completed);
  assertEquals(7,r.tv);assertEquals(1,r.radio);assertEquals(3,r.free);
  assertEquals(5,r.encrypted);assertEquals(7,r.hd);assertEquals(8,r.displayedTotal);
 }
 @Test public void resultCannotBeConfusedWithCurrentChannelInfobar(){
  List<AurumScanSummary.Field> data=observed();
  data.removeIf(f->f.id.endsWith("tv_tips"));
  assertNull(AurumScanSummary.parse(data,1793990000000L));
 }
 @Test public void inconsistentTotalsRejectedInsteadOfInventingChannels(){
  List<AurumScanSummary.Field> data=observed();
  data.removeIf(f->f.id.endsWith("channel_all_total_count"));
  data.add(field("channel_all_total_count","Total: 999"));
  assertNull(AurumScanSummary.parse(data,1793990000000L));
 }
 @Test public void emptyAndUnfinishedResultsNotClaimedCompleted(){
  assertNull(AurumScanSummary.parse(Collections.emptyList(),1793990000000L));
  assertNull(AurumScanSummary.parse(observed(),0));
 }
 @Test public void storedSummaryPreservesIdentityAndCounts()throws Exception{
  AurumScanSummary.Result r=AurumScanSummary.parse(observed(),1793990000000L);
  AurumScanSummary.Result saved=AurumScanSummary.Result.fromJson(r.json().toString());
  assertNotNull(saved);assertEquals(r.identity(),saved.identity());
  assertEquals(r.observedAt,saved.observedAt);
  assertNull(AurumScanSummary.Result.fromJson("{\"source\":\"untrusted\"}"));
 }
}
