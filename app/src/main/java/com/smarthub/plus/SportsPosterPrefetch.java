package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URL;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Verified TheSportsDB match image prefetch. A full event identity is required,
 * not a title match; prefetches team badges for the Infobar fallback card.
 * All work is background-only and bounded during live DVB playback.
 */
final class SportsPosterPrefetch {
 private SportsPosterPrefetch(){}
 static int prefetch(Context context){
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  if(prefs.getBoolean("video_priority_active",false)||prefs.getBoolean("software_dvb_422_active",false)
    ||prefs.getBoolean("dvb_tuning",false)
    ||!prefs.getString("iraqhub_pvr_session","").isEmpty())return 0;
  List<SportsMatch> matches=MatchRepository.get(context).snapshot();
  long now=System.currentTimeMillis();
  boolean watching=prefs.getBoolean("dvb_player_visible",false);
  int eventBudget=watching?1:4,networkBudget=watching?2:10;
  int verified=0,attempts=0,events=0;
  Set<String> unique=new HashSet<>();
  for(SportsMatch m:matches){
   if(Thread.currentThread().isInterrupted()||events>=eventBudget||attempts>=networkBudget||prefs.getBoolean("software_dvb_422_active",false)||prefs.getBoolean("dvb_tuning",false))break;
   if(!SportsProgrammeArtwork.verified(m)
      ||m.kickoff<now-86400000L||m.kickoff>now+7L*86400000L)continue;
   events++;
   // Prefer the real match poster, but prefetch club badges for an event card
   // if the poster is absent. All URLs must be supplied by this exact event.
   for(String address:new String[]{m.image,m.homeLogo,m.awayLogo,m.leagueLogo}){
    if(Thread.currentThread().isInterrupted()||attempts>=networkBudget)break;
    if(address==null||!address.startsWith("https://")
      ||!TeamLogoManager.trusted(address)||!unique.add(address))continue;
    attempts++;
    try{
     byte[] bytes=ArtworkCache.fetch(context,new URL(address),4*1024*1024);
     if(ArtworkCache.validImage(bytes))verified++;
    }catch(Exception unavailable){
     // Retain last verified cached image on provider failures.
    }
   }
  }
  prefs.edit().putInt("sports_poster_prefetch_verified",verified)
    .putInt("sports_poster_prefetch_attempted",attempts)
    .putInt("sports_poster_prefetch_events",events)
    .putLong("sports_poster_prefetch_checked_at",now).apply();
  return verified;
 }
}
