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
 private enum Section { VIDEO, CHANNELS, INFOBAR, ANTENNA, ADVANCED }

 private final Button[] sectionButtons=new Button[Section.values().length];
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
  heading.addView(text(tx("الإعدادات","Settings"),28,true));
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
  }
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
   case INFOBAR: showInfoBar(); break;
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
   case VIDEO: return tx("الفيديو و4:2:2","Video & 4:2:2");
   case CHANNELS: return tx("القنوات والبحث","Channels & Search");
   case INFOBAR: return tx("الإنفوبار والصور","InfoBar & Artwork");
   case ANTENNA: return tx("الهوائي والموتور","Antenna & Motor");
   default: return tx("متقدم وتشخيص","Advanced & Diagnostics");
  }
 }

 private String subtitle(Section section){
  switch(section){
   case VIDEO: return tx("محرك العرض والتسجيل والصوت","Playback engine, recording and audio");
   case CHANNELS: return tx("إدارة القنوات، البحث، والنسخ الاحتياطية","Channel management, scans and backups");
   case INFOBAR: return tx("الدليل، البوسترات، الشعارات والمظهر","Guide, posters, logos and appearance");
   case ANTENNA: return tx("الأقمار والترددات وتوصيلات الطبق","Satellites, transponders and dish configuration");
   default: return tx("إعدادات الجهاز وحالة الخدمات والريموت","Device settings, service status and remote");
  }
 }

 private void showVideo(){
  link(tx("محرك IRAQhub 422 الموحد","Unified IRAQhub 422"),
    tx("دقة العرض، GPU FAST، واختيار محرك الفيديو","Output resolution, GPU FAST and decoder selection"),
    Unified422SettingsActivity.class);
  link(tx("تشغيل وفحص 4:2:2","4:2:2 playback and testing"),
    tx("اختبار قناة DVB أو رابط مصرح به","Test a DVB channel or authorised stream"),Software422CenterActivity.class);
  CheckBox safe=check(options,tx("الوضع الآمن · مشغّل DVB العادي","Safe mode · normal DVB player"),
    SafePlaybackMode.enabled(this));
  safe.setPadding(dp(10),dp(9),dp(10),dp(9));
  safe.setButtonTintList(android.content.res.ColorStateList.valueOf(GOLD));
  safe.setBackground(focusBackground());
  LinearLayout.LayoutParams safeSize=new LinearLayout.LayoutParams(-1,-2);
  safeSize.bottomMargin=dp(7);
  safe.setLayoutParams(safeSize);
  connectToRail(safe);
  safe.setOnCheckedChangeListener((button,on)->{
   SafePlaybackMode.set(this,on);
   toast(on?tx("الوضع الآمن مفعل. افتح القناة مجدداً.","Safe mode on. Reopen the channel."):
    tx("المحركات التجريبية متاحة","Experimental players available"));
  });
  link(tx("تسجيل PVR","PVR recording"),tx("تسجيل القنوات داخل IRAQhub","Record channels in IRAQhub"),
    PvrRecordingActivity.class);
  link(tx("صوت الإنترنت للقناة","Channel Internet Audio"),"",IPAudioActivity.class);
 }

 private void showChannels(){
  link(tx("قائمة القنوات","Channel list"),tx("الأقمار والقنوات والمفضلة","Satellites, channels and favourites"),
    ReceiverChannelsActivity.class);
  link(tx("ترتيب وإدارة القنوات","Sort and manage channels"),"",ChannelManageActivity.class);
  link(tx("البحث اليدوي","Manual search"),tx("التردد والاستقطاب والترميز","Frequency, polarity and symbol rate"),
    ManualTransponderActivity.class);
  link(tx("خيارات البحث السريع","Fast search options"),tx("بحث Aurum وفحص ترددات IRAQhub","Aurum scan and IRAQhub TP sweep"),
    ReceiverFastScanMenuActivity.class);
  option(tx("حفظ نسخة قنوات ZIP","Export channel backup ZIP"),
    tx("اختر مكان حفظ نسخة قاعدة القنوات","Choose where to save your channel database backup"),this::backupChannels);
  option(tx("تحقق من نسخة قنوات ZIP","Verify channel backup ZIP"),
    tx("فحص سلامة النسخة الاحتياطية للقراءة فقط","Read-only backup integrity check"),this::verifyChannelsBackup);
 }

 private void showInfoBar(){
  link(tx("مركز الإنفوبار والبوسترات والشعارات","InfoBar, posters and logos"),
    tx("عرض NOW / NEXT ومصادر الصور والدليل","NOW / NEXT display, artwork sources and programme guide"),
    ViewingSettingsActivity.class);
  link(tx("جلب EPG خارجي يدوياً للقناة الحالية","Manual external EPG for current channel"),
    tx("EPG Pro وDVB أولاً. EPGShare أو XMLTV عند اختيارك فقط.",
      "Factory and DVB first. EPGShare/XMLTV only on your request."),
    ManualEpgActivity.class);
  link(tx("الخلفيات والثيمات · RX","RX wallpapers and themes"),"",RxAppearanceActivity.class);
  link(tx("الترجمة ومزامنتها","Subtitles and synchronization"),"",SatelliteActivity.class);
  link(tx("DVB / TXT والترجمة الفورية","DVB / TXT live translation"),"",LiveTranslationActivity.class);
  long lastSync=prefs().getLong("online_epg_sync_at",0);
  String last=lastSync>0?DateFormat.getDateTimeInstance().format(new Date(lastSync)):
    tx("لم يحدث بعد","Not yet");
  option(tx("تحديث DVB والبوسترات وCHMAX فقط","Sync DVB, posters and CHMAX only"),
    tx("آخر تحديث: ","Last sync: ")+last,()->{
     IraqHubSyncScheduler.runNow(this);
     toast(tx("تمت إضافة المزامنة إلى الخلفية","Sync queued in the background"));
    });
  note(options,tx("EPG الخارجي: يدوي فقط. المصنع وDVB هما المصدران التلقائيان.\n",
      "External EPG: manual only. Factory and DVB are the automatic sources.\n")+
    tx("حالة BetterPosters: ","BetterPosters status: ")+
    getSharedPreferences("poster_sources",0).getString("betterposters_manifest_status",
      tx("لم يفحص","Not checked")));
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
  link(tx("إعدادات النظام والتطبيق","App and system settings"),
    tx("اللغة وحجم الخط وHOME والشبكة وخدمة القنوات","Language, text size, HOME, network and channel bridge"),
    AdvancedSettingsActivity.class);
  link(tx("تقرير التوافق ومصادر البيانات","Compatibility and data sources"),"",CompatibilityActivity.class);
  link(tx("تشخيص DVB ورصد البحث الأصلي","DVB diagnostics and OEM scan observation"),"",
    ReceiverDeveloperMenuActivity.class);
  link(tx("ربط جهاز X5M","X5M hardware connection"),
    tx("تصحيح USB ولوحة الرسيفر","USB ADB and receiver front panel"),ReceiverHardwareBridgeActivity.class);
  link(tx("اختصارات الريموت","Remote control shortcuts"),"",RemoteHelpActivity.class);
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
