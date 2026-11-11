package com.smarthub.plus;
import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.util.*;

/** Private-key poster downloads never leave a key-bearing URL in media metadata.
 * Instead, return a key-free privateposter:<provider>:<imdb> identity. */
final class PrivatePosterCache {
 private static final long FRESH_MS=30L*86400000L;
 private static final int MAX=5*1024*1024;
 private PrivatePosterCache(){}
 private static File file(Context c,String source,String imdb){
  File dir=new File(c.getFilesDir(),"private-poster-images");dir.mkdirs();
  return new File(dir,source+"-"+imdb+".jpg");
 }
 static boolean token(String address){
  return address!=null&&address.matches("privateposter:(?:rpdb|opdb|omdb):tt[0-9]{7,10}");
 }
 static Bitmap read(Context c,String address){
  if(!token(address))return null;
  String[] components=address.split(":");
  File f=file(c,components[1],components[2]);
  if(!f.isFile()||f.length()<100||f.length()>MAX)return null;
  try{
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
   BitmapFactory.decodeFile(f.getAbsolutePath(),o);
   if(o.outWidth<120||o.outHeight<120||o.outHeight>8000||o.outWidth>8000)return null;
   BitmapFactory.Options scale=new BitmapFactory.Options();scale.inSampleSize=1;
   while(o.outHeight/scale.inSampleSize>1280||o.outWidth/scale.inSampleSize>1280)scale.inSampleSize*=2;
   return BitmapFactory.decodeFile(f.getAbsolutePath(),scale);
  }catch(Exception invalid){return null;}
 }
 static String fetch(Context c,String source,String imdb,String key){
  if(!PosterApiSecrets.supported(source)||"sportsdb".equals(source)||
     !PosterApiSecrets.valid(key)||imdb==null||!imdb.matches("tt[0-9]{7,10}"))return "";
  String identity="privateposter:"+source+":"+imdb;
  File f=file(c,source,imdb);
  if(f.isFile()&&System.currentTimeMillis()-f.lastModified()<FRESH_MS&&
     read(c,identity)!=null)return identity;
  String target;
  try{
   String escaped=URLEncoder.encode(key,"UTF-8");
   if("opdb".equals(source))target="https://openposterdb.com/"+escaped+
     "/imdb/poster-default/"+imdb+".jpg";
   else if("rpdb".equals(source))target="https://api.ratingposterdb.com/"+escaped+
     "/imdb/poster-default/"+imdb+".jpg?fallback=true";
   else if("omdb".equals(source))target="https://img.omdbapi.com/?apikey="+escaped+
     "&i="+imdb+"&h=600";
   else return "";
   HttpsURLConnection connection=(HttpsURLConnection)new URL(target).openConnection();
   connection.setInstanceFollowRedirects(false);
   connection.setConnectTimeout(5000);connection.setReadTimeout(10000);
   connection.setRequestProperty("Accept","image/jpeg,image/png,image/webp");
   try{
    if(connection.getResponseCode()!=200||connection.getContentLengthLong()>MAX)return "";
    try(InputStream in=connection.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
     byte[] buffer=new byte[16384];int n;
     while((n=in.read(buffer))!=-1){
      if(bytes.size()+n>MAX)return "";
      bytes.write(buffer,0,n);
     }
     byte[] raw=bytes.toByteArray();
     if(!VerifiedPosterFallback.valid(raw))return "";
     AtomicFile safe=new AtomicFile(f);
     FileOutputStream out=safe.startWrite();
     try{out.write(raw);safe.finishWrite(out);}
     catch(Exception io){safe.failWrite(out);return "";}
     return identity;
    }
   }finally{connection.disconnect();}
  }catch(Exception ignored){
   // URLs can contain private API keys; never log the exception or URL.
   return read(c,identity)==null?"":identity;
  }
 }
}
