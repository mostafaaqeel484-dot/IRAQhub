package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

/** A real-world API can supply "poster":"" and a valid "pic" at once. */
public class FactoryEpgArtworkFallbackTest {
 @Test public void emptyPosterKeyFallsBackToProgrammePic()throws Exception{
  JSONObject programme=new JSONObject().put("name","Eastern News Channel")
   .put("start","2026-11-06 19:00:00").put("end","2026-11-06 20:00:00")
   .put("poster","").put("pic","https://image.tmdb.org/t/p/w780/programme.jpg");
  JSONObject response=new JSONObject().put("code",0)
   .put("data",new JSONObject().put("root",new JSONArray().put(
     new JSONObject().put("list",new JSONArray().put(programme)))));
  List<FactoryProtocol.Event> events=FactoryProtocol.schedule(response,ZoneId.of("Asia/Baghdad"));
  assertEquals(1,events.size());
  assertEquals("https://image.tmdb.org/t/p/w780/programme.jpg",events.get(0).pic);
 }
 @Test public void explicitPosterTakesPrecedenceOverThumbnail()throws Exception{
  JSONObject programme=new JSONObject().put("name","Movie")
   .put("start","2026-11-06 19:00:00").put("end","2026-11-06 20:00:00")
   .put("poster","https://image.tmdb.org/t/p/w780/poster.jpg")
   .put("pic","https://image.tmdb.org/t/p/w780/thumbnail.jpg");
  JSONObject reply=new JSONObject().put("code",0).put("data",
    new JSONObject().put("root",new JSONArray().put(new JSONObject().put("list",
      new JSONArray().put(programme)))));
  assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg",
    FactoryProtocol.schedule(reply,ZoneId.of("Asia/Baghdad")).get(0).pic);
 }
}
