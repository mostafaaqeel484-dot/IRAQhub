package com.iraqhub.livefeed;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.List;

public final class FeedJobService extends JobService {
    private static final int JOB = 170611;
    private volatile boolean cancelled;
    public static void schedule(Context context) {
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        try {
            JobInfo info = new JobInfo.Builder(JOB, new ComponentName(context, FeedJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build();
            jobs.schedule(info);
        } catch (Exception ignored) { }
    }
    @Override public boolean onStartJob(JobParameters params) {
        cancelled = false;
        new Thread(() -> {
            try {
                List<FeedRepository.Feed> feeds = FeedRepository.fetch();
                if (!cancelled) FeedRepository.processNew(getApplicationContext(), feeds);
            } catch (Exception ignored) {
                // Network failures must not reset the notification baseline.
            } finally {
                jobFinished(params, false);
            }
        }, "LiveFeed-BG").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        cancelled = true;
        return true;
    }
}
