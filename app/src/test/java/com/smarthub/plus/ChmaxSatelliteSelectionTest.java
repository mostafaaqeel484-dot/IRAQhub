package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChmaxSatelliteSelectionTest {
 @Test public void bulkCanSelectOneSatelliteOrWholeImportedIndex(){
  assertTrue(ChmaxSatelliteSelection.includes(130,130));
  assertFalse(ChmaxSatelliteSelection.includes(130,160));
  assertTrue(ChmaxSatelliteSelection.includes(Integer.MIN_VALUE,130));
  assertTrue(ChmaxSatelliteSelection.includes(Integer.MIN_VALUE,-70));
  assertTrue(ChmaxSatelliteSelection.includes(1082,1082));
 }
 @Test public void zipFilenameWithoutPolarizationMustNotCrossAssign(){
  assertTrue(ChmaxSatelliteSelection.unambiguous(1));
  assertFalse(ChmaxSatelliteSelection.unambiguous(0));
  assertFalse(ChmaxSatelliteSelection.unambiguous(2));
 }
}