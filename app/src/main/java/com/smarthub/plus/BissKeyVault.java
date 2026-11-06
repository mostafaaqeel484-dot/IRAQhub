package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-local BISS-1 key storage, scoped to an exact DVB channel, never logged or sent to OEM. */
final class BissKeyVault {
 private static final String ALIAS="iraqhub_biss_keys_v1";
 private BissKeyVault(){}
 static String normalize(String raw){
  if(raw==null)return "";
  String code=raw.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s:\\-]","");
  if(!(code.length()==12||code.length()==16)||!code.matches("[0-9A-F]+"))
   throw new IllegalArgumentException("BISS_1_EXPECTED_12_OR_16_HEX");
  return code;
 }
 private static String identity(ReceiverCatalog.Channel channel){
  if(channel==null||channel.sid<0||channel.sid>65535)throw new IllegalArgumentException("DVB_CHANNEL_REQUIRED");
  return channel.physical();
 }
 private static String prefKey(ReceiverCatalog.Channel channel)throws Exception{
  byte[] hash=java.security.MessageDigest.getInstance("SHA-256")
    .digest(identity(channel).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  StringBuilder result=new StringBuilder("biss_key_");
  for(byte b:hash)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return result.toString();
 }
 private static SecretKey cipherKey()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  KeyStore.Entry existing=store.getEntry(ALIAS,null);
  if(existing instanceof KeyStore.SecretKeyEntry)
   return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,
     KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
     .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
     .setKeySize(256).build());
  return generator.generateKey();
 }
 private static SharedPreferences prefs(Context c){
  return c.getApplicationContext().getSharedPreferences("iraqhub_biss_private",Context.MODE_PRIVATE);
 }
 static synchronized void save(Context context,ReceiverCatalog.Channel channel,String raw)throws Exception{
  String key=normalize(raw);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,cipherKey());
  cipher.updateAAD(identity(channel).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  byte[] crypt=cipher.doFinal(key.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  byte[] iv=cipher.getIV();
  if(iv.length!=12)throw new java.io.IOException("BISS_KEYSTORE_NONSTANDARD_IV");
  prefs(context).edit().putString(prefKey(channel),
    Base64.encodeToString(iv,Base64.NO_WRAP)+":"+
    Base64.encodeToString(crypt,Base64.NO_WRAP)).commit();
 }
 static boolean has(Context c,ReceiverCatalog.Channel channel){
  try{return !prefs(c).getString(prefKey(channel),"").isEmpty();}
  catch(Exception unavailable){return false;}
 }
 static void remove(Context c,ReceiverCatalog.Channel channel)throws Exception{
  prefs(c).edit().remove(prefKey(channel)).commit();
 }
}
