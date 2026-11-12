package com.smarthub.plus;

import android.content.Context;
import android.net.Uri;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit HTTPS artwork import for the selected DVB channel. No URL polling. */
final class ChannelImageUrlImport {
 private static final int LIMIT=4*1024*1024;
 private static final OkHttpClient HTTP=new OkHttpClient.Builder()
  .dns(host->{
   List<InetAddress> addresses=Dns.SYSTEM.lookup(host);
   if(addresses.isEmpty())throw new UnknownHostException("EMPTY_CHANNEL_IMAGE_DNS");
   for(InetAddress ip:addresses)if(!ProviderApprovedArtwork.publicAddress(ip))
    throw new UnknownHostException("NON_PUBLIC_CHANNEL_IMAGE_IP");
   return addresses;
  })
  .connectTimeout(7,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS)
  .callTimeout(18,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
 private ChannelImageUrlImport(){}
 static void add(Context app,String physical,String address)throws Exception{
  if(!ChannelLogoAppearance.physical(physical))throw new IOException("SELECT_EXACT_DVB_SERVICE");
  if(!ProviderApprovedArtwork.safeUrl(address))throw new IOException("PUBLIC_HTTPS_REQUIRED");
  File temp=File.createTempFile("rx6-channel-",".png",app.getCacheDir());
  try{
   Request request=new Request.Builder().url(address).get()
     .header("Accept","image/png,image/jpeg,image/webp").build();
   try(Response response=HTTP.newCall(request).execute()){
    if(response.code()!=200||response.body()==null)
     throw new IOException("IMAGE_HTTP_"+response.code());
    String type=response.header("Content-Type","");
    if(!type.isEmpty()&&!type.toLowerCase(Locale.ROOT).startsWith("image/"))
     throw new IOException("NOT_AN_IMAGE");
    if(response.body().contentLength()>LIMIT)throw new IOException("IMAGE_TOO_LARGE");
    try(InputStream input=response.body().byteStream();
        FileOutputStream output=new FileOutputStream(temp)){
     byte[] bytes=new byte[8192];int n,total=0;
     while((n=input.read(bytes))!=-1){
      total+=n;if(total>LIMIT)throw new IOException("IMAGE_TOO_LARGE");
      output.write(bytes,0,n);
     }
    }
   }
   ChannelArtworkOverrides.add(app,physical,Uri.fromFile(temp));
  }finally{temp.delete();}
 }
}
