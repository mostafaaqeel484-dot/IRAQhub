package com.smarthub.plus;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.View;
import android.widget.*;

/** Non-destructive RX6 channel-logo appearance editor. Factory and CHMAX images are read-only. */
public final class ChannelLogoEditorActivity extends HubActivity {
 private static final int PICK_IMAGE=9168;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private TextView status;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  SatelliteState channel=SatelliteState.read(this);
  final String physical=channel.channelId;
  final boolean canScope=ChannelLogoAppearance.physical(physical);
  final LinearLayout panel=page(tx("تخصيص شعار القناة والعلم","Channel logo & flag appearance"),
   tx("التخصيص يحفظ داخل RX6 ولا يغيّر شعارات CHMAX أو قاعدة الرسيفر.",
    "Appearance is stored only in RX6; OEM and CHMAX files remain untouched."));
  panel.addView(text(tx("القناة: ","Channel: ")+channel.channelName,17,true));
  if(!canScope)panel.addView(text(tx("لا توجد هوية DVB دقيقة؛ متاح التعديل العام فقط.",
    "No exact DVB service selected. Only global appearance can be edited."),14,false));

  final CheckBox global=check(panel,tx("تطبيق الإعدادات على جميع القنوات","Apply these settings to all channels"),!canScope);
  global.setEnabled(canScope);
  ChannelLogoAppearance.Config original=ChannelLogoAppearance.read(this,canScope?physical:null);

  final TextView sizeLabel=text("",16,false);panel.addView(sizeLabel);
  final SeekBar size=new SeekBar(this);
  size.setMax(60);size.setProgress(original.size-65);
  sizeLabel.setText(tx("حجم الشعار: ","Logo size: ")+(65+size.getProgress())+"%");
  size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
    sizeLabel.setText(tx("حجم الشعار: ","Logo size: ")+(65+progress)+"%");
   }
   @Override public void onStartTrackingTouch(SeekBar seek){}
   @Override public void onStopTrackingTouch(SeekBar seek){}
  });
  panel.addView(size);

  final CheckBox white=check(panel,tx("إزالة الإطار الأبيض المتصل بالحافة",
    "Remove edge-connected white matte"),original.removeWhite);
  final CheckBox black=check(panel,tx("إزالة الخلفية السوداء المتصلة بالحافة",
    "Remove edge-connected black matte"),original.removeBlack);
  final CheckBox crop=check(panel,tx("قص الفراغ الشفاف المحيط بالشعار",
    "Trim outer transparent padding"),original.crop);
  final CheckBox flag=check(panel,tx("عرض صورة علم البلد تحت اسم القناة",
    "Show actual country flag below channel name"),original.flag);

  panel.addView(text(tx("خلفية مساحة الشعار","Logo area background"),16,true));
  final RadioGroup backgrounds=new RadioGroup(this);
  backgrounds.setOrientation(RadioGroup.HORIZONTAL);
  final RadioButton transparent=new RadioButton(this),dark=new RadioButton(this),light=new RadioButton(this);
  transparent.setText(tx("شفافة","Transparent"));dark.setText(tx("سوداء","Black"));light.setText(tx("بيضاء","White"));
  transparent.setTextColor(0xffffffff);dark.setTextColor(0xffffffff);light.setTextColor(0xffffffff);
  transparent.setId(View.generateViewId());dark.setId(View.generateViewId());light.setId(View.generateViewId());
  backgrounds.addView(transparent);backgrounds.addView(dark);backgrounds.addView(light);
  backgrounds.check("black".equals(original.background)?dark.getId():
    "white".equals(original.background)?light.getId():transparent.getId());
  panel.addView(backgrounds);

  if(canScope){
   final EditText url=field(panel,tx("رابط HTTPS مباشر لشعار القناة",
     "Direct HTTPS URL for this channel's logo"),"");
   action(panel,tx("استيراد شعار قناة من رابط","Import channel logo via HTTPS"),()->{
    final String address=url.getText().toString().trim();
    status.setText(tx("جارٍ جلب الصورة…","Importing image…"));
    io.execute(()->{
     try{
      ChannelImageUrlImport.add(getApplicationContext(),physical,address);
      notice(tx("تم حفظ شعار القناة","Channel artwork saved"));
     }catch(Exception failure){notice(tx("فشل استيراد الشعار: ",
       "Channel artwork rejected: ")+failure.getClass().getSimpleName());}
    });
   });
   action(panel,tx("استيراد صورة من USB أو الجهاز","Choose channel logo from USB/storage"),()->{
    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("image/*");
    launchDocument(picker,PICK_IMAGE);
   });
   action(panel,tx("حذف الصورة اليدوية والعودة لشعار CHMAX","Remove custom image; return to CHMAX"),()->{
    io.execute(()->{
     ChannelArtworkOverrides.remove(getApplicationContext(),physical);
     notice(tx("تم استرجاع مصدر شعارات القنوات","Original channel logo source restored"));
    });
   });
  }

  status=text(tx("النتيجة تُعرض عند فتح InfoBar مجدداً.",
   "Changes appear on the next InfoBar refresh."),14,false);
  action(panel,tx("حفظ تخصيص الشعار","Save channel logo appearance"),()->{
   String background=backgrounds.getCheckedRadioButtonId()==dark.getId()?"black":
      backgrounds.getCheckedRadioButtonId()==light.getId()?"white":"transparent";
   ChannelLogoAppearance.Config changed=new ChannelLogoAppearance.Config(
      65+size.getProgress(),white.isChecked(),black.isChecked(),
      crop.isChecked(),flag.isChecked(),background);
   ChannelLogoAppearance.save(this,global.isChecked()?null:physical,changed);
   status.setText(tx("تم حفظ تخصيص الشعار","Logo appearance saved"));
  });
  action(panel,tx("استرجاع المظهر الأصلي","Reset to default appearance"),()->{
   ChannelLogoAppearance.reset(this,global.isChecked()?null:physical);
   status.setText(tx("تم استرجاع مظهر الشعار الافتراضي",
     "Original appearance restored for selected scope"));
  });
  panel.addView(status);
 }
 private void notice(String message){runOnUiThread(()->{if(!closed&&status!=null)status.setText(message);});}
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;
  final String physical=SatelliteState.read(this).channelId;
  final Uri image=data.getData();
  if(!ChannelLogoAppearance.physical(physical)){notice(tx("حدد قناة DVB أولاً","Select a DVB channel"));return;}
  io.execute(()->{
   try{ChannelArtworkOverrides.add(getApplicationContext(),physical,image);
    notice(tx("تم حفظ صورة القناة","Channel image saved"));}
   catch(Exception failed){notice(tx("تعذر استيراد الصورة: ","Invalid image: ")+failed.getClass().getSimpleName());}
  });
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
