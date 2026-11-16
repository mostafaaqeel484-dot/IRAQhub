package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SavedSubtitleTracksTest {
 @Test public void keepsDifferentTeletextPagesWithoutGuessingByPid() throws Exception {
  JSONObject dutch=track(321,1,888,"nld");
  List<SavedSubtitleTracks.Track> tracks=SavedSubtitleTracks.from(new JSONArray()
    .put(dutch).put(dutch).put(track(321,1,889,"eng")).put(track(322,0,2,"ara")));

  assertEquals(3,tracks.size());
  assertEquals("nld",tracks.get(0).language);
  assertEquals(889,tracks.get(1).page);
  assertFalse(SavedSubtitleTracks.uniquePid(tracks,321));
  assertTrue(SavedSubtitleTracks.uniquePid(tracks,322));
 }

 @Test public void rejectsInvalidDescriptors() throws Exception {
  assertTrue(SavedSubtitleTracks.from(new JSONArray()
    .put(new JSONObject()).put(track(8191,1,888,"nld"))
    .put(track(321,256,888,"eng")).put(track(321,1,65536,"eng"))).isEmpty());
 }

 private JSONObject track(int pid,int type,int page,String language) throws Exception {
  return new JSONObject().put("PID",pid).put("Type",type)
    .put("CompPageID",page).put("Lang",language);
 }
}
