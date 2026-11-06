package com.smarthub.plus;
import android.app.*;
import android.content.*;
import java.util.*;

/** Configuration remains in the firmware which owns CA and BISS. */
final class ChannelControls {
 static void open(Activity c,ReceiverCatalog.Channel ch){
  boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  List<String> ids=new ArrayList<>(),labels=new ArrayList<>();
  String[] routes={"native_tv_controls","server","ca","patch"};
  String[] names=ar?new String[]{"فتح التلفاز الأصلي واختيار هذه القناة","إعدادات السيرفر المركزية","حالة CA والتخويل من النظام","إدارة BISS / Patch الأصلية"}:new String[]{"Open firmware TV and select this channel","Central Server Settings","Firmware CA / authorization status","Firmware BISS / Patch management"};
  for(int n=0;n<routes.length;n++){
   ManufacturerFeatures.Entry e=ManufacturerFeatures.find(c,routes[n]);
   if(e!=null&&ManufacturerFeatures.unavailable(c,e).isEmpty()){ids.add(routes[n]);labels.add(names[n]);}
  }
  if(ids.isEmpty()){
   new AlertDialog.Builder(c).setTitle(ch.name).setMessage(ar?"لا توجد واجهة مصنع عامة متاحة لهذه الوظيفة.":"No public firmware interface is available for this function.").setPositiveButton("OK",null).show();return;
  }
  String detail=ch.name+" · SID "+ch.sid+"\n"+ch.rf()+"\n"+(ar?"التشغيل يستخدم تخويل Firmware المحفوظ. افتح هنا إعدادات السيرفر أو CA وBISS؛ تبقى الحسابات والمفاتيح داخل النظام.":"Playback uses the receiver’s saved firmware authorization. Manage central Server Settings, CA and BISS here; accounts and keys stay in the system.");
  new AlertDialog.Builder(c).setTitle(ar?"خدمات القناة في الرسيفر":"Receiver channel services").setMessage(detail).setPositiveButton(ar?"المتابعة":"Continue",(d,w)->ReceiverPanelStyle.choose(c,ch.name,labels.toArray(new String[0]),0,index->{
   ManufacturerFeatures.Entry e=ManufacturerFeatures.find(c,ids.get(index));
   if(!ManufacturerFeatures.open(c,e))android.widget.Toast.makeText(c,ar?"تعذر فتح واجهة المصنع":"Firmware interface unavailable",android.widget.Toast.LENGTH_LONG).show();
  })).setNegativeButton(ar?"رجوع":"Back",null).show();
 }
 /** Opens native Patch > BISS editor; secrets are entered and stored only by firmware. */
 static boolean openBiss(Activity c){
  ManufacturerFeatures.Entry entry=ManufacturerFeatures.find(c,"patch");
  return entry!=null&&ManufacturerFeatures.open(c,entry);
 }
 static void motor(Activity c){
  ManufacturerFeatures.Entry entry=ManufacturerFeatures.find(c,"motor");boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  if(entry==null||!ManufacturerFeatures.open(c,entry))android.widget.Toast.makeText(c,ar?"تحكم الموتور غير متاح عبر واجهة المصنع":"Firmware motor controls unavailable",android.widget.Toast.LENGTH_LONG).show();
 }
}
