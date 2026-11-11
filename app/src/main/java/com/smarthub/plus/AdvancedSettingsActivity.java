package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

/** App preferences and Android settings reached from the RX settings hub. */
public final class AdvancedSettingsActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("إعدادات النظام والتطبيق","App and system settings"));
  action(body,tx("كل فئات الإعدادات","All settings categories"),()->
    startActivity(new Intent(this,HubSettingsActivity.class)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)));

  LinearLayout appearance=card(body);
  appearance.addView(text(tx("المظهر والتنقل","Appearance and navigation"),22,true));
  action(appearance,tx("ألوان الواجهة","Interface colours"),this::chooseColours);
  action(appearance,tx("حجم خط القوائم","Menu text size"),this::chooseTextSize);
  action(appearance,ar?"English":"العربية",()->{
   prefs().edit().putBoolean("arabic",!ar).apply();
   recreate();
  });
  action(appearance,tx("اختيار IRAQhub كشاشة HOME","Choose IRAQhub as HOME"),()->
    openSystem(Settings.ACTION_HOME_SETTINGS,
      tx("اختيار HOME غير متاح على الجهاز","HOME chooser unavailable on this device")));

  LinearLayout system=card(body);
  system.addView(text(tx("الاتصال والجهاز","Connection and device"),22,true));
  action(system,tx("خدمة ربط القنوات وMENU","Channel bridge and MENU"),()->{
   try{AccessibilitySetup.open(this);}
   catch(Exception unavailable){toast(tx("إعداد الخدمة غير متاح","Service settings unavailable"));}
  });
  action(system,tx("الشبكة","Network"),()->openSystem(Settings.ACTION_WIFI_SETTINGS,
    tx("إعدادات الشبكة غير متاحة","Network settings unavailable")));
 }

 private void chooseColours(){
  String[] labels={tx("بنفسجي ليلي","Midnight violet"),tx("زمردي","Emerald"),
    tx("أزرق محيطي","Ocean blue")};
  int selected=prefs().getInt("launcher_theme",0);
  new AlertDialog.Builder(this).setTitle(tx("اختر المظهر","Choose a theme"))
    .setSingleChoiceItems(labels,selected>=0&&selected<labels.length?selected:-1,(dialog,index)->{
     prefs().edit().putInt("launcher_theme",index).apply();
     dialog.dismiss();
     recreate();
    }).show();
 }

 private void chooseTextSize(){
  int[] sizes={85,100,110,125};
  String[] labels={"85%","100%","110%","125%"};
  int saved=prefs().getInt("ui_font_percent",100);
  int selected=1;
  for(int index=0;index<sizes.length;index++)if(sizes[index]==saved)selected=index;
  new AlertDialog.Builder(this).setTitle(tx("حجم الخط","Text size"))
    .setSingleChoiceItems(labels,selected,(dialog,index)->{
     prefs().edit().putInt("ui_font_percent",sizes[index]).apply();
     dialog.dismiss();
     recreate();
    }).show();
 }

 private void openSystem(String action,String unavailableMessage){
  try{startActivity(new Intent(action));}
  catch(Exception unavailable){toast(unavailableMessage);}
 }
}
