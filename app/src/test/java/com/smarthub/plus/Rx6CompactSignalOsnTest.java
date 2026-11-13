package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class Rx6CompactSignalOsnTest {
 @Test public void signalPlacementHasTwoSavedValues() {
  assertEquals("bottom",SignalPlacement.DEFAULT);
  assertEquals("side",SignalPlacement.normalize("side"));
  assertEquals("bottom",SignalPlacement.normalize("bottom"));
  assertEquals("bottom",SignalPlacement.normalize("invalid"));
  assertFalse(SignalPlacement.bottom("side"));
  assertTrue(SignalPlacement.bottom("bottom"));
 }
 @Test public void osnFallbackNeverMatchesOtherOperatorOrAmbiguousService() {
  assertTrue(OsnEpgFallback.eligible("DVB:130:H:11954:V:27500:100","OSNtv Now"));
  assertTrue(OsnEpgFallback.eligible("DVB:130:H:11954:V:27500:101","OSNtv Movies Family"));
  assertFalse(OsnEpgFallback.eligible("Aurum:1","OSNtv Now"));
  assertFalse(OsnEpgFallback.eligible("DVB:130:H:11954:V:27500:100","MBC 1"));
  assertFalse(OsnEpgFallback.eligible("DVB:130:H:11954:V:27500:100","OSN Unknown"));
 }
 @Test public void compactGeometryHasNoOldOversizedBlankRow() {
  assertEquals(185,TigerOsdMetrics.barHeightDp(false));
  assertEquals(120,TigerOsdMetrics.headerHeightDp());
  assertTrue(TigerOsdMetrics.barHeightDp(false)<211);
  assertEquals(97,TigerOsdMetrics.channelFlagWidthDp());
 }
}
