package com.smarthub.plus;
import java.util.Locale;
/** Published CAID types only, with multiple simultaneous systems allowed. */
final class CaSystemBadgePolicy {
 static final String[] LABELS={"CA","IR","BISS","VIDEOGARD","Nag2","VIA","FTA","BETA","OTHER"};
 private CaSystemBadgePolicy(){}
 static boolean[] active(String published){
  boolean[] enabled=new boolean[LABELS.length];
  if(published==null)return enabled;
  String normal=(" "+published.toUpperCase(Locale.ROOT).replace('·',' ')+" ").replaceAll("\\s+"," ");
  boolean fta=normal.contains(" FTA ")||normal.contains(" FREE TO AIR ");
  boolean hasCA=normal.contains(" CA ")||normal.contains(" NAGRA ")||
    normal.contains(" NAG2 ")||normal.contains(" BETA ")||
    normal.contains(" NDS ")||normal.contains(" BISS ")||
    normal.contains(" IR ")||normal.contains(" VI ")||
    normal.contains(" VIACCESS ")||normal.contains(" IRDETO ")||
    normal.contains(" VIDEOGUARD ");
  enabled[0]=hasCA&&!fta;
  enabled[1]=normal.contains(" IR ")||normal.contains(" IRDETO ");
  enabled[2]=normal.contains(" BISS ");
  enabled[3]=normal.contains(" NDS ")||normal.contains(" VIDEOGUARD ");
  enabled[4]=normal.contains(" NAGRA ")||normal.contains(" NAG2 ")||
    normal.contains(" NAGRAVISION ");
  enabled[5]=normal.contains(" VI ")||normal.contains(" VIA ")||
    normal.contains(" VIACCESS ");
  enabled[6]=fta;
  enabled[7]=normal.contains(" BETA ")||normal.contains(" BETACRYPT ");
  boolean specific=false;
  for(int i=1;i<enabled.length;i++)if(enabled[i])specific=true;
  enabled[8]=enabled[0]&&!specific;
  return enabled;
 }
}
