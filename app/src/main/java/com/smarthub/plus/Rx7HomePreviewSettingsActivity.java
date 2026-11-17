package com.smarthub.plus;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** RX7: a *separately authorised* network preview can be mapped to the
 * current receiver channel without creating a second DVB tuner session. */
public final class Rx7HomePreviewSettingsActivity extends HubActivity {
 private EditText stream;
 private CheckBox enabled;
 private TextView status;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("RX7 · معاينة التلفزيون المباشر","RX7 · Home Live TV Preview"),
    tx("تظهر صورة القناة والبرنامج أولاً، ويمكن تشغيل معاينة صامتة عند وجود بث مرخّص لنفس القناة.",
      "Channel metadata and artwork appear first. Muted preview is optional for an authorised stream of the current channel."));
  note(page,tx("لا يدعم مسار DVB المصنع مشاركة الفيديو مع بطاقة اللانشر بشكل مؤكد. المعاينة هنا تستخدم رابط HTTP(S) تحدده أنت، ولا تفتح موالفاً ثانياً أو تغيّر التردد.",
    "The OEM DVB decoder has no verified shared preview surface. This preview uses your explicit HTTP(S) channel stream only; it never opens another tuner or retunes DVB."));

  SatelliteState current=SatelliteState.read(this);
  status=text("",15,false);
  status.setTextColor(0xffe2c07a);
  page.addView(status);
  enabled=check(page,tx("تفعيل معاينة الفيديو عند التركيز على البطاقة فقط",
      "Enable muted video preview only while the Home card has focus"),
     prefs().getBoolean(Rx7PreviewPolicy.ENABLED,false));
  stream=field(page,tx("رابط بث القناة الحالية HTTP(S)","Current-channel HTTP(S) stream URL"),
     prefs().getString(Rx7PreviewPolicy.STREAM,""));
  stream.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  stream.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
  note(page,tx("البيانات محفوظة على هذا الجهاز. يمكن أن يحتوي الرابط على رمز وصول؛ لا تشاركه مع الآخرين. معاينة الفيديو صامتة دائماً وتتوقف عند الخروج أو تغيير القناة.",
    "Stored only on this receiver. Links may contain access tokens: keep them private. Preview is always muted and stops when leaving Home, changing focus or switching channels."));
  action(page,tx("ربط الرابط بالقناة الحالية وحفظه","Bind stream to current channel and save"),()->{
   String uri=stream.getText().toString().trim();
   SatelliteState active=SatelliteState.read(this);
   if(active.channelId.isEmpty()){
    toast(tx("لا توجد هوية قناة محفوظة؛ شغّل القناة أولاً","No current channel identity; tune it first"));return;
   }
   if(!Rx7PreviewPolicy.validStream(uri)){
    toast(tx("الرابط غير صالح: استخدم HTTP أو HTTPS بدون اسم مستخدم أو Fragment",
      "Enter a valid HTTP(S) URL without embedded credentials/fragment"));return;
   }
   prefs().edit()
     .putString(Rx7PreviewPolicy.STREAM,uri)
     .putString(Rx7PreviewPolicy.BOUND,active.channelId)
     .putBoolean(Rx7PreviewPolicy.ENABLED,enabled.isChecked())
     .apply();
   updateStatus();
   toast(tx("تم حفظ الرابط للقناة الحالية","Stream assigned to the current channel"));
  });
  action(page,tx("تعطيل المعاينة وحذف الرابط","Disable preview and delete stored link"),()->{
   prefs().edit().putBoolean(Rx7PreviewPolicy.ENABLED,false)
    .remove(Rx7PreviewPolicy.STREAM).remove(Rx7PreviewPolicy.BOUND).apply();
   enabled.setChecked(false);stream.setText("");updateStatus();
  });
  note(page,tx("حتى عند تعطيل الفيديو، تظهر معلومات EPG ولوغو القناة والبوستر أعلى يسار البطاقة من الذاكرة المحلية. الضغط على OK يفتح DVB الكامل.",
    "Even with video disabled, the Home card shows the channel, EPG and cached poster at top-left. OK opens full DVB playback."));
  updateStatus();
 }
 private void updateStatus(){
  SharedPreferences p=prefs();
  String bound=p.getString(Rx7PreviewPolicy.BOUND,"");
  String current=SatelliteState.read(this).channelId;
  String label=bound.isEmpty()?tx("لا يوجد رابط مرتبط بقناة","No stream assigned"):
   bound.equals(current)?tx("الرابط مرتبط بالقناة الحالية","Bound to current channel"):
   tx("الرابط محفوظ لقناة أخرى؛ لن يُشغّل على القناة الحالية","Stream belongs to another channel; will not play");
  status.setText(label+" · "+(p.getBoolean(Rx7PreviewPolicy.ENABLED,false)?"ON":"OFF"));
 }
 @Override protected void onResume(){super.onResume();if(status!=null)updateStatus();}
}
