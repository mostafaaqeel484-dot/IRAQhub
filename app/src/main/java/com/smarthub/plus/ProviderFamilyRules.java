package com.smarthub.plus;
import java.util.Locale;

/**
 * Provider artwork is not a channel logo. User-selected family branding has
 * priority over generic workbook ownership and over similar channel names.
 * Orbital-only rules are limited to the explicitly requested platform at SES 7/9.
 * The EPG and tuner identity are not modified here.
 */
final class ProviderFamilyRules {
 static final class Brand {
  final String display,assetOwner,country;
  final boolean knownMarket;
  Brand(String display,String assetOwner,String country,boolean market){
   this.display=display;this.assetOwner=assetOwner;this.country=country;knownMarket=market;
  }
 }
 private ProviderFamilyRules(){}
 private static final Brand SKY=new Brand("Sky","Sky Digital","GB",false);
 private static final Brand ARENA=new Brand("Arena Sport","Arena Channels Group","RS",false);
 private static final Brand DIGITALB=new Brand("DigitAlb","DigitAlb","AL",true);
 private static final Brand TRING=new Brand("Tring","Tring Digital","AL",true);
 private static final Brand CANAL_PL=new Brand("CANAL+ Polska","Platforma Canal+ Polska","PL",true);
 private static final Brand AIRTEL=new Brand("Airtel Digital TV","Airtel Digital TV","IN",true);
 private static final Brand OSN=new Brand("OSN","OSNtv","AE",false);
 private static final Brand MBC=new Brand("MBC Group","MBC Group","SA",false);
 private static final Brand ROTANA=new Brand("Rotana","Rotana","SA",false);
 private static final Brand ALJAZEERA=new Brand("Al Jazeera","Al Jazeera Media Network","QA",false);
 private static final Brand BEIN=new Brand("beIN","beIN MENA","QA",false);
 private static final Brand BEIN_TR=new Brand("Digitürk / beIN Turkey","Digitürk / beIN Turkey","TR",false);

 static String clean(String value) {
  String s=value==null?"":value.toUpperCase(Locale.ROOT).trim();
  return s.replaceAll("\\s+"," ").replaceAll("^\\d{1,3}\\s+(?=SKY(?:\\s|NEWS|SPORTS))","");
 }
 static int orbit(String physical) {
  if(physical==null||!physical.startsWith("DVB:"))return Integer.MIN_VALUE;
  String[] f=physical.split(":");
  if(f.length<7)return Integer.MIN_VALUE;
  try{return Integer.parseInt(f[1]);}catch(Exception ignored){return Integer.MIN_VALUE;}
 }
 static boolean sky(String channel){
  return channel.matches("^SKY(?:[ \\-].*|NEWS.*|SPORTS.*|SPORT .*|CINEMA.*|TG24.*|UNO.*|ATLANTIC.*|NATURE.*|CRIME.*|SERIE.*|SHOW.*|ONE.*)$")
    ||channel.equals("SKY") || channel.endsWith(" SKY NEWS") || channel.endsWith(" SKY NEWS HD");
 }
 static boolean arena(String channel){
  return channel.matches("^ARENA(?:[ \\-].*|SPORT.*|PREMIUM.*|FIGHT.*|TENIS.*|ESPORT.*)$");
 }
 static boolean supersportAlbania(String channel) {
  return channel.matches("^(?:SS(?:[- ]?HD)?[- ]?|SUPERSPORT(?: ALBANIA)?(?:[- ]?HD)?[- ]?)[1-7](?:[ -]?HD)?(?:[ .].*)?$");
 }
 static boolean tring(String channel) {
  return channel.matches("^TRING(?:[ \\-].*|SPORT.*)$");
 }
 static boolean polishCanal(String channel) {
  // Do not treat the unrelated Italian "Canale" names as CANAL+.
  return channel.startsWith("CANAL+")
   ||channel.matches("^(?:ALE KINO\\+|MINIMINI\\+|PLANETE\\+|TELETOON\\+|NOVELAS\\+)(?:[ .].*|$)");
 }
 static Brand select(String providerLabel,String channelName,String physical) {
  String ch=clean(channelName),p=clean(providerLabel);
  int sat=orbit(physical);
  // These explicit families take precedence over operator carriage lists.
  if(sky(ch)||sky(p))return SKY;
  if(arena(ch)||arena(p))return ARENA;
  if(supersportAlbania(ch) && (sat==160||sat==Integer.MIN_VALUE))return DIGITALB;
  if(tring(ch)||p.equals("TRING DIGITAL")||p.contains("/ TRING MEDIA"))return TRING;
  if(ch.startsWith("OSN")||p.startsWith("OSN"))return OSN;
  if(sat==130 && polishCanal(ch))return CANAL_PL;
  if(p.contains("CANAL+ POLSKA")||p.startsWith("PLATFORMA CANAL+"))return CANAL_PL;
  // SES 7/9 108.2°E: the receiver's Airtel DTH bouquet branding;
  // it does not assert ownership of every underlying television station.
  if(sat==1082||sat==1083||sat==10820||sat==10830||
      p.contains("AIRTEL DIGITAL TV")||ch.startsWith("AIRTEL "))return AIRTEL;
  if(ch.matches("^MBC(?:[0-9].*|[ \\-].*)$")||p.equals("MBC GROUP"))return MBC;
  if(ch.startsWith("ROTANA ")||ch.equals("ROTANA")||p.equals("ROTANA"))return ROTANA;
  if(ch.startsWith("AL JAZEERA")||ch.startsWith("ALJAZEERA")||
     p.contains("AL JAZEERA MEDIA"))return ALJAZEERA;
  if((ch.startsWith("BEIN ")||ch.startsWith("BEIN-"))&&
     (sat==414||sat==420||sat==4200||p.contains("DIGITÜRK")||p.contains("DIGITURK")))
    return BEIN_TR;
  if(ch.startsWith("BEIN ")||ch.startsWith("BEIN-")||p.equals("BEIN MENA"))return BEIN;
  return null; // Continue with workbook's unique published-channel identity.
 }
}
