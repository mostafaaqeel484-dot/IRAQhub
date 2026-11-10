package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.net.URI;
import java.net.URL;

/**
 * Verified-event fallback poster for a football match with no official poster.
 * Never substitutes a film or a similarly named team's image. Both team IDs and
 * a source event ID are required; network access is disabled in cache-only mode.
 */
final class SportsProgrammeArtwork {
 private SportsProgrammeArtwork(){}

 static boolean verified(SportsMatch match){
  if(match==null||match.id==null)return false;
  boolean sportsdb=match.id.matches("tsdb:[1-9][0-9]*");
  boolean openliga=match.id.matches("openliga:[1-9][0-9]*")&&
    match.homeId!=null&&match.homeId.matches("openliga:[1-9][0-9]*")&&
    match.awayId!=null&&match.awayId.matches("openliga:[1-9][0-9]*");
  return (sportsdb||openliga)
    &&match.leagueId!=null&&!match.leagueId.isEmpty()
    &&match.homeId!=null&&!match.homeId.isEmpty()
    &&match.awayId!=null&&!match.awayId.isEmpty()
    &&match.kickoff>0&&match.home!=null&&!match.home.isEmpty()
    &&match.away!=null&&!match.away.isEmpty();
 }

 static Bitmap epgCard(Context context,String title,boolean cacheOnly){
  if(title==null)return null;
  String[] teams=title.split("(?i)\\s+(?:vs\\.?|v\\.?|versus)\\s+",2);
  if(teams.length!=2)return null;
  String home=TheSportsDBProvider.epgTeamName(teams[0]);
  String away=TheSportsDBProvider.epgTeamName(teams[1]);
  if(home.length()<3||away.length()<3||home.length()>70||away.length()>70)return null;
  android.content.SharedPreferences prefs=context.getSharedPreferences("sports_epg_badges",0);
  SportsMatch match=new SportsMatch();
  match.home=home;match.away=away;match.league="EPG · Match not verified";
  match.homeLogo=prefs.getString("team:"+home,"");
  match.awayLogo=prefs.getString("team:"+away,"");
  match.leagueLogo="";
  if(!cacheOnly)try{
   TheSportsDBProvider provider=new TheSportsDBProvider(context);
   if(match.homeLogo.isEmpty())match.homeLogo=provider.findTeamBadge(home).optString("logo");
   if(match.awayLogo.isEmpty())match.awayLogo=provider.findTeamBadge(away).optString("logo");
   android.content.SharedPreferences.Editor edit=prefs.edit();
   if(!match.homeLogo.isEmpty())edit.putString("team:"+home,match.homeLogo);
   if(!match.awayLogo.isEmpty())edit.putString("team:"+away,match.awayLogo);
   edit.apply();
  }catch(Exception ignored){}
  return drawCard(context,match,cacheOnly,false);
 }
 static Bitmap card(Context context,SportsMatch match,boolean cacheOnly){
  if(!verified(match))return null;
  return drawCard(context,match,cacheOnly,true);
 }
 private static Bitmap drawCard(Context context,SportsMatch match,boolean cacheOnly,boolean verifiedEvent){
  Bitmap home=badge(context,match.homeLogo,cacheOnly);
  Bitmap away=badge(context,match.awayLogo,cacheOnly);
  // Verified event: show a neutral team-name card even when badges are absent.
  Bitmap league=badge(context,match.leagueLogo,true);
  try{
   Bitmap result=Bitmap.createBitmap(800,440,Bitmap.Config.RGB_565);
   Canvas canvas=new Canvas(result);
   Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
   paint.setColor(Color.rgb(16,25,42));canvas.drawColor(Color.rgb(16,25,42));
   paint.setColor(Color.rgb(31,48,69));
   canvas.drawRoundRect(new RectF(16,16,784,424),24,24,paint);
   paint.setColor(Color.WHITE);
   paint.setTextAlign(Paint.Align.CENTER);
   paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
   paint.setTextSize(25);
   canvas.drawText(shorten(match.league,42),400,55,paint);
   if(league!=null)drawBadge(canvas,paint,league,367,65,66);
   if(home!=null)drawBadge(canvas,paint,home,113,108,160);
   else drawFallbackMark(canvas,paint,match.home,113,108,160);
   if(away!=null)drawBadge(canvas,paint,away,527,108,160);
   else drawFallbackMark(canvas,paint,match.away,527,108,160);
   paint.setTextSize(25);paint.setColor(Color.rgb(242,243,245));
   canvas.drawText(shorten(match.home,22),193,313,paint);
   canvas.drawText(shorten(match.away,22),607,313,paint);
   paint.setTextSize(40);paint.setColor(Color.rgb(255,204,111));
   canvas.drawText("VS",400,220,paint);
   paint.setTextSize(23);paint.setColor(Color.rgb(218,222,230));
   canvas.drawText(shorten(match.time(),46),400,375,paint);
   return result;
  }finally{
   if(home!=null)home.recycle();
   if(away!=null)away.recycle();
   if(league!=null)league.recycle();
  }
 }
 private static void drawFallbackMark(Canvas canvas,Paint paint,String name,
     int left,int top,int side){
  // An initial only: never invent an official club crest.
  paint.setColor(Color.rgb(48,67,85));
  canvas.drawRoundRect(new RectF(left,top,left+side,top+side),20,20,paint);
  paint.setColor(Color.WHITE);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTextSize(64);
  String initial=name==null||name.isEmpty()?"?":
    name.substring(0,1).toUpperCase(java.util.Locale.ROOT);
  canvas.drawText(initial,left+side/2f,top+side/2f+23,paint);
 }
 private static String shorten(String raw,int count){
  String value=raw==null?"":raw.trim();
  return value.length()>count?value.substring(0,count-1)+"…":value;
 }
 private static void drawBadge(Canvas canvas,Paint paint,Bitmap image,int left,int top,int side){
  float ratio=Math.min(side/(float)image.getWidth(),side/(float)image.getHeight());
  int w=Math.max(1,Math.round(ratio*image.getWidth()));
  int h=Math.max(1,Math.round(ratio*image.getHeight()));
  int x=left+(side-w)/2,y=top+(side-h)/2;
  canvas.drawBitmap(image,new Rect(0,0,image.getWidth(),image.getHeight()),
   new Rect(x,y,x+w,y+h),paint);
 }
 private static Bitmap badge(Context context,String address,boolean cacheOnly){
  if(!TeamLogoManager.trusted(address)||!address.startsWith("https://"))return null;
  try{
   URI uri=new URI(address);
   if(uri.getRawUserInfo()!=null||uri.getRawFragment()!=null)return null;
   URL url=uri.toURL();
   byte[] bytes=cacheOnly?ArtworkCache.cached(context,url,4*1024*1024):
     ArtworkCache.fetch(context,url,4*1024*1024);
   if(bytes==null||bytes.length==0)return null;
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
   if(options.outWidth<=0||options.outHeight<=0||
      options.outWidth>8192||options.outHeight>8192)return null;
   options.inJustDecodeBounds=false;options.inSampleSize=1;
   while(options.outWidth/options.inSampleSize>320||
         options.outHeight/options.inSampleSize>320)options.inSampleSize*=2;
   return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  }catch(Exception unavailable){return null;}
 }
}
