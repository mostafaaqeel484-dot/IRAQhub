package com.smarthub.plus;
import android.content.Context;
import android.security.keystore.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
/** User credentials live only in an authenticated AES-GCM file sealed by Android Keystore. */
final class ProviderSecrets {
 final String opensubtitles,subsource,tmdb;
 ProviderSecrets(String os,String sub)throws IOException{this(os,sub,"");}
 ProviderSecrets(String os,String sub,String tmdb)throws IOException{opensubtitles=clean(os);subsource=clean(sub);this.tmdb=clean(tmdb);}
 private static String clean(String value)throws IOException{String s=value==null?"":value.trim();if(s.length()>4096||s.contains("\r")||s.contains("\n"))throw new IOException("Invalid credential format");return s;}
 private static javax.crypto.SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(!s.containsAlias("smarthub_provider_keys")){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("smarthub_provider_keys",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (javax.crypto.SecretKey)s.getKey("smarthub_provider_keys",null);}
 static ProviderSecrets read(Context c)throws Exception{File file=new File(c.getFilesDir(),"providers.sealed");if(!file.isFile())return new ProviderSecrets("","");byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());if(bytes.length<30||bytes[0]!=12)throw new IOException("Invalid encrypted provider settings");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(bytes,1,13)));JSONObject j=new JSONObject(new String(cipher.doFinal(java.util.Arrays.copyOfRange(bytes,13,bytes.length)),StandardCharsets.UTF_8));return new ProviderSecrets(j.optString("opensubtitles"),j.optString("subsource"),j.optString("tmdb"));}
 void save(Context c)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] bytes=new JSONObject().put("opensubtitles",opensubtitles).put("subsource",subsource).put("tmdb",tmdb).toString().getBytes(StandardCharsets.UTF_8);android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getFilesDir(),"providers.sealed"));FileOutputStream out=file.startWrite();try{out.write(12);out.write(cipher.getIV());out.write(cipher.doFinal(bytes));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}clearLegacy(c);c.getSharedPreferences("smarthub",0).edit().putLong("providers_changed_at",System.currentTimeMillis()).putLong("epg_art_changed_at",System.currentTimeMillis()).apply();}
 static boolean configured(Context c){try{ProviderSecrets s=read(c);return !s.opensubtitles.isEmpty()||!s.subsource.isEmpty();}catch(Exception e){return false;}}
 static void clearLegacy(Context c){c.getSharedPreferences("smarthub",0).edit().remove("opensubtitles_api_key").remove("opensubtitles_token").remove("opensubtitles_base_url").remove("translation_key").apply();}
}
