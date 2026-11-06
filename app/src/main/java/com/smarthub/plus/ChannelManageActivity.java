package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Real channel management in IRAQhub's own catalogue. No OEM menus or writes. */
public final class ChannelManageActivity extends HubActivity {
 private final ExecutorService work=Executors.newSingleThreadExecutor();
 private EditText search;
 private ListView list;
 private TextView status;
 private List<ReceiverCatalog.Channel> visible=Collections.emptyList();
 private volatile boolean closed;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("إدارة القنوات","Channel manager"),
   tx("نقل، إعادة تسمية، حذف، ومفضلة قنوات IRAQhub من دون فتح شاشة المصنع.",
     "Move, rename, remove and favourite IRAQhub channels without opening OEM menus."));
  LinearLayout tools=card(page);
  search=new EditText(this);
  search.setSingleLine(true);search.setHint(tx("اسم القناة أو ترددها","Channel name or frequency"));
  tools.addView(search,new LinearLayout.LayoutParams(-1,dp(49)));
  action(tools,tx("بحث عن القناة","Search channels"),this::refresh);
  action(tools,tx("قائمة القنوات والمفضلة","Open channel list / favourites"),
    ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));
  note(tools,tx("التغييرات تؤثر على قاعدة قنوات IRAQhub فقط. ملف CHL المستورد مجدداً قد يستبدل ترتيب وأسماء القنوات؛ قاعدة المصنع لا تُعدّل.",
    "Only IRAQhub's database is edited. Reimporting CHL may replace names/order; firmware channel database is untouched."));
  status=text("",14,false);tools.addView(status);
  list=new ListView(this);
  list.setDividerHeight(dp(1));
  LinearLayout main=card(page);
  main.addView(list,new LinearLayout.LayoutParams(-1,dp(420)));
  list.setOnItemClickListener((parent,row,pos,id)->manage(visible.get(pos)));
  list.setOnItemLongClickListener((parent,row,pos,id)->{
   ReceiverCatalog.Channel c=visible.get(pos);
   startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",c.id));return true;
  });
  refresh();
 }
 private void refresh(){
  String q=search.getText().toString().trim();
  work.execute(()->{
   ReceiverCatalog db=ReceiverCatalog.get(this);db.ensure();
   List<ReceiverCatalog.Channel> rows=db.channels(q,-1,-1,false,false);
   runOnUiThread(()->{
    if(closed)return;visible=rows;
    status.setText(tx("القنوات المعروضة: ","Showing channels: ")+rows.size()+
      tx(" (حتى 120؛ استخدم البحث للتصفية)"," (up to 120; use search to filter)"));
    list.setAdapter(new BaseAdapter(){
     public int getCount(){return visible.size();}
     public Object getItem(int i){return visible.get(i);}
     public long getItemId(int i){return visible.get(i).id;}
     public View getView(int i,View reused,ViewGroup parent){
      ReceiverCatalog.Channel channel=visible.get(i);
      TextView row=text(channel.name+"\n"+channel.rf(),17,true);
      row.setPadding(dp(16),dp(12),dp(16),dp(12));
      row.setTextColor(ReceiverPanelStyle.GOLD);
      row.setBackground(ReceiverPanelStyle.row(ChannelManageActivity.this));
      return row;
     }
    });
   });
  });
 }
 private void manage(ReceiverCatalog.Channel item){
  String[] labels=new String[]{
   tx("تشغيل القناة","Play channel"),
   tx("تغيير الاسم","Rename channel"),
   tx("نقل لأعلى","Move up"),
   tx("نقل لأسفل","Move down"),
   tx("إضافة / إزالة من المفضلة","Toggle favourite"),
   tx("إدارة مجموعات المفضلة","Favourite groups"),
   tx("حذف القناة من IRAQhub","Delete from IRAQhub")
  };
  ReceiverPanelStyle.choose(this,item.name,labels,0,index->{
   switch(index){
    case 0:startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",item.id));break;
    case 1:rename(item);break;
    case 2:modify(()->ReceiverCatalog.get(this).moveLocal(item.id,-1));break;
    case 3:modify(()->ReceiverCatalog.get(this).moveLocal(item.id,1));break;
    case 4:work.execute(()->{ReceiverCatalog db=ReceiverCatalog.get(this);
      db.favorite(item.id,!db.favorite(item.id));runOnUiThread(()->{if(!closed)refresh();});});break;
    case 5:startActivity(new Intent(this,ReceiverChannelsActivity.class).putExtra("favorites",true));break;
    case 6:remove(item);break;
   }
  });
 }
 private interface Edit{boolean apply();}
 private void modify(Edit action){
  work.execute(()->{boolean changed=action.apply();runOnUiThread(()->{
    if(!closed){if(!changed)toast(tx("لا يمكن نقل القناة","Move not available"));refresh();}
  });});
 }
 private void rename(ReceiverCatalog.Channel item){
  EditText input=new EditText(this);input.setSingleLine(true);
  input.setText(item.name);input.setSelectAllOnFocus(true);
  input.setInputType(InputType.TYPE_CLASS_TEXT);
  new AlertDialog.Builder(this).setTitle(tx("اسم القناة","Channel name"))
   .setView(input).setPositiveButton(tx("حفظ","Save"),(d,w)->modify(
     ()->ReceiverCatalog.get(this).renameChannelLocal(item.id,input.getText().toString())))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void remove(ReceiverCatalog.Channel item){
  new AlertDialog.Builder(this).setTitle(tx("حذف القناة؟","Delete channel?"))
   .setMessage(item.name+"\n"+tx("الحذف فقط من IRAQhub ولا يغيّر قنوات المصنع.",
      "Deletes from IRAQhub's list only, not the OEM database."))
   .setPositiveButton(tx("حذف","Delete"),(d,w)->modify(
      ()->ReceiverCatalog.get(this).deleteLocal(item.id)))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 @Override protected void onDestroy(){closed=true;work.shutdownNow();super.onDestroy();}
}
