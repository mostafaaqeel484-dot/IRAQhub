package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Own-screen, explicitly started FTA DVB recording through observed Pvr 0002/0003.
 * No OEM activity, no CA override, no direct USB filesystem access.
 */
public final class PvrRecordingActivity extends HubActivity {
 private static final int EXPORT=4112;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView channelText,state,files;
 private volatile boolean destroyed,busy,recording;
 private int captureId=-1;
 private File output;
 private long startedAt;
 private String session="";
 private final Runnable monitor=new Runnable(){
  @Override public void run(){
   if(destroyed)return;
   if(recording&&output!=null){
    long bytes=output.length();
    long elapsed=SystemClock.elapsedRealtime()-startedAt;
    state.setText(tx("تسجيل DVB جارٍ: ","DVB recording active: ")+format(bytes)+
        " · "+(elapsed/1000)+"s");
    if(elapsed>2*60*60*1000L||bytes>4L*1024*1024*1024||output.getUsableSpace()<128L*1024*1024)
      stop(true);
   }
   main.postDelayed(this,1500);
  }
 };
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("تسجيل قنوات DVB · PVR","DVB recording centre"),
      tx("تسجيل قناة تلفزيونية مفتوحة عبر خدمة Pvr المحلية داخل IRAQhub",
         "Record an FTA DVB service using the receiver Pvr protocol, within IRAQhub."));
  LinearLayout active=card(body);
  channelText=text("",17,true);state=text("",15,false);
  active.addView(channelText);active.addView(state);
  note(active,tx("يتم التسجيل في مساحة التطبيق ثم يمكن تصديره إلى USB عبر اختيار ملف Android. لا يمكن ضمان دعم خدمة Pvr قبل التجربة على الجهاز.",
    "Recordings use app-owned storage; use Android's file picker to export to USB. Real Pvr availability requires on-device validation."));
  action(active,tx("بدء تسجيل القناة الحالية","Record selected DVB channel"),this::confirmStart);
  action(active,tx("إيقاف التسجيل","Stop recording"),()->stop(false));
  action(active,tx("حفظ ملف التسجيل في USB / مستند","Export last recording via file picker"),this::export);
  action(active,tx("قائمة القنوات","Open channel list"),()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));

  LinearLayout stored=card(body);
  stored.addView(text(tx("تسجيلاتي","Saved recordings"),20,true));
  files=text("",14,false);
  stored.addView(files);
  action(stored,tx("تحديث قائمة الملفات","Refresh saved files"),this::showFiles);
  note(stored,tx("تسجيل القنوات المشفرة والتسجيل المؤقت وTimeShift يحتاجان تكاملًا إضافياً مع الروم. لا توجد أزرار شكلية لهذه الوظائف.",
     "Encrypted recording, scheduled recording and TimeShift need further native integration; no fake controls are shown."));
  showFiles();
  long current=prefs().getLong("own_dvb_channel",-1);
  worker.execute(()->{
   ReceiverCatalog.Channel ch=ReceiverCatalog.get(this).channel(current);
   main.post(()->{if(!destroyed)
     channelText.setText(ch==null?tx("ما محدد قناة؛ افتح القائمة واختر قناة DVB أولاً","No channel selected; tune a DVB channel first"):
       tx("القناة المختارة: ","Selected channel: ")+ch.name+" · "+ch.rf());});
  });
  state.setText(tx("غير مسجل","Not recording"));
  main.post(monitor);
 }
 private File directory(){
  File parent=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
  if(parent==null)return null;
  File dir=new File(parent,"IRAQhub-Recordings");
  return dir.isDirectory()||dir.mkdirs()?dir:null;
 }
 private void confirmStart(){
  if(busy||recording){toast(tx("التسجيل مشغول","Recording already active"));return;}
  new AlertDialog.Builder(this).setTitle(tx("تسجيل مباشر","Record now"))
   .setMessage(tx("ستُضبط القناة الحالية ويسجَّل بثها المفتوح إلى ملف في مساحة IRAQhub. يمكن أن تتوقف المشاهدة الأخرى أثناء ضبط التونر.",
      "The selected free-to-air service will be tuned and captured into IRAQhub storage. Tuning may interrupt other playback."))
   .setPositiveButton(tx("ابدأ التسجيل","Start recording"),(d,w)->start())
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void start(){
  if(busy||recording)return;
  busy=true;
  state.setText(tx("تهيئة التونر ومسار التسجيل…","Preparing tuner and recording…"));
  worker.execute(()->{
   File recorded=null;
   int id=-1;
   try{
    if(prefs().getBoolean("software_dvb_422_active",false)||
       prefs().getBoolean("dvb_player_visible",false)||
       !prefs().getString("capture_session","").isEmpty())
      throw new IOException("DVB_PLAYER_ALREADY_OWNS_TUNER");
    long number=prefs().getLong("own_dvb_channel",-1);
    ReceiverCatalog db=ReceiverCatalog.get(this);
    ReceiverCatalog.Channel ch=db.channel(number);
    if(ch==null||ch.ca!=0||ch.tv!=0)throw new IOException("FTA_TV_CHANNEL_REQUIRED");
    File folder=directory();
    if(folder==null||folder.getUsableSpace()<256L*1024*1024)
      throw new IOException("RECORDING_STORAGE_UNAVAILABLE");
    String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());
    recorded=new File(folder,"DVB-"+stamp+"-SID"+ch.sid+".ts");
    if(recorded.exists())throw new IOException("RECORD_FILE_EXISTS");
    ReceiverSignalReading tuned=DvbTuner.ensure(this,ch);
    JSONObject packet=DvbPlayRequest.create(ch.data,(int)ch.id,ch.sid,ch.angle,ch.frequency,
      ch.pol,ch.symbolRate,tuned.tp,0,false);
    id=ch.programToken();
    packet.put("iProgId",id).put("path_dir",folder.getAbsolutePath())
      .put("path",recorded.getAbsolutePath()).put("bTimeShift",false)
      .put("FsType",0).put("tp_record",0);
    if(!NativeCapture.record(id,packet))throw new IOException("PVR_RECORD_COMMAND_REJECTED");
    final int accepted=id;
    final File file=recorded;
    main.post(()->{
     if(destroyed){NativeCapture.stop(accepted);return;}
     captureId=accepted;output=file;recording=true;startedAt=SystemClock.elapsedRealtime();
     session=UUID.randomUUID().toString();
     prefs().edit().putString("iraqhub_pvr_session",session)
       .putInt("iraqhub_pvr_id",accepted).putString("iraqhub_pvr_path",file.getAbsolutePath()).apply();
     state.setText(tx("تم قبول التسجيل من خدمة Pvr؛ راقب نمو ملف TS","Pvr accepted the record request; check TS file growth"));
    });
   }catch(Exception failure){
    String message=failure.getMessage()==null?"PVR_UNAVAILABLE":failure.getMessage();
    final File created=recorded;
    main.post(()->{if(!destroyed)state.setText(tx("فشل التسجيل: ","Record failed: ")+message);});
    if(created!=null&&created.isFile()&&created.length()==0)created.delete();
   }finally{main.post(()->busy=false);}
  });
 }
 private void stop(boolean automatic){
  if(!recording||busy)return;
  busy=true;
  final int id=captureId;final String owner=session;
  state.setText(tx("إيقاف التسجيل…","Stopping recording…"));
  worker.execute(()->{
   boolean stopped=NativeCapture.stop(id);
   main.post(()->{
    if(destroyed)return;
    busy=false;
    if(stopped){
     recording=false;captureId=-1;
     if(owner.equals(prefs().getString("iraqhub_pvr_session","")))
      prefs().edit().remove("iraqhub_pvr_session").remove("iraqhub_pvr_id").apply();
     state.setText(automatic?tx("انتهى التسجيل بسبب حد الحجم/المدة/التخزين","Recording stopped at size/time/storage limit"):
       tx("تم إيقاف التسجيل","Recording stopped"));
     showFiles();
    }else state.setText(tx("تعذر تأكيد إيقاف Pvr. لا تبدأ تسجيلاً آخر حتى تتأكد من الجهاز.",
       "Pvr did not acknowledge STOP. Verify hardware before another recording."));
   });
  });
 }
 private void showFiles(){
  File dir=directory();File[] rows=dir==null?null:dir.listFiles(f->f.isFile()&&f.getName().matches("DVB-[0-9]{8}-[0-9]{6}-SID[0-9]{1,5}\\.ts"));
  if(rows==null||rows.length==0){files.setText(tx("لا توجد ملفات تسجيل","No saved recordings"));return;}
  Arrays.sort(rows,Comparator.comparingLong(File::lastModified).reversed());
  StringBuilder out=new StringBuilder();
  for(int i=0;i<Math.min(15,rows.length);i++)
   out.append(rows[i].getName()).append(" · ").append(format(rows[i].length())).append('\n');
  files.setText(out.toString());
 }
 private void export(){
  if(recording){toast(tx("أوقف التسجيل قبل التصدير","Stop the recording before exporting"));return;}
  File dir=directory();File[] rows=dir==null?null:dir.listFiles(f->f.isFile()&&f.getName().endsWith(".ts"));
  if(rows==null||rows.length==0){toast(tx("ماكو ملف تسجيل للتصدير","No recording to export"));return;}
  Arrays.sort(rows,Comparator.comparingLong(File::lastModified).reversed());
  output=rows[0];
  Intent choose=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  choose.addCategory(Intent.CATEGORY_OPENABLE);
  choose.setType("video/mp2t");
  choose.putExtra(Intent.EXTRA_TITLE,output.getName());
  startActivityForResult(choose,EXPORT);
 }
 @Override protected void onActivityResult(int request,int result,Intent intent){
  super.onActivityResult(request,result,intent);
  if(request!=EXPORT||result!=RESULT_OK||intent==null||intent.getData()==null||output==null)return;
  Uri selected=intent.getData();File file=output;
  worker.execute(()->{
   try(InputStream input=new FileInputStream(file);
       OutputStream output=getContentResolver().openOutputStream(selected,"w")){
    if(output==null)throw new IOException("USB_DESTINATION_UNAVAILABLE");
    byte[] b=new byte[64*1024];int n;
    while((n=input.read(b))!=-1)output.write(b,0,n);
    main.post(()->{if(!destroyed)toast(tx("تم تصدير ملف التسجيل","Recording exported"));});
   }catch(Exception failed){
    main.post(()->{if(!destroyed)toast(tx("تعذر التصدير إلى USB","USB export failed"));});
   }
  });
 }
 private static String format(long size){return String.format(Locale.ROOT,"%.1f MB",size/1048576d);}
 @Override protected void onDestroy(){
  destroyed=true;main.removeCallbacks(monitor);
  if(recording){final int id=captureId;worker.execute(()->NativeCapture.stop(id));}
  worker.shutdown();super.onDestroy();
 }
}
