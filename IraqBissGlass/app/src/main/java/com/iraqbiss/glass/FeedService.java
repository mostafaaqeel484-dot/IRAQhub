package com.iraqbiss.glass;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FeedService extends Service {
    static final String UPDATED="com.iraqbiss.glass.UPDATED";
    static final String REFRESH="com.iraqbiss.glass.REFRESH";
    static final String STOP="com.iraqbiss.glass.STOP";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final List<LinearLayout> popups=new ArrayList<>();
    private WindowManager window;
    private boolean running=false;
    private final Runnable poll=new Runnable(){public void run(){fetch(false);if(running)handler.postDelayed(this,30000);}};
    private final Runnable dismiss=this::clearPopups;
    @Override public void onCreate() {
        super.onCreate();
        window=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("iraqbiss_monitor","IraqBISS feed monitor",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("iraqbiss_alerts","New satellite feeds",NotificationManager.IMPORTANCE_HIGH));
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        try{startForeground(411,notification("مراقبة مصادر الفيدات","IraqBISS Glass · المصدر العام","iraqbiss_monitor"));}
        catch(Exception e){stopSelf();return START_NOT_STICKY;}
        if(!running){running=true;handler.post(poll);}
        else if(i!=null && REFRESH.equals(i.getAction()))fetch(true);
        return START_STICKY;
    }
    private Notification notification(String body,String title,String channel){
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,channel).setSmallIcon(R.drawable.ic_iraqbiss)
                .setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(false).build();
    }
    private void fetch(boolean force){
        if(!busy.compareAndSet(false,true))return;
        worker.execute(()->{
            try{
                List<FeedData.Item> items=FeedData.refresh(this,force);
                List<FeedData.Item> fresh=FeedData.newItems(this,items);
                if(!fresh.isEmpty()){
                    sendSystemNotifications(fresh);
                    if(getSharedPreferences("iraqbiss_settings",MODE_PRIVATE).getBoolean("overlay",false))showPopups(fresh);
                }
                sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()));
            }catch(Exception e){
                sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()));
            }finally{busy.set(false);}
        });
    }
    private void sendSystemNotifications(List<FeedData.Item> fresh){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        int index=0;
        for(FeedData.Item f:fresh){
            if(index>=2)break;
            nm.notify(500+index,notification(f.sat+" · "+f.tp()+" · "+f.source,
                    "📡 "+f.title,"iraqbiss_alerts"));
            index++;
        }
    }
    private int dp(int size){return Math.round(size*getResources().getDisplayMetrics().density);}
    private GradientDrawable glass(boolean second){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(236,20,40,65),Color.argb(230,10,26,44),Color.argb(236,29,47,68)});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1),second?Color.rgb(206,183,255):Color.rgb(174,203,255));
        return bg;
    }
    private TextView txt(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void showPopups(List<FeedData.Item> fresh){
        if(Build.VERSION.SDK_INT<26 || !Settings.canDrawOverlays(this))return;
        handler.post(()->{
            clearPopups();handler.removeCallbacks(dismiss);
            for(int k=0;k<Math.min(2,fresh.size());k++){
                FeedData.Item f=fresh.get(k);
                LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(26),dp(14),dp(26),dp(15));
                box.setBackground(glass(k==1));box.setElevation(dp(12));
                LinearLayout content=new LinearLayout(this);
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setGravity(Gravity.CENTER_VERTICAL);
                ImageView picture=new ImageView(this);
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                new ImageLoader(this).into(picture,f.image);
                LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(dp(138),dp(88));
                artParams.setMargins(0,0,dp(14),0);
                content.addView(picture,artParams);
                LinearLayout words=new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(txt("●  فيد جديد  ·  "+f.source,15,Color.rgb(174,203,255),true));
                words.addView(txt(f.title,21,Color.WHITE,true));
                words.addView(txt("🛰 "+f.sat+"   •   "+f.tp()+(f.fourTwoTwo()?"   •   4:2:2":""),16,Color.rgb(197,219,240),false));
                content.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                box.addView(content);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                        Math.min(dp(670),getResources().getDisplayMetrics().widthPixels-dp(44)),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT);
                p.gravity=(k==0?Gravity.TOP:Gravity.BOTTOM)|Gravity.CENTER_HORIZONTAL;
                p.y=dp(44);
                try{window.addView(box,p);popups.add(box);box.setAlpha(0f);box.animate().alpha(1f).setDuration(230).start();}
                catch(Exception ignored){}
            }
            handler.postDelayed(dismiss,10000);
        });
    }
    private void clearPopups(){
        for(LinearLayout v:new ArrayList<>(popups)){
            try{window.removeViewImmediate(v);}catch(Exception ignored){}
        }
        popups.clear();
    }
    @Override public void onDestroy(){
        running=false;handler.removeCallbacksAndMessages(null);worker.shutdownNow();clearPopups();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
