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
    new ReceiverTileMenu.Tile("02",tx("مشغل 4:2:2","4:2:2 player"),tx("MPV مستقل وLibVLC · فيديو فقط للاختبار","Isolated MPV and LibVLC · video-only test"),()->open(Software422CenterActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("تسجيل PVR","PVR recording"),tx("تسجيل القنوات المفتوحة","FTA recordings"),()->open(PvrRecordingActivity.class)),
    new ReceiverTileMenu.Tile("04","InfoBar / EPG",tx("الدليل والبوسترات والمظهر","EPG, posters and OSD appearance"),()->open(OsdSettingsActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("05",tx("دليل البرامج","Programme guide"),"EPG",()->open(SatelliteGuideActivity.class)),
    null);
 }
 private void open(Class<?> activity){startActivity(new Intent(this,activity));}
}
