package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Optional last-team-result artwork for a football EPG title.
 * Never represents this photo as the exact scheduled fixture:
 * "Parma - Bologna" may display the newest source-available Parma image.
 * Only a verified team identity and a real trusted sports source are accepted.
 */
final class SportsLatestTeamArtwork {
 private static final long RECHECK_MS=4L*3600000L;
 private static final long IMAGE_AGE_MS=18L*3600000L;
 private SportsLatestTeamArtwork(){}

 /** Try the first listed team; if that isn't present, use the second.
  * Never turn a non-fixture film title into a team name search. */
 static String[] fixtureTeams(String raw){
  if(raw==null)return new String[0];
  String clean=raw.replaceFirst("(?i)^\\s*(?:live|football|soccer|calcio|diretta|مباشر|كرة القدم)\\s*[:：-]\\s*","").trim();
  if(clean.length()<7||clean.length()>140)return new String[0];
  String[] parts=clean.split("(?i)\\s+(?:vs\\.?|v\\.?|versus|ضد)\\s+|\\s*[–—]\\s*|\\s+-\\s+",-1);
  if(parts.length!=2)return new String[0];
  String first=canonical(parts[0]),second=canonical(parts[1]);
  if(first.isEmpty()||second.isEmpty()||first.equalsIgnoreCase(second))return new String[0];
  return new String[]{first,second};
 }
 static String canonical(String value){
  if(value==null)return "";
  String v=value.trim().replaceAll("(?i)\\s+(?:hd|fhd|uhd|4k|live)$","").trim();
  if(v.length()<3||v.length()>55||!v.matches("[\\p{L}0-9 .&'’/-]+"))return "";
  String key=v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
  if(key.equals("manunited")||key.equals("manutd")||key.equals("manchesterutd")||
     key.equals("manutd"))return "Manchester United";
  if(key.equals("intermilan"))return "Inter Milan";
  if(key.equals("spurs"))return "Tottenham Hotspur";
  return v;
 }
 static boolean sameTeam(String request,JSONObject team){
  String canonical=canonical(request);
  if(canonical.equalsIgnoreCase(team.optString("strTeam")))return true;
  String alternate=team.optString("strAlternate","");
  for(String alias:alternate.split("[,;|]"))
   if(canonical.equalsIgnoreCase(alias.trim()))return true;
  return false;
 }
 private static String validImage(JSONObject row){
  for(String field:new String[]{"strPoster","strThumb","strBanner","strFanart1","strFanart2"}){
   String url=row.optString(field,"");
   if(url.startsWith("https://")&&TeamLogoManager.trusted(url)){
    try{
     java.net.URI uri=new java.net.URI(url);
     if(uri.getRawFragment()==null&&uri.getRawUserInfo()==null&&
        uri.getPath()!=null&&uri.getPath().startsWith("/images/"))return url;
    }catch(Exception invalid){}
   }
  }
  return "";
 }
 /** Source-verified, latest available team event <= now; free v1 may return
  * only one recent home match, so "latest" means latest returned by provider. */
 static String latestImage(JSONObject data,String teamId,long now){
  if(teamId==null||!teamId.matches("[0-9]{1,18}")||data==null)return "";
  JSONArray events=data.optJSONArray("results");
  if(events==null)events=data.optJSONArray("events");
  long newest=0;String poster="";
  if(events!=null)for(int i=0;i<Math.min(events.length(),30);i++){
   JSONObject event=events.optJSONObject(i);
   if(event==null||(!teamId.equals(event.optString("idHomeTeam"))&&
                     !teamId.equals(event.optString("idAwayTeam"))))continue;
   if(!"Soccer".equalsIgnoreCase(event.optString("strSport","Soccer")))continue;
   String stamp=event.optString("strTimestamp","");
   if(stamp.isEmpty())stamp=event.optString("dateEvent","")+"T"+
                            event.optString("strTime","00:00:00");
   long at=SportsMatch.utc(stamp);
   if(at<=0||at>now+3600000L||now-at>180L*86400000L)continue;
   String url=validImage(event);
   if(!url.isEmpty()&&at>newest){newest=at;poster=url;}
  }
  return poster;
 }
 static String cached(Context c,SatelliteState state,String title,boolean next){
  if(state==null||!PosterSourceSwitches.enabled(c,PosterSourceSwitches.SPORTS))return "";
  String key=PosterSelection.key(state,title,next);
  if(key.isEmpty())return "";
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  long age=System.currentTimeMillis()-p.getLong("sports_latest_at:"+key,0);
  if(age<0||age>IMAGE_AGE_MS)return "";
  return p.getString("sports_latest_image:"+key,"");
 }
 /** Strictly a background-worker operation, no network on InfoBar render. */
 static boolean prefetch(Context c,SatelliteState state,String title,boolean next){
  if(state==null||!PosterSourceSwitches.enabled(c,PosterSourceSwitches.SPORTS))return false;
  String[] teams=fixtureTeams(title);if(teams.length!=2)return false;
  String key=PosterSelection.key(state,title,next);
  if(key.isEmpty())return false;
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  if(PlaybackResourceMode.active()||p.getBoolean("software_dvb_422_active",false)||
      p.getBoolean("dvb_tuning",false)||!p.getString("iraqhub_pvr_session","").isEmpty())return false;
  long now=System.currentTimeMillis();
  long last=p.getLong("sports_latest_attempt:"+key,0);
  if(last>0&&now-last>=0&&now-last<RECHECK_MS)return !cached(c,state,title,next).isEmpty();
  p.edit().putLong("sports_latest_attempt:"+key,now).apply();
  TheSportsDBProvider provider=new TheSportsDBProvider(c);
  for(String team:teams)try{
   JSONObject found=provider.findTeamForRecent(team);
   if(!"verified".equals(found.optString("status")))continue;
   String id=found.optString("id","");
   JSONObject response=provider.lastEventsForTeam(id);
   String image=latestImage(response,id,now);
   // If no published picture of a recent result, use verified team fanart
   // rather than attaching an unrelated fixture photo.
   if(image.isEmpty())image=found.optString("fanart","");
   if(image.isEmpty()||!TeamLogoManager.trusted(image))continue;
   byte[] bytes=ArtworkCache.fetch(c,new URL(image),4*1024*1024);
   if(!ArtworkCache.validImage(bytes))continue;
   p.edit().putString("sports_latest_image:"+key,image)
     .putString("sports_latest_team:"+key,team)
     .putString("sports_latest_status:"+key,"VERIFIED_RECENT_TEAM_NOT_THIS_FIXTURE")
     .putLong("sports_latest_at:"+key,now)
     .putLong("epg_art_changed_at",now).apply();
   return true;
  }catch(Exception unavailable){
   p.edit().putString("sports_latest_status:"+key,
      "SPORTS_SOURCE_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
  }
  p.edit().putString("sports_latest_status:"+key,"NO_RECENT_TEAM_ART").apply();
  return false;
 }
}
