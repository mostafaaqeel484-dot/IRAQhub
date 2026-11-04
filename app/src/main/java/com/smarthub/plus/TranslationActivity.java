package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Existing OpenSubtitles files for the identified programme, plus offline file tools. */
public final class TranslationActivity extends HubActivity {
    private static final int IMPORT=301,EXPORT=302;
    private static final String[] LANGS={"ar","en","fr","es","de","tr","fa","hi","ur","ru","it","id","pt-br"};
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private EditText key,username,password,title,imdb,season,episode,profile,offset;
    private Spinner language;
    private TextView status,preview;
    private LinearLayout results;
    private CheckBox epgPosition,shiftExport;
    private final List<Button> controls=new ArrayList<>();
    private List<SubtitleParser.Cue> cues=new ArrayList<>();
    private volatile OpenSubtitlesClient client;
    private String clientKey="",channelId="";
    private long eventStart;
    private Future<?> task;
    private boolean busy;
    private volatile boolean destroyed;
    private volatile int generation;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        String currentTitle=getIntent().getStringExtra("title");if(currentTitle==null)currentTitle=prefs().getString("satellite_title","");
        Object channelExtra=getIntent().getExtras()==null?null:getIntent().getExtras().get("channel_id");channelId=channelExtra==null?prefs().getString("satellite_channel_id",""):channelExtra.toString();
        eventStart=getIntent().getLongExtra("event_start_ms",prefs().getLong("satellite_event_start_ms",0));
        String currentImdb=getIntent().getStringExtra("imdb_id");if(currentImdb==null)currentImdb=currentTitle.equals(prefs().getString("satellite_imdb_title",""))?prefs().getString("satellite_imdb_id",""):"";
        LinearLayout body=page(tx("ترجمة البرنامج عبر OpenSubtitles","Programme subtitles · OpenSubtitles"),tx("ملفات ترجمة جاهزة للفيلم أو الحلقة المعروضة","Existing subtitle files for the current film or episode"));
        LinearLayout account=card(body);
        note(account,tx("المزودان مدمجان ويتصل بهما التلفزيون مباشرة عبر HTTPS. مفاتيحك محفوظة مشفّرة بواسطة Android Keystore.","Both built-in providers connect directly over HTTPS. Android Keystore protects your keys."));control(account,tx("إعداد مفاتيح مزودي الترجمة","Built-in subtitle provider settings"),()->startActivity(new Intent(this,ProviderSetupActivity.class)));ProviderSecrets.clearLegacy(this);
        LinearLayout search=card(body);
        note(search,tx("القناة الحالية: ","Current channel: ")+(channelId.isEmpty()?tx("غير مرتبطة؛ اختر البرنامج يدوياً","not linked; choose the programme manually"):channelId));
        title=field(search,tx("عنوان الفيلم أو البرنامج من EPG","Film or programme title from EPG"),currentTitle);
        imdb=field(search,tx("IMDb اختياري، مثال tt0133093","Optional IMDb ID, e.g. tt0133093"),currentImdb);
        note(search,tx("معرّف IMDb للفيلم أدق من العنوان. للمسلسل أدخل معرّف المسلسل مع الموسم والحلقة، أو معرّف الحلقة وحده.","A film IMDb ID is more precise than its title. For series, enter the parent series ID with season and episode, or an episode ID alone."));
        season=field(search,tx("الموسم · اختياري","Season · optional"),"");season.setInputType(InputType.TYPE_CLASS_NUMBER);
        episode=field(search,tx("الحلقة · اختياري","Episode · optional"),"");episode.setInputType(InputType.TYPE_CLASS_NUMBER);
        language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,LANGS));search.addView(language,new LinearLayout.LayoutParams(-1,dp(52)));
        control(search,tx("البحث عن ملفات الترجمة","Search subtitle files"),this::search);
        control(search,tx("إلغاء الطلب الحالي","Cancel current request"),this::cancel);
        status=text(tx("أدخل مفاتيح مزودي الترجمة للبحث المباشر، أو استورد ملفاً لاستخدامه دون إنترنت.","Set your subtitle provider keys for direct searches, or import a file for offline use."),15,false);search.addView(status);
        results=col();search.addView(results);
        LinearLayout local=card(body);
        control(local,tx("استيراد SRT / VTT بترميز UTF-8","Import UTF-8 SRT / VTT"),this::importFile);
        control(local,tx("تحميل آخر ملف محفوظ دون اتصال","Load last saved file offline"),()->load(null));
        preview=text("",16,false);local.addView(preview);
        control(local,tx("تصدير الملف بصيغة SRT","Export SRT file"),this::exportFile);
        LinearLayout sync=card(body);
        profile=field(sync,tx("اسم القناة / ملف المزامنة","Channel / synchronization profile"),!currentImdb.isEmpty()?"imdb:"+currentImdb:!currentTitle.isEmpty()?"film:"+PlaylistRepository.normalize(currentTitle):prefs().getString("subtitle_profile","default"));
        String initialProfile=profile.getText().toString();offset=field(sync,tx("التأخير بالمللي ثانية: موجب يؤخر النص","Offset milliseconds: positive delays text"),Long.toString(prefs().getLong("subtitle_offset:"+initialProfile,prefs().getLong("subtitle_offset",0))));offset.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        control(sync,tx("حفظ مزامنة هذه القناة","Save this channel sync"),this::saveSync);
        control(sync,tx("تحميل المزامنة المحفوظة","Load saved sync"),()->offset.setText(Long.toString(prefs().getLong("subtitle_offset:"+profileName(),0))));
        shiftExport=check(sync,tx("تطبيق التأخير عند التصدير","Apply offset on export"),false);
        epgPosition=check(sync,tx("بدء تقريبي من الوقت المنقضي منذ بداية EPG","Approximate position from EPG elapsed time"),false);
        note(sync,tx("البدء الافتراضي من الثانية صفر، ثم اضبط التوقيت من أدوات الشاشة. توقيت EPG تقديري ولا يعالج الإعلانات أو النسخ المختلفة. الربط بالقناة الحالية يُحفظ عند تشغيل الترجمة صراحةً.","By default captions start at zero; adjust timing in Screen tools. EPG timing is approximate and does not handle advertisements or different cuts. The current-channel association is saved only when you explicitly start captions."));
        control(sync,tx("تشغيل الملف فوق القناة الحالية","Start captions over current channel"),this::startCaptions);
        control(sync,tx("إيقاف ترجمة الشاشة","Stop screen captions"),this::stopCaptions);
    }
    private Button control(LinearLayout box,String label,Runnable run){Button button=action(box,label,run);controls.add(button);return button;}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast(tx("لا يوجد متصفح؛ افتح opensubtitles.com/en/consumers","No browser installed; open opensubtitles.com/en/consumers"));}}
    private OpenSubtitlesClient configured()throws Exception{if(client==null)client=OpenSubtitlesClient.fromPreferences(this);return client;}
    private boolean active(int token){return !destroyed&&token==generation&&!Thread.currentThread().isInterrupted();}
    private void working(boolean value){busy=value;for(Button b:controls)b.setEnabled(!value||b.getText().toString().equals(tx("إلغاء الطلب الحالي","Cancel current request")));title.setEnabled(!value);imdb.setEnabled(!value);season.setEnabled(!value);episode.setEnabled(!value);language.setEnabled(!value);}
    private void fail(int token,String prefix,Exception error){runOnUiThread(()->{if(!active(token))return;working(false);status.setText(prefix+error.getMessage());});}
    private int number(EditText field){try{return Math.max(0,Math.min(9999,Integer.parseInt(field.getText().toString().trim())));}catch(Exception e){return 0;}}
    private void search(){if(busy)return;final OpenSubtitlesClient selected;try{selected=configured();}catch(Exception e){status.setText(e.getMessage());return;}String query=title.getText().toString(),id=imdb.getText().toString(),lang=LANGS[language.getSelectedItemPosition()];int s=number(season),ep=number(episode);working(true);results.removeAllViews();int token=++generation;status.setText(tx("البحث في OpenSubtitles…","Searching OpenSubtitles…"));task=io.submit(()->{try{List<OpenSubtitlesClient.SubtitleFile> found=selected.search(query,id,lang,s,ep);runOnUiThread(()->{if(!active(token))return;working(false);status.setText(found.isEmpty()?tx("لم توجد ملفات مطابقة. جرّب الاسم الأصلي أو IMDb الصحيح.","No matching files. Try the original title or correct IMDb ID."):tx("اختر الملف وإصدار البث المناسب. عدد الملفات: ","Choose a file matching your broadcast release. Files: ")+found.size());for(OpenSubtitlesClient.SubtitleFile item:found){Button choose=action(results,item.label(),()->choose(selected,item));choose.setTextSize(14);}});}catch(Exception e){fail(token,tx("فشل البحث: ","Search failed: "),e);}});}
    private void choose(OpenSubtitlesClient selected,OpenSubtitlesClient.SubtitleFile file){if(busy)return;new AlertDialog.Builder(this).setTitle(tx("تنزيل ملف الترجمة","Download subtitle file")).setMessage(file.label()+"\n\n"+tx("يُستهلك تنزيل من حصتك عند طلب الرابط. التوقيت قد يحتاج ضبطاً مع بث القناة.","Requesting a link consumes one download from your allowance. Broadcast timing may need adjustment.")).setNegativeButton(tx("إلغاء","Cancel"),null).setPositiveButton(tx("تنزيل","Download"),(d,w)->{try{download(configured(),file);}catch(Exception e){status.setText(e.getMessage());}}).show();}
    private void download(OpenSubtitlesClient selected,OpenSubtitlesClient.SubtitleFile file){if(busy)return;client=selected;working(true);int token=++generation;status.setText(tx("تنزيل ملف الترجمة…","Downloading subtitles…"));task=io.submit(()->{OpenSubtitlesClient.Download details=null;try{details=selected.download(file.id);saveDownload(selected.fetch(details),details,token,file.id);}catch(OpenSubtitlesClient.ArchiveChoice choice){final OpenSubtitlesClient.Download kept=details;runOnUiThread(()->{if(!active(token))return;working(false);String[] labels=new String[choice.files.size()];for(int i=0;i<labels.length;i++)labels[i]=choice.files.get(i).name;new AlertDialog.Builder(this).setTitle(tx("اختر نسخة البث داخل الأرشيف","Choose the release inside the archive")).setItems(labels,(d,w)->{working(true);task=io.submit(()->{try{saveDownload(choice.files.get(w).text,kept,token,file.id);}catch(Exception e){fail(token,"Subtitle: ",e);}});}).setNegativeButton(tx("إلغاء","Cancel"),null).show();});}catch(Exception e){fail(token,tx("فشل التنزيل: ","Download failed: "),e);}});}
    private void saveDownload(String text,OpenSubtitlesClient.Download details,int token,long fileId)throws Exception{List<SubtitleParser.Cue> parsed=SubtitleParser.parse(text);if(parsed.isEmpty())throw new java.io.IOException("No valid subtitle cues");if(!active(token))return;SubtitleStorage.save(this,text);associate();SubtitleSyncRepository.bind(this,profileName(),fileId+":"+SubtitleSyncRepository.digest(text),fileId>=1000000000000L?"Subsource":"OpenSubtitles",details.name);runOnUiThread(()->{if(!active(token))return;cues=parsed;offset.setText(Long.toString(SubtitleSyncRepository.offset(this)));working(false);preview.setText(preview(parsed));status.setText(tx("تم حفظ الترجمة: ","Subtitle saved: ")+details.name+"\n"+tx("المتبقي حسب المزود: ","Provider remaining: ")+(details.remaining<0?"?":details.remaining));});}
    private void associate(){prefs().edit().putString("subtitle_channel_id",channelId).putLong("subtitle_event_start_ms",eventStart).apply();}
    private void cancel(){if(!busy)return;generation++;if(client!=null)client.cancel();client=null;clientKey="";if(task!=null)task.cancel(true);working(false);status.setText(tx("أُلغيت العملية. الملف المحفوظ متاح دون اتصال.","Request cancelled. Saved file remains available offline."));}
    private void importFile(){if(busy)return;Intent request=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);request.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-subrip","text/vtt","text/plain","application/octet-stream"});launchDocument(request,IMPORT);}
    private void load(Uri uri){if(busy)return;working(true);int token=++generation;status.setText(tx("قراءة الملف…","Reading file…"));task=io.submit(()->{try{String raw=uri==null?SubtitleStorage.readInternal(this):SubtitleStorage.read(this,uri);List<SubtitleParser.Cue> loaded=SubtitleParser.parse(raw);if(loaded.isEmpty())throw new java.io.IOException("No valid SRT/VTT cues");if(!active(token))return;if(uri!=null){SubtitleStorage.save(this,raw);associate();SubtitleSyncRepository.bind(this,profileName(),SubtitleSyncRepository.digest(raw),"Local","Imported subtitle");}runOnUiThread(()->{if(!active(token))return;cues=loaded;offset.setText(Long.toString(SubtitleSyncRepository.offset(this)));working(false);preview.setText(preview(loaded));status.setText(tx("تم تحميل ","Loaded ")+loaded.size()+tx(" سطر ترجمة دون اتصال"," subtitle cues offline"));});}catch(Exception e){fail(token,tx("تعذر فتح الملف: ","Cannot open file: "),e);}});}
    private void startCaptions(){if(busy)return;if(prefs().getBoolean("satellite_companion_enabled",false)){SatelliteState actual=SatelliteState.read(this);long activeStart=actual.startMs;if(actual.title.isEmpty()){PlaylistRepository.Programme mapped=new SatelliteGuideRepository(this).currentFor(actual,System.currentTimeMillis());if(mapped!=null)activeStart=mapped.startMs;}if(channelId.isEmpty()||!channelId.equals(actual.channelId)||eventStart!=activeStart){toast(tx("تغيّرت القناة أو البرنامج. افتح INFO ثم اختر ترجمة البرنامج الحالي من المساعد.","Channel or programme changed. Open INFO and choose the current programme subtitle from the companion."));return;}}if(cues.isEmpty()){toast(tx("نزّل أو حمّل ملف الترجمة أولاً","Download or load a subtitle file first"));return;}if(!Settings.canDrawOverlays(this)){try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast(tx("فعّل الظهور فوق التطبيقات من إعدادات Android","Enable display over other apps in Android settings"));}return;}saveSync();associate();long position=epgPosition.isChecked()&&eventStart>0?Math.max(0,Math.min(24*3600000L,System.currentTimeMillis()-eventStart)):0;prefs().edit().putBoolean("overlay_caption",true).apply();try{startForegroundService(new Intent(this,ScreenOverlayService.class).setAction(ScreenOverlayService.ACTION_CAPTION).putExtra(ScreenOverlayService.EXTRA_SEEK_MS,position).putExtra(ScreenOverlayService.EXTRA_PLAY,true));toast(tx("شُغلت الترجمة؛ ارجع إلى القناة واضبط المزامنة عند الحاجة","Captions started; return to the channel and adjust synchronization as needed"));}catch(Exception e){prefs().edit().putBoolean("overlay_caption",false).apply();toast(e.getMessage());}}
    private void stopCaptions(){prefs().edit().putBoolean("overlay_caption",false).apply();if(prefs().getBoolean("screen_running",false))try{startForegroundService(new Intent(this,ScreenOverlayService.class).setAction(ScreenOverlayService.ACTION_CAPTION).putExtra(ScreenOverlayService.EXTRA_PLAY,false));}catch(Exception e){toast(e.getMessage());}}
    private void exportFile(){if(busy||cues.isEmpty()){toast(tx("حمّل ملفاً أولاً","Load a file first"));return;}try{long shift=shiftExport.isChecked()?offsetValue():0;HubFiles.save(this,"pending_subtitle_export.json",new JSONObject().put("srt",SubtitleParser.toSrt(cues,shift)));launchDocument(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-subrip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"SmartHub_subtitles.srt"),EXPORT);}catch(Exception e){toast(e.getMessage());}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==IMPORT)load(uri);else if(request==EXPORT)io.execute(()->{try{JSONObject pending=HubFiles.read(this,"pending_subtitle_export.json");if(pending==null||!pending.has("srt"))throw new java.io.IOException("No saved export");String srt=pending.getString("srt");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("Cannot open destination");out.write(srt.getBytes(StandardCharsets.UTF_8));}runOnUiThread(()->{if(!destroyed)toast(tx("تم تصدير SRT","SRT exported"));});}catch(Exception e){runOnUiThread(()->{if(!destroyed)toast(e.getMessage());});}});}
    private String preview(List<SubtitleParser.Cue> list){StringBuilder out=new StringBuilder();for(int i=0;i<Math.min(3,list.size());i++){if(i>0)out.append("\n\n");out.append(list.get(i).text);}return out.toString();}
    private String profileName(){String value=profile.getText().toString().trim();return value.isEmpty()?"default":value.substring(0,Math.min(100,value.length()));}
    private long offsetValue(){try{return Math.max(-3600000,Math.min(3600000,Long.parseLong(offset.getText().toString().trim())));}catch(Exception e){return 0;}}
    private void saveSync(){String name=profileName();long value=offsetValue();if(!name.equals(prefs().getString("subtitle_profile",""))&&!prefs().getString("subtitle_file_identity","").isEmpty())SubtitleSyncRepository.bind(this,name,prefs().getString("subtitle_file_identity",""),prefs().getString("subtitle_provider",""),prefs().getString("subtitle_display_name",""));else prefs().edit().putString("subtitle_profile",name).apply();SubtitleSyncRepository.set(this,value);toast(tx("حُفظت المزامنة","Synchronization saved"));}
    @Override protected void onDestroy(){destroyed=true;generation++;if(client!=null)client.cancel();if(task!=null)task.cancel(true);io.shutdownNow();super.onDestroy();}
}
