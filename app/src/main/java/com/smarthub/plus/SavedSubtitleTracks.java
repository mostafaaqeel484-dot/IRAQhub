package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Descriptors imported with a channel. The live player independently verifies availability. */
final class SavedSubtitleTracks {
 static final class Track {
  final int pid, type, page;
  final String language;
  Track(int pid,int type,int page,String language){
   this.pid=pid;
   this.type=type;
   this.page=page;
   this.language=language;
  }
 }

 private SavedSubtitleTracks(){}

 static List<Track> from(JSONArray rows){
  List<Track> result=new ArrayList<>();
  Set<String> listed=new HashSet<>();
  if(rows==null)return result;
  for(int i=0;i<Math.min(32,rows.length());i++){
   JSONObject row=rows.optJSONObject(i);
   if(row==null)continue;
   int pid=row.optInt("PID",-1),type=row.optInt("Type",-1);
   if(pid<=0||pid>=8191||type<0||type>255)continue;
   String language=row.optString("Lang","und").trim();
   if(language.isEmpty()||language.length()>32||language.codePoints().anyMatch(Character::isISOControl))
    language="und";
   int page=row.optInt("CompPageID",-1),ancillary=row.optInt("AnciPageID",-1);
   if(page < -1||page>65535||ancillary < -1||ancillary>65535)continue;
   if(listed.add(pid+"/"+type+"/"+page+"/"+ancillary+"/"+language))
    result.add(new Track(pid,type,page,language));
  }
  return result;
 }

 static boolean uniquePid(List<Track> tracks,int pid){
  int matches=0;
  for(Track track:tracks)if(track.pid==pid&&++matches>1)return false;
  return matches==1;
 }
}
