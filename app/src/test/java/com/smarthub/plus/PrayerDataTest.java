package com.smarthub.plus;
import org.json.*;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class PrayerDataTest {
 private JSONObject day()throws Exception{return new JSONObject("{\"date\":{\"gregorian\":{\"date\":\"31-10-2026\"}},\"meta\":{\"timezone\":\"Asia/Baghdad\"},\"timings\":{\"Fajr\":\"04:34 (+03)\",\"Dhuhr\":\"11:52 (+03)\",\"Asr\":\"15:16 (+03)\",\"Maghrib\":\"17:47 (+03)\",\"Isha\":\"19:05 (+03)\"}}");}
 @Test public void cityTimezoneIsUsedRatherThanDeviceTimezone()throws Exception{assertEquals(Instant.parse("2026-10-31T01:34:00Z").toEpochMilli(),PrayerData.instant(day(),"Fajr"));}
 @Test public void remindersAreFiveMinutesEarlyAndNeverSchedulePastEvents()throws Exception{JSONObject d=day();assertEquals(Long.valueOf(Instant.parse("2026-10-31T01:29:00Z").toEpochMilli()),PrayerData.reminders(d,0).get("Fajr"));assertTrue(PrayerData.reminders(d,Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()).isEmpty());}
 @Test public void oldCalendarIsNotTreatedAsToday()throws Exception{JSONArray days=new JSONArray().put(day());assertNotNull(PrayerData.find(days,LocalDate.of(2026,10,31)));assertNull(PrayerData.find(days,LocalDate.of(2026,11,1)));}
 @Test(expected=IllegalArgumentException.class)public void malformedTimingIsRejected(){PrayerData.time("not a time");}
}
