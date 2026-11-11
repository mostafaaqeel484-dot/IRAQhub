package com.smarthub.plus;
import android.content.*;import android.os.*;import android.widget.*;import java.util.concurrent.*;
import java.util.*;import java.io.*;
/** Real per-channel Internet-audio binding. No factory tokens or catalogue credentials are copied. */
public final class IPAudioActivity extends HubActivity {
 private static final int IMPORT_CFG=9041;
 private final ExecutorService io=Executors.newSingleThreadExecutor();private ReceiverCatalog.Channel channel;private EditText url,delay,volume;private CheckBox enabled;private TextView status;private final Handler main=new Handler(Looper.getMainLooper());private boolean closed;
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;String s=prefs().getString("ip_audio_status","");status.setText(s.equals("IP_AUDIO_PLAYING")?tx("الصوت الشبكي يعمل","Internet audio playing"):s.equals("IP_AUDIO_FAILED")?tx("تعذر المصدر؛ عاد صوت القناة الأصلي","Source failed; original channel audio restored"):s.isEmpty()?tx("صوت القناة الأصلي","Original channel audio"):tx("جارٍ الاتصال أو تجهيز الصوت…","Connecting or buffering audio…"));main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle s){super.onCreate(s);LinearLayout body=page(tx("صوت عبر الإنترنت للقناة","Channel Internet Audio"));TextView name=text("",22,true);body.addView(name);url=field(body,tx("رابط مصدر صوت HTTP / HTTPS","HTTP / HTTPS audio source URL"),"");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);delay=field(body,tx("تأخير إضافي للصوت بالميلي ثانية · 0–30000","Additional audio delay in ms · 0–30000"),"0");delay.setInputType(2);volume=field(body,tx("مستوى الصوت · 0–100","Audio volume · 0–100"),"80");volume.setInputType(2);enabled=check(body,tx("تشغيل المصدر تلقائياً لهذه القناة","Automatically use this source for this channel"),false);note(body,tx("يبدأ مصدر الصوت من الإنترنت داخل المشغل، ويُكتم صوت DVB عند جاهزيته. التوقيت يدوي لأن مصدر الإنترنت والبث قد يملكان تأخيراً مختلفاً.","The player loads Internet audio and mutes DVB when it is ready. Timing is manual because Internet and broadcast sources can have different latency."));action(body,tx("🟢 استيراد صوتيات CFG من USB","🟢 Import CFG audio from USB"),()->{
  Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
  pick.setType("*/*");startActivityForResult(pick,IMPORT_CFG);
 });
 action(body,tx("المسارات المستوردة · اختيار مسار","Imported audio tracks · choose"),this::chooseImported);
 action(body,tx("حذف قائمة الصوتيات المستوردة","Delete imported CFG tracks"),()->{
  new android.app.AlertDialog.Builder(this)
   .setTitle(tx("حذف قائمة CFG؟","Delete imported CFG catalog?"))
   .setMessage(tx("هذا يحذف القائمة المستوردة فقط، ولا يحذف ارتباطات القنوات التي حفظتها.","Deletes only the imported catalog; saved channel bindings remain."))
   .setPositiveButton(tx("حذف","Delete"),(d,w)->io.execute(()->{
    ImportedAudioCatalog.delete(this);
    runOnUiThread(()->toast(tx("حُذفت القائمة المستوردة","Imported list deleted")));
   }))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 });
 note(body,tx("تُخزن روابط CFG بما فيها رموز الوصول مشفرة بمفتاح Android Keystore على هذا الرسيفر؛ لا تُرفع إلى GitHub.","CFG stream URLs and access tokens are encrypted using this receiver's Android Keystore and never uploaded to GitHub."));
 action(body,tx("حفظ وتطبيق","Save and apply"),()->{if(channel==null)return;try{IPAudioBinding binding=new IPAudioBinding(url.getText().toString().trim(),Integer.parseInt(delay.getText().toString()),Integer.parseInt(volume.getText().toString()),enabled.isChecked());ReceiverCatalog.Channel target=channel;io.execute(()->{try{binding.save(this,target.physical());runOnUiThread(()->{if(!closed){startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",target.id));finish();}});}catch(Exception error){runOnUiThread(()->toast(tx("تعذر الحفظ الآمن","Secure save failed")));}});}catch(Exception bad){toast(tx("تحقق من الرابط والتأخير ومستوى الصوت","Check the URL, delay and volume"));}});action(body,tx("حذف الارتباط والعودة للصوت الأصلي","Remove binding and restore channel audio"),()->{if(channel==null)return;ReceiverCatalog.Channel target=channel;io.execute(()->{try{IPAudioBinding.remove(this,target.physical());runOnUiThread(()->{if(!closed){startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",target.id));finish();}});}catch(Exception e){runOnUiThread(()->toast(tx("تعذر حذف الارتباط","Could not remove binding")));}});});status=text("",15,true);body.addView(status);io.execute(()->{ReceiverCatalog.Channel c=ReceiverCatalog.get(this).channel(prefs().getLong("own_dvb_channel",-1));IPAudioBinding b=c==null?null:IPAudioBinding.read(this,c.physical());runOnUiThread(()->{if(closed)return;channel=c;name.setText(c==null?tx("شغّل قناة في IRAQhub أولاً","Play a channel in IRAQhub first"):c.name);if(b!=null){url.setText(b.url);delay.setText(Integer.toString(b.delay));volume.setText(Integer.toString(b.volume));enabled.setChecked(b.enabled);}});});main.post(tick);}
 private void chooseImported(){
  io.execute(()->{
   List<ImportedAudioCatalog.Track> tracks=ImportedAudioCatalog.load(this);
   runOnUiThread(()->{
    if(closed)return;
    if(tracks.isEmpty()){toast(tx("استورد ملف CFG أولاً","Import a CFG file first"));return;}
    String[] names=new String[tracks.size()];
    for(int n=0;n<names.length;n++)names[n]=(n+1)+" · "+tracks.get(n).name;
    ReceiverPanelStyle.choose(this,tx("صوتيات CFG","Imported audio tracks"),names,0,index->{
     ImportedAudioCatalog.Track chosen=tracks.get(index);
     url.setText(chosen.url);enabled.setChecked(true);
     toast(tx("اختير المصدر. اضغط حفظ وتطبيق لربطه بالقناة.","Track selected. Save and apply to bind it to the channel."));
    });
   });
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=IMPORT_CFG||result!=RESULT_OK||data==null||data.getData()==null)return;
  android.net.Uri selected=data.getData();
  io.execute(()->{
   try(InputStream stream=getContentResolver().openInputStream(selected)){
    List<ImportedAudioCatalog.Track> tracks=ImportedAudioCatalog.parse(stream);
    ImportedAudioCatalog.save(this,tracks);
    runOnUiThread(()->{if(!closed)toast(tx("تم استيراد ","Imported ")+tracks.size()+tx(" مسار صوتي مشفّر"," encrypted audio tracks"));});
   }catch(Exception invalid){
    runOnUiThread(()->{if(!closed)toast(tx("ملف CFG غير صالح أو تجاوز الحدود","Invalid or oversized CFG file"));});
   }
  });
 }
 @Override protected void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
