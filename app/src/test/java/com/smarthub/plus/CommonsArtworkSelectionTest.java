package com.smarthub.plus;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Commons files must be verified, attributed and HTTPS-only. */
public class CommonsArtworkSelectionTest {
 @Test public void acceptsExplicitCreditedCommonsThumbnail()throws Exception{
  JSONObject image=new JSONObject().put("status","verified")
   .put("provider","Wikimedia Commons").put("artist","Original author")
   .put("license","CC BY-SA 4.0").put("source",
     "https://commons.wikimedia.org/wiki/File:Example.jpg")
   .put("poster","https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Example.jpg/512px-Example.jpg");
  assertEquals(image.getString("poster"),CommonsArtworkSelection.checkedImage(image));
 }
 @Test public void rejectsUnattributedOrImpersonatedImages()throws Exception{
  JSONObject image=new JSONObject().put("status","verified")
   .put("provider","Wikimedia Commons").put("artist","")
   .put("license","CC BY 4.0").put("source","https://commons.wikimedia.org/wiki/File:Example.jpg")
   .put("poster","https://upload.wikimedia.org/wikipedia/commons/x.jpg");
  try{CommonsArtworkSelection.checkedImage(image);fail("No author/attribution rejected");}
  catch(java.io.IOException expected){assertEquals("COMMONS_ATTRIBUTION_REQUIRED",expected.getMessage());}
  image.put("artist","Original author");
  image.put("poster","https://bad.example/wikipedia/commons/x.jpg");
  try{CommonsArtworkSelection.checkedImage(image);fail("Other origin accepted");}
  catch(java.io.IOException expected){assertEquals("COMMONS_IMAGE_HOST_INVALID",expected.getMessage());}
 }
}