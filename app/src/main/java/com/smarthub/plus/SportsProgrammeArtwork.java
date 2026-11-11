package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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

 static Bitmap card(Context context,SportsMatch match,boolean cacheOnly){
  if(!verified(match))return null;
  Bitmap home=badge(context,match.homeLogo,cacheOnly);
  Bitmap away=badge(context,match.awayLogo,cacheOnly);
  // Verified event: show a neutral team-name card even when badges are absent.
  Bitmap league=badge(context,match.leagueLogo,true);
  try{
   Bitmap result=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);
   Canvas canvas=new Canvas(result);
   Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
   paint.setShader(new LinearGradient(0,0,960,540,
     Color.rgb(8,22,42),Color.rgb(37,17,66),Shader.TileMode.CLAMP));
   canvas.drawRoundRect(new RectF(0,0,960,540),34,34,paint);
   paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);
   paint.setColor(Color.rgb(83,196,255));
   canvas.drawRoundRect(new RectF(12,12,948,528),28,28,paint);
   paint.setStyle(Paint.Style.FILL);
   paint.setColor(Color.argb(150,20,35,58));
   canvas.drawRoundRect(new RectF(30,92,930,430),26,26,paint);
   paint.setColor(Color.WHITE);
   paint.setTextAlign(Paint.Align.CENTER);
   paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
   paint.setTextSize(30);
   canvas.drawText(shorten(match.league,48),480,58,paint);
   if(league!=null)drawBadge(canvas,paint,league,443,72,74);
   if(home!=null)drawBadge(canvas,paint,home,125,120,190);
   else drawFallbackMark(canvas,paint,match.home,125,120,190);
   if(away!=null)drawBadge(canvas,paint,away,645,120,190);
   else drawFallbackMark(canvas,paint,match.away,645,120,190);
   paint.setTextSize(30);paint.setColor(Color.rgb(246,248,251));
   canvas.drawText(shorten(match.home,24),220,365,paint);
   canvas.drawText(shorten(match.away,24),740,365,paint);
   paint.setColor(Color.rgb(255,191,61));
   canvas.drawRoundRect(new RectF(407,182,553,292),28,28,paint);
   paint.setTextSize(48);paint.setColor(Color.rgb(12,26,44));
   canvas.drawText("VS",480,252,paint);
   paint.setTextSize(27);paint.setColor(Color.rgb(223,230,240));
   canvas.drawText(shorten(match.time(),52),480,478,paint);
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
