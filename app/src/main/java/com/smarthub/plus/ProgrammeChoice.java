package com.smarthub.plus;
import org.json.*;import java.util.*;
/** First released exact match in source order. Explicit EPG year and series type remain authoritative. */
final class ProgrammeChoice {
 static JSONObject first(List<JSONObject> exact,int epgYear,boolean series,String today){int year=Integer.parseInt(today.substring(0,4));for(JSONObject c:exact){int y=c.optInt("year");String date=c.optString("releaseDate");if(epgYear>0&&epgYear!=y||y>year||date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.*")&&date.substring(0,10).compareTo(today)>0||series&&!c.optString("type").equals("series"))continue;return c;}return null;}
}
