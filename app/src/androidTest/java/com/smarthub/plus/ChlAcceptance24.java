package com.smarthub.plus;
import android.app.Instrumentation;import android.content.Context;import android.os.Bundle;import android.graphics.Bitmap;import android.util.Base64;
import org.json.JSONObject;import java.io.*;import java.security.KeyStore;import javax.crypto.*;import javax.crypto.spec.*;import java.util.*;import java.util.concurrent.*;
/** The user's export is pushed to app cache for this test; it is never an APK asset. */
final class ChlAcceptance24 {
 static void profile(Context c,Bundle args,Bundle result)throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="smarthub_test_migration";
  Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");rsa.init(Cipher.DECRYPT_MODE,store.getKey(alias,null));byte[] key=rsa.doFinal(Base64.decode(args.getString("key"),Base64.NO_WRAP));
  Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.decode(args.getString("iv"),Base64.NO_WRAP)));byte[] plain=aes.doFinal(Base64.decode(args.getString("payload"),Base64.NO_WRAP));
  JSONObject json=new JSONObject(new String(plain,java.nio.charset.StandardCharsets.UTF_8));Arrays.fill(plain,(byte)0);Arrays.fill(key,(byte)0);String profile=json.getString("chmaxProfile");if(profile.length()<8||profile.length()>24)throw new IOException("Invalid profile");
  JSONObject saved=FactoryVault.read(c);saved.put("chmaxProfile",profile);FactoryVault.save(c,saved);store.deleteEntry(alias);result.putBoolean("encrypted_profile_saved",true);
 }
 static void run(Instrumentation i,Context c,Bundle out)throws Exception{
  ChlLogoAddon addon=ChlLogoAddon.get(c);ChlChannelIndex index;
  File input=new File(c.getCacheDir(),"import24.chl");try(Reader reader=new InputStreamReader(new FileInputStream(input),java.nio.charset.StandardCharsets.UTF_8)){index=addon.importFile(reader);}finally{input.delete();}
  out.putInt("channels",index.getServices().size());out.putInt("satellites",index.getSatellites());out.putInt("transponders",index.getTransponders());int providers=0;for(ChlChannelIndex.Service s:index.getServices())if(!s.getProvider().isEmpty())providers++;out.putInt("with_provider",providers);
  if(index.getServices().size()!=10644||providers!=2161)throw new IOException("Unexpected real-file counts");
  ChlChannelIndex.Service picked=addon.choice("MBC2");out.putBoolean("mbc2_duplicate_choice_valid",picked!=null&&index.candidates("MBC2").contains(picked));out.putBoolean("mbc2_duplicate_choice_stable",picked!=null&&picked.equals(addon.choice("MBC2")));out.putString("mbc2_provider",index.provider("MBC2"));
  ChlChannelIndex.Service love=index.unique("Love Nature 4K");if(love==null||love.getSid()!=2229)throw new IOException("Physical identity mismatch");out.putString("love_provider",love.getProvider());
  byte[] before=java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),"chl-logo-index.bin").toPath());boolean rejected=false;try{addon.importFile(new StringReader("{broken"));}catch(Exception expected){rejected=true;}if(!rejected)throw new IOException("Corrupt import accepted");out.putBoolean("failed_import_keeps_index",Arrays.equals(before,java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),"chl-logo-index.bin").toPath())));
  ChannelAliasDatabase db=new ChannelAliasDatabase(c);db.load();ChannelLogoResolver loader=new ChannelLogoResolver(c,db);Bitmap live=addon.resolve(love,loader);if(live==null)throw new IOException("CHMAX image unavailable");out.putString("chmax_real_bitmap",live.getWidth()+"x"+live.getHeight());live.recycle();Bitmap duplicate=addon.resolve("MBC2",loader);out.putBoolean("duplicate_chmax_bitmap",duplicate!=null);if(duplicate!=null)duplicate.recycle();
  addon.enable(true);CountDownLatch ready=new CountDownLatch(1);final Bitmap[] logo={null};i.runOnMainSync(()->LogoRepository.get(c).request("Love Nature 4K",new LogoRepository.Listener(){public void channel(Bitmap b){logo[0]=b;ready.countDown();}public void provider(String n,Bitmap b){}}));if(!ready.await(60,TimeUnit.SECONDS)||logo[0]==null)throw new IOException("Repository image not available");out.putBoolean("repository_chl_logo",true);
  final boolean[] instant={false};final long[] us={0};i.runOnMainSync(()->{long start=System.nanoTime();LogoRepository.get(c).request("Love Nature 4K",new LogoRepository.Listener(){public void channel(Bitmap b){instant[0]=b!=null;}public void provider(String n,Bitmap b){}});us[0]=(System.nanoTime()-start)/1000;});out.putBoolean("chl_cache_immediate",instant[0]);out.putLong("chl_warm_us",us[0]);out.putString("chmax_status",c.getSharedPreferences("smarthub",0).getString("chl_chmax_status",""));
 }
}
