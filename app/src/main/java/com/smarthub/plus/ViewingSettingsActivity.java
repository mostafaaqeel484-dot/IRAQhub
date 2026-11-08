package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Single category for poster sources, channel logos, EPG and InfoBar. */
public final class ViewingSettingsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("المشاهدة والصور والدليل","Viewing · Posters · EPG"),
   tx("جمّعنا إعدادات البوسترات والشعارات وشريط القناة في قسم واحد، بدون حذف مصادر الصور أو بيانات القنوات.",
      "Artwork, logos, programme guide and InfoBar settings in one place; source data is preserved."));
  LinearLayout posters=card(body);
  posters.addView(text(tx("إصلاح البوسترات","Poster recovery"),21,true));
  TextView status=text("",14,false);posters.addView(status);
  action(posters,tx("تشغيل بوسترات NOW/NEXT وإعادة فحصها",
    "Enable NOW/NEXT posters and refresh matching"),()->{
     prefs().edit().putBoolean("osd_posters",true)
       .putLong("epg_art_changed_at",System.currentTimeMillis())
       .putLong("osd_changed_at",System.currentTimeMillis()).apply();
     IraqHubSyncScheduler.programmeChanged(this);
     showStatus(status);
     toast(tx("الصور مفعّلة؛ التحديث يعتمد على وجود EPG وصورة موثوقة",
       "Posters enabled; artwork still needs verified event metadata."));
  });
  action(posters,tx("مصادر TMDB وTVmaze والرياضة وWikimedia",
    "TMDB, TVmaze, Sports and Commons"),()->
    startActivity(new Intent(this,ArtworkSourcesActivity.class)));
  action(posters,tx("حسابات وإعدادات مصادر البوسترات",
    "Poster provider accounts and configuration"),()->
    startActivity(new Intent(this,ProviderSetupActivity.class)));
  action(posters,tx("تشخيص سبب غياب البوسترات",
    "Why did a programme poster not load?"),()->
    startActivity(new Intent(this,ArtworkDiagnosticsActivity.class)));
  LinearLayout appearance=card(body);
  appearance.addView(text(tx("واجهة المشاهدة","TV information display"),21,true));
  action(appearance,tx("الإنفوبار والتاريخ والخط","InfoBar, date and typography"),
    ()->startActivity(new Intent(this,OsdSettingsActivity.class)));
  action(appearance,tx("شعارات القنوات وتطابق CHL",
    "Channel logos and CHL matching"),()->
    startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(appearance,tx("EPG ومواعيد البرامج","EPG schedule and programmes"),()->
    startActivity(new Intent(this,SatelliteGuideActivity.class)));
  showStatus(status);
 }
 private void showStatus(TextView status){
  status.setText(tx("البوسترات: ","Posters: ")+(prefs().getBoolean("osd_posters",true)?"ON":"OFF")+
    "\nNOW: "+prefs().getString("osd_now_poster_status","NOT_CHECKED")+
    "\nNEXT: "+prefs().getString("osd_next_poster_status","NOT_CHECKED")+
    "\nSync: "+prefs().getString("poster_sync_status","NOT_CHECKED"));
 }
}
