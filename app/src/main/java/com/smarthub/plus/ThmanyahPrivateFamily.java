package com.smarthub.plus;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Private import of user-owned, originally licensed Thmanyah fonts.
 * The APK/GitHub never contains redistributable raw font binaries.
 * License permits use in apps but prohibits standalone redistribution,
 * modification or user extraction from publicly hosted packages.
 */
final class ThmanyahPrivateFamily {
 private ThmanyahPrivateFamily(){}
 private static final String[] FILES={
  "thmanyahsans-regular.otf","thmanyahsans-medium.otf",
  "thmanyahsans-bold.otf","thmanyahsans-black.otf",
  "thmanyahsans-light.otf",
  "thmanyahserifdisplay-bold.otf",
  "thmanyahserifdisplay-black.otf"
 };
 private static File home(Context context){return new File(context.getFilesDir(),"private-thmanyah");}
 private static boolean allowed(String name){
  if(name==null||name.contains("/")||name.contains("\\")||name.contains(".."))
   return false;
  for(String f:FILES)if(f.equals(name.toLowerCase(Locale.ROOT)))return true;
  return false;
 }
 static boolean present(Context context){
  File root=home(context);
  return new File(root,FILES[0]).isFile();
 }
 static Typeface select(Context context,boolean bold){
  if(!context.getSharedPreferences("smarthub",0).getBoolean("user_font_enabled",false))
   return null;
  File folder=home(context);
  File file=new File(folder,bold?FILES[2]:FILES[0]);
  if(!file.isFile())file=new File(folder,FILES[0]);
  if(!file.isFile())return null;
  try{return Typeface.createFromFile(file);}catch(RuntimeException invalid){return null;}
 }
 static int importZip(Context context,Uri uri)throws Exception{
  File target=home(context);
  File temp=new File(context.getCacheDir(),"thmanyah-"+System.nanoTime());
  if(!temp.mkdirs())throw new IOException("FONT_STAGING_FAILED");
  int count=0;long total=0;
  try{
   InputStream raw=context.getContentResolver().openInputStream(uri);
   if(raw==null)throw new IOException("FONT_ZIP_UNREADABLE");
   try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
    ZipEntry entry;byte[] buffer=new byte[16384];
    while((entry=zip.getNextEntry())!=null){
     String name=entry.getName();
     // Only root-level official names; don't permit path traversal or
     // ZIP bombs, unexpected files or renamed/truncated fonts.
     if(!allowed(name))continue;
     String canonical=name.toLowerCase(Locale.ROOT);
     File dest=new File(temp,canonical);
     if(dest.exists())throw new IOException("DUPLICATE_FONT");
     try(FileOutputStream out=new FileOutputStream(dest)){
      long length=0;int read;
      while((read=zip.read(buffer))!=-1){
       total+=read;length+=read;
       if(total>12L*1024*1024||length>3L*1024*1024)
        throw new IOException("FONT_ARCHIVE_TOO_LARGE");
       out.write(buffer,0,read);
      }
      if(length<1000)throw new IOException("FONT_FILE_TOO_SMALL");
     }
     // Validation only; don't modify the typeface's internal metadata.
     Typeface.createFromFile(dest);
     count++;
     if(count>FILES.length)throw new IOException("TOO_MANY_FONTS");
    }
   }
   if(!new File(temp,FILES[0]).isFile())
    throw new IOException("THMANYAH_REGULAR_MISSING");
   if(!target.exists()&&!target.mkdirs())throw new IOException("FONT_PRIVATE_DIR");
   for(String filename:FILES){
    File from=new File(temp,filename);
    if(!from.isFile())continue;
    File to=new File(target,filename);
    try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){
     byte[] b=new byte[16384];int read;
     while((read=in.read(b))!=-1)out.write(b,0,read);
    }
   }
   context.getSharedPreferences("smarthub",0).edit()
    .putBoolean("user_font_enabled",true)
    .putString("user_font_family","Thmanyah")
    .apply();
   return count;
  }finally{
   File[] files=temp.listFiles();if(files!=null)for(File f:files)f.delete();
   temp.delete();
  }
 }
}
