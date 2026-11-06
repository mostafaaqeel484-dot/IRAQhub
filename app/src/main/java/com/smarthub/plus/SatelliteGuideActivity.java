package com.smarthub.plus;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.io.InputStream;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class SatelliteGuideActivity extends HubActivity {
    private static final int XMLTV_FILE=601,NOTIFICATIONS=602;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SatelliteGuideRepository repository;
    private TextView source,status;
    private EditText url,mapping,query;
    private CheckBox past;
    private LinearLayout results;
    private SatelliteState current;
    private boolean busy,showWatched;
    private volatile boolean destroyed;
    private volatile int generation;
    private Future<?> job;
    private SatelliteGuideRepository.Event pendingWatch;
    @Override public void onCreate(Bundle state){super.onCreate(state);repository=new SatelliteGuideRepository(this);current=SatelliteState.read(this);repository.record(current);build();}
    private void build(){
        LinearLayout body=page(tx("دليل برامج القنوات الفضائية","Satellite programme guide"),tx("بحث في EPG المرصود وXMLTV، ومتابعة مواعيد البرامج","Search observed EPG and XMLTV, and watch programme times"));
        LinearLayout input=card(body);
        note(input,tx("الدليل مستقل عن قوائم IPTV ولا يحتاج رابط بث. تُحفظ فقط البرامج ذات العنوان ووقت البداية والنهاية المعروفين. عند حجب EPG من نظام الرسيفر يمكنك استيراد XMLTV الخاص بك. لا تعني بيانات الدليل تأكيد البث الفعلي أو التحكم بتغيير القناة.","This guide is separate from IPTV and needs no stream URL. Only programmes with a known title, start and end time are stored. If the receiver hides its EPG, import your own XMLTV. Guide times do not prove what is actually being broadcast and do not control channel tuning."));
        source=text(repository.sourceSummary(),14,false);input.addView(source);
        action(input,tx("اكتشاف واختيار مصادر EPGShare للقناة الحالية","Discover/select EPGShare source for current channel"),this::chooseEpgShare);
        action(input,tx("حالة تحديث EPG والبوسترات","EPG and poster refresh status"),this::showUnifiedStatus);
        action(input,tx("تحديث XMLTV والبوسترات وDVB EPG الآن","Refresh XMLTV, posters and DVB EPG now"),()->{IraqHubSyncScheduler.runNow(this);toast(tx("تمت جدولة تحديثات منفصلة، من دون إيقاف البث","Independent refresh tasks queued without stopping playback"));});
        url=field(input,tx("رابط HTTPS لملف XMLTV أو XMLTV.gz","HTTPS URL for XMLTV or XMLTV.gz"),prefs().getString("satellite_xmltv_url",""));url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        action(input,tx("جلب واستيراد XMLTV من الرابط","Fetch and import XMLTV URL"),this::importUrl);
        action(input,tx("استيراد ملف XMLTV بترميز UTF-8","Import UTF-8 XMLTV file"),()->{if(busy)return;launchDocument(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),XMLTV_FILE);});
        action(input,tx("فتح دليل الرسيفر الأصلي","Open receiver's native EPG"),this::nativeGuide);
        LinearLayout binding=card(body);note(binding,tx("القناة المرصودة: ","Observed channel: ")+(current.channelName.isEmpty()?tx("غير متاحة حالياً","unavailable"):current.channelName)+" · "+current.channelId);
        mapping=field(binding,tx("XMLTV channel id للقناة الحالية؛ فارغ للمطابقة التلقائية","XMLTV channel ID for current channel; empty for automatic match"),prefs().getString("satellite_xmltv_id:"+current.channelId,""));
        action(binding,tx("حفظ الربط بالقناة الحالية","Save current channel mapping"),()->{try{repository.map(current.channelId,mapping.getText().toString().trim());toast(tx("حُفظ الربط","Mapping saved"));search(false);}catch(Exception e){toast(e.getMessage());}});
        action(binding,tx("عرض معرّفات القنوات المستوردة","Show imported channel IDs"),this::showAliases);
        note(binding,tx("المطابقة التلقائية تتطلب اسماً مطابقاً وفريداً. عند تشابه أسماء عدة قنوات لن يُختار واحد منها بالتخمين؛ حدّد المعرّف الصحيح.","Automatic mapping requires an exact, unique channel name. Ambiguous names are not guessed; select the correct XMLTV ID."));
        LinearLayout find=card(body);String requested=getIntent().getStringExtra("query");query=field(find,tx("عنوان، قناة، فئة، إعادة، وقت أو أرقام T9","Title, channel, category, repeat, time or T9 digits"),requested==null?"":requested);
        past=check(find,tx("إظهار البرامج الماضية أيضاً كأرشيف","Include past programmes as archive"),false);
        action(find,tx("البحث في دليل البرامج","Search programme guide"),()->search(false));
        action(find,tx("قائمة البرامج التي أتابعها","Watched programme list"),()->search(true));
        status=text("",15,false);find.addView(status);results=col();find.addView(results);search(false);
    }
    private void showUnifiedStatus(){
      android.content.SharedPreferences p=prefs();
      String report=tx("المصادر المختارة: ","Selected feeds: ")+EpgShareFeedDirectory.selected(this)
        +"\nXMLTV · "+when(p.getLong("online_epg_sync_at",0))
        +" · "+p.getString("online_epg_status","NOT_UPDATED")
        +"\nDVB EIT · "+when(p.getLong("native_epg_sync_success_at",0))
        +" · "+p.getString("native_epg_sync_status","NOT_OBSERVED")
        +" · events "+p.getInt("native_epg_sync_events",0)
        +"\nPoster · "+when(p.getLong("poster_sync_success_at",0))
        +" · "+p.getString("poster_sync_status","NOT_UPDATED")
        +" · verified images "+p.getInt("poster_sync_verified_prefetch",0)
        +"\nEPGShare index · "+p.getString("epgshare_discovery_status","NOT_CHECKED")
        +"\n"+tx("تتأجل المهام الثقيلة عند تشغيل 4:2:2، ولا تُحذف البيانات السابقة عند فشل المصدر.",
         "Heavy tasks defer during 4:2:2. Previous valid EPG and artwork are kept when a source fails.");
      new AlertDialog.Builder(this)
        .setTitle(tx("مصادر EPG والبوسترات","EPG and poster sources"))
        .setMessage(report).setPositiveButton("OK",null).show();
    }
    private static String when(long ms){
      if(ms<=0)return "—";
      java.text.SimpleDateFormat format=new java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm",java.util.Locale.ROOT);
      format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Baghdad"));
      return format.format(new java.util.Date(ms));
    }
    private void chooseEpgShare(){
      if(current.channelName.trim().isEmpty()){
        toast(tx("اختر قناة أولاً","Select a channel first"));return;
      }
      status.setText(tx("قراءة فهرس ملفات EPGShare…","Loading EPGShare feed index…"));
      io.execute(()->{
        List<String> found=EpgShareFeedDirectory.discover(this);
        runOnUiThread(()->{
          if(destroyed)return;
          if(found.isEmpty()){
            status.setText(tx("فهرس المصادر غير متاح حالياً، احتفظنا بالربط القديم",
              "Feed index unavailable; saved mapping remains unchanged"));return;
          }
          String[] choices=new String[found.size()];
          for(int i=0;i<choices.length;i++)choices[i]="epg_ripper_"+found.get(i)+".xml.gz";
          new AlertDialog.Builder(this).setTitle(tx("EPGShare · اختيار مصدر القناة","EPGShare · channel source"))
            .setItems(choices,(d,pos)->{
              String feed=found.get(pos);
              EpgShareFeedDirectory.selectForChannel(this,current.channelName,current.channelId,feed);
              status.setText(tx("اختير المصدر: ","Selected feed: ")+feed+
                tx(" · سيُستخدم للقناة الحالية بعد جلب XMLTV", " · will refresh from XMLTV"));
              IraqHubSyncScheduler.runNow(this);
            }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
        });
      });
    }
    private void showAliases(){Map<String,List<String>> aliases=repository.channelAliases();List<String> ids=new ArrayList<>(aliases.keySet());if(ids.isEmpty()){toast(tx("لم تُستورد قنوات XMLTV بعد","No XMLTV channels imported"));return;}int count=Math.min(500,ids.size());String[] labels=new String[count];for(int i=0;i<count;i++)labels[i]=ids.get(i)+" · "+String.join(" / ",aliases.get(ids.get(i)));new AlertDialog.Builder(this).setTitle(tx("اختر معرّف القناة · أول 500","Choose channel ID · first 500")).setItems(labels,(d,index)->mapping.setText(ids.get(index))).setNegativeButton(tx("إلغاء","Cancel"),null).show();}
    private void importUrl(){if(busy)return;String address=url.getText().toString().trim();try{URI uri=new URI(address);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException();}catch(Exception e){toast(tx("أدخل رابط HTTPS صحيحاً","Enter a valid HTTPS URL"));return;}busy=true;int token=++generation;status.setText(tx("جلب XMLTV والتحقق منه…","Fetching and validating XMLTV…"));job=io.submit(()->{try{String xml=SatelliteGuideRepository.fetchXml(address);if(!active(token))return;String host=URI.create(address).getHost();repository.importXml(xml,"XMLTV · "+host);prefs().edit().putString("satellite_xmltv_url",address).apply();runOnUiThread(()->{if(!active(token))return;busy=false;source.setText(repository.sourceSummary());toast(tx("استُورد الدليل؛ حُفظت المطابقة والتوقيت من المصدر","Guide imported with source times and identifiers"));search(false);});}catch(Exception e){failed(token,e);}});}
    private boolean active(int token){return !destroyed&&token==generation&&!Thread.currentThread().isInterrupted();}
    private void failed(int token,Exception error){runOnUiThread(()->{if(!active(token))return;busy=false;status.setText(tx("تعذرت العملية؛ بقي الدليل السابق محفوظاً: ","Operation failed; previous guide kept: ")+error.getMessage());});}
    private void search(boolean watched){if(busy)return;showWatched=watched;busy=true;int token=++generation;String text=query.getText().toString();boolean history=past.isChecked();status.setText(tx("جارٍ البحث…","Searching…"));job=io.submit(()->{try{List<SatelliteGuideRepository.Event> found=watched?repository.watchlist():repository.search(text,System.currentTimeMillis(),history);runOnUiThread(()->{if(!active(token))return;busy=false;results.removeAllViews();int count=Math.min(150,found.size());status.setText((watched?tx("البرامج المتابعة: ","Watched programmes: "):tx("النتائج: ","Results: "))+found.size()+(found.size()>count?tx(" · تُعرض أول 150؛ ضيّق البحث"," · first 150 shown; narrow your search"):"")+(found.isEmpty()?tx("\nلا توجد بيانات مطابقة. استورد دليلاً حديثاً أو شاهد شاشة معلومات القناة لالتقاط EPG المتاح.","\nNo matching data. Import a current guide or show the receiver channel information to observe available EPG."):""));for(int i=0;i<count;i++){SatelliteGuideRepository.Event event=found.get(i);Button button=action(results,label(event),()->details(event));button.setTextSize(14);}source.setText(repository.sourceSummary());});}catch(Exception e){failed(token,e);}});}
    private String label(SatelliteGuideRepository.Event event){long now=System.currentTimeMillis();String phase=event.endMs<=now?tx("أرشيف","Archive"):event.startMs<=now?tx("في موعده حسب الدليل","Scheduled now"):tx("قادم","Upcoming");return phase+(event.repeat?tx(" · إعادة من المصدر"," · marked repeat"):"")+" · "+event.channelName+"\n"+event.title+"\n"+time(event.startMs)+" → "+time(event.endMs);}
    private String time(long millis){return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));}
    private void details(SatelliteGuideRepository.Event event){boolean isWatched=false;for(SatelliteGuideRepository.Event e:repository.watchlist())if(e.id.equals(event.id))isWatched=true;final boolean saved=isWatched;String info=label(event)+"\n\n"+event.category+"\n"+event.description+"\n\n"+tx("المصدر: ","Source: ")+event.source+(event.xmlId.isEmpty()?"":"\nXMLTV: "+event.xmlId)+(event.channelId.isEmpty()?tx("\nهذه القناة غير مرتبطة بمعرّف رسيفر. اضبط الربط قبل تشغيل ترجمتها فوق البث.","\nThis channel has no native receiver binding. Map it before starting captions over its broadcast."):"");String[] choices={tx("البحث عن ملف ترجمة OpenSubtitles","Find an OpenSubtitles file"),tx("المعلومات والصور المفتوحة","Open metadata and artwork"),saved?tx("إلغاء متابعة المواعيد","Remove event reminders"):tx("تنبيه عند البداية والنهاية","Notify at start and end"),tx("فتح EPG الرسيفر الأصلي","Open receiver native EPG")};new AlertDialog.Builder(this).setTitle(event.title).setMessage(info).setPositiveButton(tx("الإجراءات","Actions"),(d,w)->new AlertDialog.Builder(this).setTitle(event.title).setItems(choices,(dialog,index)->{if(index==0)startActivity(programme(new Intent(this,TranslationActivity.class),event));else if(index==1)startActivity(programme(new Intent(this,MediaActivity.class),event));else if(index==2){if(saved)try{repository.unwatch(event.id);toast(tx("أُلغيت المتابعة","Reminders removed"));search(showWatched);}catch(Exception e){toast(e.getMessage());}else watch(event);}else nativeGuide();}).setNegativeButton(tx("رجوع","Back"),null).show()).setNegativeButton(tx("رجوع","Back"),null).show();}
    private Intent programme(Intent intent,SatelliteGuideRepository.Event event){return intent.putExtra("title",event.title).putExtra("channel_id",event.channelId).putExtra("event_start_ms",event.startMs);}
    private void watch(SatelliteGuideRepository.Event event){if(event.endMs<=System.currentTimeMillis()){toast(tx("انتهى هذا البرنامج؛ لا يمكن جدولة تنبيه قديم","This programme ended; old reminders cannot be scheduled"));return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){pendingWatch=event.copy();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);return;}try{repository.watch(event);AlarmManagerNotice();search(showWatched);}catch(Exception e){toast(e.getMessage());}}
    private void AlarmManagerNotice(){android.app.AlarmManager alarms=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);boolean exact=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms();toast(exact?tx("حُفظت المتابعة؛ تنبيهات في مواعيد الدليل","Event watched; reminders follow the guide times"):tx("حُفظت المتابعة. قد يتأخر التنبيه لأن إذن المنبه الدقيق غير متاح","Event watched. Reminders may be delayed without exact alarm access"));}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==NOTIFICATIONS){SatelliteGuideRepository.Event selected=pendingWatch;pendingWatch=null;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&selected!=null)watch(selected);else toast(tx("لم تُفعّل المتابعة لأن الإشعارات غير مسموحة","Reminders were not enabled because notifications are denied"));}}
    private void nativeGuide(){try{startActivity(new Intent("com.tosmart.action.epg_pro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e){toast(tx("واجهة EPG الأصلية غير متاحة بهذا الإجراء على جهازك","The native EPG action is not available on this device"));}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=XMLTV_FILE||result!=RESULT_OK||data==null||data.getData()==null||busy)return;Uri uri=data.getData();busy=true;int token=++generation;status.setText(tx("قراءة XMLTV والتحقق منه…","Reading and validating XMLTV…"));job=io.submit(()->{try{String xml;try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Cannot open XMLTV file");xml=PlaylistRepository.readBounded(in);}if(!active(token))return;repository.importXml(xml,"XMLTV · imported file");runOnUiThread(()->{if(!active(token))return;busy=false;source.setText(repository.sourceSummary());search(false);});}catch(Exception e){failed(token,e);}});}
    @Override protected void onDestroy(){destroyed=true;generation++;if(job!=null)job.cancel(true);io.shutdownNow();super.onDestroy();}
}
