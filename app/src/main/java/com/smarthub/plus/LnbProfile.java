package com.smarthub.plus;
import org.json.*;import java.io.IOException;

/** Values match the receiver SDK enums; motor and Unicable commands are deliberately absent. */
final class LnbProfile {
 final int low,high,power,tone,port10,port11;
 LnbProfile(int low,int high,int power,int tone,int port10,int port11)throws IOException{if(low<3000||low>15000||high<3000||high>15000||power<0||power>6||tone<0||tone>2||port10<0||port10>6||port11<0||port11>16)throw new IOException("DVB_INVALID_LNB_PROFILE");this.low=low;this.high=high;this.power=power;this.tone=tone;this.port10=port10;this.port11=port11;}
 static LnbProfile parse(String raw)throws Exception{JSONObject j=new JSONObject(raw);return new LnbProfile(j.getInt("low"),j.getInt("high"),j.getInt("power"),j.getInt("tone"),j.getInt("port10"),j.getInt("port11"));}
 String json()throws Exception{return new JSONObject().put("low",low).put("high",high).put("power",power).put("tone",tone).put("port10",port10).put("port11",port11).toString();}
 static String key(int angle,String band){return "own_lnb:"+angle+":"+band;}
 JSONObject tune(int tpIndex,int frequency,String polarity,int symbolRate)throws Exception{
  if(tpIndex<0||tpIndex>1000000||frequency<100||frequency>100000||symbolRate<1||symbolRate>100000||!(polarity.equals("H")||polarity.equals("V")))throw new IOException("DVB_METADATA_REQUIRED");
  return new JSONObject().put("iTunerIndex",0).put("iTpId",1200000000+tpIndex).put("iTpType",0).put("uiFrequency",frequency).put("uiSymbolRate",symbolRate).put("iPol",polarity.equals("H")?0:1).put("iLnbFreqLow",low).put("iLnbFreqHigh",high).put("iLnbPower",power).put("b22KOn",tone).put("iDiSEqC10_Port",port10).put("iDiSEqC11_Port",port11).put("iFEC",0).put("iModulation",2).put("bBlindScan",0).put("iMotor_Mode",0).put("iMotor_SatNo",0).put("iMotor_SatAngle",0).put("iMotor_LatitudeAngle",0).put("iMotor_LongitudeAngle",0).put("bMotor_West",0).put("bUnicable_Enable",0).put("bUnicableSel",0).put("iUnicable_CableIndex",0).put("iUnicable_ToneIF",0).put("scramble_value",0).put("scramble_value_type",0).put("dvbc_mode",0).put("qam",0);
 }
}
