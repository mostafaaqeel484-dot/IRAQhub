package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.util.Locale;

/**
 * Visual identity bridge: official receiver/factory artwork always wins.
 * If a 2026 register entry has no verified logo asset, render an explicitly
 * typographic provider label, not a fabricated or mismatched trademark.
 * Original black/colored pixels are never recolored. A thin white OUTSIDE
 * stroke is added only when a real logo is hard to see on RX6's black skin.
 */
final class ProviderBrandArt {
 private static volatile ProviderBrandArt instance;
 private final ProviderCountryCatalog data;
 private final LruCache<String,Bitmap> badges=new LruCache<>(80);
 private ProviderBrandArt(Context c){data=ProviderCountryCatalog.get(c);}
 static ProviderBrandArt get(Context c){
  ProviderBrandArt art=instance;
  if(art==null)synchronized(ProviderBrandArt.class){
   art=instance;if(art==null)instance=art=new ProviderBrandArt(c.getApplicationContext());
  }
  return art;
 }
 String country(String channel,String identity,String receiverProvider){
  return data.country(channel,identity,receiverProvider);
 }
 String provider(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider p=data.owner(receiverProvider,channel);
  return p==null?"":p.name;
 }
 Bitmap badge(String receiverProvider,String channel){
  ProviderCountryCatalog.Provider row=data.owner(receiverProvider,channel);
  if(row==null)return null;
  String key=row.name+"|"+row.country;
  Bitmap cached=badges.get(key);if(cached!=null&&!cached.isRecycled())return cached;
  int width=300,height=126;
  Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(bitmap);
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  p.setColor(0xff111823);p.setStyle(Paint.Style.FILL);
  canvas.drawRoundRect(4,5,width-4,height-5,14,14,p);
  p.setColor(0xffa8b5c8);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);
  canvas.drawRoundRect(4,5,width-4,height-5,14,14,p);
  p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  String text=row.name.replace(" / "," · ");
  if(text.length()>47)text=text.substring(0,44)+"…";
  float size=text.length()>35?18:text.length()>24?22:Math.min(35,37-text.length()/3f);
  p.setTextSize(size);p.setColor(Color.WHITE);
  p.setTextAlign(Paint.Align.CENTER);
  String[] parts=lines(text,text.length()>31?27:24);
  if(parts.length==1){
   canvas.drawText(parts[0],width/2f,height/2f+size*.33f,p);
  }else{
   canvas.drawText(parts[0],width/2f,height/2f-size*.12f,p);
   canvas.drawText(parts[1],width/2f,height/2f+size*1.05f,p);
  }
  badges.put(key,bitmap);return bitmap;
 }
 private static String[] lines(String title,int limit){
  if(title.length()<=limit)return new String[]{title};
  int split=title.lastIndexOf(' ',limit);
  if(split<10)split=limit;
  return new String[]{title.substring(0,split).trim(),title.substring(split).trim()};
 }
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
