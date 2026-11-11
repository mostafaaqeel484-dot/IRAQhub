package com.smarthub.plus;
import org.json.*;import java.io.IOException;import java.math.BigDecimal;import java.util.Locale;

/** Raw receiver measurements; missing/invalid values stay unavailable instead of becoming zero. */
final class ReceiverSignalReading {
 final int tuner,tp,locked;final Double strength,quality,snr,ber;final String format;
 private ReceiverSignalReading(int tuner,int tp,int locked,Double strength,Double quality,Double snr,Double ber,String format){this.tuner=tuner;this.tp=tp;this.locked=locked;this.strength=strength;this.quality=quality;this.snr=snr;this.ber=ber;this.format=format;}
 private static Double metric(JSONObject j,String key,double maximum){return metric(j,key,0,maximum);}
 private static Double metric(JSONObject j,String key,double minimum,double maximum){try{double n=j.getDouble(key);return Double.isFinite(n)&&n>=minimum&&n<=maximum?n:null;}catch(Exception e){return null;}}
 static ReceiverSignalReading parse(JSONObject j,int expected)throws Exception{int tuner=j.getInt("iTunerIndex");if(tuner!=expected||tuner<0||tuner>7)throw new IOException("DIRECT_SIGNAL_TUNER_MISMATCH");Double strength=metric(j,"iSignalStrength",100),quality=metric(j,"iSignalQuality",100),snr=metric(j,"iSigSNR",-50,100),ber=metric(j,"iSigBER",4294967295d);if(strength==null&&quality==null&&snr==null&&ber==null)throw new IOException("DIRECT_SIGNAL_METRICS_UNAVAILABLE");String format=j.isNull("signalFormat")?"":j.optString("signalFormat").trim();if(format.length()>80||format.chars().anyMatch(c->c<32))format="";int locked=j.optInt("bLocked",-1);if(locked!=0&&locked!=1)locked=-1;return new ReceiverSignalReading(tuner,j.optInt("iTpId",-1),locked,strength,quality,snr,ber,format);}
 static ReceiverSignalReading packet(String text,int expected)throws Exception{if(text.length()>8192)return null;String[] parts=text.split("\n",3);if(parts.length!=3||!parts[0].equalsIgnoreCase("Tuner")||!parts[1].equals("0003")||!parts[2].startsWith("SUCCESS:"))return null;int start=parts[2].indexOf('{');return start<0?null:parse(new JSONObject(parts[2].substring(start).replace("\u0000","")),expected);}
 private static String number(Double n){return n==null?"":BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
 String value(String field){switch(field){case "strength":return strength==null?"":number(strength)+"%";case "quality":return quality==null?"":number(quality)+"%";case "snr":return snr==null?"":String.format(Locale.ROOT,"%.2f dB",snr);case "ber":return number(ber);default:return "";}}
 static boolean fresh(String expected,String observed,long now,long seen){long age=now-seen;return expected.equals(observed)&&seen>0&&age>=0&&age<4000;}
}
