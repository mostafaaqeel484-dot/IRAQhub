package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.io.*;

/** D-pad accessible manual backup before APK updates and app-owned DB rollback. */
public final class ReceiverBackupActivity extends HubActivity {
 private static final int EXPORT=613,RESTORE=614,EXPORT_APK=615;
 private android.widget.TextView status;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout root=page(tx("نسخ القنوات والتحديثات","Backups and updates"),
     tx("قبل تثبيت نسخة جديدة، احفظ قاعدة القنوات والمفضلة. لا تتغير بيانات Aurum الأصلية.",
        "Export your app-owned channel list and favourites before upgrading. The OEM database stays untouched."));
  LinearLayout card=card(root);
  card.addView(text(tx("النسخ الاحتياطي والاسترجاع","Backup and rollback"),22,true));
  action(card,tx("حفظ نسخة احتياطية ZIP","Export channel backup ZIP"),()->choose(EXPORT));
  action(card,tx("استعادة ملف نسخة احتياطية","Restore a backup ZIP"),()->choose(RESTORE));
  action(card,tx("حفظ APK الحالي قبل التحديث","Save currently installed APK"),()->choose(EXPORT_APK));
  action(card,tx("عرض إصدارات IRAQhub","View IRAQhub Releases"),()->{
   Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(
     "https://github.com/mostafaaqeel484-dot/IRAQhub/releases"));
   try{startActivity(browser);}catch(Exception failure){
    status.setText(tx("المتصفح غير متاح","Browser unavailable"));
   }
  });
  status=text(tx("اختَر النسخ الاحتياطي قبل التحديث","Back up before installation"),14,false);
  card.addView(status);
  LinearLayout help=card(root);
  help.addView(text(tx("مهم بخصوص التحديث","Important update notes"),18,true));
  note(help,tx("نسخة ZIP تحفظ القنوات وترتيبها والمفضلة وإعدادات المظهر فقط. لا تحتوي على مفاتيح BISS أو كلمات المرور أو البيانات الخاصة.",
   "ZIP saves channel DB, order, favourites and cosmetic settings only; excludes CA/BISS keys, secrets and accounts."));
  note(help,tx("الرجوع إلى APK أقدم يتطلب توقيعاً مطابقاً وموافقة Android. استعادة القنوات لا تقوم بتثبيت APK أو تخطي صلاحيات النظام.",
   "APK downgrade requires the same signing key and Android permission. This screen restores data only, never installs an APK or overrides Package Manager."));
 }
 private void choose(int request){
  Intent picker=new Intent(request==RESTORE?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);
  picker.addCategory(Intent.CATEGORY_OPENABLE);
  picker.setType(request==EXPORT_APK?"application/vnd.android.package-archive":"application/zip");
  if(request==EXPORT)picker.putExtra(Intent.EXTRA_TITLE,"IRAQhub_channels_3.7.6_backup.zip");
  if(request==EXPORT_APK)picker.putExtra(Intent.EXTRA_TITLE,"IRAQhub_installed_preupdate.apk");
  try{startActivityForResult(picker,request);}
  catch(Exception failure){status.setText(tx("منتقي الملفات غير متاح","Document picker unavailable"));}
 }
 @Override protected void onActivityResult(int request,int code,Intent data){
  super.onActivityResult(request,code,data);
  if(code!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  if(request==EXPORT_APK){exportInstalledApk(uri);return;}
  if(request==RESTORE){
   new AlertDialog.Builder(this)
    .setTitle(tx("تأكيد استرجاع قائمة القنوات","Confirm channel database rollback"))
    .setMessage(tx("سيتم استبدال قاعدة القنوات والمفضلة المحلية بنسختك الاحتياطية بعد فحصها. لا تتغير قاعدة Aurum.",
      "Replaces IRAQhub local channels and favourites after validation; does not touch OEM Aurum."))
    .setNegativeButton(tx("إلغاء","Cancel"),null)
    .setPositiveButton(tx("استرجاع","Restore"),(d,n)->process(uri,false)).show();
  }else if(request==EXPORT)process(uri,true);
 }
 private void exportInstalledApk(Uri uri){
  status.setText(tx("نسخ APK المثبت…","Copying installed APK…"));
  new Thread(()->{
   String result;
   try{
    if(getApplicationInfo().splitSourceDirs!=null&&
       getApplicationInfo().splitSourceDirs.length>0)
      throw new IOException("INSTALLED_SPLIT_APK_USE_PACKAGE_BACKUP");
    File packageFile=new File(getApplicationInfo().sourceDir);
    if(!packageFile.isFile()||packageFile.length()>700L*1024L*1024L)
      throw new IOException("APK_SOURCE_UNAVAILABLE");
    try(InputStream in=new BufferedInputStream(new FileInputStream(packageFile));
        OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
     if(out==null)throw new IOException("APK_DESTINATION_UNAVAILABLE");
     byte[] buffer=new byte[32768];int len;
     while((len=in.read(buffer))>=0)out.write(buffer,0,len);
     out.flush();
    }
    result=tx("تم حفظ APK المثبت، احتفظ به مع ZIP القنوات",
      "Installed APK copied. Keep it beside your channel ZIP.");
   }catch(Exception e){result="APK_BACKUP_FAILED: "+e.getMessage();}
   String text=result;
   runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(text);});
  },"iraqhub-apk-backup").start();
 }
 private void process(Uri uri,boolean export){
  status.setText(export?tx("جارٍ النسخ الاحتياطي…","Backing up…"):
    tx("جارٍ فحص النسخة واسترجاعها…","Checking and restoring…"));
  new Thread(()->{
   String result;
   try{
    if(export){
     try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
      if(stream==null)throw new IOException("BACKUP_DESTINATION_MISSING");
      ReceiverBackupArchive.exportTo(this,stream);
     }
     result=tx("تم حفظ النسخة الاحتياطية بنجاح","Backup exported successfully");
    }else{
     ReceiverBackupArchive.RestoreResult restored;
     try(InputStream stream=getContentResolver().openInputStream(uri)){
      restored=ReceiverBackupArchive.restoreFrom(this,stream);
     }
     result=tx("تم استرجاع ","Restored ")+restored.channels+
       tx(" قناة، إعدادات محفوظة: "," channels; preferences: ")+restored.preferences;
    }
   }catch(Exception failure){
    result=(export?"BACKUP_FAILED: ":"RESTORE_FAILED: ")+failure.getMessage();
   }
   String report=result;
   runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(report);});
  },"iraqhub-safe-backup").start();
 }
}
