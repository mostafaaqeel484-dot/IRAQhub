package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Single 4:2:2/4:2:0 entry point. Internal LibVLC and libmpv decoders are
 * retained only for bounded automatic fallback on the same authorised TS.
 * Android VPU 4:2:2 and moving frames require physical X5M validation.
 */
public final class Software422CenterActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("IRAQhub 422 · مشغل واحد","IRAQhub 422 · one playback engine"),
    tx("واجهة واحدة لتشغيل LibVLC/FFmpeg أو libmpv/FFmpeg تلقائياً، بدون محركات مكررة في القائمة.",
       "One player UI: LibVLC/FFmpeg and libmpv/FFmpeg are internal recovery backends."));
  LinearLayout settings=card(body);
  settings.addView(text(tx("المحرك الموحد · X5M","Unified 422 · X5M"),21,true));
  note(settings,tx("يدعم إخراج 4:2:2 أو 4:2:0 بدقات 1080 / 720 / 540 / 360 / 260 وGPU FAST. الدقة تعني خرج العرض بعد فك المصدر؛ لا تخفّف تلقائياً تكلفة فك HEVC الأصلي.",
    "Output 4:2:2 or 4:2:0 at 1080 / 720 / 540 / 360 / 260, plus GPU FAST. These are post-decode display sizes; CPU source decode is unchanged."));
  action(settings,tx("دقة العرض / GPU FAST / SW وHW","Output format / GPU FAST / SW and HW"),
    ()->startActivity(new Intent(this,Unified422SettingsActivity.class)));
  note(settings,Software422Profile.status(this));
  LinearLayout source=card(body);
  source.addView(text(tx("تشغيل رابط بث مصرح به","Play an authorised network stream"),18,true));
  EditText url=new EditText(this);
  url.setSingleLine(true);
  url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  url.setHint("https://.../channel.ts");
  source.addView(url);
  action(source,tx("تشغيل بـIRAQhub 422 الموحد","Play with unified IRAQhub 422"),()->{
   String value=url.getText().toString().trim();
   if(!Software422Source.validNetwork(value)){
    toast(tx("رابط البث غير صالح","Invalid stream URL"));return;
   }
   startActivity(new Intent(this,SoftwareVideoActivity.class)
    .putExtra("media_uri",value).putExtra("decoder_engine","unified"));
  });
  LinearLayout sat=card(body);
  sat.addView(text(tx("قنوات DVB الفضائية","DVB satellite services"),18,true));
  note(sat,tx("يشغّل MPEG-TS حقيقي من الموالف؛ لا يكفي تعريف 4:2:2 لدعم الديكودر العتادي. تُعرض إحصاءات الإطارات الفعلية للتشخيص.",
    "Uses actual tuner MPEG-TS. A codec name does not prove hardware 4:2:2. Displayed/decoded frame counters remain available."));
  action(sat,tx("اختيار قناة للمحرك الموحد","Choose a DVB channel for unified decoder"),
    ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("prefer_422_engine",true)));
  action(sat,tx("اختبار قدرات الفيديو وتشخيص X5M","X5M decoder diagnostics"),
    ()->startActivity(new Intent(this,CompatibilityActivity.class)));
 }
}
