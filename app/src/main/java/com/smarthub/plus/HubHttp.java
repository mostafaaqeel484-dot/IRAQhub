package com.smarthub.plus;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public final class HubHttp {
 public static String get(String address) throws IOException {return get(address,4*1024*1024);}
 public static String get(String address,int limit) throws IOException {
  URL url=new URL(address);if(!"https".equals(url.getProtocol()))throw new IOException("HTTPS required");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","SmartHubPlus/1.4 (Android TV)");c.setRequestProperty("Accept","application/json");
  try{int status=c.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}finally{c.disconnect();}
 }
 private HubHttp(){}
}
