package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Advanced satellite diagnostics grouped separately from the four installation commands. */
public final class ReceiverDeveloperMenuActivity extends HubActivity {
 private TextView report;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("أدوات المطور","Developer tools"),
    tx("فحص العتاد وخدمات المصنع بدون تعديل ملفات الروم","Read-only firmware diagnostics and app-owned advanced tools"));
  ReceiverTileMenu.pair(this,body,
   new ReceiverTileMenu.Tile("01",tx("تشخيص 4:2:2","4:2:2 diagnostics"),
     tx("المشغل والإطارات وكاش TS","Software player, frames and TS cache"),
     ()->open(Software422CenterActivity.class)),
   new ReceiverTileMenu.Tile("02",tx("تحليل MPEG-TS","MPEG-TS inspection"),
     tx("قنوات من PAT/PMT/SDT","PAT, PMT and SDT service discovery"),
     ()->open(TsServiceImportActivity.class)));
  ReceiverTileMenu.pair(this,body,
   new ReceiverTileMenu.Tile("03",tx("ربط CHMAX وEPG","Factory EPG & CHMAX"),
     tx("فحص اتصال الخدمات والصور","Read-only service and artwork status"),
     ()->open(FactorySettingsActivity.class)),
   new ReceiverTileMenu.Tile("04",tx("مراقبة الإشارة","Signal monitoring"),
     tx("القفل وBER وSNR","Tuner lock, BER and SNR"),
     ()->open(SignalInformationActivity.class)));
  LinearLayout nativePanel=card(body);
  nativePanel.addView(text(tx("بحث المصنع الأصلي","Original Aurum search"),19,true));
  action(nativePanel,tx("فتح Aurum للبحث اليدوي والسريع والأعمى","Open Aurum manual / fast / blind scan"),
    this::openNative);
  CheckBox observe=check(nativePanel,
    tx("رصد نتيجة البحث الأصلية الظاهرة وحفظ عدد القنوات","Observe visible completed OEM scan results"),
    prefs().getBoolean("native_scan_observation_enabled",false));
  observe.setOnCheckedChangeListener((v,on)->prefs().edit()
    .putBoolean("native_scan_observation_enabled",on).apply());
  report=text("",14,false);nativePanel.addView(report);
  note(nativePanel,tx("الرصد يحتاج تفعيل IRAQhub Accessibility من إعدادات أندرويد. لا تُستدعى أوامر بحث المصنع أو موتور الطبق في الخلفية.",
    "Enable the IRAQhub Accessibility bridge manually. Monitoring issues no OEM scan or motor commands."));
  updateReport();
 }
 private void open(Class<?> cls){startActivity(new Intent(this,cls));}
 private void openNative(){
  try{startActivity(new Intent("com.tosmart.action.NavHost").setPackage("com.tosmart.aurum"));}
  catch(RuntimeException rejected){toast(tx("الروم لم يسمح بفتح القائمة؛ استخدم ريموت الرسيفر.","OEM menu entry was denied; use the receiver remote."));}
 }
 @Override protected void onResume(){super.onResume();updateReport();}
 private void updateReport(){
  if(report==null)return;
  AurumScanSummary.Result r=AurumScanSummary.Result.fromJson(
    prefs().getString("native_scan_last_result",""));
  if(r==null){report.setText(tx("لا توجد نتيجة بحث أصلية مرصودة بعد.","No OEM scan results observed yet."));return;}
  report.setText(tx("آخر بحث: تلفزيون ","Last scan: TV ")+r.tv+
    tx(" · راديو "," · Radio ")+r.radio+
    tx(" · مفتوحة "," · Free ")+r.free+
    tx(" · مشفرة "," · Encrypted ")+r.encrypted+
    tx("\nمن واجهة Aurum، ولم تُستورد القنوات إلى قاعدة IRAQhub.",
       "\nFrom the Aurum UI only; channels have not been imported into IRAQhub."));
 }
}
