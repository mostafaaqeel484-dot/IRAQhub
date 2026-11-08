package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Remote-first settings hub; advanced options remain one click away. */
public final class HubSettingsActivity extends HubActivity {
 private static final int EXPORT_RECEIVER_BACKUP=8806;
 private void backupChannels(){
  Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("application/zip");
  intent.putExtra(Intent.EXTRA_TITLE,"IRAQhub_channels_backup_"+System.currentTimeMillis()+".zip");
  try{startActivityForResult(intent,EXPORT_RECEIVER_BACKUP);}
  catch(Exception e){toast(tx("منتقي الملفات غير متاح","Document picker unavailable"));}
 }
 private static final int VERIFY_RECEIVER_BACKUP=8807;
 private void verifyChannelsBackup(){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
  launchDocument(i,VERIFY_RECEIVER_BACKUP);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==VERIFY_RECEIVER_BACKUP&&result==RESULT_OK&&data!=null&&data.getData()!=null){
   android.net.Uri uri=data.getData();
   new Thread(()->{
    try{
     String summary=ReceiverBackup.verify(this,uri);
     runOnUiThread(()->new android.app.AlertDialog.Builder(this)
       .setTitle(tx("النسخة الاحتياطية سليمة","Backup integrity verified"))
       .setMessage(summary).setPositiveButton("OK",null).show());
    }catch(Exception failed){
     runOnUiThread(()->toast(tx("النسخة الاحتياطية غير صالحة","Backup verification failed")));
    }
   },"receiver-backup-verify").start();
   return;
  }
  if(request!=EXPORT_RECEIVER_BACKUP||result!=RESULT_OK||data==null||data.getData()==null)return;
  android.net.Uri uri=data.getData();
  new Thread(()->{
   try{
    ReceiverBackup.export(this,uri);
    runOnUiThread(()->toast(tx("تم حفظ نسخة القنوات الاحتياطية","Channel backup saved")));
   }catch(Exception failure){
    runOnUiThread(()->new android.app.AlertDialog.Builder(this)
      .setTitle(tx("تعذر إنشاء النسخة الاحتياطية","Backup failed"))
      .setMessage(String.valueOf(failure.getMessage()))
      .setPositiveButton("OK",null).show());
   }
  },"iraqhub-catalog-backup").start();
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("إعدادات IRAQhub","IRAQhub settings"),
    tx("أهم إعدادات التلفاز والقنوات بصورة مختصرة، وكل الإعدادات القديمة محفوظة تحت متقدم.",
      "Simple TV-focused settings; all previous options remain under Advanced."));
  LinearLayout appearance=card(page);
  appearance.addView(text(tx("المظهر · RX Edition","RX Edition · appearance"),22,true));
  open(appearance,tx("الخلفيات والثيمات · العراق وLinux ومانشستر","Wallpapers · Iraq, Linux & Manchester"),RxAppearanceActivity.class);
  LinearLayout satellite=card(page);
  satellite.addView(text(tx("الطبق والقنوات","Dish and channels"),22,true));
  open(satellite,tx("تركيب الأقمار والترددات والبحث","Satellite setup and scanning"),ReceiverToolsActivity.class);
  open(satellite,tx("بحث التردد اليدوي · قائمة مستقلة","Manual search · separate screen"),ManualTransponderActivity.class);
  open(satellite,tx("ضبط LNB — Universal 9750/10600","LNB — universal 9750/10600"),TunerSettingsActivity.class);
  open(satellite,tx("ترتيب وإدارة القنوات","Sort and manage channels"),ChannelManageActivity.class);
  action(satellite,tx("حفظ نسخة احتياطية لقاعدة القنوات قبل التحديث","Export channel database backup before updating"),this::backupChannels);
  LinearLayout viewing=card(page);
  viewing.addView(text(tx("المشاهدة والصور","Viewing and artwork"),22,true));
  open(viewing,tx("الشعارات — جلب شعارات القنوات","Channel logos"),LogoSourcesActivity.class);
  open(viewing,tx("الـInfobar","Information bar"),OsdSettingsActivity.class);
  open(viewing,tx("دليل البرامج ومصادر EPG","EPG and feed sources"),SatelliteGuideActivity.class);
  open(viewing,tx("البوسترات ومصادر الصور","Posters and artwork providers"),ProviderSetupActivity.class);
  open(viewing,tx("إصلاح 12 · مصادر لوغوات وبوسترات TVmaze والرياضة وWikimedia","Fix 12 · TVmaze, sports and Commons artwork sources"),ArtworkSourcesActivity.class);
  LinearLayout recovery=card(page);
  recovery.addView(text(tx("الاستقرار والنسخ الاحتياطي","Stability & backups"),22,true));
  android.widget.CheckBox safe=check(recovery,
    tx("الوضع الآمن: استعمل مشغّل DVB العادي بدل تجارب 4:2:2",
      "Safe mode: use normal DVB instead of experimental 4:2:2"),
    SafePlaybackMode.enabled(this));
  safe.setOnCheckedChangeListener((v,on)->{
   SafePlaybackMode.set(this,on);
   toast(on?tx("الوضع الآمن مفعل؛ افتح القناة مجدداً","Safe mode on; reopen channel"):
    tx("العودة للتجارب متاحة","Experimental players available"));
  });
  action(recovery,tx("تحقق من نسخة القنوات الاحتياطية ZIP قبل استرجاعها",
    "Verify existing channel backup ZIP (read-only)"),this::verifyChannelsBackup);
  note(recovery,tx("الفحص لا يستعيد قاعدة البيانات ولا يعدّل ملفات النظام. التحديثات التجريبية لا تغيّر توقيع التطبيق القديم.",
    "Verification is read-only; it does not restore a database or change firmware. Preview installs separately."));
  LinearLayout help=card(page);
  help.addView(text(tx("المساعدة والأدوات","Help and tools"),22,true));
  open(help,tx("لوحة المساعدة واختصارات الريموت","Remote control help"),RemoteHelpActivity.class);
  open(help,tx("الإعدادات المتقدمة — كل الخيارات السابقة","Advanced — all previous settings"),AdvancedSettingsActivity.class);
  open(help,tx("فحص الإشارة والتوافق","Signal and compatibility"),CompatibilityActivity.class);
  open(help,tx("ربط جهاز X5M · تصحيح USB ولوحة الرسيفر",
    "X5M hardware · USB ADB and front-panel bridge"),ReceiverHardwareBridgeActivity.class);
  action(help,tx("تصحيح USB (ADB) · خيارات المطور","USB ADB debugging · Developer options"),
    ()->UsbAdbSetup.show(this));
 }
 private void open(LinearLayout parent,String label,Class<?> target){
  action(parent,label,()->startActivity(new Intent(this,target)));
 }
}