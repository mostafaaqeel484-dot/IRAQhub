package com.smarthub.plus;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import android.util.LruCache;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Exact PROVIDER-name candidate URLs explicitly approved by the receiver owner.
 * No CHMAX channel data, no private API credentials, no name-only channel art.
 * A selected URL is not a claim that its contents are an official trademark. */
final class ProviderApprovedArtwork {
 private static final int MAX_BYTES=4*1024*1024;
 private static final Map<String,String> urls=new HashMap<>();
 private static boolean ready;
 private static final ConcurrentHashMap<String,Long> failedUntil=new ConcurrentHashMap<>();
 private static final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024){
  @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
 };
 private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
  .dns(hostname->{
   List<InetAddress> found=Dns.SYSTEM.lookup(hostname);
   if(found.isEmpty())throw new UnknownHostException("EMPTY_PROVIDER_DNS");
   for(InetAddress ip:found)if(!publicAddress(ip))
    throw new UnknownHostException("PROVIDER_DNS_NOT_PUBLIC");
   return found;
  })
  .connectTimeout(5,TimeUnit.SECONDS)
  .readTimeout(8,TimeUnit.SECONDS)
  .callTimeout(13,TimeUnit.SECONDS)
  .followRedirects(false).followSslRedirects(false).build();
 private ProviderApprovedArtwork(){}
 static boolean publicAddress(InetAddress address){
  if(address.isAnyLocalAddress()||address.isLoopbackAddress()||
    address.isSiteLocalAddress()||address.isLinkLocalAddress()||
    address.isMulticastAddress())return false;
  byte[] ip=address.getAddress();
  if(ip.length==4){
   int a=ip[0]&255,b=ip[1]&255;
   if(a==0||a==10||a==127||a>=224||a==169&&b==254||
      a==172&&b>=16&&b<=31||a==192&&b==168||
      a==100&&b>=64&&b<=127||a==198&&b>=18&&b<=19||
      a==192&&b==0||a==192&&b==0||a==192&&b==2||
      a==198&&b==51||a==203&&b==0)return false;
  }
  return true;
 }
 private static String key(String provider){return ProviderCountryCatalog.key(provider);}
 private static synchronized void init(Context context){
  if(ready)return;
  try(InputStream in=context.getAssets().open("provider-branding/approved-provider-sources.json");
      ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1){
    if(bytes.size()+n>150000)throw new IOException("TOO_MANY_USER_SOURCES");
    bytes.write(buffer,0,n);
   }
   JSONArray entries=new JSONObject(bytes.toString("UTF-8")).getJSONArray("entries");
   if(entries.length()!=133)throw new IOException("SOURCE_APPROVAL_COUNT_MISMATCH");
   for(int i=0;i<entries.length();i++){
    JSONObject entry=entries.getJSONObject(i);
    if(!"user_approved_pending_binary_validation".equals(entry.optString("review")))continue;
    String name=entry.optString("name"),url=entry.optString("url");
    if(name.length()<2||name.length()>110||!safeUrl(url))continue;
    String k=key(name);
    if(k.length()>=2&&!urls.containsKey(k))urls.put(k,url);
   }
  }catch(Exception ignored){}
  ready=true;
 }
 static boolean selected(Context c,String provider){
  init(c.getApplicationContext());
  return urls.containsKey(key(provider));
 }
 private static boolean safeUrl(String value){
  if(value==null||value.length()>1800)return false;
  try{
   URI u=new URI(value);
   String host=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||host==null||
      u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1)return false;
   String h=host.toLowerCase(Locale.ROOT);
   if(h.indexOf('.')<1||h.equals("localhost")||h.endsWith(".local")||
      h.endsWith(".internal")||h.endsWith(".lan")||
      h.matches("[0-9.]+")||h.contains(":"))return false;
   return true;
  }catch(Exception invalid){return false;}
 }
 private static String hash(String text){
  try{
   byte[] raw=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
   StringBuilder s=new StringBuilder();
   for(byte a:raw)s.append(String.format(Locale.ROOT,"%02x",a&255));
   return s.toString();
  }catch(Exception e){return "";}
 }
 private static File file(Context c,String provider,String source){
  File dir=new File(c.getFilesDir(),"rx6-approved-provider-art");dir.mkdirs();
  return new File(dir,hash(key(provider)+"|"+source)+".png");
 }
 static Bitmap cached(Context c,String provider){
  init(c);
  String address=urls.get(key(provider));if(address==null)return null;
  return cached(c,provider,address);
 }
 private static Bitmap cached(Context c,String provider,String url){
  String token=hash(key(provider)+"|"+url);
  Bitmap fromMemory=memory.get(token);
  if(fromMemory!=null&&!fromMemory.isRecycled())return fromMemory;
  File f=file(c,provider,url);
  if(!f.isFile()||f.length()<80||f.length()>MAX_BYTES)return null;
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
  if(bounds.outWidth<20||bounds.outWidth>1024||bounds.outHeight<20||bounds.outHeight>1024)return null;
  Bitmap out=BitmapFactory.decodeFile(f.getAbsolutePath());
  if(out!=null)memory.put(token,out);
  return out;
 }
 static Bitmap resolve(Context c,String provider){
  if(!ChannelLogoSourcePrefs.providers(c)||PlaybackResourceMode.active())return null;
  init(c);
  String url=urls.get(key(provider));
  if(url==null)return null;
  Bitmap existing=cached(c,provider,url);if(existing!=null)return existing;
  String token=hash(key(provider)+"|"+url);
  Long blocked=failedUntil.get(token);
  if(blocked!=null&&blocked>System.currentTimeMillis())return null;
  try{
   if(!safeUrl(url))return null;
   Request request=new Request.Builder().url(url)
     .header("User-Agent","IRAQhub-RX6/4.0.5 personal artwork preview")
     .header("Accept","image/png,image/jpeg,image/webp,image/svg+xml").build();
   byte[] payload;
   try(Response response=CLIENT.newCall(request).execute()){
    if(!response.isSuccessful()||response.body()==null||
      response.body().contentLength()>MAX_BYTES)throw new IOException("IMAGE_HTTP_UNAVAILABLE");
    try(InputStream input=response.body().byteStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] buffer=new byte[16384];int n;
     while((n=input.read(buffer))!=-1){
      if(out.size()+n>MAX_BYTES)throw new IOException("IMAGE_EXCEEDS_LIMIT");
      out.write(buffer,0,n);
     }
     payload=out.toByteArray();
    }
   }
   Bitmap image=decode(payload);
   if(image==null)throw new IOException("IMAGE_FORMAT_INVALID");
   File f=file(c,provider,url);
   AtomicFile atomic=new AtomicFile(f);FileOutputStream output=atomic.startWrite();
   try{
    if(!image.compress(Bitmap.CompressFormat.PNG,100,output))
     throw new IOException("IMAGE_SAVE_FAILED");
    atomic.finishWrite(output);
   }catch(Exception error){atomic.failWrite(output);throw error;}
   memory.put(token,image);
   return image;
  }catch(Exception unavailable){
   // Never log the URL or a response that may contain source-specific tokens.
   failedUntil.put(token,System.currentTimeMillis()+60L*60*1000);
   return null;
  }
 }
 private static Bitmap decode(byte[] bytes){
  if(bytes.length<80)return null;
  String start=new String(bytes,0,Math.min(500,bytes.length),StandardCharsets.UTF_8);
  if(start.contains("<svg")||start.contains("<?xml")){
   String xml=new String(bytes,StandardCharsets.UTF_8);
   String lower=xml.toLowerCase(Locale.ROOT);
   if(xml.length()>500000||lower.contains("<!doctype")||lower.contains("<!entity")||
     lower.contains("<script")||lower.contains("<foreignobject")||
     lower.contains("href=\\\"http:")||lower.contains("href=\\\"https:")||
     lower.contains("href='http:")||lower.contains("href='https:"))return null;
   try{
    com.caverock.androidsvg.SVG svg=com.caverock.androidsvg.SVG.getFromString(xml);
    float aspect=svg.getDocumentAspectRatio();
    if(!Float.isFinite(aspect)||aspect<=0)aspect=1;
    int width=aspect>=1?512:Math.max(16,(int)(512*aspect));
    int height=aspect>=1?Math.max(16,(int)(512/aspect)):512;
    svg.setDocumentWidth((float)width);svg.setDocumentHeight((float)height);
    Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    new Canvas(result).drawPicture(svg.renderToPicture(width,height));
    return result;
   }catch(Exception invalid){return null;}
  }
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
  if(bounds.outWidth<24||bounds.outHeight<24||bounds.outWidth>6000||bounds.outHeight>6000)return null;
  BitmapFactory.Options options=new BitmapFactory.Options();
  options.inSampleSize=1;
  while(bounds.outWidth/options.inSampleSize>512||bounds.outHeight/options.inSampleSize>512)
   options.inSampleSize*=2;
  return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
 }
}
