package com.smarthub.plus;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Remote-friendly Tiger-style two-column audio/subtitle selector. */
final class TigerAvDialog {
 interface Actions {
  void audioTrack(int index);
  void restoreNativeAudio();
  void openCfgAudio();
  void openCaptions();
 }
 private TigerAvDialog(){}
 private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 private static GradientDrawable bg(int color,int stroke,Activity a){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,10));
  d.setStroke(dp(a,1),stroke);return d;
 }
 private static TextView label(Activity a,String text,int size,int color,boolean bold){
  TextView v=new TextView(a);v.setText(text);v.setTextColor(color);v.setTextSize(size);
  v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(a,13),dp(a,5),dp(a,9),dp(a,5));
  if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
  v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;
 }
 static void show(Activity a,ReceiverCatalog.Channel channel,int activeAudio,boolean subtitle,Actions actions){
  boolean arabic=a.getSharedPreferences("smarthub",0).getBoolean("arabic",true);
  String[] sections={"Audio","Audio Track","Screen Mode","HDMI Mode","Subtitle","Subtitle Switch"};
  LinearLayout main=new LinearLayout(a);main.setOrientation(LinearLayout.VERTICAL);
  main.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,12));
  main.setBackground(bg(0xf32b253f,0xffd6c5ac,a));
  TextView title=label(a,channel.name+"  ·  Audio / Subtitle",21,Color.WHITE,true);
  main.addView(title,new LinearLayout.LayoutParams(-1,dp(a,44)));
  LinearLayout columns=new LinearLayout(a);columns.setOrientation(LinearLayout.HORIZONTAL);
  main.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
  ListView left=new ListView(a),right=new ListView(a);
  left.setDividerHeight(dp(a,5));right.setDividerHeight(dp(a,5));
  left.setBackground(bg(0x55473d62,0x00999999,a));
  right.setBackground(bg(0x55473d62,0x00999999,a));
  columns.addView(left,new LinearLayout.LayoutParams(0,-1,1));
  columns.addView(right,new LinearLayout.LayoutParams(0,-1,1.25f));
  final int[] activeSection={subtitle?4:1};
  final String[][] rows={new String[0]};
  final Runnable[] repaint={null};
  final AlertDialog dialog=new AlertDialog.Builder(a).setView(main).create();
  BaseAdapter leftAdapter=new BaseAdapter(){
   public int getCount(){return sections.length;}
   public Object getItem(int i){return sections[i];}
   public long getItemId(int i){return i;}
   public View getView(int i,View old,ViewGroup p){
    TextView v=label(a,(i==activeSection[0]?"▶  ":"    ")+sections[i],19,
      i==activeSection[0]?0xffffb54f:Color.WHITE,i==activeSection[0]);
    v.setBackground(bg(i==activeSection[0]?0xffe7a02f:0xff554968,
      i==activeSection[0]?0xffffdfb5:0x00111111,a));
    return v;
   }
  };
  BaseAdapter rightAdapter=new BaseAdapter(){
   public int getCount(){return rows[0].length;}
   public Object getItem(int i){return rows[0][i];}
   public long getItemId(int i){return i;}
   public View getView(int i,View old,ViewGroup p){
    TextView v=label(a,rows[0][i],19,Color.WHITE,false);
    v.setBackground(bg(0xff4b4368,0x00554433,a));
    return v;
   }
  };
  left.setAdapter(leftAdapter);right.setAdapter(rightAdapter);
  repaint[0]=()->{
   switch(activeSection[0]){
    case 0:rows[0]=new String[]{
     arabic?"الصوت الأصلي للقناة":"Original DVB audio",
     arabic?"صوت CFG / IP Audio":"CFG / IP Audio"
    };break;
    case 1:{
     JSONArray tracks=channel.audio();java.util.ArrayList<String> names=new java.util.ArrayList<>();
     for(int n=0;n<tracks.length();n++){
      JSONObject t=tracks.optJSONObject(n);
      if(t!=null&&DvbPlayRequest.audio(t.optString("Type"))>=0&&
        t.optInt("PID",8191)>0&&t.optInt("PID",8191)<8191){
       names.add((names.size()==activeAudio?"☑  ":"    ")+
         t.optString("Lang","und")+"  ·  "+t.optString("Type"));
      }
     }
     rows[0]=names.isEmpty()?new String[]{arabic?"لا توجد مسارات صوتية":"No audio track metadata"}:
       names.toArray(new String[0]);break;
    }
    case 2:rows[0]=new String[]{arabic?"إعدادات الصورة من النظام":"Display settings are controlled by the device"};break;
    case 3:rows[0]=new String[]{arabic?"إعدادات HDMI من النظام":"HDMI mode is controlled by the device"};break;
    case 4:{
     JSONArray list=channel.data.optJSONArray("Sub");
     java.util.ArrayList<String> names=new java.util.ArrayList<>();
     if(list!=null)for(int n=0;n<list.length();n++){
      JSONObject t=list.optJSONObject(n);
      if(t!=null)names.add(t.optString("Lang","und")+"  ["+
       (t.optInt("Type")==1?"ttx":"dvb")+"] · PID "+t.optInt("PID"));
     }
     rows[0]=names.isEmpty()?new String[]{arabic?"لا توجد مسارات في قاعدة القنوات":"No saved subtitle track"}:
       names.toArray(new String[0]);break;
    }
    default:rows[0]=new String[]{
      "🔴 Set Translation [Arabic]","🟢 Set Subtitle Color",
      arabic?"إعدادات OCR والترجمة":"OCR and translation settings"
    };break;
   }
   leftAdapter.notifyDataSetChanged();rightAdapter.notifyDataSetChanged();right.setSelection(0);
  };
  left.setOnItemClickListener((p,v,index,id)->{activeSection[0]=index;repaint[0].run();right.requestFocus();});
  right.setOnItemClickListener((p,v,index,id)->{
   switch(activeSection[0]){
    case 0:
     if(index==0)actions.restoreNativeAudio();else actions.openCfgAudio();
     dialog.dismiss();break;
    case 1:
     if(channel.audio().length()>0&&rows[0].length>0&&!rows[0][0].contains("No audio"))
      actions.audioTrack(index);
     dialog.dismiss();break;
    case 4:
     // Saved CHL subtitle PIDs are metadata, not a writable OEM subtitle track API.
     actions.openCaptions();dialog.dismiss();break;
    case 5:
     if(index==0){
      a.getSharedPreferences("smarthub",0).edit().putBoolean("live_translate",true)
       .putBoolean("native_caption_ocr",true)
       .putString("live_target_language","ar").apply();
      Toast.makeText(a,arabic?"تم اختيار الترجمة العربية · افتح إعدادات OCR للموافقة":"Arabic chosen · enable OCR capture consent",Toast.LENGTH_LONG).show();
     }else actions.openCaptions();
     dialog.dismiss();break;
    default:
     Toast.makeText(a,arabic?"استخدم إعدادات الجهاز الأصلية":"Use the receiver's original system settings",Toast.LENGTH_SHORT).show();
   }
  });
  LinearLayout footer=new LinearLayout(a);
  footer.setGravity(Gravity.CENTER_VERTICAL);main.addView(footer,new LinearLayout.LayoutParams(-1,dp(a,55)));
  String[] shortcuts={arabic?"🔴 Delay":"🔴 Delay",arabic?"🟢 Import CFG":"🟢 Import CFG",arabic?"🟡 Delete Import":"🟡 Delete Import"};
  for(String action:shortcuts){
   TextView b=label(a,action,14,Color.WHITE,true);
   footer.addView(b,new LinearLayout.LayoutParams(0,-1,1));
   b.setOnClickListener(v->{actions.openCfgAudio();dialog.dismiss();});
  }
  dialog.show();Window w=dialog.getWindow();
  if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);
   w.setLayout(Math.min(dp(a,980),a.getResources().getDisplayMetrics().widthPixels-dp(a,40)),
     Math.min(dp(a,610),a.getResources().getDisplayMetrics().heightPixels-dp(a,35)));}
  repaint[0].run();left.setSelection(activeSection[0]);right.requestFocus();
 }
}
