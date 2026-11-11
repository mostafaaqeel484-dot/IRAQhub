package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * OEM tools surfaced again from the user's 3.7.0 APK.
 *
 * The legacy APK had a dynamic ManufacturerTools menu backed by
 * ManufacturerFeatures.read/open. Later IRAQhub builds replaced it with a
 * redirect, hiding the manufacturer's original installation actions.
 *
 * The actions below are permitted only if PackageManager reports an exported
 * component without inaccessible permissions. This screen never reaches into
 * Aurum private data, emulates SearchManager, or starts a scan automatically.
 */
public final class ManufacturerToolsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(
    tx("التركيب الأصلي · Aurum","Original installation · Aurum"),
    tx("أدوات المصنع التي يعرضها الروم؛ البحث يتم بالريموت داخل Aurum",
      "Firmware menu entries, subject to ROM permissions. Start scans with the receiver remote."));

  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("تركيب وبحث الأقمار","Satellite installation & search"),
      tx("بحث يدوي وسريع وأعمى ضمن قائمة المصنع","Original manual, fast and blind scan menu"),
      ()->openOem("satellite")),
    new ReceiverTileMenu.Tile("02",tx("الموتور وUSALS","Motor / USALS"),
      tx("إعدادات المصنع؛ لا تتحرك الصحن تلقائياً","Factory motor menu; no automatic movement"),
      ()->openOem("motor")));

  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("تعديل قنوات التلفزيون","Edit TV channels"),
      tx("القائمة الأصلية للقنوات","Original manufacturer TV channel list"),
      ()->openOem("edit_tv")),
    new ReceiverTileMenu.Tile("04",tx("تعديل قنوات الراديو","Edit radio channels"),
      tx("قنوات الراديو الأصلية","Original radio channels"),
      ()->openOem("edit_radio")));

  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("05",tx("المفضلة الأصلية","Original favourites"),
      tx("التحرير ضمن Aurum","Edit within Aurum"),
      ()->openOem("edit_favorites")),
    new ReceiverTileMenu.Tile("06",tx("ترتيب قنوات المصنع","Sort manufacturer channels"),
      tx("لا يعدّل قاعدة IRAQhub المحلية","Does not alter IRAQhub's local database"),
      ()->openOem("sort_channels")));

  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("07",tx("نسخة القنوات الاحتياطية","OEM channel backup"),
      tx("حسب دعم روم الرسيفر","Only where the firmware provides it"),
      ()->openOem("channel_backup")),
    new ReceiverTileMenu.Tile("08",tx("إعدادات IRAQhub المستقلة","IRAQhub independent installation"),
      tx("بحث الترددات وضبط LNB والتشخيص","Local transponder tuning, LNB and diagnostics"),
      ()->startActivity(new Intent(this,ReceiverToolsActivity.class))));

  note(body,tx(
    "هذه اختصارات لقوائم Aurum المصرّح بفتحها. تصحيح USB عبر ADB يساعدنا على مراقبة النتائج لكنه لا يمنح التطبيق صلاحيات توقيع المصنع. اعمل نسخة احتياطية قبل أي بحث يغيّر القنوات.",
    "These are entry points to accessible Aurum menus. USB debugging helps inspect live results but does not grant manufacturer signing permissions. Back up channels before active scans."));
 }
 private void openOem(String id){
  ManufacturerFeatures.Entry item=ManufacturerFeatures.find(this,id);
  if(item==null){
   prefs().edit().putString("oem_last_open_error","OEM_ROUTE_NOT_PUBLISHED:"+id).apply();
   toast(tx("وظيفة المصنع غير موجودة على هذا الروم","This OEM function is absent in this firmware"));
   return;
  }
  String status=ManufacturerFeatures.unavailable(this,item);
  if(!status.isEmpty()){
   prefs().edit().putString("oem_last_open_error",status+":"+id).apply();
   toast(tx("الروم يمنع فتح هذه الوظيفة: ","The ROM blocks this function: ")+status);
   return;
  }
  if(ManufacturerFeatures.open(this,item)){
   prefs().edit().putString("oem_last_open_status","OEM_MENU_OPENED:"+id)
     .remove("oem_last_open_error").apply();
  }else{
   prefs().edit().putString("oem_last_open_error","OEM_MENU_REJECTED:"+id).apply();
   toast(tx("تعذّر فتح قائمة المصنع؛ افحصها عبر ADB","Manufacturer menu denied; inspect using ADB"));
  }
 }
}
