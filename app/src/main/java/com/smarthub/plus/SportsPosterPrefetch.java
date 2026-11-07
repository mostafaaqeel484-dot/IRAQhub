package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URL;
import java.util.List;

/**
 * Source-ID-verified sports image prefetch. No match is inferred from titles.
 * Reads existing published event records; never blocks the DVB UI or decoder.
 */
final class SportsPosterPrefetch {
 private SportsPosterPrefetch(){}
 static int prefetch(Context context){
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  if(prefs.getBoolean("software_dvb_422_active",false)
    ||prefs.getBoolean("dvb_tuning",false)
    ||prefs.getBoolean("dvb_player_visible",false)
    ||!prefs.getString("iraqhub_pvr_session","").isEmpty())return 0;
  List<SportsMatch> matches=MatchRepository.get(context).snapshot();
  long now=System.currentTimeMillis();
  int verified=0,attempts=0;
  for(SportsMatch m:matches){
   if(Thread.currentThread().isInterrupted()||attempts>=6)break;
   // A real TheSportsDB event and its club identities are required.
   // A similarly named team, film or TV programme must never supply artwork.
   if(m==null||m.id==null||!m.id.matches("tsdb:[0-9]+")
     ||m.homeId.isEmpty()||m.awayId.isEmpty()||m.leagueId.isEmpty()
     ||m.kickoff<now-86400000L||m.kickoff>now+7L*86400000L
     ||!TeamLogoManager.trusted(m.image)||!m.image.startsWith("https://"))
    continue;
   attempts++;
   try{
    URL u=new URL(m.image);
    byte[] image=ArtworkCache.fetch(context,u,4*1024*1024); // 24h TTL; stale-cache fallback
    if(ArtworkCache.validImage(image))verified++;
   }catch(Exception unavailable){
    // A failed event image never overwrites a previously verified cached copy.
   }
  }
  prefs.edit().putInt("sports_poster_prefetch_verified",verified)
    .putInt("sports_poster_prefetch_attempted",attempts)
    .putLong("sports_poster_prefetch_checked_at",now).apply();
  return verified;
 }
}
