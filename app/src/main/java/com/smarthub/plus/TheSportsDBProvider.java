package com.smarthub.plus;
import android.content.Context;import org.json.*;import java.time.*;import java.util.*;import java.io.*;
/** Public documented v1 shared key 123 is not a private credential. Free tier is explicitly limited. */
final class TheSportsDBProvider implements SportsDataProvider {
 static final String API="https://www.thesportsdb.com/api/v1/json/123/";
 private final Context context;private final SportsCache cache;
 private String api(){String key=PosterApiSecrets.get(context,"sportsdb");return "https://www.thesportsdb.com/api/v1/json/"+(PosterApiSecrets.valid(key)?key:"123")+"/";}
 TheSportsDBProvider(Context c){context=c.getApplicationContext();cache=new SportsCache(c);}
 public String name(){return "TheSportsDB · free limited schedules";}
 private void requireEnabled()throws IOException{
  if(!PosterSourceSwitches.enabled(context,PosterSourceSwitches.SPORTS))
   throw new IOException("SPORTSDB_DISABLED");
 }
 JSONObject league(LeagueRepository.League l)throws Exception{requireEnabled();if(l.id.isEmpty())throw new IOException("No verified TheSportsDB league ID");JSONObject wrapper=cache.get(api()+"lookupleague.php?id="+l.id,7*86400000L);JSONArray a=wrapper.getJSONObject("data").optJSONArray("leagues");JSONObject row=a==null?null:a.optJSONObject(0);if(row==null||!l.id.equals(row.optString("idLeague"))||!"Soccer".equals(row.optString("strSport"))||!LeagueRepository.nameMatches(l,row.optString("strLeague")))throw new IOException("League identity could not be verified");return row;}
 public List<SportsMatch> matches(LeagueRepository.League l,LocalDate day,String window)throws Exception{if(!context.getSharedPreferences("smarthub",0).getBoolean("art_source_sportsdb_enabled",true))throw new IOException("SPORTSDB_DISABLED");JSONObject metadata=league(l);String route=window.equals("next")?"eventsnextleague.php?id="+l.id:window.equals("past")?"eventspastleague.php?id="+l.id:"eventsday.php?d="+day+"&s=Soccer&l="+l.id;JSONObject wrapper=cache.get(api()+route,window.equals("day")?120000L:15*60000L);JSONArray rows=wrapper.getJSONObject("data").optJSONArray("events");List<SportsMatch> out=new ArrayList<>();if(rows!=null)for(int i=0;i<rows.length();i++){SportsMatch m=parse(rows.optJSONObject(i),l,wrapper.optLong("at"),wrapper.optBoolean("stale"));if(m!=null){if(m.leagueLogo.isEmpty())m.leagueLogo=metadata.optString("strBadge");out.add(m);}}return out;}
 static SportsMatch parse(JSONObject j,LeagueRepository.League l,long at,boolean stale){if(j==null||!l.id.equals(j.optString("idLeague"))||!j.optString("strSport","Soccer").equals("Soccer")||!j.optString("idEvent").matches("[0-9]+")||j.optString("strHomeTeam").isEmpty()||j.optString("strAwayTeam").isEmpty())return null;SportsMatch m=new SportsMatch();m.id="tsdb:"+j.optString("idEvent");m.leagueId=l.id;m.league=l.name;m.home=j.optString("strHomeTeam");m.away=j.optString("strAwayTeam");m.homeId=j.optString("idHomeTeam");m.awayId=j.optString("idAwayTeam");m.homeLogo=j.optString("strHomeTeamBadge","");m.awayLogo=j.optString("strAwayTeamBadge","");m.leagueLogo=j.optString("strLeagueBadge","");m.image=j.optString("strPoster","");if(m.image.isEmpty())m.image=j.optString("strThumb","");
 if(m.image.isEmpty())m.image=j.optString("strBanner","");m.homeScore=number(j,"intHomeScore");m.awayScore=number(j,"intAwayScore");m.status=j.optString("strStatus","UNKNOWN");if(m.status.isEmpty())m.status="UNKNOWN";m.minute=j.optString("strProgress","");m.venue=j.optString("strVenue","");m.description=j.optString("strDescriptionEN","");String timestamp=j.optString("strTimestamp","");if(timestamp.isEmpty()&&!j.optString("strTime").isEmpty())timestamp=j.optString("dateEvent")+"T"+j.optString("strTime");m.kickoff=SportsMatch.utc(timestamp);m.updatedAt=at;m.cached=stale;m.source="TheSportsDB · free: up to 3/day; next/past 1 · not a live feed";return m;}
 private static String number(JSONObject j,String k){if(j.isNull(k))return "";String v=j.optString(k);return v.matches("[0-9]{1,3}")?v:"";}
 /**
  * Free V1 public key 123. searchteams is capped to one result on this tier,
  * so accept only an exact normalised team name; no guesswork or Premium V2.
  * SportsCache spaces source requests >=2200ms (~27/min, below 30/min).
  */
 JSONObject findTeamBadge(String supplied)throws Exception{requireEnabled();
  String query=supplied==null?"":supplied.trim();
  if(query.length()<3||query.length()>90)throw new IOException("TEAM_NAME_REQUIRED");
  String url=api()+"searchteams.php?t="+java.net.URLEncoder.encode(query,"UTF-8");
  JSONObject response=cache.get(url,24L*3600000L).getJSONObject("data");
  JSONArray array=response.optJSONArray("teams");
  JSONObject match=null;
  if(array!=null)for(int i=0;i<Math.min(array.length(),1);i++){
   JSONObject team=array.optJSONObject(i);
   if(team==null||!team.optString("idTeam").matches("[0-9]+"))continue;
   if(!PlaylistRepository.normalize(team.optString("strTeam"))
      .equals(PlaylistRepository.normalize(query)))continue;
   String badge=team.optString("strBadge");
   if(badge.isEmpty())badge=team.optString("strTeamBadge");
   java.net.URI uri;
   try{uri=new java.net.URI(badge);}catch(Exception ignored){continue;}
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||
      uri.getFragment()!=null||
      !java.util.Arrays.asList("www.thesportsdb.com","r2.thesportsdb.com",
        "www.thesportsdb.com","thesportsdb.com").contains(uri.getHost())||
      uri.getPath()==null||!uri.getPath().startsWith("/images/"))continue;
   match=new JSONObject().put("status","verified")
     .put("team",team.optString("strTeam"))
     .put("id",team.optString("idTeam")).put("logo",badge)
     .put("provider","TheSportsDB V1 free 123")
     .put("source","https://www.thesportsdb.com/team/"+team.optString("idTeam"));
  }
  return match==null?new JSONObject().put("status","not_found"):match;
 }
 /** Search only a single canonical team, never the whole fixture title.
  * The free V1 endpoint returns one team, so require an exact verified name.
  * Used by latest-team artwork fallback, not by fixture/match identity. */
 JSONObject findTeamForRecent(String supplied)throws Exception{
  requireEnabled();
  String team=SportsLatestTeamArtwork.canonical(supplied);
  if(team.isEmpty())return new JSONObject().put("status","not_found");
  String path="searchteams.php?t="+java.net.URLEncoder.encode(team,"UTF-8");
  JSONObject data=cache.get(api()+path,24L*3600000L).getJSONObject("data");
  JSONArray rows=data.optJSONArray("teams");
  if(rows==null)return new JSONObject().put("status","not_found");
  for(int i=0;i<Math.min(rows.length(),1);i++){
   JSONObject row=rows.optJSONObject(i);
   if(row==null||!row.optString("idTeam").matches("[0-9]{1,18}")||
      !"Soccer".equalsIgnoreCase(row.optString("strSport","Soccer"))||
      !SportsLatestTeamArtwork.sameTeam(team,row))continue;
   String fanart="";
   for(String field:new String[]{"strFanart1","strFanart2","strFanart3",
         "strFanart4","strBanner","strBadge"}){
    String value=row.optString(field,"");
    if(value.startsWith("https://")&&TeamLogoManager.trusted(value)&&
       value.contains("/images/")){fanart=value;break;}
   }
   return new JSONObject().put("status","verified")
     .put("id",row.optString("idTeam"))
     .put("team",row.optString("strTeam"))
     .put("fanart",fanart);
  }
  return new JSONObject().put("status","not_found");
 }
 /** Documented V1 team-history route; the public key can return just one
  * home event. We use the newest published entry, not a made-up result. */
 JSONObject lastEventsForTeam(String numericId)throws Exception{
  requireEnabled();
  if(numericId==null||!numericId.matches("[0-9]{1,18}"))
   throw new IOException("SPORTS_TEAM_ID_REQUIRED");
  return cache.get(api()+"eventslast.php?id="+numericId,4L*3600000L)
    .getJSONObject("data");
 }
 /** Resolve an official team badge by numeric team ID, not a guessed name.
  * SportsCache enforces the public API's free-rate budget. */
 String badgeById(String id,String name)throws Exception{requireEnabled();
  if(id==null||!id.matches("[0-9]{1,18}")||name==null||name.isEmpty())return "";
  JSONObject wrapper=cache.get(api()+"lookupteam.php?id="+id,7L*86400000L);
  JSONArray teams=wrapper.getJSONObject("data").optJSONArray("teams");
  if(teams==null)return "";
  for(int i=0;i<Math.min(teams.length(),3);i++){
   JSONObject row=teams.optJSONObject(i);
   if(row==null||!id.equals(row.optString("idTeam")))continue;
   String known=row.optString("strTeam");
   if(!MatchRepository.programmeMatches(name,known,known) &&
      !PlaylistRepository.normalize(name).equals(PlaylistRepository.normalize(known)))continue;
   String address=row.optString("strBadge");
   if(address.isEmpty())address=row.optString("strTeamBadge");
   if(TeamLogoManager.trusted(address))return address;
  }
  return "";
 }
 /** Free V1 player search is limited to ONE result, so reject partial names. */
 JSONObject findPlayerPortrait(String supplied)throws Exception{requireEnabled();
  String query=supplied==null?"":supplied.trim();
  if(query.length()<3||query.length()>90)throw new IOException("PLAYER_NAME_REQUIRED");
  String url=api()+"searchplayers.php?p="+java.net.URLEncoder.encode(query,"UTF-8");
  JSONObject reply=cache.get(url,24L*3600000L).getJSONObject("data");
  JSONArray people=reply.optJSONArray("player");
  if(people==null)people=reply.optJSONArray("players");
  if(people!=null)for(int i=0;i<Math.min(people.length(),1);i++){
   JSONObject person=people.optJSONObject(i);
   if(person==null||!person.optString("idPlayer").matches("[0-9]+"))continue;
   if(!PlaylistRepository.normalize(person.optString("strPlayer"))
     .equals(PlaylistRepository.normalize(query)))continue;
   String picture=person.optString("strThumb");
   if(picture.isEmpty())picture=person.optString("strCutout");
   try{
    java.net.URI uri=new java.net.URI(picture);
    if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null
      ||uri.getFragment()!=null||uri.getPath()==null||
      !uri.getPath().startsWith("/images/")||
      !Arrays.asList("www.thesportsdb.com","r2.thesportsdb.com",
         "thesportsdb.com").contains(uri.getHost()))continue;
   }catch(Exception invalid){continue;}
   return new JSONObject().put("status","verified")
      .put("player",person.optString("strPlayer"))
      .put("id",person.optString("idPlayer"))
      .put("poster",picture)
      .put("provider","TheSportsDB V1 free 123")
      .put("source","https://www.thesportsdb.com/player/"+person.optString("idPlayer"));
  }
  return new JSONObject().put("status","not_found");
 }
 JSONObject details(String id,String endpoint)throws Exception{requireEnabled();if(!id.matches("tsdb:[0-9]+"))throw new IOException("Details not supplied by this source");if(!Arrays.asList("lookupevent","lookuptimeline","lookuplineup","lookupeventstats").contains(endpoint))throw new IOException("Unsupported details endpoint");return cache.get(api()+endpoint+".php?id="+id.substring(5),5*60000L);}
}
