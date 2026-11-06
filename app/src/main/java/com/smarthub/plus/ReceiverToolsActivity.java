package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** IRAQhub installation tree corresponding to the inspected Aurum navigation graph. */
public final class ReceiverToolsActivity extends HubActivity {
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
  note(scan,tx("البحث الأعمى ومسح الشبكة وحفظ القنوات الجديدة من PID لم تُربط بعد بمحرك SearchManager؛ هذه القائمة لا تدّعي عملها ولا تفتح Aurum.",
    "Blind/network scan and saving newly discovered PID services still lack a proven SearchManager API. No fake action or Aurum redirect."));

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
 private void open(LinearLayout parent,String label,Class<?> type){
  action(parent,label,()->startActivity(new Intent(this,type)));
 }
}
