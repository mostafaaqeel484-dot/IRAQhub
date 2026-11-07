package com.smarthub.plus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.app.AlertDialog;

/**
 * Native IRAQhub user interface for the existing LibVLC software 4:2:2 decoder.
 * Satellite DVB needs a real MPEG-TS source from the tuner; this screen does
 * not invent one or switch to Aurum.
 */
public final class Software422CenterActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("مركز تشغيل 4:2:2","4:2:2 software playback centre"),
   tx("LibVLC مع فك برمجي للملفات والبث المتاح داخل IRAQhub","LibVLC software decoding for accessible video streams"));
  LinearLayout info=card(body);
  info.addView(text(tx("مشغل LibVLC موجود في IRAQhub 3.7.0","LibVLC is already included in IRAQhub 3.7.0"),20,true));
  note(info,tx("يمكن تجربة MPEG-2 وAVC High422 وHEVC RExt من رابط بث مباشر. قدرة المعالج تحدد سلاسة التشغيل. القنوات الفضائية تحتاج تصدير MPEG-TS من خدمة التونر؛ وجود المكتبة وحده لا يكفي.",
    "Try MPEG-2, AVC High422 and HEVC RExt from a genuine stream URL. Performance depends on CPU. Satellite DVB requires a real tuner MPEG-TS output; LibVLC alone cannot access the tuner."));
  LinearLayout performance=card(body);
  performance.addView(text(tx("أداء معالج X5M وفك 4:2:2","X5M CPU / software 4:2:2"),20,true));
  note(performance,Software422Profile.status(this));
  action(performance,tx("اختيار وضع الأداء: سريع / متوازن / جودة","Performance mode: smooth / balanced / quality"),()->{
   String[] modes={Software422Profile.SMOOTH,Software422Profile.BALANCED,Software422Profile.QUALITY};
   String[] labels={tx("سريع · مناسب لبث ثقيل","Smooth · heavy feeds"),
     tx("متوازن · التشغيل الاعتيادي","Balanced · standard"),
     tx("جودة · فك كل الإطارات","Quality · decode all frames")};
   int current=1;
   for(int i=0;i<modes.length;i++)if(modes[i].equals(Software422Profile.current(this)))current=i;
   new AlertDialog.Builder(this).setTitle(tx("وضع 4:2:2","4:2:2 mode"))
     .setSingleChoiceItems(labels,current,(d,selected)->{
      Software422Profile.save(this,modes[selected]);d.dismiss();
      toast(tx("حُفظ نمط التشغيل","Playback mode saved"));
      recreate();
     }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
  });
    EditText url=new EditText(this);
  url.setSingleLine(true);
  url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  url.setHint("https://.../channel.ts  ·  udp://239.x.x.x:port");
  info.addView(url);
  action(info,tx("تشغيل محرك IRAQhub 422 المستقل · mpv/FFmpeg",
    "Play with isolated IRAQhub 422 engine · mpv/FFmpeg"),()->{
   String input=url.getText().toString().trim();
   if(!Software422Source.validNetwork(input)){
    toast(tx("الرابط غير صالح؛ استخدم بثاً عبر HTTP/HTTPS/RTSP/RTP/UDP",
      "Invalid source; use a supported network stream"));return;
   }
   startActivity(new Intent(this,Independent422Activity.class)
    .putExtra("media_uri",input)
    .putExtra("video_only",prefs().getBoolean("independent_422_video_only",true)));
  });
  action(info,tx("تشغيل عبر LibVLC داخل IRAQhub","Play inside IRAQhub with LibVLC"),()->{
   String input=url.getText().toString().trim();
   if(!Software422Source.validNetwork(input)){
    toast(tx("الرابط غير صالح. أدخل HTTP / HTTPS / RTSP / RTP / UDP.",
      "Invalid source. Use HTTP, HTTPS, RTSP, RTP or UDP."));return;
   }
   startActivity(new Intent(this,SoftwareVideoActivity.class).putExtra("media_uri",input));
  });

  LinearLayout dedicated=card(body);
  dedicated.addView(text(tx("محرك فك مستقل بعملية Android منفصلة",
    "Independent Android decoder process"),19,true));
  note(dedicated,tx("MPV/FFmpeg منفصل عن LibVLC وواجهة IRAQhub. يبدأ الفيديو بدون فك صوت افتراضياً لتوفير CPU، مع إمكانية تفعيل الصوت داخل المشغل. يستخدم نفس DVB MPEG-TS الحقيقي ولا يحتاج Root.",
    "Separate MPV/FFmpeg process, not LibVLC. Video-only by default to save CPU, audio toggle in player. Same real DVB TS; no root required."));
  action(dedicated,tx("تشغيل قنوات DVB بالمحرك المستقل",
    "Choose DVB channel for isolated decoder"),
   ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("prefer_422_engine",true)));
  LinearLayout status=card(body);
  status.addView(text(tx("قنوات DVB الفضائية","DVB satellite channels"),20,true));
  note(status,tx("تم ربط التقاط TS المحلي من التونر بمشغل LibVLC، مع تخزين مؤقت محدود وحماية 4:2:2 من انقطاع الصوت. نجاح فك الصورة يعتمد على دعم الرسيفر الحقيقي وبيانات PID؛ المشفرة تحتاج فك BISS مُصرّحاً به داخل الجهاز قبل عرضها برمجياً.",
    "The native tuner TS capture is connected to LibVLC. Playback still requires real PIDs, receiver-authorised descrambling for encrypted feeds and sufficient CPU. Software 4:2:2 cannot guarantee smooth 1080i/4K on this chipset."));
  action(status,tx("قنوات IRAQhub الفضائية","IRAQhub satellite channels"),
   ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("prefer_422_engine",true)));
  action(status,tx("معلومات دعم Decoder والتوافق","Decoder capabilities"),
   ()->startActivity(new Intent(this,CompatibilityActivity.class)));
 }
}
