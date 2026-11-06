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
  EditText url=new EditText(this);
  url.setSingleLine(true);
  url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  url.setHint("https://.../channel.ts  ·  udp://239.x.x.x:port");
  info.addView(url);
  action(info,tx("تشغيل عبر LibVLC داخل IRAQhub","Play inside IRAQhub with LibVLC"),()->{
   String input=url.getText().toString().trim();
   if(!Software422Source.validNetwork(input)){
    toast(tx("الرابط غير صالح. أدخل HTTP / HTTPS / RTSP / RTP / UDP.",
      "Invalid source. Use HTTP, HTTPS, RTSP, RTP or UDP."));return;
   }
   startActivity(new Intent(this,SoftwareVideoActivity.class).putExtra("media_uri",input));
  });

  LinearLayout status=card(body);
  status.addView(text(tx("قنوات DVB الفضائية","DVB satellite channels"),20,true));
  note(status,tx("تحتاج 4:2:2 الفضائية إلى تسليم بيانات الفيديو الخام من التونر إلى LibVLC. ملف 3.7.0 المجمع فيه LiveTsRelay وNativeCapture، لكن سورس 3.7.0 الموجود بالمستودع لا يحتويهما. لن أضيف زر قناة 4:2:2 وهمياً قبل إعادة دمج مسار TS والتحقق من الإشارة.",
    "DVB 4:2:2 needs MPEG-TS delivery from the tuner to LibVLC. The compiled 3.7.0 APK contains LiveTsRelay/NativeCapture, but the corresponding 3.7.0 source branch lacks them. Native DVB software decoding requires restoring that bridge and verifying stream access."));
  action(status,tx("قنوات IRAQhub الفضائية","IRAQhub satellite channels"),
   ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));
  action(status,tx("معلومات دعم Decoder والتوافق","Decoder capabilities"),
   ()->startActivity(new Intent(this,CompatibilityActivity.class)));
 }
}
