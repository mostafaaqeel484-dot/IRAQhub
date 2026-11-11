package com.smarthub.plus;

import android.content.Context;

/** Never attach the description of a different EPG event to a poster. */
final class ProgrammeSynopsisSource {
 private ProgrammeSynopsisSource(){}
 static String local(Context context,SatelliteState snapshot,boolean upcoming){
  if(snapshot==null)return "";
  String title=upcoming?snapshot.nextTitle:snapshot.title;
  if(title==null||title.trim().isEmpty())return "";
  try{
   SatelliteGuideRepository guide=new SatelliteGuideRepository(context);
   PlaylistRepository.Programme event=upcoming?
     guide.nextFor(snapshot,System.currentTimeMillis()):
     guide.currentFor(snapshot,System.currentTimeMillis());
   if(event!=null&&PlaylistRepository.normalize(title).equals(
       PlaylistRepository.normalize(event.title))&&
       event.description!=null&&!event.description.isEmpty())
    return ProgrammeSynopsisText.clean(event.description,160);
  }catch(Exception unavailable){/* Try the live sports metadata below. */}
  try{
   SatelliteState event=snapshot.copy();
   if(upcoming){
    event.title=snapshot.nextTitle;event.startMs=snapshot.nextStartMs;
    event.endMs=snapshot.nextEndMs;
   }
   SportsMatch match=MatchRepository.get(context).forProgramme(event);
   if(match!=null){
    String details=ProgrammeSynopsisText.clean(match.description,160);
    if(!details.isEmpty())return details;
    String names=match.home+" × "+match.away;
    return ProgrammeSynopsisText.clean(
      (match.league.isEmpty()?"":match.league+" · ")+names+" · "+match.time(),160);
   }
  }catch(Exception unavailable){}
  return "";
 }
}
