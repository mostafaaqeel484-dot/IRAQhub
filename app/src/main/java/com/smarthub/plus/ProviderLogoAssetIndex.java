package com.smarthub.plus;
import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.util.*;

/** Exact-country provider/channel logo images from a pinned audited manifest.
 * No synthetic wordmark and no name-only choice for ambiguous regional services.
 * Branded images are bundled under assets/provider-branding/images in the APK.
 */
final class ProviderLogoAssetIndex {
 private static volatile ProviderLogoAssetIndex instance;
 private final Context context;
 private final Map<String,String> provider=new HashMap<>(),channel=new HashMap<>();
 private final LruCache<String,Bitmap> bitmaps=new LruCache<>(48);
 private ProviderLogoAssetIndex(Context app){
  context=app;
  try(InputStream input=app.getAssets().open("provider-branding/real-logo-manifest.json")){
   ByteArrayOutputStream output=new ByteArrayOutputStream();
   byte[] buf=new byte[16384];int n;
   while((n=input.read(buf))!=-1){
    if(output.size()+n>500000)throw new IOException("INVALID_LOGO_MANIFEST");
    output.write(buf,0,n);
   }
   JSONObject root=new JSONObject(output.toString("UTF-8"));
   JSONArray providers=root.getJSONArray("providers");
   for(int i=0;i<providers.length();i++){
    JSONObject p=providers.getJSONObject(i);
    if(valid(p))provider.put(key(p),p.getString("asset"));
   }
   JSONArray channels=root.getJSONArray("channels");
   for(int i=0;i<channels.length();i++){
    JSONObject c=channels.getJSONObject(i);
    if(valid(c))channel.put(key(c),c.getString("asset"));
   }
  }catch(Exception ignored){}
 }
 static ProviderLogoAssetIndex get(Context app){
  ProviderLogoAssetIndex index=instance;
  if(index==null)synchronized(ProviderLogoAssetIndex.class){
   index=instance;if(index==null)instance=index=new ProviderLogoAssetIndex(app.getApplicationContext());
  }
  return index;
 }
 private static boolean valid(JSONObject item){
  String n=item.optString("name"),country=item.optString("country"),asset=item.optString("asset");
  return !n.isEmpty()&&ProviderCountryCatalog.iso(country)&&
    asset.matches("(?:tv|simple)-[a-f0-9]{40}\\.(?:png|svg)|(?:commons)-[A-Za-z0-9_-]{1,50}\\.(?:png|svg)");
 }
 private static String key(JSONObject row){return key(row.optString("name"),row.optString("country"));}
 private static String key(String n,String country){return ProviderCountryCatalog.key(n)+"|"+country;}
 Bitmap provider(String name,String country){return load(provider.get(key(name,country)));}
 Bitmap channel(String name,String country){return load(channel.get(key(name,country)));}
 private Bitmap load(String asset){
  if(asset==null||asset.isEmpty())return null;
  Bitmap cached=bitmaps.get(asset);
  if(cached!=null&&!cached.isRecycled())return cached;
  try(InputStream input=context.getAssets().open("provider-branding/images/"+asset)){
   Bitmap image;
   if(asset.endsWith(".svg")){
    ByteArrayOutputStream data=new ByteArrayOutputStream();
    byte[] b=new byte[8192];int n;
    while((n=input.read(b))!=-1){
     if(data.size()+n>200000)throw new IOException("SVG_TOO_LARGE");
     data.write(b,0,n);
    }
    String xml=data.toString("UTF-8");
    if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY")||
       xml.contains("href=\"http:")||xml.contains("href=\"https:")||
       xml.contains("href='http:")||xml.contains("href='https:"))
       throw new IOException("SVG_EXTERNAL_REFERENCE");
    com.caverock.androidsvg.SVG svg=com.caverock.androidsvg.SVG.getFromString(xml);
    float ratio=svg.getDocumentAspectRatio();
    if(!Float.isFinite(ratio)||ratio<=0)ratio=1;
    int width=ratio>=1?512:Math.max(8,(int)(512*ratio));
    int height=ratio>=1?Math.max(8,(int)(512/ratio)):512;
    svg.setDocumentWidth((float)width);svg.setDocumentHeight((float)height);
    image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    new Canvas(image).drawPicture(svg.renderToPicture(width,height));
   }else{
    BitmapFactory.Options o=new BitmapFactory.Options();
    o.inJustDecodeBounds=true;
    byte[] raw=readBounded(input,2*1024*1024);
    BitmapFactory.decodeByteArray(raw,0,raw.length,o);
    if(o.outWidth<2||o.outHeight<2||o.outWidth>6000||o.outHeight>6000)return null;
    o.inJustDecodeBounds=false;o.inSampleSize=1;
    while(o.outWidth/o.inSampleSize>512||o.outHeight/o.inSampleSize>512)o.inSampleSize*=2;
    image=BitmapFactory.decodeByteArray(raw,0,raw.length,o);
   }
   if(image!=null)bitmaps.put(asset,image);
   return image;
  }catch(Exception unavailable){return null;}
 }
 private static byte[] readBounded(InputStream input,int max)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  byte[] data=new byte[8192];int n;
  while((n=input.read(data))!=-1){
   if(out.size()+n>max)throw new IOException("IMAGE_TOO_LARGE");
   out.write(data,0,n);
  }
  return out.toByteArray();
 }
}
