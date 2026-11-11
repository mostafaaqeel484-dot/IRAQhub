package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;

/** Authentic provider mark or nothing: a fabricated text badge is NOT a logo.
 * Real OSN/Arena/Airtel and verified channel marks stay independent from EPG.
 */
final class ProviderBrandArt {
 private static volatile ProviderBrandArt instance;
 private final ProviderCountryCatalog data;
 private final ProviderLogoAssetIndex assets;
 private ProviderBrandArt(Context c){
  data=ProviderCountryCatalog.get(c);assets=ProviderLogoAssetIndex.get(c);
 }
 static ProviderBrandArt get(Context c){
  ProviderBrandArt index=instance;
  if(index==null)synchronized(ProviderBrandArt.class){
   index=instance;if(index==null)instance=index=new ProviderBrandArt(c.getApplicationContext());
  }
  return index;
 }
 String country(String channel,String identity,String receiverProvider){
  return data.country(channel,identity,receiverProvider);
 }
 String provider(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider p=data.owner(receiverProvider,channel);
  return p==null?"":p.name;
 }
 /** Legacy method name retained for existing list/InfoBar call sites.
  * Returns a genuine bundled image; if there is no verified brand logo,
  * leave that logo slot empty instead of painting the provider name. */
 Bitmap badge(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider row=data.owner(receiverProvider,channel);
  return row==null?null:outlineDark(assets.provider(row.name,row.country));
 }
 Bitmap channelLogo(String channel,String physical,String receiverProvider){
  String country=data.country(channel,physical,receiverProvider);
  return country.isEmpty()?null:assets.channel(channel,country);
 }
 /** Thin white stroke OUTSIDE a mostly black transparent logo.
  * Never convert the trademark's black interior pixels to white.
  */
 static Bitmap outlineDark(Bitmap raw){
  if(raw==null||raw.isRecycled()||raw.getConfig()==Bitmap.Config.HARDWARE||
     raw.getWidth()<3||raw.getHeight()<3||raw.getWidth()>1200||raw.getHeight()>1200)return raw;
  int w=raw.getWidth(),h=raw.getHeight(),count=0,dim=0;
  int sx=Math.max(1,w/40),sy=Math.max(1,h/25);
  for(int y=0;y<h;y+=sy)for(int x=0;x<w;x+=sx){
   int px=raw.getPixel(x,y),alpha=px>>>24;
   if(alpha<160)continue;
   int r=(px>>16)&255,g=(px>>8)&255,b=px&255;
   count++;if((r*299+g*587+b*114)/1000<86)dim++;
  }
  if(count<8||dim*100/count<65)return raw;
  final int pad=3;
  Bitmap result=Bitmap.createBitmap(w+2*pad,h+2*pad,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(result);
  Paint white=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  white.setColorFilter(new android.graphics.PorterDuffColorFilter(
    Color.WHITE,PorterDuff.Mode.SRC_IN));
  for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++){
   if(dx*dx+dy*dy>5||dx==0&&dy==0)continue;
   c.drawBitmap(raw,pad+dx,pad+dy,white);
  }
  c.drawBitmap(raw,pad,pad,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
  return result;
 }
}
