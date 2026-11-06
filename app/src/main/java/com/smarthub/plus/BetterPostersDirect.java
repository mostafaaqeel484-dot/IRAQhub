package com.smarthub.plus;

import android.content.Context;
import android.graphics.BitmapFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Optional BetterPosters direct IMDb endpoint. No name-only URL construction.
 * Validates HTTP 200, MIME, bounded JPEG and image dimensions before use.
 */
final class BetterPostersDirect {
 private BetterPostersDirect(){}
 static String poster(Context context,String verifiedImdb)throws Exception{
  if(verifiedImdb==null||!verifiedImdb.matches("tt[0-9]{7,10}"))
   return "";
  String address="https://btttr.cc/poster/imdb/poster-default/"+
    verifiedImdb+".jpg";
  File folder=new File(context.getCacheDir(),"betterposters-verified");
  if(!folder.isDirectory()&&!folder.mkdirs())return "";
  File cached=new File(folder,verifiedImdb+"-default.jpg");
  if(cached.isFile()&&System.currentTimeMillis()-cached.lastModified()<7L*86400000
     &&valid(read(cached,5*1024*1024))){
   ArtworkCache.saveVerified(context,new URL(address),read(cached,5*1024*1024));
   return address;
  }
  HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
  connection.setInstanceFollowRedirects(false);
  connection.setConnectTimeout(6000);
  connection.setReadTimeout(9000);
  connection.setRequestProperty("Accept","image/jpeg");
  try{
   if(connection.getResponseCode()!=200)throw new IOException("BTTTR_HTTP_NOT_OK");
   String content=connection.getContentType();
   if(content==null||!content.toLowerCase(Locale.ROOT).startsWith("image/jpeg"))
      throw new IOException("BTTTR_INVALID_MIME");
   byte[] bytes=bounded(connection.getInputStream(),5*1024*1024);
   if(!valid(bytes))throw new IOException("BTTTR_INVALID_IMAGE");
   File pending=new File(folder,verifiedImdb+"-ar.tmp");
   try(FileOutputStream out=new FileOutputStream(pending)){out.write(bytes);out.getFD().sync();}
   if(!pending.renameTo(cached)){
    pending.delete();throw new IOException("BTTTR_CACHE_RENAME_FAILED");
   }
   ArtworkCache.saveVerified(context,new URL(address),bytes);
   return address;
  }catch(Exception failure){
   if(cached.isFile()&&valid(read(cached,5*1024*1024))){
    ArtworkCache.saveVerified(context,new URL(address),read(cached,5*1024*1024));
    return address;
   }
   return "";
  }finally{connection.disconnect();}
 }
 private static byte[] read(File file,int max)throws Exception{
  try(InputStream in=new FileInputStream(file)){return bounded(in,max);}
 }
 private static byte[] bounded(InputStream in,int max)throws Exception{
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int n;
   while((n=in.read(b))!=-1){
    if(out.size()+n>max)throw new IOException("BTTTR_IMAGE_TOO_LARGE");
    out.write(b,0,n);
   }
   return out.toByteArray();
  }
 }
 private static boolean valid(byte[] bytes){
  if(bytes==null||bytes.length<256||bytes[0]!=(byte)0xff||bytes[1]!=(byte)0xd8)
   return false;
  BitmapFactory.Options dimensions=new BitmapFactory.Options();
  dimensions.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,dimensions);
  return dimensions.outWidth>=90&&dimensions.outHeight>=90&&
    dimensions.outWidth<=10000&&dimensions.outHeight<=10000;
 }
}
