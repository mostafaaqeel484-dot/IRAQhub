package com.smarthub.plus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Safe, opt-in CHMAX status and existing read-only artwork/EPG integration.
 * OEM cloud-channel upload/download/auto-update belong to the private Aurum UI.
 */
public final class ChmaxControlCenterActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("CHMAX · الربط والمزامنة","CHMAX · connection and synchronization"),
   tx("مركز إعدادات IRAQhub للـEPG والشعارات، مع الوصول لإدارة القنوات السحابية الأصلية.",
      "IRAQhub EPG/logo settings and access to OEM cloud-channel management."));
  LinearLayout about=card(body);
  about.addView(text(tx("الربط التلقائي الذي يدعمه IRAQhub","Automatic integration actually implemented"),20,true));
  note(about,tx("عند نجاح مصادقة المصنع، تُطابق بيانات القناة الحالية مع EPG والشعار المنشور. لا يُرفع ملف قنواتك ولا يُستبدل في الخلفية.",
    "A verified factory session may match the current channel to EPG and artwork. Your channel database is not uploaded or replaced automatically."));

  LinearLayout toggles=card(body);
  CheckBox factory=check(toggles,tx("جلب EPG وشعار القناة من مصدر المصنع عند تغيير القناة",
    "Fetch authorised CHMAX EPG and artwork on channel change"),prefs().getBoolean("factory_enabled",true));
  factory.setOnCheckedChangeListener((v,on)->prefs().edit().putBoolean("factory_enabled",on).apply());
  CheckBox logo=check(toggles,tx("مصدر CHMAX الاختياري لشعارات قنوات CHL",
    "Optional CHMAX source for CHL channel logos"),ChlLogoAddon.get(this).enabled());
  logo.setOnCheckedChangeListener((v,on)->{
   ChlLogoAddon.get(this).enable(on);
   prefs().edit().putLong("providers_changed_at",System.currentTimeMillis()).apply();
  });
  action(toggles,tx("توثيق هوية الرسيفر وربط خدمة المصنع","Authenticate receiver and factory service"),
   ()->startActivity(new Intent(this,FactorySettingsActivity.class)));
  action(toggles,tx("إدارة مصادر الشعارات","Configure channel logo sources"),
   ()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(toggles,tx("تحديث مصادر EPG والبوسترات العامة الآن","Refresh existing EPG and poster feeds"),
   ()->{IraqHubSyncScheduler.runNow(this);
    toast(tx("طُلب تحديث EPG والبوسترات، وليس تحديث قائمة CHMAX السحابية",
      "EPG and poster refresh queued; this is NOT a cloud channel-list update"));});

  LinearLayout cloud=card(body);
  cloud.addView(text(tx("قوائم القنوات السحابية في Aurum","Original Aurum cloud channel lists"),20,true));
  note(cloud,tx("يملك Aurum وظائف حفظ/تحميل قوائم CHMAX وتحديثها تلقائياً، لكنها داخل ChmService وواجهات المصنع الخاصة. لا توجد صلاحية موثقة لكتابة قاعدة قنوات الجهاز من IRAQhub.",
     "Aurum contains cloud save/load and automatic update flows in private OEM components. IRAQhub cannot call undocumented writes to the firmware channel database."));
  note(cloud,tx("مزامنة قوائم CHMAX السحابية غير منفذة داخل IRAQhub. لن تفتح هذه الشاشة Aurum؛ يلزم API مصرح بها لإضافة الوظيفة.",
    "Cloud channel list synchronization is not implemented inside IRAQhub. This screen never opens Aurum; an authorised API is required."));

  LinearLayout diag=card(body);
  diag.addView(text(tx("حالة الاتصال الحقيقية","Connection status"),20,true));
  TextView status=text("",14,false);
  diag.addView(status);
  action(diag,tx("فحص الحالة من جديد","Refresh status"),()->render(status));
  render(status);
 }
 private void render(TextView display){
  SharedPreferences p=prefs();
  String factoryStatus=p.getString("factory_status","FACTORY_NOT_CONNECTED");
  long updated=p.getLong("factory_last_success",0);
  String time=updated>0?new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date(updated)):tx("لم يكتمل اتصال موثّق","No confirmed successful sync");
  String logo=p.getString("chl_chmax_status","");
  if(logo.isEmpty())logo=tx("لم يُختبر","Not tested");
  display.setText(FactoryStatus.label(this,factoryStatus)+"\n"+
    tx("آخر نجاح CHMAX EPG: ","Last successful factory EPG: ")+time+"\n"+
    tx("مصدر شعارات CHL: ","CHL logo source: ")+(ChlLogoAddon.get(this).enabled()?"ON":"OFF")+"\n"+
    tx("حالة شعار CHMAX: ","CHMAX logo status: ")+logo+"\n"+
    tx("مزامنة قاعدة القنوات السحابية: غير منفذة عبر IRAQhub",
       "Cloud channel database sync: not implemented through IRAQhub"));
 }
}
