package com.smarthub.plus;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import java.io.*;

/**
 * User-supplied OTF font, imported locally with SAF.
 *
 * Thmanyah permits use of its typeface in apps but forbids redistribution
 * or hosting its font files. Do NOT bundle an OTF in this GitHub repo or
 * a public APK: the owner may import their legitimately obtained copy.
 */
final class UserTypeface {
 private static volatile Typeface current;
 private static volatile long loadedVersion=-1;
 private UserTypeface(){}
 private static File stored(Context c){return new File(c.getFilesDir(),"user-interface-font.otf");}
 static Typeface get(Context context){return get(context,false);}
 static Typeface get(Context context,boolean bold){
  Typeface family=ThmanyahPrivateFamily.select(context,bold);
  if(family!=null)return family;
  if(!context.getSharedPreferences("smarthub",0).getBoolean("user_font_enabled",false))
   return null;
  File file=stored(context.getApplicationContext());
  if(!file.isFile()||file.length()<1000||file.length()>2_000_000)return null;
  long version=file.lastModified();
  if(version!=loadedVersion||current==null)synchronized(UserTypeface.class){
   if(version!=loadedVersion||current==null)try{
    current=Typeface.createFromFile(file);
    loadedVersion=version;
   }catch(RuntimeException invalid){current=null;loadedVersion=-1;}
  }
  return current;
 }
 static void importFrom(Context context,Uri input)throws Exception{
  File destination=stored(context.getApplicationContext());
  File temp=new File(context.getCacheDir(),"font-import-"+System.nanoTime()+".otf");
  try{
   long n=0;
   try(InputStream in=context.getContentResolver().openInputStream(input);
       OutputStream out=new FileOutputStream(temp)){
    if(in==null)throw new IOException("FONT_URI_UNREADABLE");
    byte[] b=new byte[16384];int k;
    while((k=in.read(b))!=-1){
     n+=k;
     if(n>2_000_000)throw new IOException("FONT_TOO_LARGE");
     out.write(b,0,k);
    }
   }
   if(n<1000)throw new IOException("FONT_TOO_SMALL");
   // Verify locally *before* overwriting the previously selected font.
   Typeface candidate=Typeface.createFromFile(temp);
   if(candidate==null)throw new IOException("INVALID_OTF");
   try(InputStream in=new FileInputStream(temp);OutputStream out=new FileOutputStream(destination)){
    byte[] b=new byte[16384];int k;
    while((k=in.read(b))!=-1)out.write(b,0,k);
   }
   synchronized(UserTypeface.class){current=candidate;loadedVersion=destination.lastModified();}
   context.getSharedPreferences("smarthub",0).edit().putBoolean("user_font_enabled",true).apply();
  }finally{temp.delete();}
 }
 static void disable(Context context){
  context.getSharedPreferences("smarthub",0).edit().putBoolean("user_font_enabled",false).apply();
 }
}
