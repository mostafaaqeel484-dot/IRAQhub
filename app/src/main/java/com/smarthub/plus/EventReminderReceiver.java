package com.smarthub.plus;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

/** User-created reminders for real, timed guide entries. Never claims to control tuning. */
public final class EventReminderReceiver extends BroadcastReceiver {
    public static final String ACTION="com.smarthub.plus.EVENT_REMINDER";
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){PendingResult pending=goAsync();new Thread(()->{try{new SatelliteGuideRepository(context).reschedule();}finally{pending.finish();}},"satellite-reminder-reschedule").start();return;}
        if(!ACTION.equals(action))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        String id=intent.getStringExtra("event_id"),kind=intent.getStringExtra("kind");long at=intent.getLongExtra("event_at",0),now=System.currentTimeMillis();
        if(id==null||kind==null)return;
        SatelliteGuideRepository.Event found=null;for(SatelliteGuideRepository.Event e:new SatelliteGuideRepository(context).watchlist())if(e.id.equals(id)){found=e;break;}
        if(!SatelliteGuideRepository.reminderDue(found,kind,at,now))return;
        SharedPreferences prefs=context.getSharedPreferences("smarthub",0);String fired="satellite_event_fired:"+id+":"+kind;if(prefs.getLong(fired,0)==at)return;prefs.edit().putLong(fired,at).apply();
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("satellite_events","Satellite programme reminders",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("Start and end times supplied by your programme guide");manager.createNotificationChannel(channel);
        boolean ar=prefs.getBoolean("arabic",true),start=kind.equals("start");String heading=ar?(start?"موعد بداية البرنامج حسب الدليل":"موعد نهاية البرنامج حسب الدليل"):(start?"Programme start time from guide":"Programme end time from guide");
        Intent open=new Intent(context,SatelliteGuideActivity.class).putExtra("query",found.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,0,open.setData(android.net.Uri.parse("smarthub://guide/"+id+"/"+kind)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,"satellite_events").setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle(heading).setContentText(found.channelName+" · "+found.title).setStyle(new Notification.BigTextStyle().bigText(found.channelName+" · "+found.title+"\n"+found.source)).setContentIntent(content).setAutoCancel(true).build();
        manager.notify(id+":"+kind,1,notification);
    }
}
