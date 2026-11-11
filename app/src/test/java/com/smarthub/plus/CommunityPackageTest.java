package com.smarthub.plus;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CommunityPackageTest {
 @Test public void incomingRecommendationsDoNotEraseOrDuplicateLocalPicks()throws Exception{JSONArray old=new JSONArray().put(new JSONObject().put("title","One").put("url","https://example.com/1"));JSONArray added=new JSONArray().put(new JSONObject().put("title","One").put("url","https://example.com/1")).put(new JSONObject().put("title","Two").put("url","https://example.com/2"));assertEquals(2,CommunityPackage.mergeRecommendations(old,added).length());assertEquals(1,CommunityPackage.mergeRecommendations(old,new JSONArray()).length());}
 @Test public void pinTokensAndActivationAreNotImported()throws Exception{JSONObject safe=CommunityPackage.safeSettings(new JSONObject().put("parental_pin","1234").put("api_key","secret").put("overlay_parental",true).put("overlay_text",true).put("overlay_text_value","hello"));assertEquals(1,safe.length());assertEquals("hello",safe.getString("overlay_text_value"));}
 @Test public void screenCoordinatesAndOffsetsAreBounded()throws Exception{JSONObject safe=CommunityPackage.safeSettings(new JSONObject().put("overlay_mask_x",-100).put("overlay_logo_w",500).put("subtitle_offset",Long.MAX_VALUE));assertEquals(0,safe.getInt("overlay_mask_x"));assertEquals(100,safe.getInt("overlay_logo_w"));assertEquals(10_800_000L,safe.getLong("subtitle_offset"));}
 @Test(expected=JSONException.class)public void unrelatedJsonIsNotASettingsPackage()throws Exception{CommunityPackage.validate("{\"version\":1,\"settings\":{}}");}
}
