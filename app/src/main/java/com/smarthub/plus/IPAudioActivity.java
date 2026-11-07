package com.smarthub.plus;
import android.content.*;import android.os.*;import android.widget.*;import java.util.concurrent.*;
/** Real per-channel Internet-audio binding. No factory tokens or catalogue credentials are copied. */
public final class IPAudioActivity extends HubActivity {
 private static final int CHOOSE_CFG=3891;private final ExecutorService io=Executors.newSingleThreadExecutor();private ReceiverCatalog.Channel channel;private EditText url,delay,volume;private CheckBox enabled;private TextView status;private final Handler main=new Handler(Looper.getMainLooper());private boolean closed;
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;String s=prefs().getString("ip_audio_status","");status.setText(s.equals("IP_AUDIO_PLAYING")?tx("الصوت الشبكي يعمل","Internet audio playing"):s.equals("IP_AUDIO_FAILED")?tx("تعذر المصدر؛ عاد صوت القناة الأصلي","Source failed; original channel audio restored"):s.isEmpty()?tx("صوت القناة الأصلي","Original channel audio"):tx("جارٍ الاتصال أو تجهيز الصوت…","Connecting or buffering audio…"));main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle s){super.onCreate(s);LinearLayout body=page(tx("صوت عبر الإنترنت للقناة","Channel Internet Audio"));TextView name=text("",22,true);body.addView(name);url=field(body,tx("رابط مصدر صوت HTTP / HTTPS","HTTP / HTTPS audio source URL"),"");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);delay=field(body,tx("تأخير إضافي للصوت بالميلي ثانية · 0–30000","Additional audio delay in ms · 0–30000"),"0");delay.setInputType(2);volume=field(body,tx("مستوى الصوت · 0–100","Audio volume · 0–100"),"80");volume.setInputType(2);enabled=check(body,tx("تشغيل المصدر تلقائياً لهذه القناة","Automatically use this source for this channel"),false);
 action(body,tx("الأخضر · استيراد صوتيات CFG من USB","GREEN · Import CFG audio from USB"),this::chooseCfg);
 action(body,tx("اختيار صوت من قائمة CFG المستوردة","Select imported CFG audio"),this::selectImported);
 action(body,tx("الأصفر · حذف قائمة CFG المستوردة","YELLOW · Delete imported CFG list"),this::deleteImported);
 note(body,tx("يمكن استيراد ipaudio.cfg بصيغة 0|الاسم|الرابط. تحفظ روابط الاشتراك مشفرة محلياً ولا ترفع إلى GitHub.","Import 0|Name|URL ipaudio.cfg files. Private stream URLs are encrypted on the receiver and never uploaded to GitHub."));note(body,tx("يبدأ مصدر الصوت من الإنترنت داخل المشغل، ويُكتم صوت DVB عند جاهزيته. التوقيت يدوي لأن مصدر الإنترنت والبث قد يملكان تأخيراً مختلفاً.","The player loads Internet audio and mutes DVB when it is ready. Timing is manual because Internet and broadcast sources can have different latency."));action(body,tx("حفظ وتطبيق","Save and apply"),()->{if(channel==null)return;try{IPAudioBinding binding=new IPAudioBinding(url.getText().toString().trim(),Integer.parseInt(delay.getText().toString()),Integer.parseInt(volume.getText().toString()),enabled.isChecked());ReceiverCatalog.Channel target=channel;io.execute(()->{try{binding.save(this,target.physical());runOnUiThread(()->{if(!closed){startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",target.id));finish();}});}catch(Exception error){runOnUiThread(()->toast(tx("تعذر الحفظ الآمن","Secure save failed")));}});}catch(Exception bad){toast(tx("تحقق من الرابط والتأخير ومستوى الصوت","Check the URL, delay and volume"));}});action(body,tx("حذف الارتباط والعودة للصوت الأصلي","Remove binding and restore channel audio"),()->{if(channel==null)return;ReceiverCatalog.Channel target=channel;io.execute(()->{try{IPAudioBinding.remove(this,target.physical());runOnUiThread(()->{if(!closed){startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",target.id));finish();}});}catch(Exception e){runOnUiThread(()->toast(tx("تعذر حذف الارتباط","Could not remove binding")));}});});status=text("",15,true);body.addView(status);io.execute(()->{ReceiverCatalog.Channel c=ReceiverCatalog.get(this).channel(prefs().getLong("own_dvb_channel",-1));IPAudioBinding b=c==null?null:IPAudioBinding.read(this,c.physical());runOnUiThread(()->{if(closed)return;channel=c;name.setText(c==null?tx("شغّل قناة في IRAQhub أولاً","Play a channel in IRAQhub first"):c.name);if(b!=null){url.setText(b.url);delay.setText(Integer.toString(b.delay));volume.setText(Integer.toString(b.volume));enabled.setChecked(b.enabled);}});});main.post(tick);}

 private void chooseCfg(){
  Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
  try{startActivityForResult(pick,CHOOSE_CFG);}
  catch(Exception unavailable){toast(tx("منتقي USB غير متاح","USB document picker unavailable"));}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=CHOOSE_CFG||result!=RESULT_OK||data==null||data.getData()==null)return;
  android.net.Uri selected=data.getData();
  io.execute(()->{
   try{
    int total=ImportedAudioCatalog.importFrom(this,selected);
    runOnUiThread(()->{if(!closed)toast(tx("تم استيراد "+total+" مصدر صوت","Imported "+total+" audio sources"));});
   }catch(Exception failed){runOnUiThread(()->{if(!closed)toast(tx("ملف CFG غير صالح أو تعذر قراءته","CFG could not be read or contains no valid sources"));});}
  });
 }
 private void selectImported(){
  io.execute(()->{
   java.util.List<ImportedAudioCatalog.Entry> entries=ImportedAudioCatalog.read(this);
   runOnUiThread(()->{
    if(closed)return;
    if(entries.isEmpty()){toast(tx("استورد ملف CFG أولاً","Import a CFG file first"));return;}
    String[] labels=new String[entries.size()];
    for(int i=0;i<entries.size();i++)labels[i]=entries.get(i).label;
    new android.app.AlertDialog.Builder(this)
     .setTitle(tx("صوتيات CFG","Imported audio tracks"))
     .setItems(labels,(d,index)->{
      url.setText(entries.get(index).url);
      enabled.setChecked(true);
      toast(tx("اخترت المصدر. احفظ حتى يرتبط بالقناة","Source selected. Save to bind to the channel"));
     })
     .setNegativeButton(tx("إلغاء","Cancel"),null).show();
   });
  });
 }
 private void deleteImported(){
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("حذف قائمة الصوتيات المستوردة؟","Delete imported audio catalog?"))
   .setMessage(tx("ستحذف القائمة المحلية فقط، ولن تُحذف روابط القنوات التي حفظتها سابقاً.","Only the imported catalog is removed; previously saved channel bindings are preserved."))
   .setNegativeButton(tx("إلغاء","Cancel"),null)
   .setPositiveButton(tx("حذف","Delete"),(d,w)->io.execute(()->{
    ImportedAudioCatalog.clear(this);
    runOnUiThread(()->{if(!closed)toast(tx("حُذفت القائمة","Catalog deleted"));});
   })).show();
 }
 @Override protected void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
