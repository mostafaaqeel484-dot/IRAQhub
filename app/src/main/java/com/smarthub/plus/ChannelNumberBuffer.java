package com.smarthub.plus;

/** TV remote integer channel entry; keeps OSD numeric input independent of
 * alphabet-search list filters and database IDs.
 */
final class ChannelNumberBuffer {
 private final StringBuilder digits=new StringBuilder(5);
 boolean push(int digit){
  if(digit<0||digit>9)return false;
  if(digits.length()>=5)digits.delete(0,digits.length());
  if(digits.length()==0&&digit==0)return false;
  digits.append(digit);
  return true;
 }
 int value(){if(digits.length()==0)return 0;
  try{return Integer.parseInt(digits.toString());}catch(NumberFormatException e){return 0;}
 }
 String display(){return digits.toString();}
 boolean pending(){return digits.length()>0;}
 void clear(){digits.setLength(0);}
}
