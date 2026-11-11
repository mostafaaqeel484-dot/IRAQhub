package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Antenna setup is a separate focused menu, not a busy installation dashboard. */
public final class ReceiverAntennaMenuActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("إعدادات القمر والصحن","Satellite & dish settings"),
    tx("اضبط القمر والـLNB وDiSEqC، ثم راقب الإشارة","Satellite, LNB and DiSEqC configuration with signal diagnostics"));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("الأقمار والترددات","Satellites & TPs"),
      tx("إضافة وتعديل الأقمار والترددات","Edit satellites and transponders"),
      ()->open(InstallationEditorActivity.class)),
    new ReceiverTileMenu.Tile("02","LNB / DiSEqC",
      tx("طاقة LNB والـ22 kHz والمنافذ","Power, 22 kHz and switch ports"),
      ()->open(TunerSettingsActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("الموتور وUSALS","Motor & USALS"),
      tx("موقع الطبق وحدود الأمان وإيقافه","Dish position, safety and STOP"),
      ()->open(MotorCenterActivity.class)),
    new ReceiverTileMenu.Tile("04",tx("مقياس الإشارة","Signal finder"),
      tx("القوة والجودة والتردد الفعلي","Signal strength, quality and RF"),
      ()->open(SignalInformationActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("05",tx("التركيب الأصلي من المصنع","Original receiver installation"),
      tx("بحث الأقمار، إدارة القنوات ونسخة احتياطية في Aurum",
        "Aurum search, channel management and OEM backup"),
      ()->open(ManufacturerToolsActivity.class)),
    null);
  note(body,tx("إعدادات IRAQhub محلية؛ تعديل قاعدة قنوات Aurum المحمية يحتاج صلاحية المصنع.",
    "IRAQhub settings are local; OEM channel database writes need vendor permission."));
 }
 private void open(Class<?> destination){startActivity(new Intent(this,destination));}
}
