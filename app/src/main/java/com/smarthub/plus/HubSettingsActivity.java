package com.smarthub.plus;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

/** RX settings navigation to the existing receiver and app tools. */
public final class HubSettingsActivity extends HubActivity {
 private static final int GOLD=0xfff1c665;
 private static final int BACKGROUND=0xff0b0d11;
 private static final int PANEL_COLOR=0xff14171d;
 private static final int ROW_COLOR=0xff1b1e25;
 private static final int LINE_COLOR=0xff373a43;
 private static final int FOCUS_COLOR=0xff302718;
 private static final String ACTIVE_SECTION="active_section";
 private static final int EXPORT_RECEIVER_BACKUP=8806;
 private static final int VERIFY_RECEIVER_BACKUP=8807;
 private enum Section { VIDEO, CHANNELS, IMPORT, INFOBAR, API, ANTENNA, ADVANCED }

 private final Button[] sectionButtons=new Button[Section.values().length];
 private boolean additionalVisible;
 private Button moreButton;
 private Section activeSection;
 private LinearLayout options;
 private ScrollView optionsScroll;
 private TextView sectionTitle,sectionSubtitle;
 private View firstOption;

 private void backupChannels(){
  Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("application/zip");
  intent.putExtra(Intent.EXTRA_TITLE,"IRAQhub_channels_backup_"+System.currentTimeMillis()+".zip");
  launchDocument(intent,EXPORT_RECEIVER_BACKUP);
 }
 private void verifyChannelsBackup(){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
  launchDocument(i,VERIFY_RECEIVER_BACKUP);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==VERIFY_RECEIVER_BACKUP&&result==RESULT_OK&&data!=null&&data.getData()!=null){
   android.net.Uri uri=data.getData();
   new Thread(()->{
    try{
     String summary=ReceiverBackup.verify(this,uri);
     runOnUiThread(()->{
      if(isFinishing()||isDestroyed())return;
      new android.app.AlertDialog.Builder(this)
        .setTitle(tx("النسخة الاحتياطية سليمة","Backup integrity verified"))
        .setMessage(summary).setPositiveButton("OK",null).show();
     });
    }catch(Exception failed){
     runOnUiThread(()->toast(tx("النسخة الاحتياطية غير صالحة","Backup verification failed")));
    }
   },"receiver-backup-verify").start();
   return;
  }
  if(request!=EXPORT_RECEIVER_BACKUP||result!=RESULT_OK||data==null||data.getData()==null)return;
  android.net.Uri uri=data.getData();
  new Thread(()->{
   try{
    ReceiverBackup.export(this,uri);
    runOnUiThread(()->toast(tx("تم حفظ نسخة القنوات الاحتياطية","Channel backup saved")));
   }catch(Exception failure){
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     new android.app.AlertDialog.Builder(this)
       .setTitle(tx("تعذر إنشاء النسخة الاحتياطية","Backup failed"))
       .setMessage(String.valueOf(failure.getMessage()))
       .setPositiveButton("OK",null).show();
    });
   }
  },"iraqhub-catalog-backup").start();
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout root=col();
  root.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
  root.setPadding(dp(28),dp(18),dp(28),dp(14));
  root.setBackgroundColor(BACKGROUND);

  LinearLayout header=row();
  LinearLayout heading=col();
  TextView edition=text("IRAQhub  /  RX",12,true);
  edition.setTextColor(GOLD);
  edition.setLetterSpacing(.09f);
  heading.addView(edition);
  heading.addView(text(tx("الإعدادات · بسيطة وواضحة","Settings · Simple & clear"),27,true));
  header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Button back=settingButton(tx("رجوع","Back"));
  back.setOnClickListener(view->finish());
  header.addView(back,new LinearLayout.LayoutParams(dp(105),dp(48)));
  root.addView(header);

  LinearLayout panels=row();
  panels.setGravity(Gravity.TOP);
  LinearLayout.LayoutParams panelSize=new LinearLayout.LayoutParams(-1,0,1);
  panelSize.topMargin=dp(16);
  root.addView(panels,panelSize);

  ScrollView railScroll=new ScrollView(this);
  railScroll.setFillViewport(true);
  railScroll.setBackground(surface(PANEL_COLOR,LINE_COLOR,1));
  LinearLayout rail=col();
  rail.setPadding(dp(10),dp(10),dp(10),dp(10));
  railScroll.addView(rail);
  int railWidth=Math.max(200,Math.min(305,getResources().getConfiguration().screenWidthDp/3));
  LinearLayout.LayoutParams railSize=new LinearLayout.LayoutParams(dp(railWidth),-1);
  railSize.setMarginEnd(dp(14));
  panels.addView(railScroll,railSize);
  additionalVisible=prefs().getBoolean("rx7_show_more_settings",false);
  for(Section section:Section.values()){
   Button entry=settingButton(title(section));
   sectionButtons[section.ordinal()]=entry;
   entry.setOnFocusChangeListener((view,focused)->{
    if(focused)showSection(section);
   });
   entry.setOnClickListener(view->{
    showSection(section);
    if(firstOption!=null)firstOption.requestFocus();
   });
   LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
   size.bottomMargin=dp(7);
   rail.addView(entry,size);
   if(Rx7SimpleUiPolicy.isAdvancedCategory(section.name()))
    entry.setVisibility(Rx7SimpleUiPolicy.showCategory(section.name(),additionalVisible)?View.VISIBLE:View.GONE);
  }
  moreButton=settingButton(tx("المزيد: مفاتيح API وأدوات متقدمة","More: API keys & advanced tools"));
  moreButton.setOnClickListener(v->{
   additionalVisible=!additionalVisible;
   prefs().edit().putBoolean("rx7_show_more_settings",additionalVisible).apply();
   sectionButtons[Section.API.ordinal()].setVisibility(additionalVisible?View.VISIBLE:View.GONE);
   sectionButtons[Section.ADVANCED.ordinal()].setVisibility(additionalVisible?View.VISIBLE:View.GONE);
   moreButton.setText(additionalVisible?
      tx("إخفاء الخيارات المتقدمة","Hide advanced options"):
      tx("المزيد: مفاتيح API وأدوات متقدمة","More: API keys & advanced tools"));
   if(!additionalVisible&&(activeSection==Section.API||activeSection==Section.ADVANCED))
    showSection(Section.VIDEO);
  });
  moreButton.setText(additionalVisible?
     tx("إخفاء الخيارات المتقدمة","Hide advanced options"):
     tx("المزيد: مفاتيح API وأدوات متقدمة","More: API keys & advanced tools"));
  LinearLayout.LayoutParams moreSize=new LinearLayout.LayoutParams(-1,-2);
  moreSize.topMargin=dp(8);
  rail.addView(moreButton,moreSize);
  sectionButtons[0].setNextFocusUpId(back.getId());
  back.setNextFocusDownId(sectionButtons[0].getId());

  LinearLayout detail=col();
  detail.setPadding(dp(18),dp(13),dp(18),dp(8));
  detail.setBackground(surface(PANEL_COLOR,LINE_COLOR,1));
  panels.addView(detail,new LinearLayout.LayoutParams(0,-1,1));
  sectionTitle=text("",23,true);
  sectionTitle.setTextColor(GOLD);
  detail.addView(sectionTitle);
  sectionSubtitle=text("",13,false);
  sectionSubtitle.setTextColor(MUTED);
  sectionSubtitle.setPadding(0,dp(4),0,dp(12));
  detail.addView(sectionSubtitle);
  optionsScroll=new ScrollView(this);
  options=col();
  optionsScroll.addView(options);
  detail.addView(optionsScroll,new LinearLayout.LayoutParams(-1,0,1));

  TextView footer=text(tx("أسهم الريموت للتنقل  ·  OK للفتح  ·  رجوع للخروج",
    "Remote arrows to navigate  ·  OK to open  ·  Back to exit"),12,false);
  footer.setTextColor(MUTED);
  footer.setPadding(0,dp(9),0,0);
  root.addView(footer);

  int ordinal=state==null?0:state.getInt(ACTIVE_SECTION,0);
  Section initial=Section.values()[Math.max(0,Math.min(Section.values().length-1,ordinal))];
  if(!additionalVisible&&(initial==Section.API||initial==Section.ADVANCED))
   initial=Section.VIDEO;
  showSection(initial);
  setContentView(root);
  sectionButtons[initial.ordinal()].requestFocus();
 }

 @Override protected void onSaveInstanceState(Bundle state){
  super.onSaveInstanceState(state);
  if(activeSection!=null)state.putInt(ACTIVE_SECTION,activeSection.ordinal());
 }

 private void showSection(Section section){
  if(activeSection==section||options==null)return;
  activeSection=section;
  for(Section item:Section.values()){
   Button entry=sectionButtons[item.ordinal()];
   entry.setSelected(item==section);
   entry.setTextColor(item==section?GOLD:WHITE);
  }
  sectionTitle.setText(title(section));
  sectionSubtitle.setText(subtitle(section));
  options.removeAllViews();
  optionsScroll.scrollTo(0,0);
  firstOption=null;
  switch(section){
   case VIDEO: showVideo(); break;
   case CHANNELS: showChannels(); break;
    case IMPORT: showImport(); break;
   case INFOBAR: showInfoBar(); break;
    case API: showApi(); break;
   case ANTENNA: showAntenna(); break;
   case ADVANCED: showAdvanced(); break;
  }
  if(firstOption!=null){
   Button selected=sectionButtons[section.ordinal()];
   if(ar)selected.setNextFocusLeftId(firstOption.getId());
   else selected.setNextFocusRightId(firstOption.getId());
  }
 }

 private String title(Section section){
  switch(section){
   case VIDEO: return tx("المشاهدة","Watching TV");
   case CHANNELS: return tx("القنوات","Channels");
   case IMPORT: return tx("استيراد ونسخ","Import & backups");
   case API: return tx("مفاتيح الخدمات","Service API keys");
   case INFOBAR: return tx("الدليل والصور","Guide & posters");
   case ANTENNA: return tx("الطبق والإشارة","Dish & signal");
   default: return tx("أدوات متقدمة","Advanced tools");
  }
 }

 private String subtitle(Section section){
  switch(section){
   case VIDEO: return tx("المشاهدة، التسجيل، والصوت","Watch, record and listen");
   case CHANNELS: return tx("اختيار القنوات والبحث عنها","Browse and scan channels");
   case IMPORT: return tx("إضافة القنوات والاحتفاظ بنسخة احتياطية","Add channels and keep a backup");
   case API: return tx("إعداد مصادر البوسترات والترجمة ومفاتيحها","Poster and subtitles provider credentials");
   case INFOBAR: return tx("ما يظهر أثناء المشاهدة","What you see while watching");
   case ANTENNA: return tx("الأقمار والترددات وتوصيلات الطبق","Satellites, transponders and dish configuration");
   default: return tx("إعدادات الجهاز وحالة الخدمات والريموت","Device settings, service status and remote");
  }
 }

 private void showVideo(){
  link(tx("شاهد التلفزيون","Watch live TV"),
    tx("ارجع لآخر قناة مباشرة","Continue your last channel"),
    DvbPlaybackActivity.class);
  link(tx("التسجيل","Record TV"),tx("سجل القناة على USB","Record to USB"),
    PvrRecordingActivity.class);
  link(tx("الصوت والترجمة","Audio & subtitles"),
    tx("اختر صوت القناة أو الترجمة","Choose channel audio or subtitles"),
    SatelliteActivity.class);
  note(options,tx("ضبط محرك 4:2:2 والمعاينة الخاصة موجود في «المزيد».",
    "Special 4:2:2 engines and preview links are in More."));
 }

 private void showChannels(){
  link(tx("كل القنوات","All channels"),
    tx("القنوات والأقمار والمفضلة","Channels, satellites and favourites"),
    ReceiverChannelsActivity.class);
  link(tx("ترتيب القنوات","Organise channels"),
    tx("نقل، حذف، مفضلة وترتيب","Move, delete and favourite"),
    ChannelManageActivity.class);
  link(tx("بحث عن قناة","Find new channels"),
    tx("أدخل تردد القناة للبحث اليدوي","Enter a frequency to scan"),
    ManualTransponderActivity.class);
  link(tx("بحث سريع","Quick scan"),
    tx("ابحث عن قنوات جديدة","Scan for channels"),
    ReceiverFastScanMenuActivity.class);
 }

 private void showImport(){
  note(options,tx("ملفاتك آمنة: لا نغيّر قاعدة قنوات المصنع.",
    "Your receiver's original channels will not be modified."));
  link(tx("إضافة قنوات من ملف CHL","Add channels from CHL"),
    tx("اختر ملف القنوات واربط اللوغوات","Choose a channel file and link logos"),
    LogoSourcesActivity.class);
  link(tx("فحص ملف قنوات SQLite","Inspect channel database"),
    tx("قراءة فقط، دون استبدال قنواتك","Read-only, never replaces channels"),
    ReceiverDbImportActivity.class);
  option(tx("حفظ نسخة احتياطية","Back up my channels"),
    tx("احفظ نسخة ZIP على USB","Save a ZIP backup to USB"),this::backupChannels);
  option(tx("فحص نسخة احتياطية","Check a backup"),
    tx("تأكد من سلامة ملف ZIP قبل استخدامه","Verify ZIP integrity"),
    this::verifyChannelsBackup);
  note(options,tx("استيراد خدمات MPEG-TS وأدوات الدمج الفنية في «المزيد».",
    "MPEG-TS discovery and expert import tools are in More."));
 }

 private void showApi(){
  link(tx("مفاتيح TMDB وRPDB وOPDB وOMDb وTheSportsDB","Poster API keys"),
    tx("حفظ وتحقق وحذف المفاتيح المشفرة لكل مصدر",
      "Save, test and remove encrypted credentials per provider"),
    PosterApiSettingsActivity.class);
  link(tx("مفاتيح الترجمة وTMDB","Subtitle and metadata API keys"),
    tx("OpenSubtitles وSubsource وتوثيق TMDB المشفر",
      "OpenSubtitles, Subsource and encrypted TMDB"),
    ProviderSetupActivity.class);
  link(tx("مصادر البوسترات وترتيبها","Poster providers and priorities"),
    tx("BetterPosters أولاً وEPG المصنع قبل الإنترنت",
      "BetterPosters first; factory and DVB EPG retained"),
    ArtworkSourcesActivity.class);
  note(options,tx("المفاتيح تبقى مشفرة داخل الجهاز ولا تُضمّن في APK أو ملفات المصدر.",
    "API keys stay encrypted on-device, never embedded in APK/source."));
 }
 private void showInfoBar(){
  link(tx("معلومات القناة والبوسترات","Channel info & posters"),
    tx("شكل الـInfoBar وصور البرامج","InfoBar and programme artwork"),
    ViewingSettingsActivity.class);
  link(tx("شعارات القنوات","Channel logos"),
    tx("تحميل وتفعيل الشعارات","Fetch and enable channel logos"),
    LogoSourcesActivity.class);
  link(tx("شكل الرئيسية","Home appearance"),
    tx("الألوان والخلفيات","Colours and wallpapers"),
    RxAppearanceActivity.class);
  CheckBox extras=check(options,
    tx("عرض الطقس والصلاة والمباريات والاختصارات الإضافية بالرئيسية",
       "Show weather, prayer, matches and extra shortcuts on Home"),
    prefs().getBoolean("rx7_home_show_extras",false));
  extras.setPadding(dp(10),dp(10),dp(10),dp(10));
  extras.setBackground(focusBackground());
  connectToRail(extras);
  extras.setOnCheckedChangeListener((button,on)->{
   prefs().edit().putBoolean("rx7_home_show_extras",on).apply();
   toast(tx("يتغير شكل الرئيسية عند الرجوع إليها","Home will update when you return"));
  });
  long lastSync=prefs().getLong("online_epg_sync_at",0);
  String last=lastSync>0?DateFormat.getDateTimeInstance().format(new Date(lastSync)):
    tx("لم يحدث بعد","Not yet");
  option(tx("تحديث الصور والدليل الآن","Refresh guide & pictures"),
    tx("آخر تحديث: ","Last updated: ")+last,()->{
     IraqHubSyncScheduler.runNow(this);
     toast(tx("جارٍ التحديث في الخلفية","Updating in the background"));
    });
 }

 private void showAntenna(){
  link(tx("الأقمار والترددات","Satellites and transponders"),"",InstallationEditorActivity.class);
  link(tx("ضبط LNB وDiSEqC","LNB and DiSEqC profiles"),
    tx("Universal 9750/10600 والطاقة و22 kHz والمنافذ","Universal 9750/10600, power, 22 kHz and switch ports"),
    TunerSettingsActivity.class);
  link(tx("الموتور وUSALS","Motor and USALS"),tx("موقع الطبق وحدود الأمان والإيقاف","Dish position, safety limits and stop"),
    MotorCenterActivity.class);
  link(tx("مقياس الإشارة","Signal meter"),tx("القفل والجودة وBER وSNR","Tuner lock, quality, BER and SNR"),
    SignalInformationActivity.class);
  link(tx("أدوات تركيب Aurum الأصلية","Original Aurum installation tools"),"",ManufacturerToolsActivity.class);
 }

 private void showAdvanced(){
  note(options,tx("هذه الأدوات للتركيب وحل المشاكل فقط. لا تحتاجها للمشاهدة اليومية.",
    "These tools are for setup and troubleshooting, not daily TV use."));
  link(tx("استيراد خدمات MPEG-TS","Discover MPEG-TS services"),"",
    TsServiceImportActivity.class);
  link(tx("إعدادات 4:2:2 والمحركات","4:2:2 & decoders"),"",
    Unified422SettingsActivity.class);
  link(tx("اختبار تشغيل 4:2:2","Test 4:2:2 playback"),"",
    Software422CenterActivity.class);
  link(tx("معاينة الفيديو في الرئيسية","Home video preview"),
    tx("اختياري ويحتاج رابط بث منفصل","Optional, requires a separate stream URL"),
    Rx7HomePreviewSettingsActivity.class);
  CheckBox safe=check(options,tx("المشغل الآمن","Safe playback"),SafePlaybackMode.enabled(this));
  safe.setBackground(focusBackground());
  connectToRail(safe);
  safe.setOnCheckedChangeListener((button,on)->{
   SafePlaybackMode.set(this,on);
   toast(tx("افتح القناة مجدداً لتطبيق التغيير","Reopen the channel to apply"));
  });
  link(tx("صوت إنترنت للقناة","Internet audio for a channel"),"",IPAudioActivity.class);
  link(tx("إعدادات التطبيق والجهاز","App & system settings"),"",AdvancedSettingsActivity.class);
  link(tx("إعداد الدليل الخارجي يدوياً","Manual external EPG"),"",ManualEpgActivity.class);
  link(tx("الترجمة الفورية","Live translation"),"",LiveTranslationActivity.class);
  link(tx("شعارات مزودي القنوات","Provider logos"),"",ProviderLogoCatalogActivity.class);
  link(tx("إضافة شعار مزود","Add a provider logo"),"",ProviderLogoEditorActivity.class);
  link(tx("التوافق ومصادر البيانات","Compatibility and data sources"),"",CompatibilityActivity.class);
  link(tx("تشخيص DVB","DVB diagnostics"),"",ReceiverDeveloperMenuActivity.class);
  link(tx("اتصال ADB ولوحة الجهاز","ADB & front panel"),"",ReceiverHardwareBridgeActivity.class);
  link(tx("اختصارات الريموت","Remote controls"),"",RemoteHelpActivity.class);
 }

 private void link(String title,String description,Class<?> destination){
  option(title,description,()->startActivity(new Intent(this,destination)));
 }

 private void option(String title,String description,Runnable open){
  Button action=settingButton(title);
  if(!description.isEmpty()){
   SpannableString label=new SpannableString(title+"\n"+description);
   int start=title.length()+1;
   label.setSpan(new RelativeSizeSpan(.82f),start,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   label.setSpan(new ForegroundColorSpan(MUTED),start,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   action.setText(label);
  }
  connectToRail(action);
  action.setOnClickListener(view->open.run());
  LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
  size.bottomMargin=dp(7);
  options.addView(action,size);
 }

 private void connectToRail(View item){
  if(item.getId()==View.NO_ID)item.setId(View.generateViewId());
  item.setFocusable(true);
  if(firstOption==null)firstOption=item;
  int railId=sectionButtons[activeSection.ordinal()].getId();
  if(ar)item.setNextFocusRightId(railId);
  else item.setNextFocusLeftId(railId);
 }

 private Button settingButton(String label){
  Button button=button(label);
  button.setId(View.generateViewId());
  button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
  button.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
  button.setMinHeight(dp(53));
  button.setStateListAnimator(null);
  button.setElevation(0);
  button.setPadding(dp(13),dp(9),dp(13),dp(9));
  button.setBackground(focusBackground());
  button.setBackgroundTintList(null);
  button.setOnFocusChangeListener(null);
  return button;
 }

 private StateListDrawable focusBackground(){
  StateListDrawable states=new StateListDrawable();
  states.addState(new int[]{android.R.attr.state_focused},surface(FOCUS_COLOR,GOLD,2));
  states.addState(new int[]{android.R.attr.state_pressed},surface(FOCUS_COLOR,GOLD,2));
  states.addState(new int[]{android.R.attr.state_selected},surface(FOCUS_COLOR,GOLD,1));
  states.addState(new int[]{},surface(ROW_COLOR,LINE_COLOR,1));
  return states;
 }

 private GradientDrawable surface(int fill,int edge,int border){
  GradientDrawable panel=shape(fill,9);
  panel.setStroke(dp(border),edge);
  return panel;
 }
}
