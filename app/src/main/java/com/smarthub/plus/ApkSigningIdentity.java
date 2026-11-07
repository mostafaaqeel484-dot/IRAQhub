package com.smarthub.plus;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

/** Show the installed certificate SHA-256 fingerprint before any APK update. */
final class ApkSigningIdentity {
 private ApkSigningIdentity(){}
 static String installedFingerprint(Context context){
  try{
   PackageManager manager=context.getPackageManager();
   Signature[] certificates;
   if(Build.VERSION.SDK_INT>=28){
    PackageInfo packageInfo=manager.getPackageInfo(context.getPackageName(),
      PackageManager.GET_SIGNING_CERTIFICATES);
    if(packageInfo.signingInfo==null)return "SIGNING_INFO_UNAVAILABLE";
    certificates=packageInfo.signingInfo.getApkContentsSigners();
   }else{
    PackageInfo packageInfo=manager.getPackageInfo(context.getPackageName(),
      PackageManager.GET_SIGNATURES);
    certificates=packageInfo.signatures;
   }
   if(certificates==null||certificates.length!=1)return "SIGNERS_NOT_SINGLE";
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(certificates[0].toByteArray());
   StringBuilder builder=new StringBuilder();
   for(byte value:hash)builder.append(String.format(Locale.ROOT,"%02X",value&0xff));
   return builder.toString();
  }catch(Exception failure){return "SIGNING_CHECK_UNAVAILABLE";}
 }
}
