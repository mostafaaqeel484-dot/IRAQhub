package com.smarthub.plus;
import java.text.Normalizer;
import java.util.Locale;

/** Exact first-letter index for a 10,000+ DVB service list: no keyboard. */
final class ChannelInitialFilter {
 static final String LATIN="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
 static final String ARABIC="ابتثجحخدذرزسشصضطظعغفقكلمنهوي";
 private ChannelInitialFilter(){}
 static String[] choices(){
  String[] values=new String[2+LATIN.length()+ARABIC.length()];
  values[0]="";values[1]="#";
  int at=2;
  for(int i=0;i<LATIN.length();i++)values[at++]=LATIN.substring(i,i+1);
  for(int i=0;i<ARABIC.length();i++)values[at++]=ARABIC.substring(i,i+1);
  return values;
 }
 static String initial(String value){
  if(value==null)return "#";
  String normalized=Normalizer.normalize(value.trim(),Normalizer.Form.NFKD);
  int i=0;
  while(i<normalized.length()){
   int cp=normalized.codePointAt(i);
   i+=Character.charCount(cp);
   if(Character.getType(cp)==Character.NON_SPACING_MARK||cp==0x0640)continue;
   if(cp==0x0623||cp==0x0625||cp==0x0622||cp==0x0671)cp=0x0627;
   if(cp==0x0649)cp=0x064A;
   if(Character.isDigit(cp))return "#";
   if(Character.isLetter(cp)){
    String first=new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
    if(LATIN.contains(first)&&first.length()==1)return first;
    if(ARABIC.contains(first)&&first.length()==1)return first;
    return "#";
   }
  }
  return "#";
 }
 static boolean matches(String name,String letter){
  return letter==null||letter.isEmpty()||initial(name).equals(letter);
 }
}
