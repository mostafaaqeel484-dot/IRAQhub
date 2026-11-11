package com.smarthub.plus;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import org.json.JSONObject;

/** Additional poster API credentials encrypted at rest with AndroidKeyStore.
 * Do not embed user keys in APK, SharedPreferences, exported manifests or logs. */
final class PosterApiSecrets {
 private static final String[] SOURCES={"opdb","rpdb","omdb","sportsdb"};
 private static final String ALIAS="iraqhub_poster_api_keys_v1";
 private PosterApiSecrets(){}
 static boolean supported(String s){return Arrays.asList(SOURCES).contains(s);}
 static boolean valid(String value){
  return value!=null && value.matches("[A-Za-z0-9_-]{3,128}");
 }
 private static SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){
   KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   generator.init(new KeyGenParameterSpec.Builder(ALIAS,
     KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
     .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   generator.generateKey();
  }
  return (SecretKey)store.getKey(ALIAS,null);
 }
 private static File path(Context c){return new File(c.getFilesDir(),"poster-apis.sealed");}
 private static synchronized JSONObject read(Context c)throws Exception{
  File f=path(c);if(!f.isFile())return new JSONObject();
  byte[] b=java.nio.file.Files.readAllBytes(f.toPath());
  if(b.length<30||b[0]!=1||b.length>8000)throw new IOException("INVALID_POSTER_SECRETS");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(b,1,13)));
  JSONObject j=new JSONObject(new String(cipher.doFinal(Arrays.copyOfRange(b,13,b.length)),
    StandardCharsets.UTF_8));
  return j;
 }
 static String get(Context c,String id){
  if(!supported(id))return "";
  try{return read(c).optString(id,"");}catch(Exception unavailable){return "";}
 }
 static boolean configured(Context c,String id){return !get(c,id).isEmpty();}
 static synchronized void update(Context c,Map<String,String> changes)throws Exception{
  JSONObject j=read(c);
  for(Map.Entry<String,String> entry:changes.entrySet()){
   if(!supported(entry.getKey()))throw new IOException("UNKNOWN_POSTER_PROVIDER");
   String value=entry.getValue();
   if(value==null||value.isEmpty())continue; // blank = keep old
   if(!valid(value))throw new IOException("INVALID_"+entry.getKey().toUpperCase(Locale.ROOT)+"_API_KEY");
   j.put(entry.getKey(),value);
  }
  save(c,j);
 }
 static synchronized void remove(Context c,String id)throws Exception{
  if(!supported(id))throw new IOException("UNKNOWN_POSTER_PROVIDER");
  JSONObject j=read(c);j.remove(id);save(c,j);
 }
 private static void save(Context c,JSONObject j)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] iv=cipher.getIV();
  if(iv.length!=12)throw new IOException("INVALID_CRYPTO_IV");
  byte[] body=cipher.doFinal(j.toString().getBytes(StandardCharsets.UTF_8));
  AtomicFile file=new AtomicFile(path(c));
  FileOutputStream output=file.startWrite();
  try{output.write(1);output.write(iv);output.write(body);file.finishWrite(output);}
  catch(Exception error){file.failWrite(output);throw error;}
  // Remove obsolete plaintext OPDB preference if present.
  c.getSharedPreferences("smarthub",0).edit()
    .remove("openposterdb_api_key")
    .putLong("poster_sources_changed_at",System.currentTimeMillis())
    .putLong("epg_art_changed_at",System.currentTimeMillis()).apply();
 }
}
