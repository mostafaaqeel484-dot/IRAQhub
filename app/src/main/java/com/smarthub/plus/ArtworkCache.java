package com.smarthub.plus;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import android.graphics.BitmapFactory;
/** Short-lived image bytes, used only after online channel/programme identity lookup. */
final class ArtworkCache {
 /** Decode-bounds validation prevents caching HTML/JSON error pages as images.
  * Accept SVG only when it has an SVG element and no active XML constructs.
  */
 static boolean validImage(byte[] bytes){
  if(bytes==null||bytes.length<100||bytes.length>5*1024*1024)return false;
  boolean svg=false;
  if(bytes[0]=='<'||bytes[0]==' '||bytes[0]==(byte)0xef){
   String prefix=new String(bytes,0,Math.min(bytes.length,4096),StandardCharsets.UTF_8)
      .toLowerCase(Locale.ROOT);
   svg=prefix.contains("<svg")&&!prefix.contains("<!doctype")
     &&!prefix.contains("<!entity")&&!prefix.contains("<script")
     &&!prefix.contains("<foreignobject");
  }
  if(svg)return true;
  BitmapFactory.Options o=new BitmapFactory.Options();
  o.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
  return o.outWidth>=24&&o.outHeight>=24&&o.outWidth<=8192&&o.outHeight<=8192
      &&o.outMimeType!=null&&o.outMimeType.startsWith("image/");
 }
 /** Cached-only lookup for active software DVB 4:2:2; never starts a download. */
 static byte[] cached(Context c,URL url,int limit){
  if(url==null||!"https".equals(url.getProtocol()))return null;
  try{
   StringBuilder key=new StringBuilder();
   for(byte b:MessageDigest.getInstance("SHA-256").digest(url.toString().getBytes(StandardCharsets.UTF_8)))
    key.append(String.format(Locale.ROOT,"%02x",b&255));
   File f=new File(new File(c.getCacheDir(),"online-artwork"),key.toString());
   if(!f.isFile()||f.length()<100||f.length()>limit)return null;
   byte[] bytes=java.nio.file.Files.readAllBytes(f.toPath());
   return validImage(bytes)?bytes:null;
  }catch(Exception failed){return null;}
 }
 static void saveVerified(Context c,URL url,byte[] bytes)throws Exception{
  if(url==null||!"https".equals(url.getProtocol())||bytes==null
    ||bytes.length<200||bytes.length>5*1024*1024)
   throw new IOException("INVALID_VERIFIED_IMAGE");
  StringBuilder key=new StringBuilder();
  for(byte b:MessageDigest.getInstance("SHA-256").digest(url.toString().getBytes(StandardCharsets.UTF_8)))
   key.append(String.format(Locale.ROOT,"%02x",b&255));
  File dir=new File(c.getCacheDir(),"online-artwork");
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("ART_CACHE_UNAVAILABLE");
  android.util.AtomicFile safe=new android.util.AtomicFile(new File(dir,key.toString()));
  FileOutputStream out=safe.startWrite();
  try{out.write(bytes);safe.finishWrite(out);}
  catch(Exception failed){safe.failWrite(out);throw failed;}
 }
 static byte[] fetch(Context c,URL url,int limit)throws Exception{
  if(!"https".equals(url.getProtocol()))throw new IOException("Image must use HTTPS");StringBuilder key=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.toString().getBytes(StandardCharsets.UTF_8)))key.append(String.format(Locale.ROOT,"%02x",b&255));File dir=new File(c.getCacheDir(),"online-artwork");dir.mkdirs();File saved=new File(dir,key.toString());
  if(saved.isFile()&&saved.length()<=limit&&System.currentTimeMillis()-saved.lastModified()<24L*3600000){
   byte[] cached=java.nio.file.Files.readAllBytes(saved.toPath());
   if(validImage(cached))return cached;
  }
  byte[] bytes;try{DirectHttp.Reply reply=DirectHttp.request(url.toString(),Collections.emptyMap(),null,limit,null);if(reply.code!=200)throw new IOException("Artwork HTTP "+reply.code);bytes=reply.bytes;}catch(Exception error){if(saved.isFile()&&saved.length()<=limit){byte[] fallback=java.nio.file.Files.readAllBytes(saved.toPath());if(validImage(fallback)){c.getSharedPreferences("smarthub",0).edit().putString("artwork_status","Online image refresh failed; showing matching cached image").apply();return fallback;}}throw error;}if(!validImage(bytes))throw new IOException("ARTWORK_NOT_A_VALID_IMAGE");
  android.util.AtomicFile atomic=new android.util.AtomicFile(saved);
  FileOutputStream out=atomic.startWrite();
  try{out.write(bytes);atomic.finishWrite(out);}
  catch(Exception failure){atomic.failWrite(out);throw failure;}
  File[] files=dir.listFiles();if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long total=0;for(int n=0;n<files.length;n++){total+=files[n].length();if(n>=24||total>40*1024*1024)files[n].delete();}}
  return bytes;
 }
}
