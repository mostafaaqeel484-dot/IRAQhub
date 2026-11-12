package com.smarthub.plus;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Explicit user-triggered API credential checks. No tokens in logs, errors,
 * request headers sent to other origins, or persisted in status messages. */
final class PosterApiProbe {
 private static final String SAMPLE="tt0133093"; // The Matrix, stable IMDb ID
 private PosterApiProbe(){}
 static String test(String provider,String key){
  if(!PosterApiSecrets.supported(provider))return "UNKNOWN_SERVICE";
  if("sportsdb".equals(provider)&&key.isEmpty())key="123";
  if(!PosterApiSecrets.valid(key))return "MISSING_OR_INVALID_KEY_FORMAT";
  try{
   if("sportsdb".equals(provider))return testSports(key);
   String escaped=URLEncoder.encode(key,"UTF-8");
   String url;
   if("rpdb".equals(provider))
    url="https://api.ratingposterdb.com/"+escaped+"/imdb/poster-default/"+SAMPLE+".jpg?fallback=true";
   else if("opdb".equals(provider))
    url="https://openposterdb.com/"+escaped+"/imdb/poster-default/"+SAMPLE+".jpg";
   else if("omdb".equals(provider))
    url="https://img.omdbapi.com/?apikey="+escaped+"&i="+SAMPLE+"&h=600";
   else return "UNKNOWN_SERVICE";
   HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
   connection.setRequestMethod("HEAD");
   connection.setInstanceFollowRedirects(false);
   connection.setConnectTimeout(6000);connection.setReadTimeout(9000);
   connection.setRequestProperty("Accept","image/jpeg,image/png,image/webp");
   try{
    int code=connection.getResponseCode();
    String type=connection.getContentType();
    if(code==200&&type!=null&&type.toLowerCase(Locale.ROOT).startsWith("image/"))
     return "CONNECTED_AND_POSTER_AVAILABLE";
    if(code==401||code==403)return "KEY_REJECTED_OR_NO_POSTER_ENTITLEMENT";
    if(code==429)return "RATE_LIMITED_RETRY_LATER";
    if(code==405||code==501)return "SOURCE_DOES_NOT_SUPPORT_HEAD_CHECK";
    if(code==404)return "REACHABLE_BUT_SAMPLE_POSTER_NOT_FOUND";
    if(code>=300&&code<400)return "REDIRECT_NOT_FOLLOWED";
    return "HTTP_"+code+"_UNVERIFIED";
   }finally{connection.disconnect();}
  }catch(Exception error){
   // Exception messages can contain the private key URL: never surface/log them.
   return "NETWORK_OR_TLS_ERROR";
  }
 }
 private static String testSports(String key)throws Exception{
  String url="https://www.thesportsdb.com/api/v1/json/"+
   URLEncoder.encode(key,"UTF-8")+"/searchteams.php?t=Arsenal";
  HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
  connection.setRequestMethod("GET");connection.setInstanceFollowRedirects(false);
  connection.setConnectTimeout(6000);connection.setReadTimeout(9000);
  connection.setRequestProperty("Accept","application/json");
  try{
   int code=connection.getResponseCode();
   if(code==401||code==403)return "KEY_REJECTED";
   if(code==429)return "RATE_LIMITED_RETRY_LATER";
   if(code!=200)return "HTTP_"+code+"_UNVERIFIED";
   byte[] data;
   try(InputStream input=connection.getInputStream();
       ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[4096];int n;
    while((n=input.read(buffer))!=-1){
     if(out.size()+n>100000)return "INVALID_SERVICE_RESPONSE";
     out.write(buffer,0,n);
    }
    data=out.toByteArray();
   }
   JSONObject reply=new JSONObject(new String(data,StandardCharsets.UTF_8));
   JSONArray teams=reply.optJSONArray("teams");
   if(teams!=null)for(int i=0;i<teams.length();i++){
    JSONObject item=teams.optJSONObject(i);
    if(item!=null&&"Arsenal".equalsIgnoreCase(item.optString("strTeam")))
     return "CONNECTED_SPORTSDB_"+("123".equals(key)?"PUBLIC_SHARED_KEY":"PERSONAL_KEY");
   }
   return "SOURCE_REACHABLE_NO_EXACT_SAMPLE";
  }finally{connection.disconnect();}
 }
}
