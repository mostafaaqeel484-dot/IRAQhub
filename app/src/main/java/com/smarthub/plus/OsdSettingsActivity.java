package com.smarthub.plus;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
public final class OsdSettingsActivity extends HubActivity {
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout body=page(tx("تصميم شريط المعلومات","Information bar appearance"));EditText accent=field(body,tx("لون التمييز #RRGGBB","Accent #RRGGBB"),prefs().getString("osd_accent","#DC49EC"));EditText opacity=field(body,tx("وضوح خلفية الشريط 30–95%","Bar opacity 30–95%"),Integer.toString(prefs().getInt("osd_opacity",88)));CheckBox dock=check(body,tx("تثبيت الإنفوبار على حافة الشاشة السفلية بدون طفو","Dock InfoBar flush to bottom edge"),prefs().getBoolean("osd_dock_to_bottom",true));CheckBox receiverSize=check(body,tx("مطابقة حجم شريط الرسيفر تلقائياً","Automatically match the receiver information bar"),prefs().getBoolean("osd_receiver_size",false));EditText barPercent=field(body,tx("حجم الشريط اليدوي من ارتفاع الشاشة 16–40%","Manual bar height 16–40% of screen"),Integer.toString(prefs().getInt("osd_bar_percent",20)));EditText duration=field(body,tx("مدة ظهور الشريط · 1–15 ثانية","InfoBar duration · 1–15 seconds"),
 Integer.toString(InfoBarDuration.clamp(prefs().getInt(InfoBarDuration.KEY,InfoBarDuration.DEFAULT_SECONDS))));duration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);barPercent.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);note(body,tx("الحجم الافتراضي المضغوط 20% من الشاشة؛ يمكنك تغييره أو اختيار قياس شريط المصنع.","The compact default is 20% of the screen; you can change it or use the measured native size."));CheckBox avoid=check(body,tx("انتظار اختفاء شريط المصنع عند استعمال مشغله لمنع تداخل الشريطين","Wait for the native bar to close when using its player"),prefs().getBoolean("osd_avoid_native_overlap",true));CheckBox rxDetails=check(body,tx("RX: عرض التفاصيل التقنية الموسعة (افتراضياً انفوبار صغير)","RX: Expanded technical mode (default compact ribbon)"),prefs().getBoolean("osd_rx_detailed",false));CheckBox clock=check(body,tx("عرض الساعة","Show clock"),prefs().getBoolean("osd_clock",true));CheckBox swap=check(body,tx("تبديل موضعي بوستر الحالي والقادم","Swap current/next poster positions"),prefs().getBoolean("osd_swap_posters",false));CheckBox posters=check(body,tx("عرض بوسترات الحالي والقادم","Show current/next posters"),prefs().getBoolean("osd_posters",true));CheckBox similarPosters=check(body,tx("السماح ببوستر عنوان مشابه عند غياب التطابق الدقيق","Allow a similar-title poster when no exact match exists"),prefs().getBoolean("osd_similar_posters",true));note(body,tx("البوستر التقريبي لا يُحفظ كتطابق مؤكد، وتُميّز النبذة بأنها لعنوان مشابه.","Approximate artwork is labelled and is never saved as an exact programme match."));action(body,tx("إصلاح البوسترات: إظهار NOW/NEXT وتحديث الكاش",
  "Restore NOW/NEXT posters and refresh artwork"),
 ()->{
  posters.setChecked(true);
  prefs().edit().putBoolean("osd_posters",true)
   .putLong("epg_art_changed_at",System.currentTimeMillis())
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
  toast(tx("تم تفعيل البوسترات؛ افتح INFO مجدداً",
    "Posters enabled; reopen INFO."));
});
CheckBox chmaxLogos=check(body,tx("مطابقة شعارات CHMAX للقنوات عبر CHL عند توفر جلسة مخولة","Use CHMAX channel logos through verified CHL service IDs"),ChannelLogoSourcePrefs.chmax(this));note(body,tx("الانفوبار داخل مشغلي IRAQhub لا يحتاج إذن الظهور فوق التطبيقات. استخدام CHMAX للشعارات يتطلب اتصالاً مصرحاً وبيانات قناة مطابقة.","The in-player infobar does not require display-over-apps permission. CHMAX logos need an authorised connection and exact channel matching."));CheckBox descriptions=check(body,tx("عرض وصف البرنامج","Show programme description"),prefs().getBoolean("osd_description",true));CheckBox ticker=check(body,tx("عرض شريط المباريات","Show match ticker"),prefs().getBoolean("sports_ticker",false));CheckBox weather=check(body,tx("الطقس اختياري · الموقع الافتراضي البصرة","Optional weather · default location Basra"),prefs().getBoolean("osd_weather",false));EditText city=field(body,"Weather city",prefs().getString("weather_city","Basra, Iraq")),lat=field(body,"Latitude",prefs().getString("weather_lat","30.5081")),lon=field(body,"Longitude",prefs().getString("weather_lon","47.7835"));note(body,prefs().getString("published_epg_status",""));action(body,tx("اختيار خط ثمانية المحلي (OTF)","Import personal Thmanyah OTF"),()->{
 android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
   .setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE);
 launchDocument(intent,4301);
});
action(body,tx("الرجوع لخط الرسيفر","Restore default receiver font"),()->{
 UserTypeface.disable(this);toast(tx("تم تعطيل الخط المستورد","Custom font disabled"));
});
note(body,tx("ترخيص ثمانية يمنع إعادة توزيع ملف الخط. استخرج OTF من نسختك الخاصة واختره محلياً، وسيبقى داخل بيانات التطبيق.","Font licence forbids re-hosting. Select an OTF you own locally; it stays within app-private data."));
CaptionAppearance.controls(body,ar,null);note(body,tx("قوة الإشارة والجودة وSNR وBER من خدمة الموالف مباشرة، وتُحدّث كل ثانية أثناء مشاهدة القنوات. لا تؤخذ من نص إنفوبار الرسيفر؛ الحقل غير المتاح يظهر بعلامة —.","Signal, quality, SNR and BER come directly from the receiver tuner, refreshed every second while watching TV. INFO text is not their source; unavailable fields show —."));note(body,tx("شعار القناة من CHMAX أو رسيفر المصنع، وشعار المزوّد منفصل في أقصى يمين الشريط. الشعارات السوداء تُعرض باللون الأبيض.",
"Channel logos have independent CHMAX and external switches. Provider marks have a separate switch and manual import."));action(body,tx("إصلاح 12 · مصادر البوسترات والشعارات","Fix 12 · artwork sources"),()->startActivity(new android.content.Intent(this,ArtworkSourcesActivity.class)));action(body,tx("تعديل مصادر الشعارات والأسماء البديلة","Edit logo sources and aliases"),()->startActivity(new android.content.Intent(this,LogoSourcesActivity.class)));action(body,tx("حفظ مظهر الشريط","Save bar appearance"),()->{
 int seconds;
 try{
  seconds=Integer.parseInt(duration.getText().toString().trim());
  if(seconds!=InfoBarDuration.clamp(seconds))throw new NumberFormatException();
 }catch(NumberFormatException invalid){
  duration.setError(tx("أدخل مدة صحيحة من 1 إلى 15 ثانية","Enter a whole number from 1 to 15 seconds"));
  duration.requestFocus();
  return;
 }
 duration.setError(null);
 try{double latitude=Double.parseDouble(lat.getText().toString()),longitude=Double.parseDouble(lon.getText().toString());if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new Exception();Color.parseColor(accent.getText().toString());int barSize=Integer.parseInt(barPercent.getText().toString());if(barSize<16||barSize>40)throw new Exception();int alpha=Integer.parseInt(opacity.getText().toString());if(alpha<30||alpha>95)throw new Exception();prefs().edit().putInt(InfoBarDuration.KEY,seconds).putBoolean("osd_avoid_native_overlap",avoid.isChecked()).putBoolean("osd_dock_to_bottom",dock.isChecked()).putBoolean("osd_receiver_size",receiverSize.isChecked()).putInt("osd_bar_percent",barSize).putBoolean("osd_logo_right",false).putBoolean("osd_swap_posters",swap.isChecked()).putBoolean(ChannelLogoSourcePrefs.CHMAX,chmaxLogos.isChecked()).putBoolean("osd_chmax_logos",chmaxLogos.isChecked()).putBoolean("rx6_chmax_logo_user_choice",chmaxLogos.isChecked()).putBoolean("osd_posters",posters.isChecked()).putBoolean("osd_similar_posters",similarPosters.isChecked()).putBoolean("osd_description",descriptions.isChecked()).putBoolean("sports_ticker",ticker.isChecked()).putBoolean("osd_weather",weather.isChecked()).putString("weather_city",city.getText().toString().trim()).putString("weather_lat",Double.toString(latitude)).putString("weather_lon",Double.toString(longitude)).putString("osd_accent",accent.getText().toString()).putInt("osd_opacity",alpha).putBoolean("osd_rx_detailed",rxDetails.isChecked()).putBoolean("osd_clock",clock.isChecked()).putLong("osd_changed_at",System.currentTimeMillis()).apply();toast(tx("تم الحفظ","Saved"));}catch(Exception e){toast(tx("تحقق من اللون والشفافية وحجم الشريط وإحداثيات الطقس","Check the colour, opacity, bar height and weather coordinates"));}});}

 @Override protected void onActivityResult(int request,int result,android.content.Intent data){
  super.onActivityResult(request,result,data);
  if(request!=4301||result!=RESULT_OK||data==null||data.getData()==null)return;
  try{
   UserTypeface.importFrom(this,data.getData());
   toast(tx("تم استيراد الخط؛ افتح الواجهة مجدداً لتطبيقه","Font imported. Reopen UI to apply."));
  }catch(Exception invalid){
   toast(tx("ملف الخط غير صالح. اختر OTF الأصلي","Invalid font file. Select original OTF."));
  }
 }
}
