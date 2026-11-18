package com.iraqhub.livefeed;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-enabled foreground monitor. Android 14 requires the specialUse FGS type.
 * It has an ongoing notification and uses 5s polling while the user-enabled foreground service is running; JobScheduler remains
 * the OS-managed fallback for ordinary notifications if this service stops.
 */
public final class FeedMonitorService extends Service {
    public static final String ACTION_STOP = "com.iraqhub.livefeed.ACTION_STOP_MONITOR";
    public static final String MONITOR_CHANNEL = "livefeed_overlay_monitor";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean started;
    private boolean fetching;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (!fetching) {
                fetching = true;
                worker.execute(() -> {
                    try {
                        List<FeedRepository.Feed> feeds = MultiSourceFeeds.fetch(getApplicationContext(), false);
                        FeedRepository.processNew(getApplicationContext(), feeds);
                    } catch (Exception ignored) {
                        // Ignore transient outages; never wipe seen-feed IDs.
                    } finally {
                        main.post(() -> fetching = false);
                    }
                });
            }
            main.postDelayed(this, 5_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(MONITOR_CHANNEL,
                    "مراقبة الفيدات أثناء مشاهدة التلفزيون",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("إبقاء فحص الفيدات وتشغيل التنبيهات العائمة أثناء استخدام تطبيقات أخرى");
            nm.createNotificationChannel(ch);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences("livefeed_settings", MODE_PRIVATE).edit().putBoolean("overlay", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean enabled = getSharedPreferences("livefeed_settings", MODE_PRIVATE).getBoolean("overlay", false);
        if (!enabled || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Intent toApp = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, toApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, FeedMonitorService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 100, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, MONITOR_CHANNEL)
                .setSmallIcon(R.drawable.ic_satellite)
                .setContentTitle("LiveFeed DVB • التنبيهات العائمة تعمل")
                .setContentText("فحص الفيدات كل 5 ثوانٍ أثناء مشاهدة القنوات")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "إيقاف المراقبة", stop).build())
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(231, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(231, n);
            }
        } catch (RuntimeException failure) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            main.post(poll);
        }
        return START_STICKY;
    }
    @Override public void onDestroy() {
        started = false;
        main.removeCallbacks(poll);
        worker.shutdownNow();
        FloatingAlert.hide();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
