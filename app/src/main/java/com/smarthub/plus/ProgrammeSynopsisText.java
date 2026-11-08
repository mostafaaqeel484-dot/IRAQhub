package com.smarthub.plus;

/** Small, readable receiver-side programme synopsis with no guessed facts. */
final class ProgrammeSynopsisText {
 private ProgrammeSynopsisText(){}
 static String clean(String value,int max){
  if(value==null||max<8)return "";
  String text=value.replaceAll("<[^>]*>"," ").replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
  if(text.length()<=max)return text;
  // Avoid cutting a surrogate pair when truncating non-BMP characters.
  int end=Math.min(max-1,text.length()-1);
  if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;
  while(end>Math.max(12,max-25)&&!Character.isWhitespace(text.charAt(end-1)))end--;
  if(end<12)end=max-1;
  return text.substring(0,Math.max(1,end)).trim()+"…";
 }
 static String choose(String guide,String online,String sports,int max){
  for(String candidate:new String[]{guide,online,sports}){
   String clean=clean(candidate,max);
   if(!clean.isEmpty())return clean;
  }
  return "";
 }
}
