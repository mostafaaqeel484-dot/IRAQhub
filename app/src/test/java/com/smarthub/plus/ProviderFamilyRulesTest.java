package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression checks from owner's 2026-11-11 original DVB receiver dump. */
public final class ProviderFamilyRulesTest {
 private static final String ORBIT16="DVB:160:KU:10846:H:30000:1004";
 private static final String HOTBIRD="DVB:130:KU:11278:V:27500:13020";
 private static final String SES="DVB:1082:KU:12711:H:44990:13724";
 private static String group(String provider,String channel,String physical){
  ProviderFamilyRules.Brand brand=ProviderFamilyRules.select(provider,channel,physical);
  return brand==null?"":brand.display;
 }
 @Test public void allSkyChannelsUseSkyProviderEvenOnHotbird(){
  assertEquals("Sky",group("","Sky Sport Arena",HOTBIRD));
  assertEquals("Sky",group("","Sky Sport 4k",HOTBIRD));
  assertEquals("Sky",group("","SkyNews HD","DVB:160:KU:11594:H:30000:1355"));
  assertEquals("Sky",group("","18 Sky Sports","DVB:90:KU:11222:H:24760:8005"));
  assertEquals("Sky",group("Sky Italia","Sky TG24",""));
 }
 @Test public void allArenaVersionsUseOneProviderMark(){
  for(String name:new String[]{"Arena Sport 1 HD","Arena Premium 5","Arena Fight","Arena Tenis","Arena Sport Crna Gora"}){
   assertEquals(name,"Arena Sport",group("",name,ORBIT16));
  }
 }
 @Test public void allSevenSuperSportAlbaniaAndTringSportChannelsAreCovered(){
  for(int n=1;n<=7;n++){
   assertEquals("DigitAlb",group("","SS-"+n+" HD",ORBIT16));
   assertEquals("Tring",group("","Tring Sport "+n+" HD",ORBIT16));
  }
  assertEquals("DigitAlb",group("","SS-HD 1",ORBIT16));
  assertEquals("Tring",group("","Tring Sport News",ORBIT16));
  assertEquals("",group("","SS-1 HD","DVB:130:KU:11278:V:27500:13020"));
  assertEquals("",group("","SS-8 HD",ORBIT16));
 }
 @Test public void polishCanalIsOnlyHotbirdOrExplicitPolishOperator(){
  assertEquals("CANAL+ Polska",group("","CANAL+ PREMIUM HD",HOTBIRD));
  assertEquals("CANAL+ Polska",group("","CANAL+ SPORT 6","DVB:130:KU:11449:H:27500:15014"));
  assertEquals("CANAL+ Polska",group("","Ale Kino+ HD",HOTBIRD));
  assertEquals("CANAL+ Polska",group("Platforma Canal+ Polska","TVP HD",""));
  assertEquals("",group("","CANAL+ SPORT","DVB:192:KU:12012:H:29700:8804"));
  assertEquals("",group("","Canale Italia",HOTBIRD));
  assertEquals("Sky",group("","Sky Sport 4k",HOTBIRD));
 }
 @Test public void airtelIsTheRequestedSes7And9DthPlatform(){
  assertEquals("Airtel Digital TV",group("","Star Sports 1 HD Hindi @ Rs 19",SES));
  assertEquals("Airtel Digital TV",group("","Program 2","DVB:1082:KU:12281:H:44920:2"));
  assertEquals("Airtel Digital TV",group("Airtel Digital TV","Star Sports 2",""));
  assertEquals("",group("","Star Sports 2","DVB:130:KU:11278:V:27500:33"));
 }
 @Test public void osnAndArabicNetworksRemainDistinct(){
  assertEquals("OSN",group("","OSNtv Movies Premiere","DVB:-70:KU:11994:H:30000:301"));
  assertEquals("MBC Group",group("","MBC IRAQ HD","DVB:260:KU:11269:H:27500:1"));
  assertEquals("Rotana",group("","Rotana Cinema KSA","DVB:-70:KU:12222:H:27500:2107"));
  assertEquals("Al Jazeera",group("","Al Jazeera English HD","DVB:130:KU:11137:H:27500:7306"));
  assertEquals("beIN",group("","beIN SPORTS 1","DVB:260:KU:11300:H:27500:11"));
  assertEquals("Digitürk / beIN Turkey",group("","beIN SPORTS 1","DVB:420:KU:11386:H:30000:14172"));
 }
}
