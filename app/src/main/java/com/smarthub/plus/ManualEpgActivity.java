package com.smarthub.plus;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RX6 skin-aware, explicit single-channel external EPG import. Never scheduled. */
public final class ManualEpgActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile boolean closed;
 private ManualEpgStore.Preview preview;
 private SatelliteState selected;
 private TextView status,results;
 private Button save;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  selected=SatelliteState.read(this);
  LinearLayout content=page(tx("جلب EPG خارجي يدوياً","Manual external EPG"),
   tx("EPG Pro وDVB EIT أولاً. لا تحميل خارجي تلقائي ولا استبدال لبيانات المصنع.",
      "Factory EPG Pro and DVB EIT always take priority. No automatic external downloads."));
  if(selected.channelName.toUpperCase(java.util.Locale.ROOT).contains("OSN")){
   final String directGuide=OsnGuideReference.urlFor(selected.channelName);
   action(content,tx("فتح دليل برامج OSN الرسمي للمقارنة",
     "Open official OSN TV Guide for reference"),()->{
    try{
     android.content.Intent browser=new android.content.Intent(
       android.content.Intent.ACTION_VIEW,
       android.net.Uri.parse(directGuide.isEmpty()?OsnGuideReference.channels():directGuide));
     startActivity(browser);
    }catch(android.content.ActivityNotFoundException missing){
     toast(tx("لا يوجد متصفح لفتح موقع OSN","No browser available to open OSN"));
    }
   });
   note(content,tx("صفحة OSN للمراجعة فقط. لا تتوفر هنا API عامة موثقة؛ لا تحل محل EPG المصنع.",
    "An exact OSN Iraq channel-guide page opens where matched. Reference only; there is no verified public API. Factory/DVB EPG remains primary."));
   note(content,tx("الربط الاحتياطي لجدول OSN يستخدم EPGShare SA1، وليس واجهة OSN الرسمية. يُستخدم لسد النقص فقط.",
     "Optional OSN missing-event fallback uses EPGShare SA1, not an official OSN API. Factory/DVB EPG remains primary."));
   CheckBox osnGap=check(content,
     tx("تفعيل سد نقص EPG لقنوات OSN المطابقة فقط",
        "Fill missing EPG on exact OSN channels only"),
     getSharedPreferences("smarthub",0).getBoolean(OsnEpgFallback.ENABLED,false));
   osnGap.setOnCheckedChangeListener((button,enabled)->{
    getSharedPreferences("smarthub",0).edit()
     .putBoolean(OsnEpgFallback.ENABLED,enabled)
     .putLong("manual_epg_changed_at",System.currentTimeMillis()).apply();
   });
   note(content,tx("حالة ربط OSN: ","OSN EPG status: ")+
     getSharedPreferences("smarthub",0).getString(OsnEpgFallback.STATUS,"NOT_CHECKED"));
  }
  LinearLayout identity=card(content);
  identity.addView(text(tx("القناة المحددة: ","Selected channel: ")+
    selected.channelName+"  ·  "+selected.channelId,17,true));
  note(identity,tx("الجدول الخارجي خاص بالقناة الحالية فقط؛ يتم حفظه في ملفات التطبيق وليس قاعدة بيانات المصنع.",
    "Imported guide is scoped to this service only, in app files, never in the OEM EPG database."));
  if(!ManualEpgStore.validChannel(selected.channelId,selected.channelName)){
   note(identity,tx("افتح قناة DVB أولاً ثم ارجع إلى هذه الشاشة.",
     "Tune a DVB channel before using manual EPG."));
   return;
  }
  LinearLayout options=card(content);
  options.addView(text(tx("مصدر الجدول","Guide source"),18,true));
  RadioGroup mode=new RadioGroup(this);
  RadioButton epg=new RadioButton(this),xml=new RadioButton(this);
  epg.setText(tx("EPGShare — قائمة ملفات الدول","EPGShare — country feeds"));
  xml.setText(tx("XMLTV — رابط HTTPS مباشر","XMLTV — direct HTTPS feed"));
  epg.setTextColor(WHITE);xml.setTextColor(WHITE);
  epg.setId(android.view.View.generateViewId());xml.setId(android.view.View.generateViewId());
  mode.addView(epg);mode.addView(xml);mode.check(epg.getId());options.addView(mode);
  EditText feed=field(options,tx("ملف EPGShare (مثل SA1 لقنوات OSN)",
    "EPGShare feed, e.g. SA1 for OSN"),"SA1");
  EditText url=field(options,tx("رابط XMLTV الآمن HTTPS (XML أو XML.GZ)",
    "Public HTTPS URL, XML or XML.GZ"),"");
  EditText xmlId=field(options,tx("معرّف القناة داخل XMLTV — اختياري",
    "Exact XMLTV channel ID (optional)"),"");
  url.setVisibility(android.view.View.GONE);
  mode.setOnCheckedChangeListener((g,id)->{
   boolean custom=id==xml.getId();
   feed.setVisibility(custom?android.view.View.GONE:android.view.View.VISIBLE);
   url.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
   preview=null;save.setEnabled(false);
  });
  LinearLayout output=card(content);
  status=text(tx("لا توجد عملية تحميل. بيانات المصنع وDVB غير متأثرة.",
    "No download requested. Factory and DVB data are untouched."),15,false);
  output.addView(status);
  results=text("",14,false);output.addView(results);
  save=action(output,tx("حفظ للقناة — سد النقص فقط","Save for this channel — fill gaps only"),()->{
   if(preview==null)return;
   try{
    ManualEpgStore.save(this,preview);
    status.setText(tx("تم الحفظ. المصدر الخارجي لا يستبدل بيانات المصنع أو DVB.",
      "Saved. Manufacturer and DVB events remain first."));
   }catch(Exception error){
    status.setText(tx("تعذر حفظ EPG: ","Cannot save EPG: ")+error.getClass().getSimpleName());
   }
  });
  save.setEnabled(false);
  action(options,tx("جلب ومعاينة فقط","Download and preview only"),()->{
   if(closed)return;
   preview=null;save.setEnabled(false);
   String provider=mode.getCheckedRadioButtonId()==xml.getId()?"XMLTV":"EPGShare";
   String selectedFeed=feed.getText().toString().trim().toUpperCase(Locale.ROOT);
   String selectedUrl=url.getText().toString().trim();
   String selectedId=xmlId.getText().toString().trim();
   status.setText(tx("جاري جلب الملف يدوياً…","Downloading the requested guide…"));
   results.setText("");
   SatelliteState requested=selected.copy();
   io.execute(()->{
    try{
     ManualEpgStore.Preview received=ManualEpgStore.fetch(
       getApplicationContext(),requested,provider,selectedFeed,selectedUrl,selectedId);
     main.post(()->{
      if(closed)return;
      if(!requested.channelId.equals(SatelliteState.read(this).channelId)){
       status.setText(tx("القناة تغيّرت؛ افتح الشاشة مجدداً.",
         "Channel changed. Reopen the manual guide screen."));return;
      }
      preview=received;save.setEnabled(true);
      status.setText(tx("تم العثور على ","Matched ")+received.events.size()+
        tx(" برنامجاً من "," programmes from ")+received.source);
      SimpleDateFormat date=new SimpleDateFormat("dd/MM HH:mm",Locale.ROOT);
      StringBuilder b=new StringBuilder();
      for(int i=0;i<Math.min(received.events.size(),8);i++){
       NativeEpgEvents.Event e=received.events.get(i);
       b.append(date.format(new Date(e.start))).append("  ·  ")
        .append(e.title).append("\n");
      }
      results.setText(b.toString());
     });
    }catch(Exception error){
     main.post(()->{if(!closed)status.setText(
       tx("تعذر الجلب: ","Import failed: ")+
       (error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));});
    }
   });
  });
  action(content,tx("حذف الجدول الخارجي لهذه القناة","Remove manual guide for this channel"),()->{
   ManualEpgStore.clear(this,selected.channelId);
   preview=null;save.setEnabled(false);
   status.setText(tx("حُذف الجدول اليدوي فقط؛ لم نلمس EPG المصنع.",
     "Only manual guide was removed. Manufacturer guide is unchanged."));
   results.setText("");
  });
  note(content,tx("لا يوجد تحديث تلقائي من EPGShare أو XMLTV. الصور من TMDB وغيرها تبقى مستقلة عن مصدر EPG.",
    "EPGShare/XMLTV are never polled automatically. Poster providers are controlled separately."));
 }
 @Override protected void onDestroy(){
  closed=true;io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();
 }
}
