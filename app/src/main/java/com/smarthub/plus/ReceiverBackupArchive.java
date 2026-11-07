package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Portable IRAQhub app-owned channel DB and harmless UI preferences.
 * Never exports BISS keys, provider tokens, credentials, OEM databases or
 * device identifiers. Database RESTORE uses the existing validated,
 * transactional ReceiverSqliteImport.importStream().
 */
final class ReceiverBackupArchive {
 private ReceiverBackupArchive(){}
 static final long ZIP_ENTRY_MAX=64L*1024L*1024L;
 static final String[] PREFS={
  "rx_wallpaper","rx_glass_effect","rx_wallpaper_rotate",
  "arabic","ui_font_percent","channel_list_wide_mode",
  "osd_opacity","osd_rx_detailed","osd_clock",
  "software_422_profile","software_422_preferred_engine"
 };
 private static JSONObject safePreferences(Context context)throws Exception{
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  JSONObject obj=new JSONObject();
  for(String key:PREFS){
   Object value=p.getAll().get(key);
   if(value instanceof String||value instanceof Boolean||
      value instanceof Integer||value instanceof Long)
    obj.put(key,value);
  }
  return obj;
 }
 static void exportTo(Context context,OutputStream destination)throws Exception{
  if(destination==null)throw new IOException("BACKUP_NO_DESTINATION");
  ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(destination));
  out.setLevel(3);
  out.putNextEntry(new ZipEntry("channels.db"));
  ReceiverSqliteImport.exportStream(context,out);
  out.closeEntry();
  JSONObject header=new JSONObject();
  header.put("kind","IRAQhubPortableBackup");
  header.put("schema",1);
  header.put("created_at",System.currentTimeMillis());
  header.put("version","3.7.6");
  header.put("preferences",safePreferences(context));
  out.putNextEntry(new ZipEntry("manifest.json"));
  out.write(header.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  out.closeEntry();
  out.finish();out.flush();
 }
 static final class RestoreResult {
  final int channels,preferences;
  RestoreResult(int c,int p){channels=c;preferences=p;}
 }
 static RestoreResult restoreFrom(Context context,InputStream source)throws Exception{
  if(source==null)throw new IOException("BACKUP_UNAVAILABLE");
  File temp=File.createTempFile("iraqhub-rollback-",".db",context.getCacheDir());
  boolean foundDb=false,foundManifest=false;
  JSONObject prefs=null;
  try{
   try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){
    ZipEntry entry;
    while((entry=zip.getNextEntry())!=null){
     String name=entry.getName();long count=0;
     if(entry.isDirectory()||(!"channels.db".equals(name)&&
        !"manifest.json".equals(name)))throw new IOException("BACKUP_UNKNOWN_ENTRY");
     if("channels.db".equals(name)){
      if(foundDb)throw new IOException("BACKUP_DUPLICATE_DB");
      foundDb=true;
      try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
       byte[] block=new byte[32768];int n;
       while((n=zip.read(block))!=-1){
        count+=n;
        if(count>ZIP_ENTRY_MAX)throw new IOException("BACKUP_DB_OVERSIZE");
        out.write(block,0,n);
       }
      }
     }else{
      if(foundManifest)throw new IOException("BACKUP_DUPLICATE_MANIFEST");
      foundManifest=true;
      ByteArrayOutputStream meta=new ByteArrayOutputStream();
      byte[] block=new byte[4096];int n;
      while((n=zip.read(block))!=-1){
       count+=n;
       if(count>64*1024)throw new IOException("BACKUP_MANIFEST_OVERSIZE");
       meta.write(block,0,n);
      }
      JSONObject header=new JSONObject(meta.toString("UTF-8"));
      if(!"IRAQhubPortableBackup".equals(header.optString("kind"))||
         header.optInt("schema",-1)!=1)throw new IOException("BACKUP_SCHEMA_INVALID");
      prefs=header.optJSONObject("preferences");
     }
     zip.closeEntry(); // CRC validated by ZipInputStream.
    }
   }
   if(!foundDb||!foundManifest||temp.length()<4096)
    throw new IOException("BACKUP_INCOMPLETE");
   // A validated ATTACH/transaction restores all channel tables atomically.
   int channels;
   try(InputStream input=new BufferedInputStream(new FileInputStream(temp))){
    channels=ReceiverSqliteImport.importStream(context,input);
   }
   int restored=0;
   if(prefs!=null){
    SharedPreferences.Editor edit=context.getSharedPreferences("smarthub",0).edit();
    for(String key:PREFS){
     if(!prefs.has(key))continue;
     Object value=prefs.opt(key);
     if(value instanceof Boolean){edit.putBoolean(key,(Boolean)value);restored++;}
     else if(value instanceof String){
      String text=(String)value;
      if(text.length()>120)continue;
      edit.putString(key,text);restored++;
     }else if(value instanceof Integer){edit.putInt(key,(Integer)value);restored++;}
     else if(value instanceof Long){edit.putLong(key,(Long)value);restored++;}
    }
    edit.putLong("installation_changed_at",System.currentTimeMillis()).apply();
   }
   return new RestoreResult(channels,restored);
  }finally{if(temp.isFile())temp.delete();}
 }
}
