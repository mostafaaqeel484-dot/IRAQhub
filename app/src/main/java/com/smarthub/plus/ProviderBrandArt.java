package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;
import java.util.*;

/** Provider identity lives outside CHMAX channel art. Country is never guessed. */
final class ProviderBrandArt {
 private static volatile ProviderBrandArt instance;
 private final ProviderCountryCatalog data;
 private final ProviderLogoAssetIndex assets;
 private static final Map<Bitmap,Bitmap> FILTERED =
   Collections.synchronizedMap(new WeakHashMap<Bitmap,Bitmap>());
 private ProviderBrandArt(Context c){
  data=ProviderCountryCatalog.get(c);assets=ProviderLogoAssetIndex.get(c);
 }
 static ProviderBrandArt get(Context c){
  ProviderBrandArt result=instance;
  if(result==null)synchronized(ProviderBrandArt.class){
   result=instance;if(result==null)instance=result=new ProviderBrandArt(c.getApplicationContext());
  }
  return result;
 }
 String country(String channel,String identity,String receiverProvider){
  return data.country(channel,identity,receiverProvider);
 }
 String provider(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider p=data.owner(receiverProvider,channel);
  if(p!=null)return p.name;
  String name=ProviderCountryCatalog.key(channel);
  if(name.startsWith("OSN ")||name.equals("OSN"))return "OSNtv";
  if(name.startsWith("ARENA SPORT"))return "Arena Sport";
  if(name.startsWith("TRING SPORT"))return "Tring Digital";
  if(isAlbanianSuperSport(name))return "DigitAlb";
  return "";
 }
 /** An exact corporate mark first, then safe family-name aliases.
  * TVP IDs from the user workbook are NOT broadcast provider IDs. */
 Bitmap badge(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider p=data.owner(receiverProvider,channel);
  Bitmap real=p==null?null:assets.provider(p.name,p.country);
  String normalized=ProviderCountryCatalog.key(channel);
  String label=ProviderCountryCatalog.key(receiverProvider);
  boolean osn=normalized.equals("OSN")||normalized.startsWith("OSN ")||
    label.startsWith("OSN")||normalized.startsWith("OSNTV");
  if(osn)real=assets.provider("OSNtv","AE");
  else if(normalized.startsWith("ARENA SPORT")||label.startsWith("ARENA SPORT"))
    real=assets.provider("Arena Channels Group","RS");
  else if(normalized.startsWith("TRING SPORT")||label.startsWith("TRING "))
    real=assets.provider("Tring Digital","AL");
  else if(isAlbanianSuperSport(normalized)||label.contains("DIGITALB"))
    real=assets.provider("DigitAlb","AL");
  else if(label.equals("D SMART")||label.equals("DSMART"))
    real=assets.provider("D-Smart","TR");
  if(osn)return allWhite(outlineDark(real));
  return outlineDark(real);
 }
 private static boolean isAlbanianSuperSport(String n){
  return n.matches("(?:SS|SUPERSPORT)(?: ALBANIA)?[ -]?(?:[1-7])(?: HD)?")&&!n.matches(".*5(?: HD)?");
 }
 /** Legacy call site kept; never supplies a bundled channel logo. */
 Bitmap channelLogo(String channel,String physical,String receiverProvider){return null;}

 private static Bitmap allWhite(Bitmap src){
  if(src==null)return null;
  final int w=src.getWidth(),h=src.getHeight();
  int[] pixels=new int[w*h];src.getPixels(pixels,0,w,0,0,w,h);
  for(int i=0;i<pixels.length;i++){pixels[i]=(pixels[i]&0xff000000)|0x00ffffff;}
  return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
 }
 /** Convert dark monochrome text/marks into white while preserving bright brand
  * hues (Sky gradient, DigitAlb red, D-Smart orange) and alpha.
  * Completely black/white border-connected mattes are removed. */
 static Bitmap outlineDark(Bitmap raw){
  if(raw==null||raw.isRecycled()||raw.getConfig()==Bitmap.Config.HARDWARE||
      raw.getWidth()<3||raw.getHeight()<3||raw.getWidth()>1200||raw.getHeight()>1200)return raw;
  Bitmap cached=FILTERED.get(raw);if(cached!=null&&!cached.isRecycled())return cached;
  final int w=raw.getWidth(),h=raw.getHeight(),n=w*h;
  int[] p=new int[n];raw.getPixels(p,0,w,0,0,w,h);
  int[] corners={p[0],p[w-1],p[(h-1)*w],p[n-1]};
  int matteType=0,black=0,white=0;
  for(int c:corners){if(dark(c))black++;else if(pale(c))white++;}
  if(black>=3)matteType=1;else if(white>=3)matteType=2;
  boolean changed=false;
  if(matteType!=0){
   // Only transparentize a large connected matte, not a logo's isolated interior.
   boolean[] mark=new boolean[n];int cnt=0;
   for(int i=0;i<n;i++)if(matteType==1?dark(p[i]):pale(p[i])){mark[i]=true;cnt++;}
   if(cnt>n/3){
    boolean[] visited=new boolean[n];int[] queue=new int[n];int head=0,tail=0;
    for(int x=0;x<w;x++){
     int a=x,b=(h-1)*w+x;
     if(mark[a]&&!visited[a]){visited[a]=true;queue[tail++]=a;}
     if(mark[b]&&!visited[b]){visited[b]=true;queue[tail++]=b;}
    }
    for(int y=0;y<h;y++){
     int a=y*w,b=a+w-1;
     if(mark[a]&&!visited[a]){visited[a]=true;queue[tail++]=a;}
     if(mark[b]&&!visited[b]){visited[b]=true;queue[tail++]=b;}
    }
    while(head<tail){
     int i=queue[head++],x=i%w,y=i/w;
     int[] near={x>0?i-1:-1,x+1<w?i+1:-1,y>0?i-w:-1,y+1<h?i+w:-1};
     for(int j:near)if(j>=0&&mark[j]&&!visited[j]){
      visited[j]=true;queue[tail++]=j;
     }
    }
    if(tail>n/4)for(int i=0;i<n;i++)if(visited[i]){p[i]=0;changed=true;}
   }
  }
  for(int i=0;i<n;i++){
   int argb=p[i],a=argb>>>24;
   if(a<12)continue;
   int r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;
   int lo=Math.min(r,Math.min(g,b)),hi=Math.max(r,Math.max(g,b));
   if(hi<120&&hi-lo<28){
    p[i]=(a<<24)|0x00ffffff;changed=true;
   }
  }
  Bitmap output=changed?Bitmap.createBitmap(p,w,h,Bitmap.Config.ARGB_8888):raw;
  FILTERED.put(raw,output);
  return output;
 }
 private static boolean dark(int p){
  int a=p>>>24,r=p>>>16&255,g=p>>>8&255,b=p&255;
  return a>235&&Math.max(r,Math.max(g,b))<36;
 }
 private static boolean pale(int p){
  int a=p>>>24,r=p>>>16&255,g=p>>>8&255,b=p&255;
  return a>235&&Math.min(r,Math.min(g,b))>237;
 }
}
