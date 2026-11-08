package com.smarthub.plus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Opt-in approximate title poster fallback for live EPG. Never treats an
 * approximate result as verified programme identity or persists its mapping.
 */
final class SimilarPosterChoice {
 private SimilarPosterChoice(){}
 private static Set<String> words(String raw){
  Set<String> out=new HashSet<>();
  String normalized=BetterPostersProvider.titleTokens(raw);
  for(String token:normalized.split("\\s+"))
   if(token.length()>=2)out.add(token.toLowerCase(Locale.ROOT));
  return out;
 }
 static JSONObject choose(JSONArray candidates,String broadcast){
  if(candidates==null)return null;
  BroadcastTitle wanted=BroadcastTitle.parse(broadcast);
  Set<String> query=words(wanted.title);
  if(query.isEmpty()||wanted.title.trim().length()<5)return null;
  if(query.size()==1&&query.iterator().next().length()<6)return null;
  JSONObject best=null;
  double first=0,second=0;
  for(int i=0;i<Math.min(30,candidates.length());i++){
   JSONObject c=candidates.optJSONObject(i);
   if(c==null||!c.optString("id").matches("tmdb:[1-9][0-9]{0,10}")||
      !Arrays.asList("movie","series").contains(c.optString("type"))||
      !c.optString("poster").startsWith("https://image.tmdb.org/t/p/"))continue;
   if(wanted.year>0&&c.optInt("year")!=wanted.year)continue;
   Set<String> title=words(c.optString("title"));
   if(title.isEmpty())continue;
   int common=0;
   for(String w:query)if(title.contains(w))common++;
   if(common==0||(query.size()>1&&common<2))continue;
   double recall=common/(double)query.size();
   double precision=common/(double)title.size();
   double score=recall*.65+precision*.35;
   if(score>first){
    second=first;first=score;best=c;
   }else if(score>second)second=score;
  }
  return best!=null&&first>=.76&&first-second>=.08?best:null;
 }
}
