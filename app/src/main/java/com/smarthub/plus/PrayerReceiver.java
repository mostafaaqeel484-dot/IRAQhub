package com.smarthub.plus;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
public final class PrayerReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){if(!"com.smarthub.plus.PRAYER".equals(i.getAction())){PendingResult result=goAsync();java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());Runnable finish=()->{if(finished.compareAndSet(false,true))result.finish();};timer.postDelayed(finish,8500);Thread job=new Thread(()->{try{PrayerRefresh.ifNeeded(c);PrayerScheduler.schedule(c);}finally{timer.removeCallbacks(finish);finish.run();}},"prayer-calendar-refresh");job.start();return;}if(!c.getSharedPreferences("smarthub",0).getBoolean("prayer_alerts",false))return;
  long at=i.getLongExtra("prayer_at",0);if(at<System.currentTimeMillis()-2*60_000L||at>System.currentTimeMillis()+20*60_000L)return;
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("prayer_reminders","Prayer reminders",NotificationManager.IMPORTANCE_DEFAULT);nm.createNotificationChannel(channel);
  String name=i.getStringExtra("prayer");String[] ar={"الفجر","الظهر","العصر","المغرب","العشاء"};int index=java.util.Arrays.asList(PrayerData.PRAYERS).indexOf(name);boolean arabic=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);String title=arabic?"اقترب موعد صلاة "+(index<0?name:ar[index]):"Prayer approaching: "+name;
  PendingIntent open=PendingIntent.getActivity(c,33,new Intent(c,PrayerActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  nm.notify(700+Math.max(0,index),new NotificationCompat.Builder(c,"prayer_reminders").setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText((i.getStringExtra("city")==null?"":i.getStringExtra("city"))+ (arabic?" • تنبيه قبل الموعد بخمس دقائق":" • Five minute reminder")).setContentIntent(open).setAutoCancel(true).build());
 }
}
