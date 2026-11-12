package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

/** Manually imported per-service art. Never writes CHMAX cache or receiver channel database. */
final class ChannelArtworkOverrides {
 private static final int MAX_BYTES=4*1024*1024;
 private static volatile String cachedKey="";
 private static volatile Bitmap cachedImage;
 private ChannelArtworkOverrides(){}
 private static File file(Context c,String physical){
  if(!ChannelLogoAppearance.physical(physical))return null;
  return new File(new File(c.getFilesDir(),"rx6-channel-logo-overrides"),
    ChannelLogoAppearance.key(physical).substring(ChannelLogoAppearance.PREFIX.length())+".png");
 }
 static Bitmap peek(String physical){
  Bitmap b=cachedImage;
  return physical!=null&&physical.equals(cachedKey)&&b!=null&&!b.isRecycled()?b:null;
 }
 static synchronized Bitmap load(Context c,String physical){
  Bitmap memory=peek(physical);if(memory!=null)return memory;
  File file=file(c,physical);
  if(file==null||!file.isFile()||file.length()<80||file.length()>MAX_BYTES)return null;
  BitmapFactory.Options bounds=new BitmapFactory.Options();
  bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
  if(bounds.outWidth<16||bounds.outHeight<16||bounds.outWidth>2048||bounds.outHeight>2048)return null;
  BitmapFactory.Options options=new BitmapFactory.Options();
  options.inSampleSize=Math.max(1,(int)Math.ceil(Math.max(bounds.outWidth,bounds.outHeight)/512.0));
  Bitmap image=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
  cachedImage=image;cachedKey=physical;
  return image;
 }
 static synchronized void add(Context c,String physical,Uri image)throws Exception {
  File dst=file(c,physical);
  if(dst==null)throw new IOException("SELECT_EXACT_DVB_CHANNEL");
  byte[] bytes;
  try(InputStream input=c.getContentResolver().openInputStream(image);
      ByteArrayOutputStream output=new ByteArrayOutputStream()){
   if(input==null)throw new IOException("IMAGE_UNREADABLE");
   byte[] buf=new byte[8192];int count;
   while((count=input.read(buf))!=-1){
    if(output.size()+count>MAX_BYTES)throw new IOException("IMAGE_TOO_LARGE");
    output.write(buf,0,count);
   }
   bytes=output.toByteArray();
  }
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
  if(bounds.outWidth<32||bounds.outHeight<24||bounds.outWidth>4096||bounds.outHeight>4096)
   throw new IOException("INVALID_CHANNEL_IMAGE");
  BitmapFactory.Options scaled=new BitmapFactory.Options();
  scaled.inSampleSize=Math.max(1,(int)Math.ceil(Math.max(bounds.outWidth,bounds.outHeight)/512.0));
  Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,scaled);
  if(bitmap==null)throw new IOException("IMAGE_DECODE_FAILED");
  File directory=dst.getParentFile();
  if(directory==null||!directory.isDirectory()&&!directory.mkdirs())throw new IOException("PRIVATE_STORAGE_UNAVAILABLE");
  AtomicFile atomic=new AtomicFile(dst);FileOutputStream out=atomic.startWrite();
  try{
   if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG_ENCODING_FAILED");
   atomic.finishWrite(out);
  }catch(Exception failed){atomic.failWrite(out);bitmap.recycle();throw failed;}
  cachedKey=physical;cachedImage=bitmap;
  changed(c);
 }
 static synchronized void remove(Context c,String physical){
  File f=file(c,physical);
  if(f!=null&&f.isFile())f.delete();
  if(physical!=null&&physical.equals(cachedKey)){
   cachedKey="";cachedImage=null;
  }
  changed(c);
 }
 private static void changed(Context c){
  long now=System.currentTimeMillis();
  c.getSharedPreferences("smarthub",0).edit().putLong("logo_mapping_changed_at",now)
   .putLong("osd_changed_at",now).apply();
 }
}
