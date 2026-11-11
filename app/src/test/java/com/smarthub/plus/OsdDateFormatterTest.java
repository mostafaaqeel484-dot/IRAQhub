package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
public final class OsdDateFormatterTest {
 @Test public void formatsLiveDeviceClockWithoutHardCodedYear(){
  Calendar cal=new GregorianCalendar(TimeZone.getTimeZone("Asia/Baghdad"));
  cal.clear();cal.set(2026,Calendar.NOVEMBER,8,5,55);
  long at=cal.getTimeInMillis();
  assertEquals("2026",OsdDateFormatter.year(at,TimeZone.getTimeZone("Asia/Baghdad")));
  assertTrue(OsdDateFormatter.dateLine(at,TimeZone.getTimeZone("Asia/Baghdad"),false).contains("Sunday"));
  assertTrue(OsdDateFormatter.dateLine(at,TimeZone.getTimeZone("Asia/Baghdad"),false).contains("5:55"));
  assertFalse(OsdDateFormatter.dateLine(at,TimeZone.getTimeZone("Asia/Baghdad"),true).contains("2026"));
 }
}
