package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Button;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full receiver feature directory. This is a navigation layer only:
 * private OEM fragments, tuner writes, cloud storage and PVR stay in Aurum.
 */
public final class OemFullSettingsActivity extends HubActivity {
 private Map<String,ManufacturerFeatures.Entry> menus;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  List<ManufacturerFeatures.Entry> published=ManufacturerFeatures.read(this);
  menus=new HashMap<>();
  for(ManufacturerFeatures.Entry e:published)menus.put(e.id,e);
  LinearLayout body=page(tx("كل وظائف الرسيفر الأصلي","All original receiver functions"),
    tx("IRAQhub واجهتك الأساسية. الخيارات التي تتطلب صلاحيات الشركة تفتح قوائم Aurum المنشورة فقط.",
       "IRAQhub stays the launcher. OEM-privileged features open their original published menus."));

  LinearLayout warning=card(body);
  warning.addView(text(tx("لا تحذف Aurum ولا تبدأ بحثاً أو تحديثاً من دون موافقتك.",
     "Keep Aurum installed. No scan, motor move or firmware update starts automatically."),16,true));
  note(warning,tx("الخيار المتاح هنا يعني أن شاشة المصنع قابلة للفتح، مو إثبات نجاح التونر أو CHMAX أو التسجيل. الصفحات الداخلية غير مصدرة ولا يمكن استدعاؤها مباشرة.",
    "An accessible route is not proof of hardware, CHMAX or recording support. Internal OEM fragments are not exported."));
  addGroup(body,"SCAN",tx("البحث: سريع / يدوي / أعمى / DVB-T / DVB-C","Scanning and tuning"));
  addGroup(body,"DISH",tx("الأقمار وUSALS والموتور وPID","Satellite setup, USALS and PID"));
  addGroup(body,"CHANNEL",tx("إدارة القنوات والمفضلة وUSB","Channel editing and management"));
  addGroup(body,"CLOUD",tx("CHMAX والقوائم السحابية","CHMAX and cloud channel services"));
  addGroup(body,"RECORD",tx("PVR والتسجيل وTimeShift","Recording and TimeShift"));
  addGroup(body,"TV",tx("التلفاز وInfobar والريموت","Live TV, Infobar and remote"));
  addGroup(body,"OTHER",tx("خدمات المصنع والتحديثات","Manufacturer services and updates"));

