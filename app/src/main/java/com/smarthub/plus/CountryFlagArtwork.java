package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.InputStream;
import java.util.Locale;

/** Small, real ISO-country flag PNGs bundled offline at build time; never emoji or text. */
final class CountryFlagArtwork {
 private CountryFlagArtwork() {}
 private static final LruCache<String,Bitmap> FLAGS=new LruCache<>(48);
 static Bitmap get(Context context,String country){
  if(country==null||!country.matches("[A-Z]{2}"))return null;
  synchronized(FLAGS){
   Bitmap cached=FLAGS.get(country);
   if(cached!=null&&!cached.isRecycled())return cached;
  }
  String path="provider-branding/country-flags/"+country.toLowerCase(Locale.ROOT)+".png";
  try(InputStream stream=context.getAssets().open(path)){
   BitmapFactory.Options settings=new BitmapFactory.Options();
   settings.inScaled=false;
   settings.inPreferredConfig=Bitmap.Config.ARGB_8888;
   Bitmap image=BitmapFactory.decodeStream(stream,null,settings);
   if(image==null||image.getWidth()<24||image.getWidth()>250||
      image.getHeight()<12||image.getHeight()>160)return null;
   synchronized(FLAGS){FLAGS.put(country,image);}
   return image;
  }catch(Exception absent){
   // An unresolved service is intentionally shown without a guessed flag.
   return null;
  }
 }
}
