package com.smarthub.plus;

import java.util.Locale;

/** Classifies actual decoder properties. It never infers quality from a
 * channel name. The 560 class intentionally includes broadcast 540/576-line
 * sources while the raw dimensions remain available in diagnostics. */
final class BroadcastVideoClass {
 private BroadcastVideoClass(){}
 static String resolution(int width,int height){
  int lines=Math.min(width,height);
  if(lines>=900)return "1080";
  if(lines>=650)return "720";
  if(lines>=500)return "560";
  if(lines>=300)return "360";
  if(lines>0)return "240";
  return "UNKNOWN";
 }
 static String chroma(String pixelFormat){
  String value=pixelFormat==null?"":pixelFormat.toLowerCase(Locale.ROOT);
  if(value.contains("422"))return "4:2:2";
  if(value.contains("420")||value.contains("nv12")||value.contains("nv21"))return "4:2:0";
  return "UNKNOWN";
 }
 static String label(int width,int height,String pixelFormat){
  return resolution(width,height)+"p · "+chroma(pixelFormat)+
    (width>0&&height>0?" · "+width+"×"+height:"");
 }
}
