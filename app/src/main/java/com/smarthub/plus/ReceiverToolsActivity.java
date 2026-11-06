package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import android.content.SharedPreferences;

/** IRAQhub installation tree corresponding to the inspected Aurum navigation graph. */
public final class ReceiverToolsActivity extends HubActivity {
 private TextView nativeScanReport;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("التركيب · IRAQhub","Installation · IRAQhub"),
   tx("التركيب، البحث، إدارة القنوات وإعدادات الطبق كلها داخل واجهتك.",
      "Satellite setup, tune sweep and local channel management, entirely in IRAQhub."));
  SatelliteState tuned=SatelliteState.read(this);
  LinearLayout live=card(body);
  live.addView(text(tx("لوحة الرسيفر · القناة الحالية","Receiver panel · active channel"),18,true));
  TextView liveName=text(tuned.channelName.isEmpty()?tx("لا توجد قناة نشطة","No active channel"):
     tuned.channelName,23,true);
  liveName.setTextColor(0xffffd36c);
  live.addView(liveName);
  if(!tuned.title.isEmpty())live.addView(text(tx("الآن: ","Now: ")+tuned.title,14,false));
    LinearLayout installation=card(body);
  installation.addView(text(tx("تركيب الصحن والأقمار","Dish installation"),22,true));
  open(installation,tx("الأقمار والترددات · إضافة وتعديل","Satellite / TP add and edit"),
    InstallationEditorActivity.class);
  open(installation,tx("إعدادات الهوائي LNB / DiSEqC 1.0 / 1.1","Antenna LNB / DiSEqC 1.0 / 1.1"),
    TunerSettingsActivity.class);
  open(installation,tx("مركز موتور USALS وإيقاف الطبق","USALS motor centre / STOP"),
    MotorCenterActivity.class);
  open(installation,tx("العثور على القمر · إشارة S / Q","Satellite finder · S / Q"),
    SignalInformationActivity.class);

  LinearLayout scan=card(body);
  scan.addView(text(tx("البحث وقياس الترددات","Scanning and tuning"),22,true));
  open(scan,tx("البحث اليدوي المستقل · ضبط وحفظ التردد وقراءة SID","Manual satellite search · tune, save TP and read SID"),
    ManualTransponderActivity.class);
  open(scan,tx("فحص سريع للترددات المحفوظة · نتائج فعلية","Saved TP sweep · measured results"),
    TransponderSweepActivity.class);
  open(scan,tx("اكتشاف خدمات من تسجيل DVB MPEG-TS وحفظ القنوات","Discover/save channels from real DVB MPEG-TS"),
    TsServiceImportActivity.class);
  note(scan,tx("البحث الأعمى ومسح الشبكة وحفظ القنوات الجديدة مباشرةً لا تزال تحتاج API مخوّلة من المصنع. الخيارات أعلاه داخل IRAQhub هي ضبط تردد وفحص SID واستيراد TS، وليست بحث المصنع الكامل.",
    "Blind/network scan and direct channel-saving require an authorised OEM search API. The above IRAQhub functions tune/measure SIDs or import real TS; they are not the full OEM channel scanner."));
  LinearLayout original=card(body);
  original.addView(text(tx("البحث الأصلي للرسيفر · Aurum","Original receiver search · Aurum"),21,true));
  note(original,tx("يمكنك استخدام البحث اليدوي والسريع والأعمى في Aurum بدون تغيير قاعدة قنواته من IRAQhub. هذا ينقلك إلى واجهة المصنع، وبعد فتح التركيب بالريموت تظهر أدوات البحث الأصلية. ليس نقلاً لمحرك SearchManager2 ولا بدء بحث تلقائي.",
    "The OEM's manual, fast and blind search still runs in Aurum. This opens the vendor navigation UI; use the remote to reach Installation. It does not invoke a privileged SearchManager2 scan automatically."));
  action(original,tx("فتح واجهة Aurum الأصلية · بعدها اختر التركيب","Open Aurum native UI · then choose Installation"),()->{
   try{startActivity(new Intent("com.tosmart.action.NavHost").setPackage("com.tosmart.aurum"));}
   catch(RuntimeException unavailable){toast(tx("لا يسمح النظام بفتح واجهة البحث الأصلية من تطبيق آخر","The firmware does not permit opening the native menu from another app"));}
  });
  CheckBox observe=check(original,tx("قراءة وحفظ إحصائيات نهاية البحث الظاهرة في Aurum (اختياري)","Observe and save visible Aurum scan-completion counters (opt-in)"),prefs().getBoolean("native_scan_observation_enabled",false));
  observe.setOnCheckedChangeListener((button,on)->prefs().edit().putBoolean("native_scan_observation_enabled",on).apply());
  nativeScanReport=text("",14,false);original.addView(nativeScanReport);
  note(original,tx("يتطلب تفعيل خدمة IRAQhub — ربط القنوات Accessibility من إعدادات النظام. لا يقرأ بيانات خاصة أو يرسل أوامر بحث أو يحذف قنوات.",
    "Requires enabling the IRAQhub receiver Accessibility bridge in Android settings. It neither reads protected OEM data nor sends search/motor/channel-delete commands."));
  updateNativeScanReport();

  LinearLayout channels=card(body);
  channels.addView(text(tx("إدارة القنوات والمفضلة","Channel management"),22,true));
  open(channels,tx("إدارة القنوات: إعادة تسمية، نقل، حذف، مفضلة","Rename, reorder, delete and favourite"),
    ChannelManageActivity.class);
  open(channels,tx("قائمة القنوات وقنوات الأقمار","Channel browser"),
    ReceiverChannelsActivity.class);
  open(channels,tx("استيراد CHL وشعارات القنوات","CHL import / logos"),
    LogoSourcesActivity.class);

  LinearLayout viewing=card(body);
  viewing.addView(text(tx("المشاهدة والتسجيل","Playback and recording"),22,true));
  open(viewing,tx("تلفاز DVB داخل IRAQhub","IRAQhub DVB TV"),DvbPlaybackActivity.class);
  open(viewing,tx("تشغيل 4:2:2 البرمجي","Software 4:2:2"),Software422CenterActivity.class);
  open(viewing,tx("تسجيل PVR للقنوات المفتوحة","FTA PVR recording"),PvrRecordingActivity.class);
  open(viewing,tx("Infobar والبوسترات","Infobar / posters"),OsdSettingsActivity.class);
  open(viewing,tx("دليل EPG","EPG guide"),SatelliteGuideActivity.class);
 }
 @Override protected void onResume(){super.onResume();updateNativeScanReport();}
 private void updateNativeScanReport(){
  if(nativeScanReport==null)return;
  AurumScanSummary.Result saved=AurumScanSummary.Result.fromJson(
    prefs().getString("native_scan_last_result",""));
  if(saved==null){nativeScanReport.setText(tx("لم تُلتقط نتيجة بحث أصلية بعد.","No native scan result observed yet."));return;}
  String date=java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(saved.observedAt));
  nativeScanReport.setText(tx("آخر نتيجة بحث مرصودة · ","Last observed scan result · ")+date+
    tx("\\nتلفزيون: ","\\nTV: ")+saved.tv+tx(" · راديو: "," · Radio: ")+saved.radio+
    tx("\\nمفتوحة: ","\\nFree: ")+saved.free+tx(" · مشفرة: "," · Encrypted: ")+saved.encrypted+
    tx(" · HD: "," · HD: ")+saved.hd+
    tx("\\nهذه إحصائيات واجهة المصنع فقط؛ القنوات ليست مستوردة داخل IRAQhub.",
       "\\nOEM screen counters only; channels have not been imported into IRAQhub."));
 }
 private void open(LinearLayout parent,String label,Class<?> type){
  action(parent,label,()->startActivity(new Intent(this,type)));
 }
}
