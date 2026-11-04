package com.smarthub.plus;
import org.json.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutomaticArtworkTest {
 private JSONObject film(String id,int year,String date)throws Exception{return new JSONObject().put("id",id).put("type","movie").put("year",year).put("releaseDate",date);}
 @Test public void repeatedFilmNameUsesFirstVerifiedSourceResult()throws Exception{JSONObject older=film("old",1970,"1970-04-01"),newer=film("new",2010,"2010-04-23");assertEquals("old",ProgrammeChoice.first(Arrays.asList(older,newer),0,false,"2026-10-31").getString("id"));}
 @Test public void explicitEpgYearStillIdentifiesTheActualBroadcast()throws Exception{assertEquals("old",ProgrammeChoice.first(Arrays.asList(film("old",1970,"1970-04-01"),film("new",2010,"2010-04-23")),1970,false,"2026-10-31").getString("id"));}
 @Test public void announcedFutureReleaseDoesNotDisplaceAReleasedFilm()throws Exception{assertEquals("released",ProgrammeChoice.first(Arrays.asList(film("future",2027,"2027-01-01"),film("released",2026,"2026-09-01")),0,false,"2026-10-31").getString("id"));}
 @Test public void latestMovieAndLatestSeriesAreChosenWithinTheirContentType()throws Exception{JSONObject movie=film("movie",2020,"2020-01-01"),series=film("series",2025,"2025-01-01").put("type","series");assertEquals("movie",ProgrammeChoice.first(Arrays.asList(movie,series),0,false,"2026-10-31").getString("id"));assertEquals("series",ProgrammeChoice.first(Arrays.asList(movie,series),0,true,"2026-10-31").getString("id"));}
 @Test public void englishLogoNamesAreCanonicalAndCannotInjectAHost()throws Exception{assertEquals("https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/cnn-us.png",EnglishChannelLogos.url("CNN HD","US"));assertEquals("https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-kingdom/bbc-one-uk.png",EnglishChannelLogos.url("BBC One","GB"));assertEquals("",EnglishChannelLogos.url("CNN","../../private"));}
 @Test public void englishArtworkWinsBeforeResolutionWithoutASelectionDialog()throws Exception{JSONArray channels=new JSONArray("[{id:'one',name:'Sample TV',owners:['Owner']}]");JSONArray logos=new JSONArray("[{channel:'one',in_use:true,url:'https://example.org/ar.png',language:'ar',width:2000,height:2000},{channel:'one',in_use:true,url:'https://example.org/en.png',language:'en',width:500,height:500}]");JSONObject picked=ChannelCatalogue.match("Sample TV HD","",channels,logos);assertEquals("matched",picked.getString("status"));assertEquals("https://example.org/en.png",picked.getJSONArray("images").getJSONObject(0).getString("url"));}
}
