package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Four primary installation actions, with channel and viewing tools in
 * separate categories. No long rectangular list of unrelated features.
 */
public final class ReceiverToolsActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("التركيب · IRAQhub","Installation · IRAQhub"),
    tx("أربع قوائم رئيسية — واضحة وسريعة بالريموت","Four installation choices, designed for remote navigation"));
  TextView caption=text(tx("التركيب","INSTALLATION"),21,true);
  caption.setTextColor(AMBER);caption.setPadding(dp(4),dp(10),0,dp(12));body.addView(caption);

  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("البحث اليدوي","Manual search"),
      tx("تردد، استقطاب، ترميز، إشارة","Frequency, polarity, symbol rate, lock"),
      ()->open(ManualTransponderActivity.class)),
    new ReceiverTileMenu.Tile("02",tx("إعدادات القمر","Satellite settings"),
      tx("الأقمار وLNB وDiSEqC وUSALS","Satellite, LNB, DiSEqC and motor"),
      ()->open(ReceiverAntennaMenuActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("البحث السريع","Fast search"),
      tx("بحث Aurum الأصلي وفحص الترددات","OEM fast scan / saved TP sweep"),
      ()->open(ReceiverFastScanMenuActivity.class)),
    new ReceiverTileMenu.Tile("04",tx("المطور","Developer"),
      tx("4:2:2 والتشخيص وأدوات DVB","4:2:2 and DVB diagnostics"),
      ()->open(ReceiverDeveloperMenuActivity.class)));

  TextView more=text(tx("الأقسام الأخرى","OTHER SECTIONS"),17,true);
  more.setTextColor(AMBER);more.setPadding(dp(4),dp(20),0,dp(12));body.addView(more);
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("05",tx("القنوات والمفضلة","Channels & favourites"),
      tx("تصفح ونقل وترتيب وشعارات","Browse, reorder and logos"),
      ()->open(ReceiverChannelMenuActivity.class)),
    new ReceiverTileMenu.Tile("06",tx("المشاهدة والتسجيل","Playback & recording"),
      tx("DVB و4:2:2 وPVR وInfoBar","DVB, 4:2:2, PVR and EPG"),
      ()->open(ReceiverViewingMenuActivity.class)));

  note(body,tx("قائمة البحث الأصلية في Aurum تبقى مسؤولة عن حفظ قنوات المصنع؛ IRAQhub لا يغيّر قاعدة النظام من دون الصلاحيات الرسمية.",
    "Aurum's original scan saves OEM channels. IRAQhub does not alter protected firmware databases."));
 }
 private void open(Class<?> next){startActivity(new Intent(this,next));}
}
