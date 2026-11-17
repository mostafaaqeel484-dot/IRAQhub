package com.smarthub.plus;

/** Keeps RX7 Home artwork slots visible while exact-channel assets load.
 * Changing an event clears the previous event's image; a retry never does. */
final class Rx7HeroArtworkPolicy {
 private Rx7HeroArtworkPolicy(){}
 static boolean eventChanged(String previous,String current){
  return previous==null||!previous.equals(current);
 }
 static boolean shouldRefresh(String previous,String current,long now,long due){
  return eventChanged(previous,current)||now>=due;
 }
 static boolean canLoadPoster(String channel,String title){
  return channel!=null&&!channel.trim().isEmpty()
      &&title!=null&&!title.trim().isEmpty();
 }
}
