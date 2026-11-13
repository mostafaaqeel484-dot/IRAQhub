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
 private static final int BLACK=0xed191020,PURPLE=0xff494057,ORANGE=0xffffa324;
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
  surface.setStroke(dp(2),0xfff3d2ab);pane.setBackground(surface);
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
   ReceiverCatalog.Channel found=ReceiverCatalog.get(this).channel(
       getSharedPreferences("smarthub",0).getLong("own_dvb_channel",-1));
   runOnUiThread(()->{if(!closed){channel=found;render();}});
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
    "🔴 Set Translation [Arabic]       🟢 Set Subtitle Color":
    "🔴 Delay       🟢 Import CFG       🟡 Del Import");
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
  JSONArray tracks=channel.data.optJSONArray("Sub");
  if(tracks==null||tracks.length()==0)note(tx("لا توجد مسارات ترجمة محفوظة في DB","No subtitle entries in DB"));
  if(tracks!=null)for(int i=0;i<tracks.length();i++){
   JSONObject t=tracks.optJSONObject(i);if(t==null)continue;
   final int pid=t.optInt("PID",-1);
   String kind=t.optInt("Type")==1?"ttx":"dvb";
   addRow(t.optString("Lang","und")+"    ["+kind+"]     PID "+pid,()->{
    if(pid>0&&pid<8191)getSharedPreferences("smarthub",0).edit()
       .putInt("subtitle_preferred_pid:"+channel.physical(),pid)
       .putInt("subtitle_preferred_type:"+channel.physical(),t.optInt("Type"))
       .apply();
    new AlertDialog.Builder(this).setTitle(t.optString("Lang","und")+" ["+kind+"]")
      .setMessage(tx("تم حفظ تفضيل المسار. تشغيل ترجمة Firmware بهذا PID غير مؤكد داخل المشغل المستقل؛ افتح أدوات التعريب لقراءة النص أو OCR.",
         "Track preference saved. OEM subtitle decoding by PID is not yet verified in the standalone player; use translation/OCR settings."))
      .setPositiveButton(tx("إعداد الترجمة","Translation settings"),
       (d,w)->startActivity(new Intent(this,LiveTranslationActivity.class)))
      .setNegativeButton(tx("إغلاق","Close"),null).show();
   });
  }
  addRow(tx("تفعيل الترجمة العربية من النص الظاهر (بإذن Android)",
    "Enable Arabic caption OCR with Android consent"),this::enableVisibleCaptions);
  addRow(tx("عرض الترجمة الأصلية من قوائم المصنع",
    "Open receiver's own subtitle controls"),()->ReceiverNavigation.open(this,ReceiverNavigation.menu()));
  addRow(tx("إعداد لون وحجم الترجمة","Caption appearance"),
    ()->startActivity(new Intent(this,LiveTranslationActivity.class)));
  note(tx("هذه PIDs من قاعدة القنوات، وليست مسارات مفكوكة. يجب أن تعرض الترجمة أولاً في مشغل المصنع أو يتوفر مسار نصي من Media3.",
    "These are CHL subtitle PIDs, not decoded text. The native player must render subtitles or Media3 must expose a text cue track."));
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
  getSharedPreferences("smarthub",0).edit()
    .putString("live_target_language","ar").putBoolean("live_translate",true).apply();
  toast(tx("تم اختيار العربية للمسارات النصية المتاحة. DVB المصوّر يحتاج OCR وإذن التقاط.",
    "Arabic selected for readable cues. Bitmap DVB requires OCR and capture consent."));
 }
 private void enableVisibleCaptions(){
  getSharedPreferences("smarthub",0).edit()
    .putString("live_target_language","ar")
    .putBoolean("live_translate",true)
    .putBoolean("native_caption_ocr",true)
    .putBoolean("overlay_caption",false).apply();
  // A real user action launches the Android system MediaProjection permission
  // dialog. No OEM subtitle PID is falsely reported as decoded.
  new AlertDialog.Builder(this)
   .setTitle(tx("تشغيل الترجمة العربية","Enable Arabic subtitles"))
   .setMessage(tx("لترجمة DVB/TXT يجب أن تكون الترجمة الأصلية ظاهرة أولاً على الشاشة. سيتم طلب إذن التقاط شاشة Android وتعالج الجمل محلياً. القنوات المحمية لا يمكن التقاطها.",
     "Original DVB/TXT captions must already be visible. Android will request screen capture consent and text is processed locally. Protected channels cannot be captured."))
   .setPositiveButton(tx("الموافقة وفتح إذن Android","Open Android consent"),(dialog,which)->
     startActivity(new Intent(this,CaptionCaptureActivity.class)))
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
  d.setCornerRadius(dp(12));d.setStroke(dp(1),0xffe8b99a);return d;
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
