package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MediaRepositoryTest {
    @Test public void distinguishesFilmSeriesAndUnrelatedSameTitle() {
        assertEquals("film",MediaRepository.kind(Collections.singletonList("Q11424"),Collections.emptyMap()));
        assertEquals("series",MediaRepository.kind(Collections.singletonList("Q5398426"),Collections.emptyMap()));
        assertEquals("",MediaRepository.kind(Collections.singletonList("Q7889"),Collections.emptyMap()));
        Map<String,List<String>> parents=new HashMap<>();parents.put("Q100000",Collections.singletonList("Q5398426"));
        assertEquals("series",MediaRepository.kind(Collections.singletonList("Q100000"),parents));
        parents.put("Q100000",Collections.singletonList("Q100001"));parents.put("Q100001",Collections.singletonList("Q100000"));
        assertEquals("",MediaRepository.kind(Collections.singletonList("Q100000"),parents));
    }
    @Test public void deprecatedClaimsAndUnknownValuesDoNotBecomeFacts() throws Exception {
        JSONObject entity=new JSONObject("{claims:{P31:[{rank:'deprecated',mainsnak:{snaktype:'value',datavalue:{value:{id:'Q11424'}}}},{rank:'normal',mainsnak:{snaktype:'novalue'}},{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:{id:'Q7889'}}}}]}}");
        assertEquals(Collections.singletonList("Q7889"),MediaRepository.entityValues(entity,"P31",10));
    }
    @Test public void respectsReleasePrecisionAndDurationUnits() throws Exception {
        JSONObject entity=new JSONObject();JSONObject claims=new JSONObject();entity.put("claims",claims);
        claims.put("P577",new JSONArray().put(claim(new JSONObject().put("time","+2010-07-08T00:00:00Z").put("precision",9))));
        assertEquals("2010",MediaRepository.release(entity));
        claims.put("P2047",new JSONArray().put(claim(new JSONObject().put("amount","+8880").put("unit","http://www.wikidata.org/entity/Q11574"))));
        assertEquals("148 min",MediaRepository.runtime(entity));
        claims.put("P2047",new JSONArray().put(claim(new JSONObject().put("amount","+148").put("unit","1"))));
        assertEquals("",MediaRepository.runtime(entity));
    }
    @Test public void wikipediaSummaryMustBelongToExactValidatedEntity() throws Exception {
        MediaRepository.Item item=new MediaRepository.Item();item.id="Q25188";
        MediaRepository.applySummary(item,new JSONObject("{wikibase_item:'Q1',extract:'Wrong title'}"));assertEquals("",item.summary);
        MediaRepository.applySummary(item,new JSONObject("{wikibase_item:'Q25188',type:'disambiguation',extract:'Other titles'}"));assertEquals("",item.summary);
        MediaRepository.applySummary(item,new JSONObject("{wikibase_item:'Q25188',type:'standard',extract:'A verified article lead',thumbnail:{source:'https://upload.wikimedia.org/image.png'},content_urls:{desktop:{page:'https://en.wikipedia.org/wiki/Inception'}}}"));
        assertEquals("A verified article lead",item.summary);assertEquals("",item.image);assertTrue(item.article.endsWith("/Inception"));
    }
    @Test public void optionalMetadataStaysAbsentAndMediaRoundTripsOffline() throws Exception {
        JSONObject entity=new JSONObject("{id:'Q25188',labels:{en:{value:'Inception'}},descriptions:{en:{value:'2010 film'}},sitelinks:{enwiki:{title:'Inception'}}}");
        MediaRepository.Item item=MediaRepository.basic(entity,"ar","film");assertEquals("Inception",item.title);assertEquals("en",item.wikiLanguage);assertEquals("",item.release);assertEquals("",item.runtime);assertEquals("",item.ratings);
        item.summary="Saved summary";item.fetchedAt=1000;MediaRepository.Item saved=MediaRepository.Item.fromJson(item.json());assertEquals(item.id,saved.id);assertEquals(item.summary,saved.summary);assertEquals(1000,saved.fetchedAt);
    }
    @Test public void acceptsOnlyWikimediaHttpsImagesAndEntityIds() {
        assertTrue(MediaRepository.safeImage("https://upload.wikimedia.org/wikipedia/commons/a/ab/image.jpg"));
        assertTrue(MediaRepository.safeImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/3/38/Horst_von_Harbou_-_Metropolis_set_photograph_05.jpg/330px-Horst_von_Harbou_-_Metropolis_set_photograph_05.jpg?utm_source=commons.wikimedia.org"));
        assertFalse(MediaRepository.safeImage("https://upload.wikimedia.org/wikipedia/en/a/ab/nonfree-poster.jpg"));
        assertFalse(MediaRepository.safeImage("https://thumb.wikimedia.org/wikipedia/en/a/ab/nonfree-poster.jpg"));
        assertFalse(MediaRepository.safeImage("http://upload.wikimedia.org/image.jpg"));assertFalse(MediaRepository.safeImage("https://upload.wikimedia.org.evil.example/image.jpg"));
        assertTrue(MediaRepository.validId("Q25188"));assertFalse(MediaRepository.validId("Q0"));assertFalse(MediaRepository.validId("../../Q1"));
    }
    @Test public void imdbAndCommonsFilenameComeFromNonDeprecatedClaimsOnVerifiedWork() throws Exception {
        JSONObject entity=new JSONObject("{id:'Q25188',claims:{P345:[{rank:'deprecated',mainsnak:{snaktype:'value',datavalue:{value:'tt9999999'}}},{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:'not-an-imdb-id'}}},{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:'tt1375666'}}}],P18:[{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:'Verified work image.jpg'}}}]}}");
        MediaRepository.Item film=MediaRepository.basic(entity,"en","film");assertEquals("tt1375666",film.imdbId);assertEquals("Verified work image.jpg",film.commonsFile);assertEquals("",film.image);
        MediaRepository.Item unrelated=MediaRepository.basic(entity,"en","");assertEquals("",unrelated.imdbId);assertEquals("",unrelated.commonsFile);
    }
    @Test public void actualPosterPropertyIsPreferredToRelatedCastOrSetImage()throws Exception{
        JSONObject entity=new JSONObject("{id:'Q151599',claims:{P3383:[{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:'An actual film poster.jpg'}}}],P18:[{rank:'normal',mainsnak:{snaktype:'value',datavalue:{value:'A cast photograph.jpg'}}}]}}");
        MediaRepository.Item item=MediaRepository.basic(entity,"en","film");assertEquals("An actual film poster.jpg",item.commonsFile);assertEquals("poster",item.imageKind);
        entity.getJSONObject("claims").remove("P3383");item=MediaRepository.basic(entity,"en","film");assertEquals("A cast photograph.jpg",item.commonsFile);assertEquals("image",item.imageKind);
    }
    @Test public void commonsImageRequiresAnOpenLicenseAndRetainsAttributionOffline() throws Exception {
        MediaRepository.Item item=new MediaRepository.Item();item.id="Q151599";item.commonsFile="A verified poster.jpg";
        JSONObject info=commonsInfo("CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0/");
        assertTrue(MediaRepository.applyCommonsImage(item,info));assertEquals("CC BY-SA 4.0",item.imageLicense);assertEquals("Artist Name · Museum",item.imageCredit);assertEquals("https://commons.wikimedia.org/wiki/File:A_verified_poster.jpg",item.imagePage);
        item.imdbId="tt0017136";MediaRepository.Item saved=MediaRepository.Item.fromJson(item.json());assertEquals(item.image,saved.image);assertEquals(item.imageCredit,saved.imageCredit);assertEquals(item.imagePage,saved.imagePage);assertEquals(item.imdbId,saved.imdbId);
        JSONObject legacy=item.json();legacy.remove("imageLicense");legacy.remove("imageLicenseUrl");assertEquals("",MediaRepository.Item.fromJson(legacy).image);
    }
    @Test public void unknownFairUseAndRestrictedLicensesNeverBecomeOpenPosters() throws Exception {
        assertTrue(MediaRepository.openLicense("Public domain","",""));assertTrue(MediaRepository.openLicense("CC0","https://creativecommons.org/publicdomain/zero/1.0/",""));
        assertFalse(MediaRepository.openLicense("Fair use","",""));assertFalse(MediaRepository.openLicense("CC BY-NC 4.0","https://creativecommons.org/licenses/by-nc/4.0/",""));assertFalse(MediaRepository.openLicense("CC BY-ND 4.0","https://creativecommons.org/licenses/by-nd/4.0/",""));assertFalse(MediaRepository.openLicense("","",""));
        MediaRepository.Item item=new MediaRepository.Item();assertFalse(MediaRepository.applyCommonsImage(item,commonsInfo("Fair use","")));assertEquals("",item.image);
        JSONObject external=commonsInfo("Public domain","").put("thumburl","https://example.com/poster.jpg");assertFalse(MediaRepository.applyCommonsImage(item,external));assertEquals("",item.image);
        JSONObject nonfree=commonsInfo("Public domain","").put("thumburl","https://upload.wikimedia.org/wikipedia/en/a/aa/nonfree.jpg");assertFalse(MediaRepository.applyCommonsImage(item,nonfree));
    }
    private JSONObject commonsInfo(String license,String licenseUrl)throws Exception{return new JSONObject().put("thumburl","https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/A_verified_poster.jpg/500px-A_verified_poster.jpg").put("url","https://upload.wikimedia.org/wikipedia/commons/a/ab/A_verified_poster.jpg").put("descriptionurl","https://commons.wikimedia.org/wiki/File:A_verified_poster.jpg").put("extmetadata",new JSONObject().put("LicenseShortName",new JSONObject().put("value",license)).put("LicenseUrl",new JSONObject().put("value",licenseUrl)).put("Artist",new JSONObject().put("value","<a href='https://example.com'>Artist Name</a>")).put("Credit",new JSONObject().put("value","Museum")).put("AttributionRequired",new JSONObject().put("value","true")));}
    private JSONObject claim(JSONObject value)throws Exception{return new JSONObject().put("rank","normal").put("mainsnak",new JSONObject().put("snaktype","value").put("datavalue",new JSONObject().put("value",value)));}
}
