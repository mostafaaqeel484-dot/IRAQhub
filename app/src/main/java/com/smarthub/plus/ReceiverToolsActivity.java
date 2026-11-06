package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

/**
 * IRAQhub-owned receiver tools only. No manufacturer home/menu launches.
 * OEM-only capabilities are listed as pending SDK integration.
 */
public final class ReceiverToolsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("الرسيفر والبحث داخل IRAQhub","Receiver and scanning inside IRAQhub"),
    tx("كل زر في هذه الشاشة يبقى داخل IRAQhub؛ لا يفتح Aurum.",
       "No button on this page opens the original Aurum launcher."));
  LinearLayout owned=card(body);
  owned.addView(text(tx("وظائف IRAQhub المتاحة","Available IRAQhub functions"),20,true));
  action(owned,tx("قائمة القنوات والمفضلة","Local channel catalogue and favourites"),
      ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));
  action(owned,tx("الأقمار والترددات المستوردة","Imported satellite and transponder list"),
      ()->startActivity(new Intent(this,SatelliteBrowserActivity.class)));
  action(owned,tx("الضبط المحلي LNB / DiSEqC","App-owned LNB / DiSEqC profiles"),
      ()->startActivity(new Intent(this,TunerSettingsActivity.class)));
  action(owned,tx("مشغل القنوات الفضائية","IRAQhub DVB player"),
      ()->startActivity(new Intent(this,DvbPlaybackActivity.class)));
  action(owned,tx("معلومات الإشارة المباشرة","Live tuner signal"),
      ()->startActivity(new Intent(this,SignalInformationActivity.class)));
  action(owned,tx("الصوت عبر الإنترنت","Channel internet audio"),
      ()->startActivity(new Intent(this,IPAudioActivity.class)));
  action(owned,tx("CHMAX: EPG والشعارات وحالة الاتصال","CHMAX: EPG and logo connection"),
      ()->startActivity(new Intent(this,ChmaxControlCenterActivity.class)));
  action(owned,tx("اختبار أزرار الريموت","Remote key diagnostics"),
      ()->startActivity(new Intent(this,RemoteKeyDiagnosticsActivity.class)));

  LinearLayout missing=card(body);
  missing.addView(text(tx("وظائف تحتاج خدمة المصنع ولم تُنقل بعد","OEM functions not yet natively ported"),20,true));
  note(missing,tx("Fast/Manual/Blind/Network Scan، USALS/Motor، Unicable، TP/PID، تحرير قنوات المصنع وBISS وPVR/TimeShift وTKGS وDVB-T/C: كل واحدة تحتاج بروتوكولاً موثقاً وصلاحيات ملائمة. ماكو زر ينقلك إلى Aurum حتى يخفي هذا النقص.",
    "Native scan, motor, Unicable, OEM channel edits, BISS, PVR/TimeShift, TKGS and DVB-T/C still need authorised firmware APIs. None of these buttons redirects to Aurum."));
  action(missing,tx("اعرض حالة كل وظيفة وسبب عدم اكتمالها","View per-feature implementation status"),
    ()->startActivity(new Intent(this,OemFullSettingsActivity.class)));

  LinearLayout extra=card(body);
  action(extra,tx("معلومات توافق الجهاز","Device compatibility report"),
    ()->startActivity(new Intent(this,CompatibilityActivity.class)));
  action(extra,tx("مصادر الشعارات وملفات CHL","CHL imports and logos"),
    ()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(extra,tx("مساعد القنوات والترجمة","Channel companion and subtitles"),
    ()->startActivity(new Intent(this,SatelliteActivity.class)));
  action(extra,tx("إعدادات Android","Android settings"),
    ()->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
      catch(RuntimeException ignored){toast(tx("الإعدادات غير متاحة","Settings unavailable"));}});
 }
}
