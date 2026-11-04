package com.smarthub.plus;
import java.util.*;
/** IDs checked against TheSportsDB league metadata; blank means no verified mapping. */
final class LeagueRepository {
 static final class League {final String id,name,group;League(String i,String n,String g){id=i;name=n;group=g;}}
 static final List<League> ALL=Collections.unmodifiableList(Arrays.asList(
 new League("4328","English Premier League","big5"),new League("4335","Spanish La Liga","big5"),new League("4332","Italian Serie A","big5"),new League("4331","German Bundesliga","big5"),new League("4334","French Ligue 1","big5"),
 new League("4480","UEFA Champions League","europe"),new League("4481","UEFA Europa League","europe"),new League("5071","UEFA Conference League","europe"),new League("4502","UEFA European Championships","international"),
 new League("5056","Iraqi Stars League","iraq"),new League("","Iraqi FA Cup","iraq"),new League("4429","FIFA World Cup","international"),new League("4866","AFC Asian Cup","international"),new League("4719","AFC Champions League Elite","asia")));
 static League byId(String id){for(League l:ALL)if(l.id.equals(id)&&!id.isEmpty())return l;return null;}
 static boolean nameMatches(League l,String name){String a=PlaylistRepository.normalize(name),b=PlaylistRepository.normalize(l.name);return a.equals(b)||l.id.equals("5056")&&a.equals(PlaylistRepository.normalize("Iraqi Premier League"))||l.id.equals("5071")&&a.equals(PlaylistRepository.normalize("UEFA Europa Conference League"));}
}
