package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * USB ADB can export authorised, readable image files to a ZIP; it never grants
 * this APK the adb shell UID. Only user-chosen SAF ZIP files are imported.
 * Exact DVB transponder/SID identity is REQUIRED before applying a logo.
 */
final class AdbLogoBundleImporter {
 private AdbLogoBundleImporter(){}
 static final class Result{
  final int imported,missing,invalid;
  Result(int imported,int missing,int invalid){
   this.imported=imported;this.missing=missing;this.invalid=invalid;
  }
 }
 static boolean safeName(String value){
  return value!=null&&value.length()>0&&value.length()<180&&
   !value.startsWith("/")&&!value.contains("..")&&!value.contains("\\")&&
   value.matches("[A-Za-z0-9_./-]+")&&(value.equals("manifest.json")||
      value.startsWith("logos/")&&value.matches("logos/[A-Za-z0-9_.-]+\\.(png|jpg|jpeg)"));
 }
 static byte[] limited(InputStream in,int max)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  byte[] buf=new byte[8192];int n;
  while((n=in.read(buf))>=0){
   if(out.size()+n>max)throw new IOException("ZIP_ENTRY_TOO_LARGE");
   out.write(buf,0,n);
  }
  return out.toByteArray();
 }
 static Result importUserSelected(Context context,InputStream input)throws Exception{
  Map<String,byte[]> files=new HashMap<>();long total=0;
  try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
   ZipEntry entry;int entries=0;
   while((entry=zip.getNextEntry())!=null){
    if(++entries>450)throw new IOException("ZIP_TOO_MANY_FILES");
    String name=entry.getName();
    if(entry.isDirectory()){zip.closeEntry();continue;}
    if(!safeName(name))throw new IOException("ZIP_ENTRY_PATH_REJECTED");
    if(files.containsKey(name))throw new IOException("ZIP_DUPLICATE_FILE");
    byte[] bytes=limited(zip,name.equals("manifest.json")?256*1024:1024*1024);
    total+=bytes.length;
    if(total>40L*1024*1024)throw new IOException("ZIP_TOTAL_TOO_LARGE");
    files.put(name,bytes);zip.closeEntry();
   }
  }
  byte[] raw=files.get("manifest.json");
  if(raw==null)throw new IOException("ZIP_MANIFEST_REQUIRED");
  JSONObject manifest=new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8));
  if(manifest.optInt("version")!=1)throw new IOException("ZIP_MANIFEST_VERSION");
  JSONArray rows=manifest.optJSONArray("channels");
  if(rows==null||rows.length()>400)throw new IOException("ZIP_MANIFEST_CHANNELS");
  ReceiverCatalog catalog=ReceiverCatalog.get(context);catalog.ensure();
  Set<String> real=new HashSet<>();
  for(ReceiverCatalog.Channel channel:
      catalog.channelsContinuous("",-1,-1,false,false,-1,false))
   real.add("DVB:"+channel.physical());
  ChannelLogoCache cache=new ChannelLogoCache(context);
  int imported=0,missing=0,invalid=0;
  Set<String> seen=new HashSet<>();
  android.content.SharedPreferences.Editor preferences=context
    .getSharedPreferences("smarthub",0).edit();
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.optJSONObject(i);if(row==null){invalid++;continue;}
   String physical=row.optString("physical");
   String name=row.optString("file");
   if(!physical.matches("DVB:[0-9:-]+[A-Za-z0-9:-]*")||
      !safeName(name)||!name.startsWith("logos/")||
      !seen.add(physical)){invalid++;continue;}
   if(!real.contains(physical)){missing++;continue;}
   byte[] bytes=files.get(name);
   if(bytes==null){missing++;continue;}
   String expected=row.optString("sha256");
   if(!expected.isEmpty()&&!expected.equalsIgnoreCase(hex(
      MessageDigest.getInstance("SHA-256").digest(bytes)))){invalid++;continue;}
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
   if(options.outWidth<20||options.outHeight<20||
       options.outWidth>8192||options.outHeight>8192){invalid++;continue;}
   options.inJustDecodeBounds=false;options.inSampleSize=1;
   while(options.outWidth/options.inSampleSize>512||
        options.outHeight/options.inSampleSize>512)options.inSampleSize*=2;
   Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
   if(image==null){invalid++;continue;}
   try{cache.save("channel:physical:v2:"+physical,image);}
   finally{image.recycle();}
   preferences.putBoolean("adb_logo_imported:"+physical,true);
   imported++;
  }
  preferences.putInt("adb_logo_import_count",imported)
    .putLong("adb_logo_import_at",System.currentTimeMillis())
    .putLong("logo_mapping_changed_at",System.currentTimeMillis()).apply();
  LogoRepository.get(context).invalidate();
  return new Result(imported,missing,invalid);
 }
 private static String hex(byte[] bytes){
  StringBuilder out=new StringBuilder();
  for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return out.toString();
 }
}
