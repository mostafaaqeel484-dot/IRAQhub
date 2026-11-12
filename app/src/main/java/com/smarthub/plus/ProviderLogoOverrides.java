package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** User-added provider logos live in app-private files; never in CHMAX,
 * never in the channel-logo cache, never in OEM receiver storage. */
final class ProviderLogoOverrides {
 static final String REVISION="rx6_provider_override_revision";
 private static final int MAX_IMAGE=4*1024*1024;
 private ProviderLogoOverrides(){}
 private static File root(Context c){return new File(c.getFilesDir(),"rx6-provider-overrides");}
 private static File metadata(Context c){return new File(root(c),"providers.json");}
 private static String key(String name,String country){
  return ProviderCountryCatalog.key(name)+"|"+country.toUpperCase(Locale.ROOT);
 }
 private static String hash(String value)throws Exception{
  byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
  StringBuilder out=new StringBuilder();
  for(byte c:b)out.append(String.format(Locale.ROOT,"%02x",c&255));
  return out.toString();
 }
 private static JSONObject read(Context c){
  File file=metadata(c);
  if(!file.isFile()||file.length()>100000)return new JSONObject();
  try(FileInputStream input=new FileInputStream(file);
      ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n;
   while((n=input.read(buffer))!=-1){if(out.size()+n>100000)return new JSONObject();out.write(buffer,0,n);}
   return new JSONObject(out.toString("UTF-8"));
  }catch(Exception ignored){return new JSONObject();}
 }
 static JSONObject forChannel(Context c,String channelId){
  if(channelId==null||!channelId.startsWith("DVB:"))return null;
  return read(c).optJSONObject("channel:"+channelId);
 }
 static JSONObject forProvider(Context c,String name,String country){
  if(name==null||name.trim().isEmpty()||!ProviderCountryCatalog.iso(country))return null;
  return read(c).optJSONObject("provider:"+key(name,country));
 }
 static String providerName(Context c,String channelId){
  JSONObject row=forChannel(c,channelId);return row==null?"":row.optString("name","");
 }
 static String country(Context c,String channelId){
  JSONObject row=forChannel(c,channelId);return row==null?"":row.optString("country","");
 }
 static Bitmap logo(Context c,String channelId,String name,String country){
  JSONObject row=forChannel(c,channelId);
  if(row==null)row=forProvider(c,name,country);
  if(row==null)return null;
  String filename=row.optString("filename","");
  if(!filename.matches("[a-f0-9]{64}\\.png"))return null;
  File file=new File(root(c),filename);
  if(!file.isFile()||file.length()<80||file.length()>MAX_IMAGE)return null;
  BitmapFactory.Options bounds=new BitmapFactory.Options();
  bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
  if(bounds.outWidth<16||bounds.outWidth>2048||bounds.outHeight<16||bounds.outHeight>2048)return null;
  BitmapFactory.Options scaled=new BitmapFactory.Options();
  scaled.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/512);
  return BitmapFactory.decodeFile(file.getAbsolutePath(),scaled);
 }
 static void add(Context c,String name,String country,String channelId,Uri image)throws Exception{
  name=name==null?"":name.trim();
  country=country==null?"":country.trim().toUpperCase(Locale.ROOT);
  if(name.length()<2||name.length()>96||name.chars().anyMatch(c->c<32)||
     !ProviderCountryCatalog.iso(country))throw new IOException("PROVIDER_NAME_OR_COUNTRY_INVALID");
  if(channelId==null)channelId="";
  if(!channelId.isEmpty()&&!channelId.startsWith("DVB:"))
   throw new IOException("CHANNEL_ID_NOT_DVB");
  byte[] bytes;
  try(InputStream source=c.getContentResolver().openInputStream(image);
      ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(source==null)throw new IOException("IMAGE_UNREADABLE");
   byte[] buffer=new byte[16384];int n;
   while((n=source.read(buffer))!=-1){
    if(out.size()+n>MAX_IMAGE)throw new IOException("IMAGE_TOO_LARGE");
    out.write(buffer,0,n);
   }
   bytes=out.toByteArray();
  }
  if(bytes.length<80)throw new IOException("IMAGE_TOO_SMALL");
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
  if(bounds.outWidth<32||bounds.outHeight<24||bounds.outWidth>4096||bounds.outHeight>4096)
   throw new IOException("INVALID_PROVIDER_ARTWORK");
  BitmapFactory.Options decode=new BitmapFactory.Options();
  decode.inSampleSize=Math.max(1,(int)Math.ceil(Math.max(bounds.outWidth,bounds.outHeight)/512.0));
  Bitmap original=BitmapFactory.decodeByteArray(bytes,0,bytes.length,decode);
  if(original==null)throw new IOException("PROVIDER_IMAGE_DECODE_FAILED");
  root(c).mkdirs();
  String logical=channelId.isEmpty()?"provider:"+key(name,country):"channel:"+channelId;
  String filename=hash(logical)+".png";
  AtomicFile target=new AtomicFile(new File(root(c),filename));
  FileOutputStream output=target.startWrite();
  try{
   if(!original.compress(Bitmap.CompressFormat.PNG,100,output))
    throw new IOException("PNG_ENCODE_FAILED");
   target.finishWrite(output);
  }catch(Exception failed){target.failWrite(output);throw failed;}
  finally{original.recycle();}
  JSONObject entries=read(c);
  entries.put(logical,new JSONObject().put("name",name)
    .put("country",country).put("filename",filename));
  byte[] raw=entries.toString().getBytes(StandardCharsets.UTF_8);
  if(raw.length>100000)throw new IOException("TOO_MANY_PROVIDER_OVERRIDES");
  AtomicFile saved=new AtomicFile(metadata(c));
  FileOutputStream out=saved.startWrite();
  try{out.write(raw);saved.finishWrite(out);}
  catch(Exception problem){saved.failWrite(out);throw problem;}
  c.getSharedPreferences("smarthub",0).edit().putLong(REVISION,System.currentTimeMillis())
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
 }
 static int count(Context c){return read(c).length();}
 static void remove(Context c,String name,String country,String channelId)throws Exception{
  String id=channelId!=null&&!channelId.isEmpty()?
    "channel:"+channelId:"provider:"+key(name,country);
  JSONObject entries=read(c);
  JSONObject row=entries.optJSONObject(id);
  if(row==null)return;
  String filename=row.optString("filename","");
  entries.remove(id);
  AtomicFile saved=new AtomicFile(metadata(c));FileOutputStream out=saved.startWrite();
  try{out.write(entries.toString().getBytes(StandardCharsets.UTF_8));saved.finishWrite(out);}
  catch(Exception failed){saved.failWrite(out);throw failed;}
  // A shared file may be referenced by another provider; do not delete blindly.
  boolean used=false;
  Iterator<String> keys=entries.keys();while(keys.hasNext()){
   JSONObject other=entries.optJSONObject(keys.next());
   if(other!=null&&filename.equals(other.optString("filename")))used=true;
  }
  if(!used&&filename.matches("[a-f0-9]{64}\\.png"))new File(root(c),filename).delete();
  c.getSharedPreferences("smarthub",0).edit().putLong(REVISION,System.currentTimeMillis()).apply();
 }
}
