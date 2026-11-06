package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Explicitly distinguish genuine OEM fast search from IRAQhub's saved-TP sweep. */
public final class ReceiverFastScanMenuActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("البحث السريع","Fast scan"),
    tx("اختر بحث المصنع الأصلي أو فحص ترددات IRAQhub","OEM scanning and IRAQhub TP sweep are different operations"));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("البحث السريع الأصلي","Native fast scan"),
      tx("فتح Aurum والانتقال إلى التركيب بالريموت","Open Aurum; choose Installation with the remote"),
      this::openNative),
    new ReceiverTileMenu.Tile("02",tx("فحص ترددات IRAQhub","Saved TP sweep"),
      tx("قياس القفل وSID دون اختراع قنوات","Check tuner lock and SIDs on saved RFs"),
      ()->open(TransponderSweepActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("بحث يدوي","Manual tuning"),
      tx("تردد واحد وقياس إشارته","One transponder and signal reading"),
      ()->open(ManualTransponderActivity.class)),
    new ReceiverTileMenu.Tile("04",tx("استيراد خدمات TS","TS service import"),
      tx("استخراج PAT/PMT/SDT من تسجيل فعلي","Discover PAT/PMT/SDT from a real recording"),
      ()->open(TsServiceImportActivity.class)));
  note(body,tx("بحث المصنع السريع يحفظ عبر خدمات Aurum الأصلية فقط. فحص IRAQhub لا يعادل Fast Scan ولا Blind Scan.",
    "OEM fast scan is performed in Aurum; IRAQhub's TP sweep is not a real Fast/Blind scan."));
 }
 private void open(Class<?> destination){startActivity(new Intent(this,destination));}
 private void openNative(){
  try{startActivity(new Intent("com.tosmart.action.NavHost").setPackage("com.tosmart.aurum"));}
  catch(RuntimeException rejected){toast(tx("لا يسمح إصدار الروم بفتح Aurum من التطبيق؛ افتح قائمة التركيب بالريموت.",
    "Firmware refused OEM navigation; open Aurum Installation with the remote."));}
 }
}
