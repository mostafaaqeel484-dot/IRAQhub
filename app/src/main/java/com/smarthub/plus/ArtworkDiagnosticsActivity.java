package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receiver-safe image diagnostics. Never prints IDs, authentication tokens,
 * artwork URLs or keys, even if the user takes a screenshot for support.
 */
public final class ArtworkDiagnosticsActivity extends HubActivity {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private TextView report;
 private FactoryProvider factory;
 private boolean closed;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout body=page(tx("تشخيص CHMAX والبوسترات","CHMAX & poster diagnostics"),
    tx("معرفة سبب اختفاء صور القنوات والبرامج بدون عرض بيانات الجهاز السرية",
       "Read-only status: no private receiver credentials or image URLs"));
  report=text("",15,false);body.addView(report);
  action(body,tx("إعادة فحص المصادر والبوسترات","Recheck sources and posters"),()->{
   prefs().edit().putString("artwork_diagnostics_command","REQUESTED").apply();
   LogoRepository.get(this).invalidate();
   try{
    WorkManager.getInstance(this).enqueue(
      new OneTimeWorkRequest.Builder(PosterRefreshWorker.class).build());
    toast(tx("أُضيف تحديث البوسترات إلى قائمة المهام","Poster refresh queued"));
   }catch(Exception e){toast(tx("تعذر جدولة التحديث","Unable to schedule poster refresh"));}
   if(factory==null)factory=new FactoryProvider(this);
   factory.enrich(SatelliteState.read(this));
   showReport();
  });
  action(body,tx("إعداد اتصال CHMAX وهوية الجهاز","CHMAX connection settings"),
   ()->startActivity(new Intent(this,FactorySettingsActivity.class)));
  action(body,tx("إعدادات InfoBar والبوسترات","InfoBar poster settings"),
   ()->startActivity(new Intent(this,OsdSettingsActivity.class)));
  note(body,tx("إذا كانت حالة CHMAX تتطلب ملف المصنع أو إذن نظام، لا يمكن لتطبيق عادي قراءة /data/ChannelImgs أو تجاوز صلاحية توقيع Aurum.",
    "If the factory profile is unavailable, an ordinary app cannot read /data/ChannelImgs or bypass Aurum's signature permissions."));
  ui.post(tick);
 }
 private final Runnable tick=new Runnable(){public void run(){
  if(closed)return;
  showReport();
  ui.postDelayed(this,2500);
 }};
 private void showReport(){
  if(report==null)return;
  SatelliteState s=SatelliteState.read(this);
  android.content.SharedPreferences p=prefs();
  long last=p.getLong("native_logos_changed_at",0);
  String current=s.title.isEmpty()?tx("لا توجد بيانات برنامج","No current EPG title"):s.title;
  String upcoming=s.nextTitle.isEmpty()?tx("لا توجد بيانات برنامج تالي","No next EPG title"):s.nextTitle;
  String result=tx("القناة: ","Channel: ")+s.channelName+
   "\n"+tx("برنامج الآن: ","Now: ")+current+
   "\n"+tx("التالي: ","Next: ")+upcoming+
   "\n"+tx("تفعيل بوسترات InfoBar: ","InfoBar posters enabled: ")+p.getBoolean("osd_posters",true)+
   "\n"+tx("خدمة المصنع: ","Factory connection: ")+p.getString("factory_status","FACTORY_NOT_CONNECTED")+
   "\n"+tx("حالة دليل المصنع: ","Factory EPG: ")+p.getString("factory_epg_status","NOT_TESTED")+
   "\n"+tx("فحص الاتصال كل: ","Factory check every: ")+(FactoryRetryPolicy.CHECK_MS/1000)+"s (active player)"+
   "\n"+tx("المحاولة التالية بعد: ","Next factory retry in: ")+
     Math.max(0L,(p.getLong("factory_next_retry_at",0)-System.currentTimeMillis()+999)/1000)+"s"+
   "\n"+tx("شعارات CHMAX حصراً: ","CHMAX-only channel logos: ")+
     p.getBoolean("chmax_exclusive_logos",true)+
   "\nCHMAX: "+p.getString("chl_chmax_status","NOT_TESTED")+
   "\nCHMAX visible icon: "+p.getString("chmax_observe_status","NOT_OBSERVED")+
   "\nAurum accessibility: "+p.getBoolean("satellite_accessibility_connected",false)+
   "\n"+tx("شعار المصنع: ","Factory logo: ")+p.getString("factory_logo_status","NO_VERIFIED_LOGO")+
   "\n"+tx("شعار محلي أو مرصود: ","Observed/native logo: ")+(last>0?"CACHE_UPDATED":"NOT_CAPTURED")+
   "\nEPG Android TV: "+p.getString("published_epg_status","NOT_TESTED")+
   "\n"+tx("بوستر الآن: ","Now poster: ")+p.getString("osd_now_poster_status","NOT_TESTED")+
   "\n"+tx("بوستر التالي: ","Next poster: ")+p.getString("osd_next_poster_status","NOT_TESTED")+
   "\n"+tx("تحديث المصادر: ","Prefetch: ")+p.getString("poster_sync_status","NOT_STARTED")+
   "\n"+tx("الحالة: ","Artwork status: ")+p.getString("osd_poster_status","READY");
  report.setText(result);
 }
 @Override protected void onDestroy(){
  closed=true;ui.removeCallbacksAndMessages(null);io.shutdownNow();
  if(factory!=null)factory.close();
  super.onDestroy();
 }
}
