package com.smarthub.plus;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Tiger-inspired two-column DVB audio/subtitle control. Never claims an unreadable OEM track is active. */
public final class ReceiverAvPanelActivity extends HubActivity {
 private static final int BLACK=0xf2080b11,PURPLE=0xff18212e,ORANGE=0xffffc24c;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final String[] sections={"Audio","Audio Track","Screen Mode","HDMI Mode","Subtitle","Subtitle Switch"};
 private LinearLayout left,right;private TextView heading,foot;
 private ReceiverCatalog.Channel channel;private int section;
 private boolean closed;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
  FrameLayout shade=new FrameLayout(this);shade.setBackgroundColor(0x90000000);
  LinearLayout pane=new LinearLayout(this);pane.setOrientation(LinearLayout.VERTICAL);
  pane.setPadding(dp(14),dp(14),dp(14),dp(12));
  GradientDrawable surface=new GradientDrawable();
  surface.setColor(BLACK);surface.setCornerRadius(dp(18));
  surface.setStroke(dp(2),0xffffc24c);pane.setBackground(surface);
  FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(
   Math.min(dp(1040),(int)(getResources().getDisplayMetrics().widthPixels*.91f)),
   Math.min(dp(580),(int)(getResources().getDisplayMetrics().heightPixels*.84f)),Gravity.CENTER);
  shade.addView(pane,placement);
  LinearLayout columns=new LinearLayout(this);columns.setOrientation(LinearLayout.HORIZONTAL);
  pane.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
  left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
  left.setPadding(dp(10),dp(10),dp(10),dp(10));left.setBackground(tile(false));
  columns.addView(left,new LinearLayout.LayoutParams(0,-1,.42f));
  LinearLayout rightPane=new LinearLayout(this);rightPane.setOrientation(LinearLayout.VERTICAL);
  rightPane.setPadding(dp(10),dp(10),dp(10),dp(10));rightPane.setBackground(tile(false));
  LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,.58f);rp.leftMargin=dp(10);
  columns.addView(rightPane,rp);
  heading=label("Audio",18,true);rightPane.addView(heading);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);
  right.setPadding(0,dp(5),0,dp(5));scroll.addView(right);
  rightPane.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  foot=label("",13,true);foot.setPadding(dp(12),dp(10),dp(12),0);
  pane.addView(foot,new LinearLayout.LayoutParams(-1,dp(55)));
  setContentView(shade);
  section=getIntent().getBooleanExtra("show_subtitle",false)?4:0;
  render();
  worker.execute(()->{
   String expected=getIntent().getStringExtra("channel_physical");
   ReceiverCatalog.Channel found=ReceiverCatalog.get(this).channel(
       getIntent().getLongExtra("channel_id",prefs().getLong("own_dvb_channel",-1)));
   ReceiverCatalog.Channel verified=found!=null&&(expected==null||expected.equals(found.physical()))?
     found:null;
   runOnUiThread(()->{if(!closed){channel=verified;render();}});
  });
 }
 private void render(){
  left.removeAllViews();
  for(int i=0;i<sections.length;i++){
   final int index=i;
   Button choice=button(sections[i],i==section);
   choice.setTextSize(19);choice.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
   left.addView(choice,new LinearLayout.LayoutParams(-1,dp(55)));
   choice.setOnClickListener(v->{section=index;render();});
  }
  right.removeAllViews();heading.setText(sections[section]+
   (channel==null?"":" · "+channel.name));
  foot.setText(section==4||section==5?
    tx("الأحمر · ضبط التعريب [العربية]     الأخضر · الإعدادات",
       "Red · Set Translation [Arabic]     Green · Settings"):
    tx("AUDIO · اختر المسار وانتظر تأكيد الجهاز",
       "AUDIO · Choose a track; receiver confirmation required"));
  if(section==0)audioTracks();
  else if(section==1)imported();
  else if(section==4)subtitleTracks();
  else if(section==5)subtitleActions();
  else if(section==2||section==3){
   addRow("Android TV · "+sections[section],()->{
    try{startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));}
    catch(Exception unavailable){toast("Display settings unavailable");}
   });
   note(tx("هذه الإعدادات تفتح شاشة Android ولا تغير نمط HDMI في Firmware مباشرة.",
       "Opens Android display settings; does not modify OEM HDMI firmware."));
  }
 }
 private void audioTracks(){
  if(channel==null){note(tx("افتح قناة DVB أولاً","Play a DVB channel first"));return;}
  JSONArray raw=channel.audio();int selected=getSharedPreferences("smarthub",0)
    .getInt("dvb_audio_index:"+channel.physical(),0),valid=0;
  for(int i=0;i<Math.min(16,raw.length());i++){
   JSONObject t=raw.optJSONObject(i);
   if(t==null||DvbPlayRequest.audio(t.optString("Type"))<0||
      t.optInt("PID",8191)<=0||t.optInt("PID",8191)>=8191)continue;
   final int index=valid++;
   String name=(index==selected?"✓  ":"  ")+t.optString("Lang","und")+
      "   ["+t.optString("Type","DVB")+"]   PID "+t.optInt("PID");
   addRow(name,()->{
    Intent result=new Intent().putExtra("audio_index",index)
      .putExtra("channel_physical",channel.physical());
    setResult(RESULT_OK,result);finish();
   });
  }
  if(valid==0)note(tx("لا توجد مسارات صوت DVB محفوظة","No valid DVB audio tracks in the database"));
  addRow(tx("IP Audio · استيراد CFG أو ربط مصدر","IP Audio · CFG import or per-channel binding"),
   ()->startActivity(new Intent(this,IPAudioActivity.class)));
  note(tx("اختيار صوت DVB يُطبق بواسطة المشغّل الأصلي في IRAQhub.",
    "DVB audio selection is passed back to IRAQhub's live decoder."));
 }
 private void imported(){
  addRow("🟢 Import / Delay / Del Import · CFG",
    ()->startActivity(new Intent(this,IPAudioActivity.class)));
  worker.execute(()->{
   List<ImportedAudioCatalog.Track> list=ImportedAudioCatalog.load(this);
   runOnUiThread(()->{if(closed||section!=1)return;
    if(list.isEmpty()){note(tx("لا توجد قائمة CFG مستوردة","No imported CFG catalog"));return;}
    for(ImportedAudioCatalog.Track t:list)
     addRow(t.name,()->{
      startActivity(new Intent(this,IPAudioActivity.class));
      toast(tx("اختَر المسار من قائمة CFG ثم احفظ ربط القناة",
          "Choose this source in CFG catalog and save its channel binding"));
     });
   });
  });
 }
 private void subtitleTracks(){
  if(channel==null){note(tx("افتح قناة DVB أولاً","Play a DVB channel first"));return;}
  addRow(tx("مسارات البث الفعلية · فتح مشغّل الترجمة",
    "Live broadcast tracks · open subtitle player"),this::openSubtitlePlayer);
  List<SavedSubtitleTracks.Track> saved=SavedSubtitleTracks.from(channel.data.optJSONArray("Sub"));
  for(SavedSubtitleTracks.Track track:saved){
   String kind=track.type==1?"TTX":"DVB";
   String page=track.page>=0?" · "+tx("صفحة","Page")+" "+track.page:"";
   addRow(track.language+" ["+kind+"] · PID "+track.pid+page+" · "+tx("محفوظ","Saved"),()->{
    SharedPreferences.Editor edit=prefs().edit();
    if(SavedSubtitleTracks.uniquePid(saved,track.pid)){
     edit.putInt("subtitle_preferred_pid:"+channel.physical(),track.pid)
       .putInt("subtitle_preferred_type:"+channel.physical(),track.type);
    }else{
     edit.remove("subtitle_preferred_pid:"+channel.physical())
       .remove("subtitle_preferred_type:"+channel.physical());
     toast(tx("هذا الرقم مشترك بين مسارات. اختر الصفحة من قائمة البث.",
       "This PID is shared by several tracks. Select the page from the live list."));
    }
    edit.apply();
    openSubtitlePlayer();
   });
  }
  if(saved.isEmpty())note(tx("لا توجد أوصاف ترجمة محفوظة لهذه القناة. افتح مسارات البث لفحص الترجمة المتاحة الآن.",
    "This channel has no saved subtitle descriptors. Open live broadcast tracks to check current availability."));
  addRow(tx("تفعيل الترجمة العربية من النص الظاهر (بإذن Android)",
    "Enable Arabic caption OCR with Android consent"),this::enableVisibleCaptions);
  addRow(tx("عرض الترجمة الأصلية من قوائم المصنع",
    "Open receiver's own subtitle controls"),()->ReceiverNavigation.open(this,ReceiverNavigation.menu()));
  addRow(tx("إعداد لون وحجم الترجمة","Caption appearance"),
    ()->startActivity(new Intent(this,LiveTranslationActivity.class)));
  if(!saved.isEmpty())note(tx("يتحقق المشغّل من المسارات المحفوظة عند قراءة البث الحي.",
    "The player verifies saved subtitle descriptors against the live stream."));
 }
 private void openSubtitlePlayer(){
  if(channel==null)return;
  if(channel.tv!=0||channel.ca<0){
   toast(tx("مسارات البث متاحة لقنوات التلفزيون ذات بيانات تشغيل صالحة.",
     "Live tracks require a TV service with valid playback metadata."));
   return;
  }
  setResult(RESULT_OK,new Intent().putExtra("software_subtitles",true)
    .putExtra("channel_physical",channel.physical()));
  finish();
 }
 private void subtitleActions(){
  addRow(tx("تفعيل ترجمة عربية من DVB/TXT الظاهرة",
    "Enable Arabic OCR from visible DVB/TXT captions"),this::enableVisibleCaptions);
  addRow(tx("إيقاف التقاط وترجمة الشاشة",
    "Disable screen caption capture"),this::disableVisibleCaptions);
  addRow(tx("لون وحجم وموضع الترجمة","Caption style, size and placement"),
    ()->startActivity(new Intent(this,LiveTranslationActivity.class)));
  boolean enabled=getSharedPreferences("smarthub",0).getBoolean("live_translate",false);
  addRow(enabled?tx("إيقاف الترجمة التلقائية","Disable automatic translation"):
       tx("تفعيل ترجمة المسارات النصية","Enable text-cue translation"),()->{
   getSharedPreferences("smarthub",0).edit().putBoolean("live_translate",!enabled).apply();render();
  });
  addRow(tx("OCR لترجمة DVB / TXT الظاهرة","OCR for visible DVB / TXT"),
    ()->startActivity(new Intent(this,LiveTranslationActivity.class)));
 }
 private void arabic(){
  new AlertDialog.Builder(this).setTitle(tx("تعريب الترجمة [العربية]","Subtitle Translation [Arabic]"))
   .setItems(new String[]{tx("العربية · ترجمة النص الظاهر","Arabic · Translate visible captions"),
     tx("العربية · المسارات النصية داخل Media3","Arabic · Media3 text tracks"),
     tx("إيقاف التعريب","Turn off translation")},(dialog,which)->{
    if(which==0)enableVisibleCaptions();
    else if(which==1){
     prefs().edit().putString("live_target_language","ar").putBoolean("live_translate",true).apply();
     toast(tx("تم تفعيل التعريب للمسارات النصية المدعومة","Arabic enabled for supported text tracks"));
    }else disableVisibleCaptions();
   }).show();
 }
 private void enableVisibleCaptions(){
  // A real user action launches the Android system MediaProjection permission
  // dialog. No OEM subtitle PID is falsely reported as decoded.
  new AlertDialog.Builder(this)
   .setTitle(tx("تشغيل الترجمة العربية","Enable Arabic subtitles"))
   .setMessage(tx("لترجمة DVB/TXT يجب أن تكون الترجمة الأصلية ظاهرة أولاً على الشاشة. سيتم طلب إذن التقاط شاشة Android وتعالج الجمل محلياً. القنوات المحمية لا يمكن التقاطها.",
     "Original DVB/TXT captions must already be visible. Android will request screen capture consent and text is processed locally. Protected channels cannot be captured."))
   .setPositiveButton(tx("متابعة","Continue"),(dialog,which)->{
     prefs().edit().putString("live_target_language","ar")
       .putBoolean("live_translate",true).apply();
     if(!CaptionCaptureService.running)startActivity(new Intent(this,CaptionCaptureActivity.class));
     finish();
   })
   .setNeutralButton(tx("إعدادات الترجمة","Caption settings"),(dialog,which)->
     startActivity(new Intent(this,LiveTranslationActivity.class)))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void disableVisibleCaptions(){
  getSharedPreferences("smarthub",0).edit()
    .putBoolean("native_caption_ocr",false)
    .putBoolean("live_translate",false).apply();
  stopService(new Intent(this,CaptionCaptureService.class));
  toast(tx("تم إيقاف OCR والترجمة العربية","OCR and Arabic caption translation stopped"));
 }
 private void note(String value){
  TextView v=label(value,13,false);v.setTextColor(0xffc8c4d5);
  v.setPadding(dp(5),dp(14),dp(5),dp(14));right.addView(v);
 }
 private void addRow(String name,Runnable callback){
  Button row=button(name,false);row.setTextSize(16);row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  right.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));
  row.setOnClickListener(v->callback.run());
 }
 private Button button(String title,boolean active){
  Button v=new Button(this);v.setAllCaps(false);v.setText(title);
  v.setTextColor(active?0xff19101f:Color.WHITE);
  v.setBackground(tile(active));v.setPadding(dp(12),0,dp(10),0);
  v.setOnFocusChangeListener((view,focused)->{
   v.setBackground(tile(focused||active));
   v.setTextColor(focused||active?0xff181018:Color.WHITE);
  });
  return v;
 }
 private GradientDrawable tile(boolean active){
  GradientDrawable d=new GradientDrawable();d.setColor(active?ORANGE:PURPLE);
  d.setCornerRadius(dp(12));d.setStroke(dp(1),0xffffb93d);return d;
 }
 private TextView label(String value,int size,boolean bold){
  TextView v=new TextView(this);v.setText(value);v.setTextSize(size);
  v.setTextColor(Color.WHITE);v.setSingleLine(false);v.setGravity(Gravity.CENTER_VERTICAL);
  if(bold)v.setTypeface(null,Typeface.BOLD);
  return v;
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
   int k=event.getKeyCode();
   if(k==KeyEvent.KEYCODE_PROG_RED){
    if(section>=4)arabic();else startActivity(new Intent(this,IPAudioActivity.class));
    return true;
   }
   if(k==KeyEvent.KEYCODE_PROG_GREEN){
    startActivity(new Intent(this,section<4?IPAudioActivity.class:LiveTranslationActivity.class));
    return true;
   }
   if(k==KeyEvent.KEYCODE_PROG_YELLOW&&section<4){
    new AlertDialog.Builder(this).setTitle(tx("حذف استيراد CFG؟","Delete imported CFG list?"))
     .setPositiveButton(tx("حذف","Delete"),(d,w)->worker.execute(()->{
      ImportedAudioCatalog.delete(this);
      runOnUiThread(()->{if(!closed){toast(tx("حُذفت القائمة","Imported catalog removed"));render();}});
     }))
     .setNegativeButton(tx("إلغاء","Cancel"),null).show();
    return true;
   }
  }
  return super.dispatchKeyEvent(event);
 }
 @Override protected void onDestroy(){closed=true;worker.shutdownNow();super.onDestroy();}
}
