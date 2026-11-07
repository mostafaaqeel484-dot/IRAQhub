package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public final class ReceiverViewingMenuActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("المشاهدة والتسجيل","Playback & recording"));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("تلفاز فضائي","Satellite TV"),"DVB-S/S2",()->open(DvbPlaybackActivity.class)),
    new ReceiverTileMenu.Tile("02",tx("مشغل 4:2:2","4:2:2 player"),tx("LibVLC مع صوت مستقل","LibVLC with separate audio"),()->open(Software422CenterActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("تسجيل PVR","PVR recording"),tx("تسجيل القنوات المفتوحة","FTA recordings"),()->open(PvrRecordingActivity.class)),
    new ReceiverTileMenu.Tile("04","InfoBar / EPG",tx("الدليل والبوسترات والمظهر","EPG, posters and OSD appearance"),()->open(OsdSettingsActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("05",tx("دليل البرامج","Programme guide"),"EPG",()->open(SatelliteGuideActivity.class)),
    new ReceiverTileMenu.Tile("06",tx("الصوتيات · CFG / Audio Track","Audio tracks · CFG import"),
      tx("استيراد USB وتأخير صوت القناة","USB import and per-channel audio delay"),()->open(IPAudioActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("07",tx("الترجمة · DVB / TTX → العربية","Subtitle · DVB / TTX to Arabic"),
      tx("Set Translation [Arabic] · Set Subtitle Color","Arabic translation and subtitle color"),
      ()->open(LiveTranslationActivity.class)),null);
 }
 private void open(Class<?> activity){startActivity(new Intent(this,activity));}
}
