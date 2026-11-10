package com.smarthub.plus;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Loads EXACT visual assets from the original RX Launcher APK supplied by the
 * user through Android's document picker. Never loads RX Java code, services,
 * providers, binaries, native libraries or manifests into IRAQhub.
 *
 * Layout is reimplemented natively; imagery comes from the actual RX package.
 */
final class RxOriginalAssets {
 private static final Map<String,String> ALLOWED=new LinkedHashMap<>();
 static {
  ALLOWED.put("cairo","assets/fonts/Cairo.ttf");
  ALLOWED.put("wall_dark","assets/walls/horizontal-dark-v230.png");
  ALLOWED.put("wall_light","assets/walls/horizontal-light-v230.png");
  ALLOWED.put("live_dark","assets/cards/live-horizontal-dark-v230.png");
  ALLOWED.put("live_light","assets/cards/live-horizontal-light-v230.png");
  ALLOWED.put("live_neutral","assets/cards/live-tv-neutral.png");
  ALLOWED.put("madrid","assets/walls/madrid-hero-v29.png");
  ALLOWED.put("egypt","assets/walls/egypt-hero-v29.png");
  ALLOWED.put("ahly","assets/walls/ahly-hero-v29.png");
  ALLOWED.put("mountains","assets/walls/mountains.jpg");
  ALLOWED.put("waterfall","assets/walls/waterfall.jpg");
  ALLOWED.put("pyramids","assets/walls/pyramids.jpg");
  ALLOWED.put("sunset","assets/walls/red-sunset.jpg");
  ALLOWED.put("space","assets/walls/black-hole.jpg");
 }
 private static volatile android.graphics.Typeface cairo;
 static android.graphics.Typeface font(Context c){
  android.graphics.Typeface found=cairo;
  if(found!=null)return found;
  File file=new File(directory(c),"cairo.ttf");
  if(!file.isFile()||file.length()<1000||file.length()>3*1024*1024L)return null;
  try{
   synchronized(RxOriginalAssets.class){
    if(cairo==null)cairo=android.graphics.Typeface.createFromFile(file);
    return cairo;
   }
  }catch(Throwable unavailable){return null;}
 }
 private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(5*1024*1024){
  @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
 };
 private static File directory(Context c){
  File d=new File(c.getFilesDir(),"rx-original-art");
  if(!d.isDirectory())d.mkdirs();
  return d;
 }
 static boolean imported(Context c){
  return new File(directory(c),"wall_dark.png").isFile()&&
    new File(directory(c),"live_dark.png").isFile();
 }
 static int importFromApk(Context c,Uri apk)throws IOException{
  if(apk==null)throw new IOException("RX_APK_MISSING");
  Map<String,String> names=new HashMap<>();
  for(Map.Entry<String,String> e:ALLOWED.entrySet())names.put(e.getValue(),e.getKey());
  File dir=directory(c);
  long cumulative=0;int seen=0;
  List<File> staged=new ArrayList<>();
  boolean validDex=false;
  try(InputStream source=c.getContentResolver().openInputStream(apk)){
   if(source==null)throw new IOException("RX_DOCUMENT_UNAVAILABLE");
   try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){
    ZipEntry e;
    while((e=zip.getNextEntry())!=null){
     String key=names.get(e.getName());
     if("classes.dex".equals(e.getName()))validDex=true;
     if(key==null||e.isDirectory()){zip.closeEntry();continue;}
     String extension=e.getName().endsWith(".ttf")?"ttf":
       e.getName().endsWith(".jpg")?"jpg":"png";
     File temp=new File(dir,key+"."+extension+".tmp");
     long length=0;
     try(FileOutputStream out=new FileOutputStream(temp)){
      byte[] buffer=new byte[16384];int n;
      while((n=zip.read(buffer))!=-1){
       length+=n;cumulative+=n;
       if(length>6*1024*1024L||cumulative>30*1024*1024L)
        throw new IOException("RX_ASSET_SIZE_LIMIT");
       out.write(buffer,0,n);
      }
     }
     if(length<100){temp.delete();zip.closeEntry();continue;}
     staged.add(temp);seen++;
     zip.closeEntry();
    }
   }
  }catch(IOException failure){
   for(File f:staged)f.delete();
   throw failure;
  }
  File live=new File(dir,"live_dark.png.tmp");
  File wall=new File(dir,"wall_dark.png.tmp");
  if(!validDex||!live.isFile()||!wall.isFile()||seen<4){
   for(File f:staged)f.delete();
   throw new IOException("NOT_RX_V239_OR_MISSING_ARTWORK");
  }
  for(File temp:staged){
   String name=temp.getName();
   File target=new File(dir,name.substring(0,name.length()-4));
   if(target.exists()&&!target.delete())throw new IOException("RX_ASSET_UPDATE_FAILED");
   if(!temp.renameTo(target))throw new IOException("RX_ASSET_COMMIT_FAILED");
  }
  synchronized(MEMORY){MEMORY.evictAll();}
  cairo=null;
  c.getSharedPreferences("smarthub",0).edit()
    .putBoolean("rx_original_art",true)
    .putLong("rx_original_art_updated_at",System.currentTimeMillis()).apply();
  return seen;
 }
 static void trim(){synchronized(MEMORY){MEMORY.evictAll();}}
 static Bitmap bitmap(Context c,String key,int targetWidth){
  if(!ALLOWED.containsKey(key))return null;
  int size=Math.max(200,Math.min(targetWidth,1600));
  String cacheKey=key+"@"+size;
  synchronized(MEMORY){
   Bitmap cached=MEMORY.get(cacheKey);if(cached!=null&&!cached.isRecycled())return cached;
  }
  File root=directory(c);
  File file=new File(root,key+".png");
  if(!file.exists())file=new File(root,key+".jpg");
  if(!file.isFile()||file.length()>6L*1024*1024)return null;
  try{
   BitmapFactory.Options bounds=new BitmapFactory.Options();
   bounds.inJustDecodeBounds=true;
   BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
   if(bounds.outWidth<=0||bounds.outHeight<=0||
      bounds.outWidth>8192||bounds.outHeight>8192)return null;
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inPreferredConfig=Bitmap.Config.RGB_565;
   options.inSampleSize=1;
   while(bounds.outWidth/options.inSampleSize>size*1.6f)options.inSampleSize*=2;
   Bitmap result=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
   if(result!=null)synchronized(MEMORY){MEMORY.put(cacheKey,result);}
   return result;
  }catch(Throwable decoding){return null;}
 }
 static Drawable drawable(Context c,String key,int size){
  Bitmap b=bitmap(c,key,size);
  return b==null?null:new BitmapDrawable(c.getResources(),b);
 }
 static Drawable wallpaper(Context c){
  int theme=RxSkin.selected(c);
  String asset=theme==1?"wall_light":theme==0?"wall_dark":
   theme==5?"madrid":theme==6?"ahly":theme==7?"egypt":
   theme==8?"mountains":theme==9?"space":theme==10?"sunset":null;
  Drawable d=asset==null?null:drawable(c,asset,1440);
  return d!=null?d:RxSkin.wallpaper(c);
 }
 static void card(Context c,android.widget.ImageView view,String id){
  Drawable bitmap=drawable(c,id,1280);
  if(bitmap!=null)view.setImageDrawable(bitmap);
  else if("live_light".equals(id))view.setImageResource(R.drawable.rx_live_horizontal_light);
  else view.setImageResource(R.drawable.rx_live_horizontal_dark);
 }
}
