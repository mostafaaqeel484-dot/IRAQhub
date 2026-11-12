package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

/** Single settings contract for user-enabled poster providers.
 * Cached downloads remain available offline after a provider is disabled. */
final class PosterSourceSwitches {
 static final String TMDB="art_source_tmdb_enabled";
 static final String OPDB="art_source_opdb_enabled";
 static final String RPDB="art_source_rpdb_enabled";
 static final String BETTER="art_source_betterposters_enabled";
 static final String TVMAZE="art_source_tvmaze_enabled";
 static final String SPORTS="art_source_sportsdb_enabled";
 static final String COMMONS="art_source_commons_enabled";
 static final String ARCHIVE="art_source_archive_enabled";
 static final String OMDB="art_source_omdb_enabled";
 static final String ANILIST="art_source_anilist_enabled";
 static final String KITSU="art_source_kitsu_enabled";
 static final String ANIDB="art_source_anidb_mapping_enabled";
 private static final List<String> KEYS=Arrays.asList(TMDB,OPDB,RPDB,BETTER,TVMAZE,SPORTS,COMMONS,ARCHIVE,OMDB,ANILIST,KITSU,ANIDB);
 private PosterSourceSwitches(){}
 static boolean known(String key){return KEYS.contains(key);}
 static boolean enabled(Context c,String key){
  return known(key)&&c.getSharedPreferences("smarthub",0).getBoolean(key,true);
 }
 static void set(Context c,String key,boolean enabled){
  if(!known(key))throw new IllegalArgumentException("Unknown poster provider");
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  if(p.getBoolean(key,true)==enabled)return;
  long now=System.currentTimeMillis();
  p.edit().putBoolean(key,enabled).putLong("poster_sources_changed_at",now)
   .putLong("epg_art_changed_at",now).apply();
 }
}
