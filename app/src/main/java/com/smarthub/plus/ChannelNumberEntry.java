package com.smarthub.plus;

/** Bounded numeric receiver channel input; no implicit tuning side effects. */
final class ChannelNumberEntry {
 private final StringBuilder digits=new StringBuilder();
 boolean pending(){return digits.length()>0;}
 String append(int digit){
  if(digit<0||digit>9)return digits.toString();
  if(digits.length()>=5)digits.setLength(0);
  if(digits.length()==0&&digit==0)return "0";
  digits.append(digit);
  return digits.toString();
 }
 int take(){
  if(!pending())return -1;
  String saved=digits.toString();
  clear();
  try{int value=Integer.parseInt(saved);return value>0&&value<=50000?value:-1;}
  catch(NumberFormatException bad){return -1;}
 }
 void clear(){digits.setLength(0);}
}
