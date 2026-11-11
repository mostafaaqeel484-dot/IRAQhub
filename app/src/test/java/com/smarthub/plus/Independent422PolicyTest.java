package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;

public class Independent422PolicyTest {
 @Test public void onlyKnownVideoServicesUseOwnedTsRelay(){
  assertTrue(Independent422Policy.eligibleDvb(0,0));
  assertTrue(Independent422Policy.eligibleDvb(0,1));
  assertFalse(Independent422Policy.eligibleDvb(1,0));
  assertFalse(Independent422Policy.eligibleDvb(0,-1));
 }
 @Test public void mpvClockCannotCertifyRenderedPictures(){
  assertTrue(Independent422Policy.clockProgressed(-1,5));
  assertTrue(Independent422Policy.clockProgressed(5,5.5));
  assertFalse(Independent422Policy.clockProgressed(5,5.1));
  assertFalse(Independent422Policy.clockProgressed(5,Double.NaN));
  assertFalse(Independent422Policy.clockProgressed(5,Double.POSITIVE_INFINITY));
 }
 @Test public void diagnoseStallOnlyAfterSufficientTimeAndActiveRelay(){
  assertFalse(Independent422Policy.clockStalled(1000,10000,true));
  assertTrue(Independent422Policy.clockStalled(1000,14000,true));
  assertFalse(Independent422Policy.clockStalled(1000,14000,false));
  assertFalse(Independent422Policy.clockStalled(0,14000,true));
 }
}
