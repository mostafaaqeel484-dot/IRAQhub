package com.smarthub.plus;

/** TV remote numeric zap buffer, independent of filtered/alphabetical list order. */
final class ChannelNumberInput {
 private static final int MAX_DIGITS=5;
 private String digits="";
 void add(int digit){
  if(digit<0||digit>9)return;
  if(digits.length()==MAX_DIGITS)digits="";
  digits+=digit;
 }
 int number(){
  if(digits.isEmpty())return -1;
  try{
   int number=Integer.parseInt(digits);
   return number>0&&number<=99999?number:-1;
  }catch(NumberFormatException bad){return -1;}
 }
 String display(){return digits;}
 boolean hasInput(){return !digits.isEmpty();}
 void clear(){digits="";}
}
