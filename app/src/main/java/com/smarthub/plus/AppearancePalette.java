package com.smarthub.plus;

/** UI-only theme palette. No side effects on tuner, playback or system UI. */
final class AppearancePalette {
 private AppearancePalette(){}
 static int accent(int theme,int wallpaperAccent){
  return theme==1?0xff53d8b5:theme==2?0xff70baff:wallpaperAccent;
 }
 static int background(int theme){
  return theme==1?0xff091c22:theme==2?0xff09182c:0xff09101c;
 }
 static int panel(int theme){
  return theme==1?0xff14343b:theme==2?0xff172f4b:0xe8182538;
 }
 static int tile(int theme){
  return theme==1?0xff214850:theme==2?0xff254564:0xff1c3045;
 }
}
