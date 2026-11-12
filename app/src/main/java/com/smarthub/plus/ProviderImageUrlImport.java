package com.smarthub.plus;

import android.content.Context;
import android.net.Uri;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Strict HTTPS provider-art import. No redirects to local services, no
 * password in image URL, and at most 4 MiB. This never touches channel logos. */
final class ProviderImageUrlImport {
 private static final int LIMIT=4*1024*1024;
 private static final OkHttpClient HTTP=new OkHttpClient.Builder()
   .dns(host->{
    List<InetAddress> addresses=Dns.SYSTEM.lookup(host);
    if(addresses.isEmpty())throw new UnknownHostException("EMPTY_PROVIDER_DNS");
    for(InetAddress ip:addresses)if(!ProviderApprovedArtwork.publicAddress(ip))
     throw new UnknownHostException("NON_PUBLIC_PROVIDER_IP");
    return addresses;
   })
   .connectTimeout(7,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS)
   .callTimeout(18,TimeUnit.SECONDS).followRedirects(false)
   .followSslRedirects(false).build();
 private ProviderImageUrlImport(){}
 static void add(Context app,String name,String country,String channelId,String address)throws Exception{
  if(!ProviderApprovedArtwork.safeUrl(address))
   throw new IOException("ONLY_PUBLIC_HTTPS_IMAGE_URLS");
  File tmp=File.createTempFile("rx6-brand-",".image",app.getCacheDir());
  try {
   Request req=new Request.Builder().url(address).get()
     .header("Accept","image/png,image/jpeg,image/webp").build();
   try(Response response=HTTP.newCall(req).execute()){
    if(response.code()!=200||response.body()==null)
     throw new IOException("IMAGE_HTTP_"+response.code());
    String type=response.header("Content-Type","");
    if(!type.isEmpty()&&!type.toLowerCase(Locale.ROOT).startsWith("image/"))
     throw new IOException("NOT_AN_IMAGE");
    if(response.body().contentLength()>LIMIT)throw new IOException("IMAGE_TOO_LARGE");
    try(InputStream in=response.body().byteStream();
        FileOutputStream out=new FileOutputStream(tmp)){
     byte[] data=new byte[8192];int n,total=0;
     while((n=in.read(data))!=-1){
      if((total+=n)>LIMIT)throw new IOException("IMAGE_TOO_LARGE");
      out.write(data,0,n);
     }
    }
   }
   ProviderLogoOverrides.add(app,name,country,channelId,Uri.fromFile(tmp));
  }finally{tmp.delete();}
 }
}