  LinearLayout dynamic=card(body);
  dynamic.addView(text(tx("القوائم الأصلية المكتشفة من الروم","Original OEM menu entries discovered on this device"),20,true));
  note(dynamic,tx("المدخلات التالية تُقرأ من dvb_host_main_menu الخاص بإصدار Aurum المثبت على الجهاز. قد تختلف من روم لآخر.",
     "The following entries come from the installed firmware's dvb_host_main_menu and can differ between devices."));
  for(ManufacturerFeatures.Entry e:published){
   if(!e.id.startsWith("xml:"))continue;
   String reason=ManufacturerFeatures.unavailable(this,e);
   Button b=action(dynamic,e.title+(reason.isEmpty()?"":tx(" · غير متاح"," · unavailable")),()->
       new AlertDialog.Builder(this).setTitle(e.title)
         .setMessage(tx("سيتم فتح شاشة المصنع المعلنة فقط. لا تتابع أي تحديث للنظام أو تعديل للقنوات دون التحقق من الملف والنسخة الاحتياطية.",
           "Only the exported OEM screen opens. Verify backups and correct firmware before changing channel data or updating."))
         .setPositiveButton(tx("فتح","Open"),(d,w)->{
           if(!ManufacturerFeatures.open(this,e))toast(tx("تعذر فتح شاشة المصنع","Cannot open OEM screen"));
         })
         .setNegativeButton(tx("إلغاء","Cancel"),null).show());
   b.setEnabled(reason.isEmpty());
  }
  LinearLayout checks=card(body);
  action(checks,tx("تقرير صلاحيات Aurum والنظام","OEM routes and permissions report"),
     ()->startActivity(new Intent(this,OemDvbIntegrationActivity.class)));
 }

 private void addGroup(LinearLayout body,String group,String title){
  LinearLayout card=card(body);
  card.addView(text(title,22,true));
  for(OemFeatureMenuIndex.Item f:OemFeatureMenuIndex.all()){
   if(!group.equals(f.group))continue;
   String routeState=availability(f);
   Button b=action(card,f.label(ar)+(routeState.isEmpty()?"":"  ·  "+routeState),
       ()->explainAndOpen(f));
   if("private_pvr".equals(f.route)) b.setEnabled(true); // opens explanation, never internal activity
  }
 }

 private String availability(OemFeatureMenuIndex.Item item){
  if(item.route.startsWith("local_")||item.route.equals("osd")||
     item.route.equals("remote")||item.route.equals("android_settings"))return "";
  if(item.route.equals("private_pvr"))return tx("واجهة خاصة","Private");
  if(item.graph!=null&&!item.graph.isEmpty()&&item.fragment!=null&&!item.fragment.isEmpty()
      &&!ManufacturerFeatures.hasFragment(this,item.graph,item.fragment))
    return tx("غير مثبت في الروم","Not found in OEM graph");
  ManufacturerFeatures.Entry entry=menus.get(item.route);
  if(entry==null&&"xml:installation".equals(item.route))entry=menus.get("satellite");
  if(entry==null&&"xml:channel".equals(item.route))entry=menus.get("edit_tv");
  if(entry==null&&"xml:other".equals(item.route))entry=menus.get("server");
  if(entry!=null){
    String err=ManufacturerFeatures.unavailable(this,entry);
    return err.isEmpty()?"":tx("مسار المصنع غير متاح","OEM route unavailable");
  }
  switch(item.route){
   case "tv":case "epg":case "timer":case "net_update":case "file_update":
   case "all_apps":case "wallpaper":case "home": return "";
   default: return tx("غير متاح","Unavailable");
  }
 }

 private void explainAndOpen(OemFeatureMenuIndex.Item feature){
  StringBuilder message=new StringBuilder();
  if(!feature.confirmed)message.append(tx("وجود هذا الخيار في روم Aurum غير مؤكد؛ يتم فتح القائمة العامة فقط.\n\n",
      "The exact feature was not verified in OEM resources; only a parent menu will open.\n\n"));
  if(feature.graph!=null&&!feature.graph.isEmpty()&&!feature.fragment.isEmpty()
      &&!ManufacturerFeatures.hasFragment(this,feature.graph,feature.fragment))
    message.append(tx("الصفحة الداخلية غير موجودة في مخطط التنقل المنشور على الجهاز.\n\n",
        "This internal fragment was not found in the installed OEM graph.\n\n"));
  if(feature.route.startsWith("xml:"))
    message.append(tx("يُفتح القسم الأصلي في Aurum؛ اختر الوظيفة بنفسك من قوائم المصنع. ماكو تحكم مباشر بخدمات التونر من IRAQhub.",
        "This opens the original Aurum parent menu. Select the function there; IRAQhub does not send private tuner commands."));
  else if(feature.route.equals("private_pvr"))
    message.append(tx("واجهة مكتبة PVR في Aurum غير مصدرة. ما أگدر أفتحها من تطبيق مستقل. يمكن فتح TV الأصلي واستخدام وظائف التسجيل من الريموت حسب دعم الجهاز.",
        "Aurum's PVR library is not exported. Use native TV and its supported remote controls instead."));
  else if(feature.route.equals("tv")&&(feature.group.equals("RECORD")))
    message.append(tx("افتح تلفاز Aurum الأصلي، ثم استخدم زر REC أو PAUSE من الريموت. هذا الزر ما يبدأ تسجيل أو TimeShift بصورة مستقلة.",
        "Open native Aurum TV and use REC or PAUSE on your remote. This button does not start recording or TimeShift."));
  else message.append(tx("سيفتح المسار المتاح فقط. النتيجة النهائية تعتمد على الروم وصلاحياته.",
      "Only a published route will open. Device support and permissions still apply."));
  if(feature.writes)message.append(tx("\n\nتغيير بيانات الأقمار أو القنوات أو التحديثات قد يؤثر على الجهاز. لا تتابع إلا إذا عندك نسخة احتياطية مناسبة.",
       "\n\nChanges to tuning, channels or firmware may affect your receiver. Make a suitable backup first."));
  new AlertDialog.Builder(this).setTitle(feature.label(ar)).setMessage(message.toString())
   .setPositiveButton(tx("فتح الواجهة","Open interface"),(d,w)->open(feature))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }

 private void open(OemFeatureMenuIndex.Item feature){
  String route=feature.route;
  Intent local=null;
  switch(route){
    case "local_lnb":local=new Intent(this,TunerSettingsActivity.class);break;
    case "local_sats":local=new Intent(this,SatelliteBrowserActivity.class);break;
    case "local_channels":local=new Intent(this,ReceiverChannelsActivity.class);break;
    case "local_signal":local=new Intent(this,SignalInformationActivity.class);break;
    case "local_chmax":local=new Intent(this,ChmaxControlCenterActivity.class);break;
    case "local_logos":local=new Intent(this,LogoSourcesActivity.class);break;
    case "remote":local=new Intent(this,RemoteKeyDiagnosticsActivity.class);break;
    case "osd":local=new Intent(this,OsdSettingsActivity.class);break;
    case "android_settings":local=new Intent(Settings.ACTION_SETTINGS);break;
    case "private_pvr":
      if(!OemDvbFeatureGateway.open(this,OemDvbFeatureGateway.Feature.OEM_LIVE_TV))
        toast(tx("التلفاز الأصلي غير متاح","Native television unavailable"));
      return;
  }
  if(local!=null){
   try{startActivity(local);}catch(android.content.ActivityNotFoundException|SecurityException e){
    toast(tx("هذه الواجهة غير متاحة","This screen is unavailable"));}
   return;
  }
  OemDvbFeatureGateway.Feature special=null;
  switch(route){
   case "tv":special=OemDvbFeatureGateway.Feature.OEM_LIVE_TV;break;
   case "timer":special=OemDvbFeatureGateway.Feature.OEM_TIMERS;break;
   case "epg":special=OemDvbFeatureGateway.Feature.OEM_EPG;break;
   case "net_update":special=OemDvbFeatureGateway.Feature.OEM_NET_UPDATE;break;
   case "file_update":special=OemDvbFeatureGateway.Feature.OEM_FILE_UPDATE;break;
   case "all_apps":special=OemDvbFeatureGateway.Feature.OEM_ALL_APPS;break;
   case "wallpaper":special=OemDvbFeatureGateway.Feature.OEM_WALLPAPER;break;
   case "home":special=OemDvbFeatureGateway.Feature.OEM_HOME;break;
  }
  if(special!=null){
   if(!OemDvbFeatureGateway.open(this,special))
    toast(tx("رفض النظام فتح الوظيفة أو لم يجدها","OEM route unavailable or denied"));
   return;
  }
  ManufacturerFeatures.Entry entry=menus.get(route);
  if(entry==null&&route.equals("xml:installation"))entry=menus.get("satellite");
  if(entry==null&&route.equals("xml:channel"))entry=menus.get("edit_tv");
  if(entry==null&&route.equals("xml:other"))entry=menus.get("server");
  if(entry==null||!ManufacturerFeatures.open(this,entry))
    toast(tx("قائمة المصنع غير متاحة على هذا الروم","OEM menu unavailable in this firmware"));
 }
}
