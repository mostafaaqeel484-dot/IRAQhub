package com.smarthub.plus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One receiver-facing settings screen, two internal FFmpeg-backed backends:
 * LibVLC (software) for automatic startup; libmpv for explicit post-decode
 * 4:2:2/4:2:0 output conversions or GPU FAST. Neither changes the input
 * 1080p/HEVC bitstream or guarantees MediaCodec 4:2:2 hardware support.
 */
public final class Unified422SettingsActivity extends HubActivity {
 private TextView summary;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("IRAQhub 422 · المحرك الموحد","IRAQhub 422 · unified playback"),
    tx("مركز واحد للتشغيل مع رجوع احتياطي تلقائي؛ FFmpeg مضمّن داخل LibVLC وlibmpv.",
       "One playback entry, with guarded internal fallback; LibVLC and libmpv both use FFmpeg."));
  LinearLayout video=card(body);
  video.addView(text(tx("دقة الإخراج والتنسيق","Output format and resolution"),22,true));
  note(video,tx("هذه الدقات تخص الصورة بعد فك الفيديو؛ مصدر 1080p 4:2:2 يبقى بحاجة لفكّ أصلي كامل. 4:2:2 10-bit قد لا تقبله شاشة Android، لذا عند الخطأ نرجع آلياً إلى مسار آخر.",
   "Post-decode display formats only; lowering resolution does not reduce original source decode work. The display may reject 4:2:2 10-bit; fallback remains available."));
  summary=text("",14,false);video.addView(summary);
  action(video,tx("اختر 422 أو 420 مع 1080 / 720 / 540 / 360 / 260 أو GPU FAST",
    "Select 422/420: 1080, 720, 540, 360, 260 or GPU FAST"),this::chooseOutput);
  action(video,tx("طريقة فك الفيديو: SW أو HW تجريبي","Decoder: software or experimental hardware"),
    this::chooseDecoder);
  android.widget.CheckBox only=check(video,
    tx("فيديو فقط لتخفيف الحمل","Video only, skip secondary audio decoding"),
    prefs().getBoolean("unified_422_video_only",true));
  only.setOnCheckedChangeListener((view,checked)->prefs().edit()
    .putBoolean("unified_422_video_only",checked).apply());
  LinearLayout fallback=card(body);
  fallback.addView(text(tx("الاستقرار والاحتياط","Recovery and validation"),20,true));
  note(fallback,tx("Auto يبدأ ببرنامج LibVLC/FFmpeg؛ خيارات الدقة وGPU FAST تشغل libmpv/FFmpeg أولاً، مع رجوع محدود إلى LibVLC عند فشل الفتح. HW لا يساوي دعماً مثبتاً لـ4:2:2.",
    "Auto starts LibVLC/FFmpeg. Explicit sizes and GPU FAST start libmpv/FFmpeg, with one guarded fallback to LibVLC on load failures. HW is never reported as proven 4:2:2."));
  LinearLayout nativeSettings=card(body);
  nativeSettings.addView(text(tx("محرك 422 JNI مستقل · تجريبي",
    "Direct Native422 JNI · experimental"),20,true));
  note(nativeSettings,tx("يفك libavcodec مصدر MPEG-TS مباشرة دون MPV أو VLC، يعرض الإطارات على Surface. يحتاج مكتبات FFmpeg ARMv7 الرسمية الموقّعة؛ العمل الأولي فيديو فقط وGPU FAST يعني Surface غير مصغّر وليس فك HEVC عتادياً.",
    "Direct libavcodec MPEG-TS, no MPV or VLC decoder. Requires verified FFmpeg ARMv7 libraries. Initial prototype is video-only; GPU FAST means unfiltered Android Surface presentation, NOT GPU HEVC decoding."));
  android.widget.CheckBox nativeExperiment=check(nativeSettings,
    tx("تجربة FFmpeg JNI على قناة DVB · مع رجوع MPV عند الفشل",
       "Try direct JNI FFmpeg on DVB · MPV fallback on failure"),
    Native422Decoder.available()&&prefs().getBoolean("native422_experiment",false));
  nativeExperiment.setEnabled(Native422Decoder.available());
  nativeExperiment.setOnCheckedChangeListener((v,on)->
    prefs().edit().putBoolean("native422_experiment",
      on&&Native422Decoder.available()).apply());
  if(!Native422Decoder.available())
    note(nativeSettings,tx("مكتبة JNI لم تُبنَ أو تُضمّن في هذا الإصدار",
      "JNI library not built into this APK; option safely disabled."));
  action(fallback,tx("إعدادات التخزين المؤقت وسلاسة LibVLC",
    "LibVLC latency/quality profile"),this::chooseVlcProfile);
  action(fallback,tx("فحص معلومات المفكك على الجهاز","Review actual decoder capabilities"),
    ()->startActivity(new android.content.Intent(this,CompatibilityActivity.class)));
  updateSummary();
 }
 private void updateSummary(){
  if(summary==null)return;
  String mode=Mpv422FluidPolicy.normalized(prefs().getString("unified_422_output_mode",
    Mpv422FluidPolicy.AUTO));
  String decoder=Mpv422FluidPolicy.decoderMode(prefs().getString("independent_422_decoder_mode",
    Mpv422FluidPolicy.SW));
  summary.setText(tx("الإخراج: ","Output: ")+Mpv422FluidPolicy.readable(mode)+
   "\n"+tx("فك الفيديو: ","Decoder: ")+decoder.toUpperCase(java.util.Locale.ROOT)+
   tx(" (HW تجريبي)", " (HW experimental)")+
   "\nLibVLC: "+Software422Profile.current(this)+
   "\nDirect JNI ARMv7: "+(Native422Decoder.available()?"COMPILED":"NOT_BUNDLED")+
   " / "+(prefs().getBoolean("native422_experiment",false)?"OPT_IN":"OFF"));
 }
 private void chooseOutput(){
  final String[] values=Mpv422FluidPolicy.publicModes();
  final String[] names=new String[values.length];
  int selected=0;
  String saved=Mpv422FluidPolicy.normalized(prefs().getString("unified_422_output_mode",
    Mpv422FluidPolicy.AUTO));
  for(int i=0;i<values.length;i++){
   names[i]=Mpv422FluidPolicy.AUTO.equals(values[i])?
     tx("تلقائي · LibVLC أولاً / MPV عند التعطل","AUTO · LibVLC first, MPV recovery"):
     Mpv422FluidPolicy.readable(values[i]);
   if(values[i].equals(saved))selected=i;
  }
  new AlertDialog.Builder(this).setTitle(tx("دقة العرض · 420/422","Output resolution / chroma"))
   .setSingleChoiceItems(names,selected,(dialog,which)->{
    String chosen=values[which];
    prefs().edit().putString("unified_422_output_mode",chosen)
      .putString("independent_422_fluid_mode",chosen)
      .putString("native422_output_mode",chosen).apply();
    dialog.dismiss();updateSummary();
   }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void chooseDecoder(){
  String[] values={Mpv422FluidPolicy.SW,Mpv422FluidPolicy.HW};
  String[] labels={tx("SW · FFmpeg برمجياً · الأفضل لتشخيص 4:2:2",
    "SW · FFmpeg software, reliable 4:2:2 diagnostic"),
    tx("HW · تجربة MediaCodec على الصيغ المدعومة فقط",
     "HW · MediaCodec experiment for supported formats only")};
  boolean hw=Mpv422FluidPolicy.HW.equals(prefs().getString("independent_422_decoder_mode",
    Mpv422FluidPolicy.SW));
  new AlertDialog.Builder(this).setTitle("SW / HW")
    .setSingleChoiceItems(labels,hw?1:0,(dialog,which)->{
     prefs().edit().putString("independent_422_decoder_mode",values[which]).apply();
     dialog.dismiss();updateSummary();
    }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void chooseVlcProfile(){
  final String[] values={Software422Profile.AUTO,Software422Profile.SMOOTH,
   Software422Profile.BALANCED,Software422Profile.QUALITY,Software422Profile.STABLE};
  String[] labels={tx("Auto","Auto"),tx("سلس","Smooth"),tx("متوازن","Balanced"),
    tx("جودة","Quality"),tx("ثبات","Stable")};
  int current=0;
  for(int i=0;i<values.length;i++)if(values[i].equals(Software422Profile.current(this)))current=i;
  new AlertDialog.Builder(this).setTitle("LibVLC / FFmpeg")
   .setSingleChoiceItems(labels,current,(dialog,which)->{
     Software422Profile.save(this,values[which]);dialog.dismiss();updateSummary();
   }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
}
