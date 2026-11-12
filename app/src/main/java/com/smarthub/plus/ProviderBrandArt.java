package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;
import java.util.*;

/** Provider identity lives outside CHMAX channel art. Country is never guessed. */
final class ProviderBrandArt {
 private static volatile ProviderBrandArt instance;
 private final ProviderCountryCatalog data;
 private final ProviderLogoAssetIndex assets;
 private static final android.util.LruCache<String,Bitmap> PLACEHOLDERS=new android.util.LruCache<>(48);
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
  ProviderFamilyRules.Brand special=ProviderFamilyRules.select(receiverProvider,channel,identity);
  // Only use a platform flag when its market is proven by the explicit rule;
  // Arena/Sky regional flags still use existing exact service/country matching.
  if(special!=null&&special.knownMarket)return special.country;
  return data.country(channel,identity,receiverProvider);
 }
 String provider(String receiverProvider,String channel){
  return provider(receiverProvider,channel,"");
 }
 String provider(String receiverProvider,String channel,String physical){
  ProviderFamilyRules.Brand family=ProviderFamilyRules.select(receiverProvider,channel,physical);
  if(family!=null)return family.display;
  ProviderCountryCatalog.Provider owner=data.owner(receiverProvider,channel);
  return owner==null?"":owner.name;
 }
 /** Provider artwork is independent from CHMAX's CHANNEL artwork.
  * Physical orbital identity prevents Canal+ France/Polska and Airtel mixups. */
 Bitmap badge(String receiverProvider,String channel){
  return badge(receiverProvider,channel,"");
 }
 Bitmap badge(String receiverProvider,String channel,String physical){
  ProviderFamilyRules.Brand family=ProviderFamilyRules.select(receiverProvider,channel,physical);
  if(family!=null){
   Bitmap image=assets.provider(family.assetOwner,family.country);
   if("OSN".equals(family.display)&&image!=null)
    return allWhite(outlineDark(image));
   if(image!=null)return outlineDark(image);
   return null; // Do not fabricate a provider logo without verified artwork.
  }
  ProviderCountryCatalog.Provider p=data.owner(receiverProvider,channel);
  if(p==null)return null;
  Bitmap logo=assets.provider(p.name,p.country);
  return logo==null?null:outlineDark(logo);
 }
 boolean verified(String provider,String country){return assets.provider(provider,country)!=null;}
 private static Bitmap colored(String label,String country){
  String key=label+"|"+country;
  Bitmap cached=PLACEHOLDERS.get(key);if(cached!=null&&!cached.isRecycled())return cached;
  int w=300,h=104;Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(image);
  float hue=(label.hashCode()&0x7fffffff)%360;
  int a=Color.HSVToColor(new float[]{hue,.78f,.68f});
  int b=Color.HSVToColor(new float[]{(hue+32)%360,.58f,.84f});
  Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  paint.setShader(new LinearGradient(0,0,w,h,a,b,Shader.TileMode.CLAMP));
  canvas.drawRoundRect(0,0,w,h,16,16,paint);
  paint.setShader(null);paint.setColor(Color.WHITE);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  float size=label.length()>22?19:label.length()>15?23:29;paint.setTextSize(size);
  while(paint.measureText(label)>w-24&&size>12){size-=1;paint.setTextSize(size);}
  canvas.drawText(label,w/2f,53,paint);
  paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
  paint.setTextSize(11);paint.setColor(0xfff1f6ff);
  canvas.drawText("PROVIDER · "+country+" · UNVERIFIED ART",w/2f,79,paint);
  PLACEHOLDERS.put(key,image);
  return image;
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
