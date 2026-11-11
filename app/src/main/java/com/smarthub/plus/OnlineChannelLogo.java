package com.smarthub.plus;
import android.content.Context;
import android.graphics.*;
import java.net.*;
import java.io.*;
import org.json.*;
/** English channel names resolve automatically; manual correction is optional. */
final class OnlineChannelLogo {
 static Bitmap fetch(Context context,String name,String id,String nativeId)throws Exception{
  Bitmap factory=FactoryProvider.cachedLogo(context,name,nativeId);
  if(factory!=null)return factory;
  Bitmap remembered=fastKnown(context,name,nativeId);
  if(remembered!=null)return remembered;SatelliteState state=SatelliteState.read(context);if(state.channelId.equals(nativeId)&&ChannelCatalogue.normalized(state.channelName).equals(ChannelCatalogue.normalized(name))){Bitmap nativeLogo=CHMAXIntegration.logo(context,state,false);if(nativeLogo!=null)return nativeLogo;}JSONObject j=new ChannelCatalogue(context).logo(name,id);
  try{Bitmap english=EnglishChannelLogos.fetch(context,j.optString("englishName",name),j.optString("country","US"),nativeId);if(english!=null){context.getSharedPreferences("smarthub",0).edit().putString("online_network:"+nativeId,j.optString("network")).putString("online_logo_public:"+nativeId,j.optString("channel")).apply();return english;}}catch(Exception unavailable){}if(!"matched".equals(j.optString("status")))return null;JSONArray images=j.optJSONArray("images");if(images==null)return null;
  for(int i=0;i<Math.min(images.length(),3);i++){JSONObject image=images.optJSONObject(i);if(image==null)continue;URI uri=new URI(image.optString("url"));if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)continue;try{byte[] bytes=ArtworkCache.fetch(context,uri.toURL(),5*1024*1024);if(("SVG".equalsIgnoreCase(image.optString("format"))||uri.getPath().endsWith(".svg"))&&new String(bytes,0,Math.min(100,bytes.length),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","").trim().startsWith("<")){String xml=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);if(xml.toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE")||xml.toUpperCase(java.util.Locale.ROOT).contains("<!ENTITY"))continue;com.caverock.androidsvg.SVG svg=com.caverock.androidsvg.SVG.getFromString(xml);float ratio=svg.getDocumentAspectRatio();if(!Float.isFinite(ratio)||ratio<=0)ratio=1;int w=ratio>=1?512:Math.max(1,Math.round(512*ratio)),h=ratio>=1?Math.max(1,Math.round(512/ratio)):512;Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawPicture(svg.renderToPicture(w,h));remember(context,j,image,nativeId,name);return bitmap;}BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>20000||bounds.outHeight>20000)continue;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>512||bounds.outHeight/options.inSampleSize>512)options.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap!=null){remember(context,j,image,nativeId,name);return bitmap;}}catch(Exception unavailable){/* try another verified logo format */}}
  return null;
 }
 /** A previously verified channel URL can render immediately on next launch
  * without another channel-search API request. Match both name and physical ID. */
 static Bitmap fastKnown(Context c,String name,String nativeId){
  if(nativeId==null||nativeId.isEmpty())return null;
  android.content.SharedPreferences p=c.getSharedPreferences("smarthub",0);
  if(!ChannelCatalogue.normalized(name).equals(p.getString("online_logo_name:"+nativeId,"")))return null;
  try{
   URI uri=new URI(p.getString("online_logo_url:"+nativeId,""));
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||
      uri.getUserInfo()!=null||uri.getFragment()!=null)return null;
   byte[] bytes=ArtworkCache.fetch(c,uri.toURL(),5*1024*1024);
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
   if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>8192||bounds.outHeight>8192)return null;
   BitmapFactory.Options options=new BitmapFactory.Options();
   options.inSampleSize=1;
   while(bounds.outWidth/options.inSampleSize>512||bounds.outHeight/options.inSampleSize>512)
    options.inSampleSize*=2;
   return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  }catch(Exception unavailable){return null;}
 }
 static void remember(Context c,JSONObject data,JSONObject image,String nativeId){
  remember(c,data,image,nativeId,"");
 }
 static void remember(Context c,JSONObject data,JSONObject image,String nativeId,String name){
  android.content.SharedPreferences.Editor edit=c.getSharedPreferences("smarthub",0).edit()
   .putString("online_network:"+nativeId,data.optString("network"))
   .putString("online_logo_public:"+nativeId,data.optString("channel"))
   .putString("online_logo_url:"+nativeId,image.optString("url"));
  if(name!=null&&!name.isEmpty())edit.putString("online_logo_name:"+nativeId,
    ChannelCatalogue.normalized(name));
  edit.apply();
 }
 static byte[] read(HttpURLConnection c,int max)throws Exception{c.setConnectTimeout(6000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);try{if(c.getResponseCode()!=200)throw new IOException("Logo source HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>max)throw new IOException("Logo exceeds size limit");out.write(bytes,0,n);}return out.toByteArray();}}finally{c.disconnect();}}
}
