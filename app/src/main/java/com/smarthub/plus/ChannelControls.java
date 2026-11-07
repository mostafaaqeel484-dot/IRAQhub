package com.smarthub.plus;
import android.app.*;
import android.content.*;

/** All channel actions remain inside IRAQhub. */
final class ChannelControls {
 private ChannelControls(){}
 static void open(Activity activity,ReceiverCatalog.Channel channel){
  boolean ar=activity.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  String[] options=ar?
   new String[]{"ملف LNB وتردد القمر","مركز الموتور وUSALS","مشغل LibVLC البرمجي 4:2:2","حالة CA/BISS"}:
   new String[]{"Satellite LNB profile","Motor / USALS centre","LibVLC software 4:2:2","CA / BISS status"};
  new AlertDialog.Builder(activity).setTitle(channel.name+" · "+channel.rf())
   .setItems(options,(dialog,which)->{
    Intent destination=null;
    switch(which){
     case 0:destination=new Intent(activity,TunerSettingsActivity.class)
       .putExtra("satellite",(long)channel.satId);break;
     case 1:destination=new Intent(activity,MotorCenterActivity.class)
       .putExtra("satellite",(long)channel.satId);break;
     case 2:destination=new Intent(activity,Software422CenterActivity.class);break;
     case 3:
      new AlertDialog.Builder(activity).setTitle("CA / BISS")
       .setMessage(ar?"لا توجد حالياً API موثقة لإدخال مفاتيح BISS مباشرة من IRAQhub إلى خدمة CA في الروم؛ لا نفتح Aurum ولا نحفظ المفاتيح."
         :"An authorised API for passing BISS keys to the firmware CA service is not available. IRAQhub never launches Aurum or stores keys.")
       .setPositiveButton("OK",null).show();return;
    }
    if(destination!=null)activity.startActivity(destination);
   }).setNegativeButton(ar?"إغلاق":"Close",null).show();
 }
 static void motor(Activity activity){
  activity.startActivity(new Intent(activity,MotorCenterActivity.class));
 }
}
