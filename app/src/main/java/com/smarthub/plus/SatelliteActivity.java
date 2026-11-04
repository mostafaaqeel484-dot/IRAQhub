package com.smarthub.plus;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Primary receiver companion page. MainActivity inherits this base. */
public class SatelliteActivity extends HubActivity {
    private static final String TV_PACKAGE="com.tosmart.aurum";
    private static final String START="com.smarthub.plus.satellite.START",OPEN="com.smarthub.plus.satellite.OPEN";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView channel,currentProgramme,nextProgramme,observed,permissionStatus,serviceStatus;
    private EditText query;
    private boolean queryDirty,updatingQuery;
    private SatelliteState state;private SatelliteGuideRepository mappedGuide;
    private final Runnable poll=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1500);}};

    @Override public void onCreate(Bundle saved){super.onCreate(saved);ProviderSecrets.clearLegacy(this);build();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs().getBoolean("notification_asked",false)){prefs().edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},91);}}
    private void build(){
        LinearLayout body=page(tx("IRAQhub • مساعد القنوات الفضائية","IRAQhub • Satellite TV companion"),tx("معلومات البرنامج وترجمته فوق القنوات الحقيقية على الرسيفر","Programme information and subtitles over your receiver's real TV channels"));
        LinearLayout live=card(body);
        channel=text("",24,true);channel.setTextColor(ACCENT);live.addView(channel);
        currentProgramme=text("",19,true);live.addView(currentProgramme);
        nextProgramme=text("",16,false);live.addView(nextProgramme);
        observed=text("",13,false);observed.setTextColor(MUTED);live.addView(observed);
        serviceStatus=text("",14,false);live.addView(serviceStatus);
        action(live,tx("تشغيل المساعد والعودة إلى القنوات","Start companion and return to TV"),()->start(START));
        action(live,tx("فتح لوحة المساعد فوق القناة","Open companion panel over TV"),()->start(OPEN));
        action(live,tx("إيقاف مساعد القنوات","Stop satellite companion"),this::stop);
        LinearLayout sports=card(body);action(sports,tx("مركز المباريات والدوريات · Sports Center","Matches and leagues · Sports Center"),()->startActivity(new Intent(this,SportsCenterActivity.class)));note(sports,CHMAXIntegration.diagnostics(this));LinearLayout permissions=card(body);action(permissions,tx("مصادر الشعارات وإضافات ملفات CHL","Logo sources and CHL file add-ons"),()->startActivity(new Intent(this,LogoSourcesActivity.class)));action(permissions,tx("ربط المصنع · CHMAX وEPG","Factory link · CHMAX and EPG"),()->startActivity(new Intent(this,FactorySettingsActivity.class)));action(permissions,tx("الترجمة الفورية DVB / TXT وOCR","DVB / TXT live translation and OCR"),()->startActivity(new Intent(this,LiveTranslationActivity.class)));action(permissions,tx("مظهر شريط المعلومات","Information bar appearance"),()->startActivity(new Intent(this,OsdSettingsActivity.class)));action(permissions,tx("إعداد مصادر الصور والترجمة داخل التطبيق","Built-in artwork and subtitle provider settings"),()->startActivity(new Intent(this,ProviderSetupActivity.class)));
        permissions.addView(text(tx("الربط والصلاحيات","Connection and permissions"),20,true));
        note(permissions,tx("إذن الظهور يتيح لوحة المساعد فوق صورة التلفاز. خدمة IRAQhub — ربط القنوات تقرأ اسم القناة وعناوين وأوقات EPG الظاهرة من تطبيق الرسيفر com.tosmart.aurum فقط، وتحفظ آخر قراءة على الجهاز. يستخدم المساعد اسم التطبيق الأمامي لتحديد ظهور لوحته. تفعيل الخدمة يتم يدوياً من إعدادات Android، ويمكن إيقافها في أي وقت.","Overlay access displays the companion over TV. IRAQhub — ربط القنوات reads visible channel names and EPG titles/times only from the receiver app com.tosmart.aurum, and keeps the last observation on this device. The companion uses the foreground app identity to control its visibility. Enable the bridge manually in Android settings; it can be disabled at any time."));
        permissionStatus=text("",14,true);permissions.addView(permissionStatus);
        action(permissions,tx("1 · منح إذن الظهور فوق التطبيقات","1 · Allow display over other apps"),this::overlaySettings);
        action(permissions,tx("2 · فتح إعدادات خدمة IRAQhub — ربط القنوات","2 · Open IRAQhub — ربط القنوات accessibility settings"),this::accessibilitySettings);
        CheckBox menu=check(permissions,tx("زر MENU يفتح قائمة IRAQhub أثناء مشاهدة القنوات","MENU opens IRAQhub home while watching TV"),prefs().getBoolean("satellite_menu_shortcut",true));
        menu.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("satellite_menu_shortcut",checked).apply());
        CheckBox blue=check(permissions,tx("اختصار الزر الأزرق: يستبدل وظيفته الأصلية أثناء عمل المساعد داخل تطبيق القنوات","Blue shortcut: replaces its original action while the companion is active in TV"),prefs().getBoolean("satellite_blue_shortcut",false));
        blue.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("satellite_blue_shortcut",checked).apply());
        note(permissions,tx("الاختصار اختياري ومتوقف افتراضياً. افتح شريط معلومات القناة أو EPG في الرسيفر حتى تظهر بيانات يمكن قراءتها. إذا لم يعرض المصنع عنوان البرنامج ووقته، تُعرض حالة عدم توفر EPG؛ يمكنك ربط XMLTV من الدليل.","The shortcut is optional and off by default. Show the receiver channel information banner or EPG so that published text can be observed. If the manufacturer exposes no programme title/time, the app reports unavailable EPG; you can map an XMLTV guide."));
        CheckBox automatic=check(permissions,tx("البحث والتنزيل التلقائي للترجمة عند مطابقة الفيلم — قد يستهلك حصة المزود","Automatic subtitle search and download for verified film matches — uses provider allowance"),prefs().getBoolean("satellite_auto_subtitles",true));automatic.setOnCheckedChangeListener((button,on)->prefs().edit().putBoolean("satellite_auto_subtitles",on).apply());
        LinearLayout programme=card(body);
        programme.addView(text(tx("البحث عن البرنامج","Find the programme"),20,true));
        query=field(programme,tx("عنوان EPG أو اسم تدخله يدوياً","EPG title or a title you enter manually"),"");
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!updatingQuery)queryDirty=true;}public void afterTextChanged(Editable text){}});
        action(programme,tx("استخدام عنوان البرنامج المرصود","Use observed programme title"),()->{SatelliteState actual=contextState();String title=freshTitle(actual,System.currentTimeMillis());if(title.isEmpty()){toast(tx("لا يوجد عنوان EPG حديث؛ اكتب عنواناً يدوياً","No recent EPG title; enter a title manually"));return;}queryDirty=false;setQuery(title);});
        action(programme,tx("ترجمات OpenSubtitles + Subsource","OpenSubtitles + Subsource subtitles"),()->programme(TranslationActivity.class));
        action(programme,tx("معلومات العمل والصور المتاحة","Programme information and available artwork"),()->programme(MediaActivity.class));
        note(programme,tx("المزودان يجلبان ملفات الترجمة مباشرة من الإنترنت، وتُحفظ مفاتيحك مشفرة على التلفزيون. اختر إصدار الفيلم أو الحلقة الصحيح واضبط التزامن عند الإعلانات أو اختلاف النسخة. الصور والمعلومات تأتي من Wikidata وWikimedia Commons مع مصدر الصورة وترخيصها حين تتوفر؛ لا يُختلق ملصق عند غياب صورة مناسبة.","Both providers retrieve subtitle files directly over HTTPS; your keys remain encrypted on this TV. Choose the correct film or episode release and adjust synchronization for advertisements or different cuts. Metadata and available images come from Wikidata and Wikimedia Commons with source and license; unavailable artwork is not fabricated."));
        LinearLayout tools=card(body);
        action(tools,tx("دليل القنوات والبحث ومتابعة البرامج","Programme guide, search and event reminders"),()->startActivity(new Intent(this,SatelliteGuideActivity.class)));
        action(tools,tx("أدوات الشاشة والشعارات ومزامنة الترجمة","Screen tools, logos and caption synchronization"),()->startActivity(new Intent(this,ScreenToolsActivity.class)));
        action(tools,tx("فتح EPG الأصلي للرسيفر","Open receiver native EPG"),()->{if(!openNative(this,true))toast(tx("تعذر فتح دليل الرسيفر الأصلي","Cannot open the receiver native EPG"));});
        action(tools,tx("إدارة وترتيب القنوات من واجهة الرسيفر","Open native channel management"),()->{try{startActivity(new Intent("com.tosmart.home.menu.navigation").setPackage(TV_PACKAGE));}catch(Exception e){toast(tx("افتح إدارة القنوات من قائمة الرسيفر الأصلية","Open channel management from the native receiver menu"));}});
        action(tools,tx("العودة إلى القنوات الفضائية","Return to satellite TV"),()->{startActivity(new Intent(this,DvbPlaybackActivity.class));});
        action(tools,tx("المزيد من الأدوات والمكتبة","More tools and personal library"),()->startActivity(new Intent(this,ToolsActivity.class)));
        note(tools,tx("قاعدة بيانات قنوات المصنع محمية بصلاحيات الشركة. يعمل المساعد مع المعلومات التي يتيحها النظام والشاشة، ومع دليل XMLTV الذي تختاره، ويحفظ إعداداته في مساحة التطبيق.","The manufacturer's channel database is protected by vendor permissions. The companion uses information published by the system and receiver UI, plus your chosen XMLTV guide, and keeps its settings in app storage."));
        action(tools,ar?"English":"العربية",()->{prefs().edit().putBoolean("arabic",!ar).apply();ar=!ar;queryDirty=false;build();});
        refresh();
    }
    private SatelliteState contextState(){return ReceiverInfoRepository.get(this).current();}
    private void setQuery(String title){if(title.contentEquals(query.getText()))return;updatingQuery=true;query.setText(title);updatingQuery=false;}
    private static String freshTitle(SatelliteState state,long now){if(state==null)return "";if(!state.source.startsWith("XMLTV")&&(state.observedAt<=0||state.observedAt>now+60000||now-state.observedAt>120000))return "";if(state.title==null||state.title.trim().isEmpty())return "";if(state.endMs>0&&state.endMs<=now||state.startMs>now)return "";return state.title;}
    private void refresh(){if(channel==null)return;state=contextState();long now=System.currentTimeMillis();boolean fresh=state.observedAt>0&&state.observedAt<=now+60000&&now-state.observedAt<=120000;boolean active=prefs().getBoolean("satellite_tv_visible",false);String name=state.channelName.isEmpty()?state.channelId:state.channelName;
        setIfChanged(channel,name.isEmpty()?tx("لم تُرصد قناة بعد","No channel observed yet"):(fresh&&active?tx("القناة المرصودة: ","Observed channel: "):tx("آخر قناة محفوظة: ","Last saved channel: "))+name);
        if(state.title.isEmpty())setIfChanged(currentProgramme,tx("EPG غير متاح: لا يوجد عنوان برنامج منشور حالياً","EPG unavailable: no programme title is currently published"));
        else{String label=fresh&&active?tx("البرنامج المرصود: ","Observed programme: "):tx("عنوان محفوظ يحتاج تأكيداً من القناة: ","Saved title awaiting channel confirmation: ");setIfChanged(currentProgramme,label+state.title+"\n"+(state.startMs>0&&state.endMs>state.startMs?time(state.startMs)+" → "+time(state.endMs):tx("وقت البرنامج غير متاح من المصدر","Programme time is unavailable from source")));}
        setIfChanged(nextProgramme,!state.nextTitle.isEmpty()&&state.nextStartMs>now?tx("التالي حسب آخر EPG: ","Next from last EPG: ")+state.nextTitle+" · "+time(state.nextStartMs):tx("لا توجد بيانات موثوقة لبرنامج تالٍ","No reliable next-programme data"));
        String source=state.source.isEmpty()?tx("غير متاح","unavailable"):state.source;setIfChanged(observed,tx("المصدر: ","Source: ")+source+"\n"+tx("آخر قراءة: ","Last observation: ")+(state.observedAt>0?time(state.observedAt):tx("لم تصل بيانات","no data received"))+(!fresh&&state.observedAt>0?tx(" · قراءة محفوظة وليست تحديثاً آنياً"," · saved observation, not a current update"):""));
        boolean allowed=Settings.canDrawOverlays(this),enabled=accessibilityEnabled(),connected=prefs().getBoolean("satellite_accessibility_connected",false);setIfChanged(permissionStatus,tx("الظهور: ","Overlay: ")+(allowed?tx("مسموح","allowed"):tx("يحتاج إذناً","needs access"))+" • "+tx("خدمة الربط: ","Receiver bridge: ")+(enabled&&connected?tx("متصلة","connected"):enabled?tx("مفعّلة؛ بانتظار الاتصال","enabled; waiting for connection"):tx("غير مفعّلة","disabled")));
        boolean running=prefs().getBoolean("satellite_companion_enabled",false);String error=prefs().getString("satellite_shortcut_error","");if(error.isEmpty())error=prefs().getString("satellite_companion_error","");if(error.isEmpty())error=prefs().getString("satellite_error","");setIfChanged(serviceStatus,(running?tx("مساعد القنوات مفعّل","Satellite companion enabled"):tx("مساعد القنوات متوقف","Satellite companion stopped"))+(error.isEmpty()?"":"\n"+error));
        if(!queryDirty&&!query.hasFocus())setQuery(freshTitle(state,now));
    }
    private static void setIfChanged(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private String time(long at){return DateTimeFormatter.ofPattern("dd/MM HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at));}
    private boolean accessibilityEnabled(){try{if(Settings.Secure.getInt(getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;String list=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(list==null)return false;ComponentName expected=new ComponentName(this,SatelliteAccessibilityService.class);for(String item:list.split(":")){ComponentName name=ComponentName.unflattenFromString(item);if(expected.equals(name))return true;}}catch(Exception ignored){}return false;}
    private void overlaySettings(){try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast(tx("افتح إذن الظهور فوق التطبيقات من إعدادات Android","Open display-over-other-apps access in Android settings"));}}
    private void accessibilitySettings(){new AlertDialog.Builder(this).setTitle("IRAQhub — ربط القنوات").setMessage(tx("فعّل هذه الخدمة لقراءة اسم القناة وEPG الظاهر من تطبيق الرسيفر، ولفتح IRAQhub بزر MENU أثناء المشاهدة. يمكنك إلغاء اختصار MENU من هذه الصفحة. يبقى الزر الأزرق بوظيفته المعتادة حتى تفعّل اختصاره. ستفتح إعدادات Android لاختيار الخدمة بنفسك.","Enable this bridge to read the receiver's visible channel and EPG data and open IRAQhub with MENU while watching. You can disable the MENU shortcut on this page. Blue retains its normal action until you enable its shortcut. Android settings will open so you can choose the service yourself.")).setNegativeButton(tx("رجوع","Back"),null).setPositiveButton(tx("فتح الإعدادات","Open settings"),(d,w)->{try{AccessibilitySetup.open(this);}catch(Exception e){toast(e.getMessage());}}).show();}
    private void start(String action){if(!Settings.canDrawOverlays(this)){toast(tx("امنح إذن الظهور ثم شغّل المساعد","Allow overlay access, then start the companion"));overlaySettings();return;}try{prefs().edit().putBoolean("satellite_companion_enabled",true).apply();startForegroundService(new Intent().setClassName(getPackageName(),"com.smarthub.plus.SatelliteCompanionService").setAction(action));startActivity(new Intent(this,DvbPlaybackActivity.class));}catch(Exception e){prefs().edit().putBoolean("satellite_companion_enabled",false).apply();toast(e.getMessage());}refresh();}
    private void stop(){prefs().edit().putBoolean("satellite_companion_enabled",false).apply();stopService(new Intent().setClassName(getPackageName(),"com.smarthub.plus.SatelliteCompanionService"));refresh();toast(tx("أُوقف مساعد القنوات","Satellite companion stopped"));}
    private void programme(Class<?> screen){String chosen=query.getText().toString().trim();if(chosen.length()<2){toast(tx("اختر عنوان EPG حديثاً أو اكتب اسم البرنامج يدوياً","Choose a recent EPG title or enter the programme name manually"));return;}SatelliteState actual=contextState();Intent intent=new Intent(this,screen).putExtra("title",chosen).putExtra("channel_id",actual.channelId).putExtra("event_start_ms",actual.startMs);String bound=prefs().getString("satellite_imdb_title","");if(chosen.equals(actual.title)&&chosen.equals(bound)){String id=prefs().getString("satellite_imdb_id","");if(!id.isEmpty())intent.putExtra("imdb_id",id);}startActivity(intent);}
    /** Opens only the known receiver application; never sends tuning or database writes. */
    public static boolean openNative(Context context,boolean epg){
        try{context.startActivity(new Intent(epg?"com.tosmart.action.epg_pro":"android.intent.action.home").setPackage(TV_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(Exception ignored){}
        try{Intent launcher=context.getPackageManager().getLeanbackLaunchIntentForPackage(TV_PACKAGE);if(launcher==null)launcher=context.getPackageManager().getLaunchIntentForPackage(TV_PACKAGE);if(launcher!=null){context.startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}}catch(Exception ignored){}return false;
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(poll);handler.post(poll);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
