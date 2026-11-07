package com.smarthub.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * USB ADB user onboarding. This is a SETTINGS SHORTCUT, not a way to enable
 * debugging, approve a host, obtain root or inherit adb shell privileges.
 * Never starts a network ADB server or modifies protected global settings.
 */
final class UsbAdbSetup {
 static final String STARTUP_PROMPT="usb_adb_prompt_on_launcher_start";
 private UsbAdbSetup(){}

 static boolean enabled(Context context){
  try{
   return Settings.Global.getInt(context.getContentResolver(),
      Settings.Global.ADB_ENABLED,0)==1;
  }catch(Exception unavailable){return false;}
 }
 /** Kept pure so this policy can be checked by JVM unit tests. */
 static boolean shouldPrompt(boolean adbEnabled,boolean configured,boolean alreadyShown){
  return configured&&!adbEnabled&&!alreadyShown;
 }
 static boolean promptEnabled(Context context){
  return context.getSharedPreferences("smarthub",Context.MODE_PRIVATE)
    .getBoolean(STARTUP_PROMPT,true);
 }
 static void setPromptEnabled(Context context,boolean value){
  context.getSharedPreferences("smarthub",Context.MODE_PRIVATE).edit()
   .putBoolean(STARTUP_PROMPT,value).apply();
 }
 static String status(Activity activity){
  boolean ar=activity.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  if(enabled(activity))return ar?
   "تصحيح ADB مفعّل بالنظام · اتصال الكمبيوتر وصلاحياته غير مؤكدة":
   "System ADB enabled · USB host connection and authorisation NOT verified";
  return ar?"تصحيح USB/ADB غير مفعّل بالنظام":"USB/ADB debugging is disabled in Android";
 }
 static void show(Activity host){
  boolean ar=host.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  boolean active=enabled(host);
  String message=active?
    (ar?"ADB مفعّل. اربط الكمبيوتر بكيبل USB ووافق على طلب الثقة من شاشة الرسيفر. هذا لا يعطي IRAQhub صلاحية root أو صلاحية فتح ملفات التطبيقات الأخرى.":
      "ADB is enabled. Connect a trusted computer by USB and approve its debugging key on the receiver. This does not grant IRAQhub root or access to other apps' private files."):
    (ar?"يمكنك تفعيل USB debugging بنفسك من خيارات المطوّر. بعد الرجوع إلى IRAQhub يظهر لك وضعه الجديد. إذا خيارات المطوّر مخفية، افتح معلومات الجهاز واضغط رقم الإصدار 7 مرات. ADB لا يلغي قيود صلاحيات التطبيقات الأخرى ولا يضمن جلب شعارات Aurum.":
      "Open Developer options to enable USB debugging yourself. Returning to IRAQhub updates the status. If Developer options are hidden, tap Build number seven times in About device. ADB does not bypass other apps' private-data permissions or guarantee Aurum logos.");
  AlertDialog dialog=new AlertDialog.Builder(host)
    .setTitle(ar?"USB ADB · أدوات المطوّر":"USB ADB · Developer setup")
    .setMessage(status(host)+"\n\n"+message)
    .setPositiveButton(ar?"فتح خيارات المطوّر":"Open Developer options",(d,which)->openDeveloperSettings(host))
    .setNegativeButton(ar?"المتابعة إلى اللانشر":"Continue to launcher",(d,which)->d.dismiss())
    .setNeutralButton(ar?"لا تظهر التنبيه عند البدء":"Don't show at startup",(d,which)->setPromptEnabled(host,false))
    .create();
  dialog.show();
 }
 static void openDeveloperSettings(Activity activity){
  Intent intent=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
  try{
   activity.startActivity(intent);
  }catch(Exception unavailable){
   try{activity.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));}
   catch(Exception noDeviceInfo){
    try{activity.startActivity(new Intent(Settings.ACTION_SETTINGS));}
    catch(Exception noSettings){
     boolean ar=activity.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
     android.widget.Toast.makeText(activity,ar?
       "إعدادات النظام غير متاحة في هذه الروم":"System Settings unavailable on this firmware",
       android.widget.Toast.LENGTH_LONG).show();
    }
   }
  }
 }
}
