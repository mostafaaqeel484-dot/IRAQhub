package com.smarthub.plus;

/** Remote navigation through an uninterrupted satellite scope. */
final class CircularChannelNavigation {
 private CircularChannelNavigation(){}
 static int move(int selected,int delta,int size){
  if(size<=0)return -1;
  return Math.floorMod(Math.max(0,selected)+delta,size);
 }
}