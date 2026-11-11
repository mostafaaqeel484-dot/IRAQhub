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
 @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
  if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
   int code=event.getKeyCode();
   if(code==android.view.KeyEvent.KEYCODE_1||code==android.view.KeyEvent.KEYCODE_NUMPAD_1){
    open(ManualTransponderActivity.class);return true;
   }
   if(code==android.view.KeyEvent.KEYCODE_2||code==android.view.KeyEvent.KEYCODE_NUMPAD_2){
    open(ReceiverAntennaMenuActivity.class);return true;
   }
   if(code==android.view.KeyEvent.KEYCODE_3||code==android.view.KeyEvent.KEYCODE_NUMPAD_3){
    open(ReceiverFastScanMenuActivity.class);return true;
   }
   if(code==android.view.KeyEvent.KEYCODE_4||code==android.view.KeyEvent.KEYCODE_NUMPAD_4){
    open(ReceiverDeveloperMenuActivity.class);return true;
   }
  }
  return super.dispatchKeyEvent(event);
 }
 private void open(Class<?> next){
  try{startActivity(new Intent(this,next));}
  catch(RuntimeException unavailable){
   toast(tx("تعذّر فتح هذه الصفحة؛ راجع تشخيص التطبيق","Unable to open this page; check diagnostics"));
  }
 }
}
