package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

/** IRAQhub native receiver hub. No original launcher, OEM menu or fake scan action. */
public final class ReceiverToolsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("مركز الرسيفر في IRAQhub","IRAQhub receiver centre"),
   tx("المشاهدة وإعدادات الطبق والقنوات داخل واجهتك، بدون الانتقال إلى Aurum.",
      "Playback, dish setup and channels in your own launcher; no Aurum interface."));
  LinearLayout dish=card(body);
  dish.addView(text(tx("الطبق والتونر","Dish and tuner"),21,true));
  action(dish,tx("مركز الموتور وUSALS","Motor and USALS centre"),
   ()->startActivity(new Intent(this,MotorCenterActivity.class)));
  action(dish,tx("بحث يدوي للتردد وفحص الإشارة","Manual transponder tune / signal search"),
   ()->startActivity(new Intent(this,ManualTransponderActivity.class)));
  action(dish,tx("ملفات LNB وDiSEqC","LNB and DiSEqC settings"),
   ()->startActivity(new Intent(this,TunerSettingsActivity.class)));
  action(dish,tx("الأقمار والترددات المستوردة","Imported satellites and transponders"),
   ()->startActivity(new Intent(this,SatelliteBrowserActivity.class)));
  action(dish,tx("حالة الإشارة","Live tuner signal"),
   ()->startActivity(new Intent(this,SignalInformationActivity.class)));

  LinearLayout video=card(body);
  video.addView(text(tx("القنوات وتشغيل 4:2:2","TV channels and 4:2:2"),21,true));
  action(video,tx("القنوات والمفضلة","Channels and favourites"),
   ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));
  action(video,tx("مشغل DVB الفضائي","Satellite DVB player"),
   ()->startActivity(new Intent(this,DvbPlaybackActivity.class)));
  action(video,tx("مشغل 4:2:2 البرمجي · LibVLC","LibVLC software 4:2:2 player"),
   ()->startActivity(new Intent(this,Software422CenterActivity.class)));
  action(video,tx("التسجيل المباشر PVR للقنوات المفتوحة وتصدير USB","FTA PVR recording and USB export"),
   ()->startActivity(new Intent(this,PvrRecordingActivity.class)));
  action(video,tx("دليل البرامج EPG","Programme guide"),
   ()->startActivity(new Intent(this,SatelliteGuideActivity.class)));
  action(video,tx("إعداد Infobar","Infobar settings"),
   ()->startActivity(new Intent(this,OsdSettingsActivity.class)));

  LinearLayout extras=card(body);
  extras.addView(text(tx("خدمات IRAQhub","IRAQhub services"),21,true));
  action(extras,tx("استيراد القنوات وشعاراتها","Channel and logo imports"),
   ()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(extras,tx("صوت القنوات عبر الإنترنت","Internet channel audio"),
   ()->startActivity(new Intent(this,IPAudioActivity.class)));
  action(extras,tx("معلومات توافق الرسيفر","Receiver compatibility"),
   ()->startActivity(new Intent(this,CompatibilityActivity.class)));
  note(extras,tx("وظائف البحث الماسح وإدارة قاعدة قنوات النظام وPVR المباشر تحتاج واجهة خدمات DVB موثقة. لا نفتح Aurum ولا ندّعي تنفيذ وظائف غير موصولة.",
    "Native scan, firmware channel database editing and direct PVR need authorised DVB APIs. IRAQhub never opens Aurum or advertises fake execution."));
 }
}
