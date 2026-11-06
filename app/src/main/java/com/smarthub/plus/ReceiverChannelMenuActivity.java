package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public final class ReceiverChannelMenuActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("القنوات والمفضلة","Channels & favourites"));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("01",tx("قائمة القنوات","Channel list"),tx("كل قمر يبدأ ترقيمه من 1","Each satellite starts at channel 1"),()->open(ReceiverChannelsActivity.class)),
    new ReceiverTileMenu.Tile("02",tx("إدارة القنوات","Manage channels"),tx("حذف أو نقل أو إعادة تسمية محلياً","Reorder, rename and delete locally"),()->open(ChannelManageActivity.class)));
  ReceiverTileMenu.pair(this,body,
    new ReceiverTileMenu.Tile("03",tx("استيراد DB / CHL والشعارات","DB / CHL import & logos"),tx("قنوات SQLite السريعة · CHMAX والشعارات","Fast SQLite channels · CHMAX and logos"),()->open(LogoSourcesActivity.class)),
    new ReceiverTileMenu.Tile("04",tx("إعداد الأقمار","Satellite setup"),tx("LNB والترددات وإشارة القمر","LNB, transponders and signal"),()->open(ReceiverAntennaMenuActivity.class)));
 }
 private void open(Class<?> activity){startActivity(new Intent(this,activity));}
}
