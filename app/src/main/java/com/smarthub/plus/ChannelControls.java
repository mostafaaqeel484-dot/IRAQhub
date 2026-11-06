package com.smarthub.plus;

import android.app.Activity;
import android.app.AlertDialog;

/** Channel service status only. Never switches to the OEM launcher. */
final class ChannelControls {
 private ChannelControls(){}

 static void open(Activity c,ReceiverCatalog.Channel ch){
  boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  String detail=ch.name+" · SID "+ch.sid+"\n"+ch.rf()+"\n\n"+
   (ar
     ?"القنوات الفضائية المجانية وإعداداتها المحلية تعمل من IRAQhub. إدارة CA/BISS في روم Aurum ليست API متاحة لهذا التطبيق حالياً؛ لذلك لا يتم فتح اللانشر الأصلي أو إدخال مفاتيح وهمية."
     :"FTA satellite playback and its local settings are IRAQhub-owned. The OEM CA/BISS editor has no exposed API for this app, so no original launcher or fake key entry is opened.");
  new AlertDialog.Builder(c)
   .setTitle(ar?"خدمات القناة داخل IRAQhub":"IRAQhub channel services")
   .setMessage(detail).setPositiveButton(ar?"موافق":"OK",null).show();
 }

 static boolean openBiss(Activity c){
  boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  new AlertDialog.Builder(c)
   .setTitle("BISS")
   .setMessage(ar?"لا توجد API مصرح بها لإرسال مفاتيح BISS إلى خدمة CA الخاصة بالروم. لم يتم نقل محرر BISS إلى IRAQhub، ولن تُفتح Aurum تلقائياً."
      :"No authorised API to submit BISS keys to the firmware CA service. Native integration is not implemented, and Aurum will not launch.")
   .setPositiveButton(ar?"موافق":"OK",null).show();
  return false;
 }

 static void motor(Activity c){
  boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  new AlertDialog.Builder(c)
   .setTitle("DiSEqC / USALS")
   .setMessage(ar?"إعدادات LNB المحلية موجودة، لكن أوامر تحريك الموتور وحدود الحركة تحتاج بروتوكول المصنع. لا نرسل أمراً خطيراً ولا نفتح Aurum."
      :"Local LNB profiles exist, but motor motion and travel limits require the OEM protocol. No motor command or Aurum launch is attempted.")
   .setPositiveButton(ar?"موافق":"OK",null).show();
 }
}
