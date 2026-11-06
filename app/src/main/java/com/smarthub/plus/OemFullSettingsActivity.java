package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

import java.util.Locale;

/**
 * Strict IRAQhub-only feature coverage: never launch Aurum.
 * Only genuine application-owned capabilities open as actions.
 * OEM-only actions explain the missing SDK instead of offering fake shortcuts.
 */
public final class OemFullSettingsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("وظائف الرسيفر داخل IRAQhub","Receiver features inside IRAQhub"),
    tx("هذه القائمة لا تفتح Aurum. الوظائف التي لا يملك IRAQhub تنفيذها تظهر بوضوح كغير مكتملة.",
      "This page never opens Aurum. Features without native IRAQhub implementation are clearly marked incomplete."));
  LinearLayout status=card(body);
  status.addView(text(tx("الشرط: التشغيل داخل IRAQhub فقط","Rule: IRAQhub-only execution"),19,true));
  note(status,tx("ملفات Aurum المفحوصة لا تكشف حتى الآن واجهات عامة كاملة للبحث والتسجيل وتعديل قاعدة قنوات المصنع. وجود Fragment داخل Aurum مو واجهة SDK قابلة للاستدعاء.",
      "The inspected Aurum APK doesn't expose a complete public scan/PVR/OEM database API. An internal Fragment is not a usable SDK."));
  group(body,"SCAN",tx("البحث الفضائي والأرضي والكابل","Satellite, terrestrial and cable scanning"));
  group(body,"DISH",tx("الأقمار والـLNB والموتور","Dish, LNB and motor"));
  group(body,"CHANNEL",tx("إدارة القنوات","Channel management"));
  group(body,"CLOUD",tx("CHMAX","CHMAX"));
  group(body,"RECORD",tx("PVR وTimeShift","PVR and TimeShift"));
  group(body,"TV",tx("المشاهدة والمعلومات والريموت","Playback, OSD and remote"));
  group(body,"OTHER",tx("الخدمات الأخرى","Other services"));
 }

 private void group(LinearLayout body,String family,String title){
  LinearLayout section=card(body);
  section.addView(text(title,20,true));
  for(OemFeatureMenuIndex.Item item:OemFeatureMenuIndex.all()){
   if(!item.group.equals(family))continue;
   boolean nativeReady=isLocal(item.route);
   String prefix=nativeReady?tx("✓ داخل IRAQhub: ","✓ In IRAQhub: "):
      tx("✕ غير منقول: ","✕ Not implemented: ");
   action(section,prefix+item.label(ar),()->{
    if(nativeReady)openLocal(item.route);
    else explainMissing(item);
   });
  }
 }

 private static boolean isLocal(String route){
  switch(route){
   case "local_lnb":case "local_sats":case "local_channels":
   case "local_signal":case "local_chmax":case "local_logos":
   case "osd":case "remote":return true;
   default:return false;
  }
 }

 private void openLocal(String route){
  Class<?> target=null;
  switch(route){
   case "local_lnb":target=TunerSettingsActivity.class;break;
   case "local_sats":target=SatelliteBrowserActivity.class;break;
   case "local_channels":target=ReceiverChannelsActivity.class;break;
   case "local_signal":target=SignalInformationActivity.class;break;
   case "local_chmax":target=ChmaxControlCenterActivity.class;break;
   case "local_logos":target=LogoSourcesActivity.class;break;
   case "osd":target=OsdSettingsActivity.class;break;
   case "remote":target=RemoteKeyDiagnosticsActivity.class;break;
  }
  if(target==null)return;
  try{startActivity(new Intent(this,target));}
  catch(RuntimeException err){toast(tx("تعذر فتح صفحة IRAQhub","Could not open IRAQhub screen"));}
 }

 private void explainMissing(OemFeatureMenuIndex.Item item){
  String explanation;
  switch(item.group){
   case "SCAN":
    explanation=tx("ناقص بروتوكول البحث: أوامر التونر ومتابعة التقدم ونتائج البحث وحفظ الخدمات في قاعدة النظام. واجهة Aurum الداخلية لا تكفي.",
       "Missing scan protocol, progress events, service results and an authorised channel-database writer.");
    break;
   case "DISH":
    explanation=tx("ناقص ربط موثق لأوامر USALS/DiSEqC/Unicable وتعديل TP/PID والتحقق من حدود الموتور وصلاحياته.",
       "Missing authorised motor/Unicable, TP/PID control and limits validation.");
    break;
   case "CHANNEL":
    explanation=tx("قاعدة قنوات IRAQhub مستقلة؛ لا توجد كتابة موثقة ومصرح بها على قاعدة قنوات Aurum الأصلية.",
       "IRAQhub has its own channel catalogue but no authorised OEM database write API.");
    break;
   case "CLOUD":
    explanation=tx("ربط EPG والشعارات موجود محلياً، لكن حفظ/استعادة ومزامنة قوائم CHMAX في المصنع لها واجهات خاصة وحقوق وصول غير متاحة.",
       "Factory EPG/logos are partially integrated; OEM cloud-list synchronization needs private authorised APIs.");
    break;
   case "RECORD":
    explanation=tx("ناقص الوصول الموثق إلى جلسات PVR/TimeShift الأصلية، وإدارة USB والتسجيل والمؤقتات عبر واجهة نظام منشورة.",
       "Missing an accessible PVR/TimeShift recording and timer service API.");
    break;
   default:
    explanation=tx("هذه الوظيفة لم تنفذ مستقلة في IRAQhub؛ تتطلب SDK أو API من المصنع مع الصلاحيات المناسبة.",
       "No independent IRAQhub implementation; the OEM SDK and appropriate permissions are required.");
  }
  String proof=item.confirmed
    ?tx("ظهرت هذه الوظيفة في موارد Aurum، لكن هذا لا يعني أن تطبيقاً آخر يستطيع تنفيذها.",
        "This feature is present in OEM resources, not necessarily accessible from another app.")
    :tx("وجود هذه الوظيفة على هذا الطراز غير مثبت من البيانات المفحوصة.",
        "Support on this model was not confirmed in the inspected data.");
  new AlertDialog.Builder(this).setTitle(item.label(ar))
   .setMessage(explanation+"\n\n"+proof+"\n\n"+
      tx("لن يفتح IRAQhub تطبيق Aurum من هذه الصفحة.","IRAQhub will not open Aurum from this page."))
   .setPositiveButton(tx("فهمت","OK"),null).show();
 }
}
