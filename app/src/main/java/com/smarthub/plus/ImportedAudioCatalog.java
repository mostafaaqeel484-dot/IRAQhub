package com.smarthub.plus;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Private, encrypted Fox/Orange CFG catalog. Stream credentials are never logged or committed. */
final class ImportedAudioCatalog {
 static final class Track {
  final String name,url;
  Track(String name,String url){this.name=name;this.url=url;}
 }
 private static final int MAX_BYTES=1024*1024,MAX_TRACKS=1500;
 private static final String ALIAS="iraqhub_cfg_catalog_v1";
 private static final byte[] AAD="IRAQhub:private:audio-cfg:v1".getBytes(StandardCharsets.UTF_8);
 private ImportedAudioCatalog(){}

 static List<Track> parse(InputStream stream)throws Exception {
  if(stream==null)throw new IOException("CFG_MISSING");
  BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
  LinkedHashMap<String,Track> unique=new LinkedHashMap<>();
  String line;int consumed=0;
  while((line=reader.readLine())!=null){
   consumed+=line.length()+2;
   if(consumed>MAX_BYTES)throw new IOException("CFG_TOO_LARGE");
   line=line.trim();
   if(line.isEmpty()||line.startsWith("#"))continue;
   String[] parts=line.split("\\|",3);
   if(parts.length!=3||!parts[0].trim().matches("[0-9]{1,5}"))continue;
   String label=parts[1].trim(),url=parts[2].trim();
   if(label.isEmpty()||label.length()>120||label.chars().anyMatch(ch->ch<32))continue;
   URI uri;try{uri=new URI(url);}catch(Exception bad){continue;}
   if(uri.getHost()==null||uri.getUserInfo()!=null||
    !("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||
    url.length()>4096)continue;
   unique.putIfAbsent(label.toLowerCase(Locale.ROOT)+"|"+url,new Track(label,url));
   if(unique.size()>MAX_TRACKS)throw new IOException("CFG_TOO_MANY_TRACKS");
  }
  if(unique.isEmpty())throw new IOException("CFG_NO_VALID_TRACKS");
  return new ArrayList<>(unique.values());
 }
 private static File file(Context c){return new File(c.getFilesDir(),"imported-audio-cfg.sealed");}
 private static SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){
   KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
   generator.init(new KeyGenParameterSpec.Builder(ALIAS,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
     .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   generator.generateKey();
  }
  return (SecretKey)store.getKey(ALIAS,null);
 }
 static synchronized void save(Context c,List<Track> tracks)throws Exception{
  if(tracks==null||tracks.isEmpty()||tracks.size()>MAX_TRACKS)throw new IOException("CFG_INVALID_TRACK_COUNT");
  JSONArray rows=new JSONArray();
  for(Track track:tracks){
   if(track.name.length()>120||track.name.isEmpty())throw new IOException("CFG_INVALID_TRACK");
   new IPAudioBinding(track.url,0,80,false);
   rows.put(new JSONObject().put("name",track.name).put("url",track.url));
  }
  byte[] plain=rows.toString().getBytes(StandardCharsets.UTF_8);
  if(plain.length>2*MAX_BYTES)throw new IOException("CFG_TOO_LARGE");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(AAD);
  byte[] encrypted;
  try{encrypted=cipher.doFinal(plain);}finally{Arrays.fill(plain,(byte)0);}
  AtomicFile destination=new AtomicFile(file(c));FileOutputStream out=destination.startWrite();
  try{out.write(1);out.write(cipher.getIV());out.write(encrypted);destination.finishWrite(out);}
  catch(Exception problem){destination.failWrite(out);throw problem;}
 }
 static synchronized List<Track> load(Context c){
  if(!file(c).isFile())return Collections.emptyList();
  try{
   byte[] stored=new AtomicFile(file(c)).readFully();
   if(stored.length<30||stored.length>3*MAX_BYTES||stored[0]!=1)return Collections.emptyList();
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(stored,1,13)));
   cipher.updateAAD(AAD);
   byte[] plain=cipher.doFinal(stored,13,stored.length-13);
   try{
    JSONArray data=new JSONArray(new String(plain,StandardCharsets.UTF_8));
    if(data.length()>MAX_TRACKS)return Collections.emptyList();
    List<Track> out=new ArrayList<>();
    for(int i=0;i<data.length();i++){
     JSONObject row=data.getJSONObject(i);
     String label=row.getString("name"),url=row.getString("url");
     new IPAudioBinding(url,0,80,false);
     if(label.length()>120||label.isEmpty())return Collections.emptyList();
     out.add(new Track(label,url));
    }
    return out;
   }finally{Arrays.fill(plain,(byte)0);}
  }catch(Exception unavailable){return Collections.emptyList();}
 }
 static synchronized void delete(Context c){new AtomicFile(file(c)).delete();}
}
