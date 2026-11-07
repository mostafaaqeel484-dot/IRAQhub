package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Remote-first settings hub; advanced options remain one click away. */
public final class HubSettingsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("إعدادات IRAQhub","IRAQhub settings"),
    tx("أهم إعدادات التلفاز والقنوات بصورة مختصرة، وكل الإعدادات القديمة محفوظة تحت متقدم.",
      "Simple TV-focused settings; all previous options remain under Advanced."));
  LinearLayout appearance=card(page);
  appearance.addView(text(tx("المظهر · RX Edition","RX Edition · appearance"),22,true));
  open(appearance,tx("الخلفيات والثيمات · العراق وLinux ومانشستر","Wallpapers · Iraq, Linux & Manchester"),RxAppearanceActivity.class);
  LinearLayout satellite=card(page);
  satellite.addView(text(tx("الطبق والقنوات","Dish and channels"),22,true));
  open(satellite,tx("تركيب الأقمار والترددات والبحث","Satellite setup and scanning"),ReceiverToolsActivity.class);
  open(satellite,tx("بحث التردد اليدوي · قائمة مستقلة","Manual search · separate screen"),ManualTransponderActivity.class);
  open(satellite,tx("ضبط LNB — Universal 9750/10600","LNB — universal 9750/10600"),TunerSettingsActivity.class);
  open(satellite,tx("ترتيب وإدارة القنوات","Sort and manage channels"),ChannelManageActivity.class);
  LinearLayout viewing=card(page);
  viewing.addView(text(tx("المشاهدة والصور","Viewing and artwork"),22,true));
  open(viewing,tx("الشعارات — جلب شعارات القنوات","Channel logos"),LogoSourcesActivity.class);
  open(viewing,tx("الـInfobar","Information bar"),OsdSettingsActivity.class);
  open(viewing,tx("دليل البرامج ومصادر EPG","EPG and feed sources"),SatelliteGuideActivity.class);
  open(viewing,tx("البوسترات ومصادر الصور","Posters and artwork providers"),ProviderSetupActivity.class);
  LinearLayout backup=card(page);
  backup.addView(text(tx("التحديث والاسترجاع","Updates and rollback"),22,true));
  open(backup,tx("نسخ القنوات والإعدادات والتحديثات","Channel backups and updates"),
    ReceiverBackupActivity.class);
  LinearLayout help=card(page);
  help.addView(text(tx("المساعدة والأدوات","Help and tools"),22,true));
  open(help,tx("لوحة المساعدة واختصارات الريموت","Remote control help"),RemoteHelpActivity.class);
  open(help,tx("الإعدادات المتقدمة — كل الخيارات السابقة","Advanced — all previous settings"),AdvancedSettingsActivity.class);
  open(help,tx("فحص الإشارة والتوافق","Signal and compatibility"),CompatibilityActivity.class);
 }
 private void open(LinearLayout parent,String label,Class<?> target){
  action(parent,label,()->startActivity(new Intent(this,target)));
 }
}