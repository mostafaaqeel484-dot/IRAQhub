package com.smarthub.plus;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.regex.*;
/** Read-only decoder telemetry, checked on the device with an ordinary app UID. */
final class DecoderDimensions {
 final int width,height;private DecoderDimensions(int w,int h){width=w;height=h;}
 private static int number(String text,String name){Matcher m=Pattern.compile("(?im)\\b"+name+"\\s*[:=]\\s*([0-9]{1,6})\\b").matcher(text);if(!m.find())return 0;int n=Integer.parseInt(m.group(1));return m.find()?0:n;}
 static DecoderDimensions parse(String text){if(text==null||text.length()>8192)return null;int w=number(text,"width"),h=number(text,"height");return w>=16&&w<=8192&&h>=16&&h<=8192?new DecoderDimensions(w,h):null;}
 static DecoderDimensions read(){try(InputStream input=new FileInputStream("/sys/class/vdec/vdec_status")){byte[] bytes=new byte[8193];int count=input.read(bytes);return count>0&&count<=8192?parse(new String(bytes,0,count,StandardCharsets.US_ASCII)):null;}catch(Exception unavailable){return null;}}
}
