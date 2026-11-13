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
  String alternate=team.optString("strTeamAlternate","")+", "+team.optString("strTeamShort","");
  for(String alias:alternate.split("[,;|]"))
   if(canonical.equalsIgnoreCase(alias.trim()))return true;
  return false;
 }
 private static String validImage(JSONObject row){
  for(String field:new String[]{"strPoster","strThumb","strBanner","strFanart","strSquare","strFanart1","strFanart2"}){
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
 static JSONObject latestEvent(JSONObject data,String teamId,long now){
  if(teamId==null||!teamId.matches("[0-9]{1,18}")||data==null)return null;
  JSONArray events=data.optJSONArray("results");
  if(events==null)events=data.optJSONArray("events");
  long newest=0;JSONObject picked=null;
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
   if(at>newest){newest=at;picked=event;}
  }
  return picked;
 }
 static String latestImage(JSONObject data,String teamId,long now){
  JSONObject event=latestEvent(data,teamId,now);
  return event==null?"":validImage(event);
 }
 static SportsMatch eventCard(JSONObject row,long now){
  if(row==null)return null;
  SportsMatch m=new SportsMatch();
  String id=row.optString("idEvent","");
  if(!id.matches("[0-9]{1,18}"))return null;
  m.id="tsdb:"+id;
  m.homeId=row.optString("idHomeTeam","");
  m.awayId=row.optString("idAwayTeam","");
  m.leagueId=row.optString("idLeague","");
  m.league=row.optString("strLeague","Last verified result");
  m.home=row.optString("strHomeTeam","");
  m.away=row.optString("strAwayTeam","");
  m.homeLogo=row.optString("strHomeTeamBadge","");
  m.awayLogo=row.optString("strAwayTeamBadge","");
  m.leagueLogo=row.optString("strLeagueBadge","");
  m.kickoff=SportsMatch.utc(row.optString("strTimestamp",""));
  if(m.kickoff==0)m.kickoff=SportsMatch.utc(row.optString("dateEvent","")+"T"+
    row.optString("strTime","00:00:00"));
  m.updatedAt=now;
  return SportsProgrammeArtwork.verified(m)?m:null;
 }
 static android.graphics.Bitmap cachedCard(Context c,SatelliteState state,
      String title,boolean next,boolean cacheOnly){
  String key=PosterSelection.key(state,title,next);
  if(key.isEmpty()||!PosterSourceSwitches.enabled(c,PosterSourceSwitches.SPORTS))return null;
  SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  long age=System.currentTimeMillis()-prefs.getLong("sports_latest_at:"+key,0);
  if(age<0||age>IMAGE_AGE_MS)return null;
  try{
   String stored=prefs.getString("sports_latest_event:"+key,"");
   if(stored.isEmpty())return null;
   SportsMatch verified=eventCard(new JSONObject(stored),System.currentTimeMillis());
   return verified==null?null:SportsProgrammeArtwork.card(c,verified,cacheOnly);
  }catch(Exception missing){return null;}
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
   JSONObject event=latestEvent(response,id,now);
   String image=event==null?"":validImage(event);
   if(image.isEmpty())image=found.optString("fanart","");
   String status="NO_RECENT_EVENT_ART";
   if(!image.isEmpty()&&TeamLogoManager.trusted(image)){
    try{
     byte[] bytes=ArtworkCache.fetch(c,new URL(image),4*1024*1024);
     if(!ArtworkCache.validImage(bytes))image="";
    }catch(Exception failed){image="";}
   }else image="";
   SportsMatch card=eventCard(event,now);
   if(image.isEmpty()&&card!=null){
    // No JPEG published? Cache badges for a real verified last match and
    // render a legible football fixture card without inventing an event.
    for(String logo:new String[]{card.homeLogo,card.awayLogo,card.leagueLogo}){
     if(TeamLogoManager.trusted(logo))try{
      ArtworkCache.fetch(c,new URL(logo),1024*1024);
     }catch(Exception ignored){}
    }
    status="LAST_VERIFIED_MATCH_CARD";
   }else if(!image.isEmpty())status="LAST_TEAM_ART";
   if(image.isEmpty()&&card==null)continue;
   SharedPreferences.Editor edit=p.edit()
     .putString("sports_latest_image:"+key,image)
     .putString("sports_latest_team:"+key,team)
     .putString("sports_latest_status:"+key,status)
     .putLong("sports_latest_at:"+key,now)
     .putLong("epg_art_changed_at",now);
   if(card!=null)edit.putString("sports_latest_event:"+key,event.toString());
   else edit.remove("sports_latest_event:"+key);
   edit.apply();
   return true;
  }catch(Exception unavailable){
   p.edit().putString("sports_latest_status:"+key,
      "SPORTS_SOURCE_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
  }
  p.edit().putString("sports_latest_status:"+key,"NO_RECENT_TEAM_ART").apply();
  return false;
 }
}
