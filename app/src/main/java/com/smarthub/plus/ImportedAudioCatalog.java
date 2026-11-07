package com.smarthub.plus;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Private Fox/Orange-style ipaudio.cfg list. Never log or commit stream URLs:
 * URL query strings can contain personal subscription bearer credentials.
 */
final class ImportedAudioCatalog {
 static final class Entry {
  final String label,url;
  Entry(String label,String url){this.label=label;this.url=url;}
 }
 private static final String ALIAS="iraqhub_imported_audio_v1";
 private static final int MAX_FILE=2*1024*1024,MAX_ROWS=512;
 private ImportedAudioCatalog(){}
 private static File location(Context c){return new File(c.getFilesDir(),"audio-catalog.sealed");}
 private static SecretKey key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ALIAS)){
   KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   gen.generateKey();
  }
  return (SecretKey)ks.getKey(ALIAS,null);
 }
 static List<Entry> parse(String text)throws IOException{
  if(text==null||text.length()>MAX_FILE)throw new IOException("CFG_TOO_LARGE");
  LinkedHashMap<String,Entry> items=new LinkedHashMap<>();
  for(String line:text.split("\\r?\\n")){
   line=line.trim();
   if(line.isEmpty()||line.startsWith("#")||line.startsWith(";"))continue;
   String[] parts=line.split("\\|",3);
   if(parts.length!=3||!parts[0].trim().matches("[0-9]{1,3}"))continue;
   String label=parts[1].trim(),url=parts[2].trim();
   if(label.isEmpty()||label.length()>100||url.length()>4096)continue;
   try{
    URI uri=new URI(url);
    if(uri.getHost()==null||!(uri.getScheme().equalsIgnoreCase("https")||
      uri.getScheme().equalsIgnoreCase("http"))||uri.getUserInfo()!=null)continue;
   }catch(Exception invalid){continue;}
   if(items.size()>=MAX_ROWS)break;
   items.putIfAbsent(label+"\u0000"+url,new Entry(label,url));
  }
  if(items.isEmpty())throw new IOException("CFG_NO_AUDIO_STREAMS");
  return new ArrayList<>(items.values());
 }
 static synchronized int importFrom(Context c,Uri uri)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(InputStream in=c.getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("CFG_NOT_ACCESSIBLE");
   byte[] chunk=new byte[8192];int count;
   while((count=in.read(chunk))!=-1){
    if(bytes.size()+count>MAX_FILE)throw new IOException("CFG_TOO_LARGE");
    bytes.write(chunk,0,count);
   }
  }
  List<Entry> items=parse(bytes.toString("UTF-8"));
  JSONArray arr=new JSONArray();
  for(Entry entry:items)arr.put(new JSONObject().put("name",entry.label).put("url",entry.url));
  byte[] plain=arr.toString().getBytes(StandardCharsets.UTF_8);
  try{
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,key());
   byte[] encrypted=cipher.doFinal(plain);
   AtomicFile file=new AtomicFile(location(c));FileOutputStream out=file.startWrite();
   try{
    out.write(1);out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);
   }catch(Exception e){file.failWrite(out);throw e;}
  }finally{Arrays.fill(plain,(byte)0);}
  return items.size();
 }
 static synchronized List<Entry> read(Context c){
  try{
   byte[] all=new AtomicFile(location(c)).readFully();
   if(all.length<30||all.length>MAX_FILE+4096||all[0]!=1)return Collections.emptyList();
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(all,1,13)));
   byte[] plain=cipher.doFinal(all,13,all.length-13);
   try{
    JSONArray arr=new JSONArray(new String(plain,StandardCharsets.UTF_8));
    ArrayList<Entry> list=new ArrayList<>();
    for(int i=0;i<Math.min(MAX_ROWS,arr.length());i++){
     JSONObject row=arr.optJSONObject(i);
     if(row!=null)list.add(new Entry(row.optString("name"),row.optString("url")));
    }
    return list;
   }finally{Arrays.fill(plain,(byte)0);}
  }catch(Exception absent){return Collections.emptyList();}
 }
 static synchronized void clear(Context c){new AtomicFile(location(c)).delete();}
}
