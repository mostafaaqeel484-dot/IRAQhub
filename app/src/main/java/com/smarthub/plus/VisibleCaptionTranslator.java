package com.smarthub.plus;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** OCR continues during model downloads/translation; obsolete work never blocks fresh recognition. */
final class VisibleCaptionTranslator implements AutoCloseable {
 interface Output{void show(String text);}
 private static final class Ticket{long token;String text,hint;Ticket(long t,String s,String h){token=t;text=s;hint=h;}}
 private final CaptionLatch latch=new CaptionLatch();private final LiveCaptionEngine engine;private final Context context;private final Output output;private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService work=Executors.newSingleThreadExecutor();private final AtomicReference<Ticket> latest=new AtomicReference<>();private final AtomicBoolean draining=new AtomicBoolean();private long submitted=-1;private volatile boolean closed;
 VisibleCaptionTranslator(Context c,Output out){context=c;engine=new LiveCaptionEngine(c);output=out;ui.post(expire);}
 private final Runnable expire=new Runnable(){public void run(){if(closed)return;if(!latch.current(latch.token(),SystemClock.elapsedRealtime()))output.show("");ui.postDelayed(this,200);}};
 void accept(String text,String hint,String identity){accept(text,hint,identity,SystemClock.elapsedRealtime());}
 void accept(String text,String hint,String identity,long capturedAt){if(closed)return;long now=SystemClock.elapsedRealtime();if(capturedAt>now||now-capturedAt>=2500){output.show("");return;}if(context.getSharedPreferences("smarthub",0).getString("live_target_language","ar").equals("ar")&&SubtitleSyncRepository.isArabic(text)){clear();context.getSharedPreferences("smarthub",0).edit().putString("native_ocr_status","الترجمة عربية بالفعل؛ لم تُترجم مرة ثانية").apply();return;}String target=context.getSharedPreferences("smarthub",0).getString("live_target_language","ar");long token=latch.offer(identity+"|"+hint+"|"+target,text,capturedAt);if(!latch.current(token,now)){output.show("");return;}if(token==submitted)return;submitted=token;output.show("");latest.set(new Ticket(token,latch.text(),hint));drain();}
 private void drain(){if(!draining.compareAndSet(false,true))return;work.execute(()->{try{Ticket next;while(!closed&&(next=latest.getAndSet(null))!=null){final Ticket ticket=next;if(!latch.current(ticket.token,SystemClock.elapsedRealtime()))continue;try{String translated=engine.translate(ticket.text,ticket.hint);ui.post(()->{if(!closed&&latch.current(ticket.token,SystemClock.elapsedRealtime())&&NativeCaptionBridge.captureSafe())output.show(translated);});}catch(Exception e){ui.post(()->{submitted=-1;context.getSharedPreferences("smarthub",0).edit().putString("native_ocr_status","تعذر تجهيز الترجمة المحلية · "+e.getClass().getSimpleName()).apply();});}}}finally{draining.set(false);if(!closed&&latest.get()!=null)drain();}});}
 void clear(){latch.clear();latest.set(null);submitted=-1;output.show("");}
 public void close(){closed=true;clear();ui.removeCallbacksAndMessages(null);work.execute(engine::close);work.shutdown();}
}
