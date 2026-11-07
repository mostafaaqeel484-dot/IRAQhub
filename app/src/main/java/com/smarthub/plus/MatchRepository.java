package com.smarthub.plus;
import android.content.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;import java.util.concurrent.*;
/** One updater shared by Sports Center, ticker and EPG matching. Per-league failures do not erase results. */
final class MatchRepository {
 interface Listener{void update(List<SportsMatch> matches,String status);}
 private static MatchRepository instance;static synchronized MatchRepository get(Context c){if(instance==null)instance=new MatchRepository(c.getApplicationContext());return instance;}
 private final Context context;private final TheSportsDBProvider primary;private final OpenLeagueSportsProvider fallback;private final ExecutorService network=Executors.newSingleThreadExecutor();private final List<Listener> listeners=new CopyOnWriteArrayList<>();private final LinkedHashMap<String,SportsMatch> rows=new LinkedHashMap<>();private final Map<String,Long> attempts=new HashMap<>();private volatile boolean busy;private String status="No update yet";
 private MatchRepository(Context c){context=c;primary=new TheSportsDBProvider(c);fallback=new OpenLeagueSportsProvider(c);load();}
 void listen(Listener l){listeners.add(l);l.update(snapshot(),status);}void unlisten(Listener l){listeners.remove(l);}
 synchronized List<SportsMatch> snapshot(){List<SportsMatch> out=new ArrayList<>(rows.values());out.sort(Comparator.comparingLong(m->m.kickoff));return out;}
 void refresh(LocalDate day,String window,boolean manual){if(busy)return;busy=true;network.execute(()->{try{int count=0;for(LeagueRepository.League league:LeagueRepository.ALL){if(!selected(league)||league.id.isEmpty())continue;String key=league.id+"|"+day+"|"+window;long now=System.currentTimeMillis();synchronized(attempts){if(!manual&&now-attempts.getOrDefault(key,0L)<(hasLive(league.id)?120000:900000))continue;attempts.put(key,now);}publish("Updating "+league.name+" · "+(++count));List<SportsMatch> result=new ArrayList<>();String error="";try{result.addAll(primary.matches(league,day,window));}catch(Exception e){error=e.getMessage();}try{List<SportsMatch> community=fallback.matches(league,day,window);for(SportsMatch item:community){boolean duplicate=false;for(SportsMatch previous:result)if(sameEvent(previous,item)){duplicate=true;break;}if(!duplicate)result.add(item);}}catch(Exception unavailable){}synchronized(this){for(SportsMatch m:result)rows.put(m.id,m);long cutoff=now-31*86400000L;rows.values().removeIf(m->m.kickoff>0&&m.kickoff<cutoff);}context.getSharedPreferences("smarthub",0).edit().putString("sports_coverage:"+league.id,result.size()+" returned · TheSportsDB free limits; OpenLigaDB when available"+(error.isEmpty()?"":" · "+error)).apply();save();publish(league.name+" · "+result.size()+" source events · "+(error.isEmpty()?"updated":"primary unavailable"));}publish("Update finished · Baghdad time · free coverage limited; no guaranteed live feed");
 // The poster worker may have run before an asynchronous fixture refresh finished.
 // Prefetch after new events arrive so the Infobar can use verified local artwork.
 if(!context.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false)
    &&!context.getSharedPreferences("smarthub",0).getBoolean("dvb_tuning",false))
  try{SportsPosterPrefetch.prefetch(context);}
  catch(Exception unavailable){}
 }finally{busy=false;}});}
 boolean selected(LeagueRepository.League l){return context.getSharedPreferences("smarthub",0).getBoolean("sports_league:"+l.name,true);}
 private synchronized boolean hasLive(String id){for(SportsMatch m:rows.values())if(m.leagueId.equals(id)&&m.live()&&!m.cached)return true;return false;}
 private void publish(String message){status=message;List<SportsMatch> snapshot=snapshot();for(Listener l:listeners)l.update(snapshot,message);}
 private static boolean sameTeam(String a,String shortA,String b,String shortB){for(String left:new String[]{a,shortA})for(String right:new String[]{b,shortB})if(!left.isEmpty()&&!right.isEmpty()&&tokens(left).equals(tokens(right)))return true;return false;}
 private static boolean sameEvent(SportsMatch a,SportsMatch b){return sameTeam(a.home,a.homeShort,b.home,b.homeShort)&&sameTeam(a.away,a.awayShort,b.away,b.awayShort)&&Math.abs(a.kickoff-b.kickoff)<3600000;}
 synchronized SportsMatch find(String id){return rows.get(id);}
 synchronized SportsMatch forProgramme(SatelliteState state){if(state.title.isEmpty()||state.startMs<=0||state.endMs<=System.currentTimeMillis())return null;SportsMatch found=null;for(SportsMatch m:rows.values()){if(m.kickoff<=0||Math.abs(m.kickoff-state.startMs)>3*3600000L||!programmeMatches(state.title,m.home,m.away)&&!programmeMatches(state.title,m.homeShort,m.awayShort))continue;if(found!=null)return null;found=m;}return found;}
 private static String tokens(String value){return PlaylistRepository.normalize(value).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 static boolean programmeMatches(String programme,String home,String away){String p=" "+tokens(programme)+" ",h=tokens(home),a=tokens(away);return h.length()>=4&&a.length()>=4&&p.contains(" "+h+" ")&&p.contains(" "+a+" ");}
 private File file(){return new File(context.getFilesDir(),"sports_matches.json");}
 private synchronized void load(){try(InputStream in=new FileInputStream(file())){JSONArray a=new JSONArray(PlaylistRepository.readBounded(in));for(int i=0;i<Math.min(a.length(),3000);i++){SportsMatch m=SportsMatch.from(a.getJSONObject(i));m.cached=true;rows.put(m.id,m);}status="Saved results · online update pending";}catch(Exception ignored){}}
 private synchronized void save(){try{JSONArray a=new JSONArray();for(SportsMatch m:rows.values())a.put(m.json());android.util.AtomicFile f=new android.util.AtomicFile(file());FileOutputStream out=f.startWrite();try{out.write(a.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){f.failWrite(out);}}catch(Exception ignored){}}
}
