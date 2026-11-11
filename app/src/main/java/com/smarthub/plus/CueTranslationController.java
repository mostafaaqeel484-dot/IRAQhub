package com.smarthub.plus;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import androidx.media3.common.text.*;
import java.util.*;
import java.util.concurrent.*;
/** Keeps the broadcaster's cue lifetime; a completed late translation is discarded. */
@androidx.media3.common.util.UnstableApi
final class CueTranslationController implements AutoCloseable {
 interface Output{void show(List<Cue> cues);}
 private final Context context;private final LiveCaptionEngine engine;private final ExecutorService work=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());private final Output output;private int generation;private boolean closed;private Future<?> job;
 CueTranslationController(Context c,Output o){context=c;engine=new LiveCaptionEngine(c);output=o;}
 void clear(){generation++;if(job!=null)job.cancel(true);output.show(Collections.emptyList());}
 void accept(List<Cue> cues,String language){clear();if(closed)return;output.show(cues);if(cues.isEmpty())return;final int token=generation;List<Cue> originals=new ArrayList<>(cues);job=work.submit(()->{try{List<Cue> translated=new ArrayList<>();for(Cue cue:originals){if(Thread.currentThread().isInterrupted())return;String text=cue.text==null?"":cue.text.toString();if(cue.bitmap!=null)text=engine.recognize(cue.bitmap);if(text.isEmpty()||SubtitleSyncRepository.isArabic(text)){translated.add(cue);continue;}String value=engine.translate(text,language);Cue.Builder builder=cue.buildUpon();if(cue.bitmap!=null)builder=new Cue.Builder().setText(value).setPosition(cue.position).setPositionAnchor(cue.positionAnchor).setLine(cue.line,cue.lineType).setLineAnchor(cue.lineAnchor).setSize(cue.size);else builder.setText(value);translated.add(builder.build());}ui.post(()->{if(!closed&&token==generation)output.show(translated);});}catch(Exception e){context.getSharedPreferences("smarthub",0).edit().putString("live_translation_status","تعذر تجهيز الترجمة المحلية · "+e.getClass().getSimpleName()).apply();}});}
 public void close(){closed=true;clear();work.execute(engine::close);work.shutdown();}
}
