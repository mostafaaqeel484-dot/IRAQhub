package com.smarthub.plus;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Wikimedia Commons artwork: only an explicitly selected, exact File: title.
 * Source, author, licence and attribution are retained alongside its URL.
 * A programme title is never treated as a Commons filename.
 */
final class CommonsArtworkProvider {
 private CommonsArtworkProvider(){}
 static JSONObject exactFile(Context c,String supplied)throws Exception{
  String title=supplied==null?"":supplied.trim();
  if(title.startsWith("File:"))title=title.substring(5);
  if(title.isEmpty()||title.length()>200||title.indexOf('\n')>=0||
    title.indexOf('\r')>=0||title.indexOf('#')>=0||title.indexOf('?')>=0)
   throw new java.io.IOException("WIKIMEDIA_FILE_TITLE_REQUIRED");
  String canonical="File:"+title.replace(' ','_');
  String endpoint="https://commons.wikimedia.org/w/api.php?action=query&format=json"+
    "&formatversion=2&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime%7Csize&iiurlwidth=512&titles="+
    URLEncoder.encode(canonical,"UTF-8");
  JSONObject root=new JSONObject(HubHttp.get(endpoint,1024*1024));
  JSONObject query=root.optJSONObject("query");
  JSONArray pages=query==null?null:query.optJSONArray("pages");
  if(pages==null||pages.length()!=1)throw new java.io.IOException("WIKIMEDIA_AMBIGUOUS_FILE");
  JSONObject page=pages.optJSONObject(0);
  if(page==null||page.has("missing")||page.optInt("ns",-1)!=6)
   throw new java.io.IOException("WIKIMEDIA_FILE_NOT_FOUND");
  JSONArray items=page.optJSONArray("imageinfo");
  JSONObject info=items==null?null:items.optJSONObject(0);
  if(info==null)throw new java.io.IOException("WIKIMEDIA_NO_IMAGE_INFO");
  String original=info.optString("url"),description=info.optString("descriptionurl");
  String mime=info.optString("mime");
  if(!("image/png".equals(mime)||"image/jpeg".equals(mime)||"image/webp".equals(mime)))
    throw new java.io.IOException("WIKIMEDIA_RASTER_ONLY");
  if(info.optLong("size")>16L*1024*1024)
    throw new java.io.IOException("WIKIMEDIA_FILE_TOO_LARGE");
  String thumb=info.optString("thumburl");
  if(valid(thumb,"upload.wikimedia.org"))original=thumb;
  if(!c.getSharedPreferences("smarthub",0).getBoolean("artwork_commons_enabled",true))
    throw new java.io.IOException("WIKIMEDIA_SOURCE_DISABLED");
  if(!valid(original,"upload.wikimedia.org")||!valid(description,"commons.wikimedia.org"))
   throw new java.io.IOException("WIKIMEDIA_INVALID_IMAGE_OR_DESCRIPTION");
  JSONObject metadata=info.optJSONObject("extmetadata");
  if(metadata==null)throw new java.io.IOException("WIKIMEDIA_MISSING_LICENSE");
  String licence=plain(metadata.optJSONObject("LicenseShortName"));
  if(!acceptedLicense(licence))
   throw new java.io.IOException("WIKIMEDIA_LICENSE_NOT_ACCEPTED");
  String author=plain(metadata.optJSONObject("Artist"));
  String credit=plain(metadata.optJSONObject("Credit"));
  String licenseUrl=plain(metadata.optJSONObject("LicenseUrl"));
  if(!licenseUrl.isEmpty()&&(!licenseUrl.startsWith("https://")||
      licenseUrl.length()>400))licenseUrl="";
  return new JSONObject().put("status","verified")
   .put("file",page.optString("title")).put("poster",original)
   .put("source",description).put("license",licence)
   .put("licenseUrl",licenseUrl).put("artist",author)
   .put("credit",credit).put("provider","Wikimedia Commons")
   .put("verifiedAt",System.currentTimeMillis());
 }
 /** Accept genuinely free redistribution terms; CC-NC and CC-ND are not free. */
 static boolean acceptedLicense(String raw){
  String value=(raw==null?"":raw).toLowerCase(Locale.ROOT).trim();
  if(value.contains("noncommercial")||value.contains("no derivatives")||
     value.contains("cc by-nc")||value.contains("cc by-nd")||
     value.contains("cc-by-nc")||value.contains("cc-by-nd"))return false;
  return value.startsWith("cc by ")||value.startsWith("cc by-")||
    value.equals("cc by")||value.startsWith("cc0")||
    value.contains("public domain")||value.contains("gemeinfrei");
 }
 private static String plain(JSONObject data){
  if(data==null)return "";
  String text=data.optString("value","");
  text=text.replaceAll("(?is)<script[^>]*>.*?</script>","")
   .replaceAll("(?is)<[^>]+>"," ").replace("&amp;","&")
   .replace("&quot;","\"").replace("&#39;","'")
   .replace("&nbsp;"," ").replaceAll("\\s+"," ").trim();
  return text.length()>400?text.substring(0,400):text;
 }
 private static boolean valid(String url,String host){
  try{
   URI uri=new URI(url);
   return "https".equals(uri.getScheme())&&host.equals(uri.getHost())
     &&uri.getUserInfo()==null&&uri.getFragment()==null&&url.length()<2048;
  }catch(Exception invalid){return false;}
 }
}
