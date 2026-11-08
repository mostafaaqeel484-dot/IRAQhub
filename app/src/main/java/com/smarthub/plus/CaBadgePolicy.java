package com.smarthub.plus;
import java.util.Locale;

/**
 * Evidence-only CAS indicators. Family-level CAIDs are NOT proof that
 * descrambling succeeded, nor do they reveal the actual Nagra revision.
 */
final class CaBadgePolicy {
 static final String[] LABELS={"CA","IR","BISS","VIDEOGARD","Nag2","VIA","FTA","BETA","OTHER"};
 private CaBadgePolicy(){}
 static boolean[] active(String raw){
  boolean[] a=new boolean[LABELS.length];
  if(raw==null||raw.trim().isEmpty())return a;
  String x=" "+raw.toUpperCase(Locale.ROOT)
   .replace('·',' ').replace('/',' ').replace(',',' ')+" ";
  boolean fta=has(x,"FTA")||x.contains("FREE TO AIR");
  boolean ir=has(x,"IR")||x.contains("IRDETO");
  boolean biss=x.contains("BISS");
  boolean nds=has(x,"NDS")||x.contains("VIDEOGUARD");
  boolean nag=x.contains("NAGRA")||x.contains("NAGRAVISION")||has(x,"NAG2");
  boolean via=has(x,"VI")||x.contains("VIACCESS")||has(x,"VIA");
  boolean beta=x.contains("BETA")||x.contains("BETACRYPT");
  boolean other=x.contains("CONAX")||x.contains("SECA")||
    x.contains("MEDIAGUARD")||x.contains("OTHER")||
    x.contains("CRYPTOWORKS")||x.contains("TANDBERG")||
    x.contains("POWER VU");
  boolean encrypted=ir||biss||nds||nag||via||beta||other||
    (has(x,"CA")&&!fta)||x.contains("ENCRYPTED");
  a[0]=encrypted;
  a[1]=ir;a[2]=biss;a[3]=nds;a[4]=nag;a[5]=via;
  a[6]=fta&&!encrypted;a[7]=beta;
  a[8]=other||(encrypted&&!ir&&!biss&&!nds&&!nag&&!via&&!beta);
  return a;
 }
 private static boolean has(String text,String word){return text.contains(" "+word+" ");}
}
