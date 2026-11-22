package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * RX7 4:2:2 user-friendly compatibility entry. One action selects a DVB
 * channel. If a verified ARMv7 JNI decoder is included, it uses avcodec ->
 * swscale YUV420P 800x450 -> RGBA Android Surface, NOT an H.264 transcoder.
 * If decoding fails the existing isolated libmpv path is tried only once.
 * The OEM tuner must first deliver actual MPEG-TS. Normal 4:2:0 TV is unchanged.
 */
public final class Software422CenterActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("توافق 4:2:2 · RX7","4:2:2 compatibility · RX7"),
    tx("خيار واحد للمشاهدة، مع رجوع احتياطي عند تعطل فك الفيديو.",
      "One option to watch, with a safe decoder fallback."));
  LinearLayout main=card(body);
  main.addView(text(tx("تشغيل فيد 4:2:2","Watch a 4:2:2 feed"),22,true));
  boolean installed=Native422Decoder.available();
  boolean enabled=prefs().getBoolean(Rx7Compat422Policy.ENABLED,true);
  note(main,installed?
     tx("FFmpeg ARMv7 جاهز: يحوّل الصورة إلى 4:2:0 بدقة 800×450، ثم يعرضها على الشاشة.",
       "ARMv7 FFmpeg ready: converts decoded pictures to 4:2:0 at 800×450 for Android Surface."):
     tx("المحرك المستقل غير مضمّن بهذه النسخة؛ سيستخدم MPV الاحتياطي.",
       "Native FFmpeg is not bundled in this APK; MPV remains available."));
  CheckBox compatibility=check(main,
    tx("استخدم محرك FFmpeg تلقائياً عند اختيار 4:2:2",
       "Prefer FFmpeg for selected 4:2:2 feeds"),
    installed&&enabled);
  compatibility.setEnabled(installed);
  compatibility.setOnCheckedChangeListener((button,checked)->
    prefs().edit().putBoolean(Rx7Compat422Policy.ENABLED,
      checked&&Native422Decoder.available()).apply());
  action(main,tx("اختيار قناة 4:2:2 وتشغيلها","Choose and play a 4:2:2 channel"),
    ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("prefer_422_engine",true)));
  String captureError=prefs().getString("software_ts_capture_error","NONE");
  if(Rx7Compat422Policy.isCaptureMissing(captureError))
    note(main,tx("تنبيه: في آخر محاولة لم يصل MPEG-TS من الرسيفر. يجب إصلاح الالتقاط ليعمل أي محرك.",
      "Last attempt had no MPEG-TS from the tuner. Capture needs fixing before decoding."));
  note(main,tx("الصوت يتوقف أثناء تجربة FFmpeg لتخفيف الحمل. اضغط رجوع للعودة؛ لا يتم تغيير إعدادات الطبق.",
    "Audio is off for this FFmpeg test to reduce CPU load. Back exits without changing dish settings."));

  LinearLayout expert=card(body);
  expert.setVisibility(View.GONE);
  action(body,tx("خيارات إضافية واختبارات","More options and tests"),()->
    expert.setVisibility(expert.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
  action(expert,tx("إعدادات الدقة والمحركات","Resolution & engine settings"),
    ()->startActivity(new Intent(this,Unified422SettingsActivity.class)));
  action(expert,tx("فحص الجهاز والديكودر","Device/decoder diagnostics"),
    ()->startActivity(new Intent(this,CompatibilityActivity.class)));
  note(expert,tx("الفيديو الأصلي يُفك كاملاً بالمعالج، ثم يتحول إلى 4:2:0 قبل العرض. ليس ترميز H.264 من جديد، ولا يثبت دعم MediaCodec لعمق 4:2:2.",
    "The full source is software-decoded then converted to 4:2:0 before display. No new H.264 encoding or proven 4:2:2 hardware support."));
  EditText url=field(expert,tx("رابط بث مرخص لاختبار MPV/VLC","Authorised stream URL for MPV/VLC tests"),"");
  url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  action(expert,tx("اختبار رابط بث","Test network stream"),()->{
   String value=url.getText().toString().trim();
   if(!Software422Source.validNetwork(value)){
    toast(tx("رابط البث غير صالح","Invalid stream URL"));return;
   }
   startActivity(new Intent(this,SoftwareVideoActivity.class)
     .putExtra("media_uri",value).putExtra("decoder_engine","unified"));
  });
 }
}
