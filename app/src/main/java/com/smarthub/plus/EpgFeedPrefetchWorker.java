package com.smarthub.plus;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Fetch only the missing/expired EPGShare feed without blocking channel switching. */
public final class EpgFeedPrefetchWorker extends Worker {
 private static final ConcurrentHashMap<String,Long> requested=new ConcurrentHashMap<>();
 public EpgFeedPrefetchWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 /** Legacy worker is deliberately inert. XMLTV is downloaded only by the UI. */
 static void request(Context context,String feed){}
 @NonNull @Override public Result doWork(){return Result.success();}

}
