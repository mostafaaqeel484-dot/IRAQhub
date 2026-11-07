package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate IRAQhub manual DVB-S/S2 frequency search. No BISS control on this screen. */
public final class ManualTransponderActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final AtomicBoolean busy=new AtomicBoolean();
 private Spinner satellite,polarity;
 private EditText frequency,symbolRate;
 private TextView result,services;
 private List<ReceiverCatalog.Sat> list=Collections.emptyList();
 private volatile boolean closed;
 private volatile int observedRuntimeTp=-1;
 private volatile long observedSatellite=-1;
 private volatile String observedFrequency="";
 private volatile DirectTransponderScanner activeScan;
 private volatile Future<?> directTask;
 private volatile boolean directMayMoveMotor;
 private volatile DvbPsiScan.Result directResults;
 private volatile Selection resultSelection;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout root=page(tx("البحث اليدوي · DVB-S/S2","Manual satellite search · DVB-S/S2"),
    tx("اختيار القمر والتردد وضبط الموالف وقياس الإشارة وحفظ التردد",
       "Choose satellite / RF, tune the actual receiver, read its lock and save TP"));
  LinearLayout panel=card(root);
  panel.addView(text(tx("بيانات البحث اليدوي","Manual search parameters"),22,true));
  satellite=new Spinner(this);
  panel.addView(satellite,new LinearLayout.LayoutParams(-1,dp(52)));
  frequency=input(panel,tx("التردد MHz، مثال: 11747","Frequency MHz, e.g. 11747"),"11747");
  polarity=new Spinner(this);
  polarity.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"H","V"}));
  panel.addView(polarity,new LinearLayout.LayoutParams(-1,dp(52)));
  symbolRate=input(panel,tx("الترميز KS/s، مثال: 27500","Symbol rate KS/s, e.g. 27500"),"27500");
  int f=getIntent().getIntExtra("frequency",-1),rate=getIntent().getIntExtra("symbol_rate",-1);
  if(f>0)frequency.setText(Integer.toString(f));
  if(rate>0)symbolRate.setText(Integer.toString(rate));
  if("V".equals(getIntent().getStringExtra("polarity")))polarity.setSelection(1);
  result=text(tx("جاهز للبحث","Ready for search"),17,true);panel.addView(result);
  services=text("",14,false);panel.addView(services);

  action(panel,tx("فحص إشارة التردد · ضبط التونر","Check transponder signal · tune"),this::confirmTune);
  action(panel,tx("بحث مباشر عن القنوات وإضافتها · PAT / PMT / SDT",
      "Discover channels directly on this frequency · PAT / PMT / SDT"),this::confirmDirectScan);
  action(panel,tx("اختيار القنوات المكتشفة وحفظها","Choose discovered channels to save"),this::chooseDirectServices);
  action(panel,tx("إيقاف البحث المباشر","Stop direct scan"),this::cancelDirectScan);
  action(panel,tx("فتح بحث Aurum الأصلي · بديل عند رفض البحث المباشر",
      "Open original Aurum scan · fallback if direct scan is unsupported"),
    ()->NativeSearchNavigation.open(this));
  action(panel,tx("حفظ التردد في IRAQhub","Save this transponder"),this::saveTransponder);
  action(panel,tx("قراءة معرّفات الخدمات SID إن توفرت","Check observed service IDs (SID)"),this::readSids);
  note(panel,tx("قوة الإشارة وSID ليست أسماء القنوات أو PID للصورة والصوت. حفظ القنوات الجديدة يتطلب استخراج بيانات PAT/PMT/SDT من بث TS حقيقي.",
    "Lock/SID alone cannot identify complete playable services. Channel import requires verified MPEG-TS PAT/PMT/SDT."));
  LinearLayout other=card(root);
  action(other,tx("اكتشاف وحفظ قنوات من تسجيل TS لهذه الترددات","Discover/import channels from an actual DVB TS recording"),
    ()->{
     Selection selected=selection();
     if(selected==null||!busy.compareAndSet(false,true))return;
     io.execute(()->{
      String error="";
      try{ReceiverCatalog.get(this).addTransponderLocal(
        selected.sat.id,selected.frequency,selected.pol,selected.rate);
      }catch(Exception failed){
       error=failed.getMessage()==null?"INVALID_TP":failed.getMessage();
       if("TRANSPONDER_ALREADY_EXISTS".equals(error))error="";
      }
      String reason=error;
      runOnUiThread(()->{
       busy.set(false);
       if(closed)return;
       if(!reason.isEmpty()){result.setText(reason);return;}
       startActivity(new Intent(this,TsServiceImportActivity.class)
        .putExtra("satellite",selected.sat.id)
        .putExtra("frequency",selected.frequency).putExtra("polarity",selected.pol)
        .putExtra("symbol_rate",selected.rate));
      });
     });
    });
  action(other,tx("إعدادات الصحن · LNB / DiSEqC","LNB / DiSEqC"),
    ()->startActivity(new Intent(this,TunerSettingsActivity.class)));
  io.execute(()->{
   ReceiverCatalog db=ReceiverCatalog.get(this);db.ensure();
   List<ReceiverCatalog.Sat> sats=db.satellites();
   runOnUiThread(()->{
    if(closed)return;
    list=sats;String[] names=new String[sats.size()];
    for(int i=0;i<sats.size();i++)names[i]=sats.get(i).label();
    satellite.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    long target=getIntent().getLongExtra("satellite",-1L);
    for(int i=0;i<sats.size();i++)if(sats.get(i).id==target){satellite.setSelection(i);break;}
    if(sats.isEmpty())result.setText(tx("أضف قمراً من التركيب أولاً","Add a satellite in Installation first"));
   });
  });
 }
 private static final class Selection {
  final ReceiverCatalog.Sat sat;final int frequency,rate;final String pol;
  Selection(ReceiverCatalog.Sat sat,int f,String pol,int rate){
   this.sat=sat;this.frequency=f;this.pol=pol;this.rate=rate;
  }
  String key(){return frequency+":"+pol+":"+rate;}
 }
 private Selection selection(){
  int pos=satellite.getSelectedItemPosition();
  if(pos<0||pos>=list.size()){toast(tx("اختر قمراً أولاً","Select satellite first"));return null;}
  try{
   int f=Integer.parseInt(frequency.getText().toString().trim());
   int rate=Integer.parseInt(symbolRate.getText().toString().trim());
   String pol=(String)polarity.getSelectedItem();
   if(f<100||f>100000||rate<1||rate>100000||
     (!"H".equals(pol)&&!"V".equals(pol)))throw new NumberFormatException();
   return new Selection(list.get(pos),f,pol,rate);
  }catch(Exception invalid){
   toast(tx("تحقق من التردد والاستقطاب والترميز","Check frequency, polarity and symbol rate"));
   return null;
  }
 }
 private void confirmTune(){
  Selection current=selection();if(current==null||!busy.compareAndSet(false,true))return;
  busy.set(false);
  new AlertDialog.Builder(this).setTitle(tx("بدء البحث اليدوي","Manual search"))
   .setMessage(current.sat.label()+"\n"+current.frequency+" "+current.pol+
      " / "+current.rate+"\n"+
      tx("سيُعاد ضبط التونر؛ قد يتوقف البث وتتحرك صينية الطبق إن كان USALS مفعلاً.",
        "The tuner will retune; playback may stop and USALS may move the dish."))
   .setPositiveButton(tx("ابدأ","Search"),(d,w)->tune(current))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void tune(Selection current){
  if(!busy.compareAndSet(false,true))return;
  observedRuntimeTp=-1;observedSatellite=-1;observedFrequency="";
  services.setText("");
  result.setText(tx("البحث عن إشارة التردد…","Tuning transponder and checking lock…"));
  io.execute(()->{
   try{
    ReceiverSignalReading signal=DvbTuner.manualTune(this,current.sat,
      current.frequency,current.pol,current.rate);
    if(signal.locked!=1)throw new Exception("MANUAL_TUNE_NO_LOCK");
    observedRuntimeTp=signal.tp;
    observedSatellite=current.sat.id;
    observedFrequency=current.key();
    runOnUiThread(()->{if(!closed)result.setText(
      tx("تم قفل التردد ✓","Transponder locked ✓")+
      "\n"+current.frequency+" "+current.pol+" / "+current.rate+
      tx("\nالقوة: ","\nStrength: ")+signal.value("strength")+
      tx(" · الجودة: "," · Quality: ")+signal.value("quality")+
      "\nSNR: "+signal.value("snr")+" · BER: "+signal.value("ber"));});
   }catch(Exception error){
    String why=error.getMessage()==null?"MANUAL_TUNE_FAILED":error.getMessage();
    runOnUiThread(()->{if(!closed)result.setText(tx("تعذر قفل التردد: ","Failed to lock TP: ")+why);});
   }finally{busy.set(false);}
  });
 }
 /**
  * Real receiver-side scan. No OEM menu launches, synthetic services or private
  * Aurum database access. Whole-TP TS recording is an optional firmware
  * capability: unsupported firmware reports a clear error.
  */
 private void confirmDirectScan(){
  Selection selected=selection();
  if(selected==null||busy.get())return;
  boolean motor=DvbTuner.usalsEnabled(this,selected.sat.angle,selected.sat.band);
  new AlertDialog.Builder(this)
   .setTitle(tx("بحث DVB يدوي مباشر","Direct DVB manual channel scan"))
   .setMessage(selected.sat.label()+"\n"+selected.frequency+" "+selected.pol+
    " / "+selected.rate+"\n"+
    tx("سيُعاد ضبط التونر وقد يتوقف البث. يجب إغلاق المشغل والتسجيل أولاً. "+
       "النتائج تأتي فقط من TS حقيقي بعد التحقق من PAT/PMT/SDT."+
       (motor?"\nUSALS مفعّل وقد يتحرك الصحن.":""),
       "The tuner will retune; stop video and PVR first. Services require "+
       "real MPEG-TS PAT/PMT/SDT."+
       (motor?"\nUSALS is enabled; the dish may move.":"")))
   .setPositiveButton(tx("ابدأ البحث","Start scan"),(d,w)->directScan(selected))
   .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void directScan(Selection selected){
  if(!busy.compareAndSet(false,true))return;
  directResults=null;resultSelection=null;services.setText("");
  result.setText(tx("ضبط التردد وقراءة القنوات من بث TS الحقيقي…",
     "Tuning and extracting real DVB services…"));
  DirectTransponderScanner scanner=new DirectTransponderScanner(this);
  directMayMoveMotor=DvbTuner.usalsEnabled(this,selected.sat.angle,selected.sat.band);
  activeScan=scanner;
  directTask=io.submit(()->{
   try(DirectTransponderScanner session=scanner){
    DvbPsiScan.Result report=session.scan(selected.sat,selected.frequency,
       selected.pol,selected.rate,status->{
        prefs().edit().putString("manual_direct_scan_status",status).apply();
        runOnUiThread(()->{
         if(!closed&&activeScan==session)result.setText("DVB · "+status);
        });
       });
    prefs().edit().putString("manual_direct_scan_status","SERVICES_VERIFIED")
      .putInt("manual_direct_scan_count",report.services.length()).apply();
    runOnUiThread(()->{
     if(closed)return;
     directResults=report;resultSelection=selected;
     result.setText(tx("اكتملت قراءة TS · قنوات حقيقية: ",
       "Verified live TS services: ")+report.services.length()+
       " · TSID "+report.transportId+" · ONID "+report.networkId);
     StringBuilder text=new StringBuilder();
     for(int i=0;i<Math.min(80,report.services.length());i++){
      JSONObject channel=report.services.optJSONObject(i);
      if(channel!=null)text.append(channel.optInt("sid"))
       .append(" · ").append(channel.optString("name"))
       .append(channel.optInt("ca",0)==0?" · FTA":" · CA")
       .append("\n");
     }
     services.setText(text.toString());
    });
   }catch(Exception problem){
    String reason=problem.getMessage()==null?"MANUAL_PSI_SCAN_UNAVAILABLE":problem.getMessage();
    prefs().edit().putString("manual_direct_scan_status",reason)
      .putInt("manual_direct_scan_count",0).apply();
    runOnUiThread(()->{
     if(closed)return;
     result.setText(tx("لم تكتمل قراءة القنوات: ","Live channel scan unavailable: ")+reason);
     if("FIRMWARE_FULL_TS_CAPTURE_REJECTED".equals(reason)||
        "FIRMWARE_CAPTURE_NO_TS_DATA".equals(reason))
      services.setText(tx("الفيرموير لم يوفر تسجيل التردد الكامل. "+
         "يمكن استعمال تسجيل TS من هذا التردد في أداة الاستيراد المنفصلة.",
         "Firmware did not expose whole-transponder TS. You may use a "+
         "real TS recording in the separate import tool."));
    });
   }finally{
    activeScan=null;
    directMayMoveMotor=false;
    runOnUiThread(()->busy.set(false));
   }
  });
 }
 private void chooseDirectServices(){
  DvbPsiScan.Result report=directResults;
  Selection source=resultSelection,current=selection();
  if(busy.get()||report==null||source==null||current==null||
     current.sat.id!=source.sat.id||!current.key().equals(source.key())){
   toast(tx("نفّذ البحث المباشر أولاً على نفس التردد",
     "Run direct scan on this frequency first"));return;
  }
  int count=report.services.length();
  if(count==0)return;
  String[] names=new String[count];boolean[] picked=new boolean[count];
  for(int i=0;i<count;i++){
   JSONObject row=report.services.optJSONObject(i);
   names[i]=row==null?"?":row.optInt("sid")+" · "+row.optString("name")+
      (row.optInt("ca",0)==0?" · FTA":" · CA");
   picked[i]=true;
  }
  new AlertDialog.Builder(this)
   .setTitle(tx("اختَر القنوات المراد حفظها","Select channels to import"))
   .setMultiChoiceItems(names,picked,(dialog,which,checked)->picked[which]=checked)
   .setPositiveButton(tx("حفظ المحدد","Save selected"),(dialog,which)->{
    JSONArray chosen=new JSONArray();
    for(int i=0;i<count;i++)if(picked[i]){
     JSONObject item=report.services.optJSONObject(i);
     if(item!=null)chosen.put(item);
    }
    if(chosen.length()==0){
     toast(tx("ما اخترت أي قناة","No channels selected"));return;
    }
    if(!busy.compareAndSet(false,true))return;
    result.setText(tx("جاري حفظ القنوات…","Importing channels…"));
    DvbPsiScan.Result selectedReport=new DvbPsiScan.Result(
       report.packets,report.sections,report.transportId,report.networkId,
       report.withoutNames,chosen);
    io.execute(()->{
     try{
      ReceiverCatalog.PsiImportResult saved=ReceiverCatalog.get(this).savePsiServices(
        source.sat.id,source.frequency,source.pol,source.rate,selectedReport);
      runOnUiThread(()->{
       if(closed)return;
       result.setText(tx("تمت إضافة ","Added ")+saved.added+
         tx(" قناة · موجودة: "," services · existing: ")+saved.duplicates+
         tx(" · غير صالحة: "," · unsupported: ")+saved.invalid);
       directResults=null;resultSelection=null;
      });
     }catch(Exception error){
      String detail=error.getMessage()==null?"DVB_IMPORT_FAILED":error.getMessage();
      runOnUiThread(()->{if(!closed)result.setText(detail);});
     }finally{runOnUiThread(()->busy.set(false));}
    });
   })
   .setNegativeButton(tx("رجوع","Back"),null).show();
 }
 private void cancelDirectScan(){
  DirectTransponderScanner active=activeScan;
  Future<?> job=directTask;
  if(job==null||job.isDone())return;
  boolean stopMotor=directMayMoveMotor;
  job.cancel(true);
  new Thread(()->{
   if(active!=null)active.close();
   if(stopMotor)DvbTuner.stopMotor();
  },"manual-dvb-scan-stop").start();
 }
 private void saveTransponder(){
  Selection current=selection();if(current==null||!busy.compareAndSet(false,true))return;
  result.setText(tx("حفظ التردد…","Saving transponder…"));
  io.execute(()->{
   String status;
   try{
    ReceiverCatalog db=ReceiverCatalog.get(this);
    long id=db.addTransponderLocal(current.sat.id,current.frequency,current.pol,current.rate);
    status=tx("تم حفظ التردد برقم ","Transponder saved #")+id;
   }catch(Exception err){
    String code=err.getMessage()==null?"TP_SAVE_FAILED":err.getMessage();
    status="TRANSPONDER_ALREADY_EXISTS".equals(code)?
       tx("التردد محفوظ مسبقاً، بدون تكرار","Transponder already saved — no duplicate"):
       tx("فشل حفظ التردد: ","Could not save TP: ")+code;
   }
   String output=status;
   runOnUiThread(()->{if(!closed)result.setText(output);busy.set(false);});
  });
 }
 private void readSids(){
  Selection current=selection();
  if(current==null||observedRuntimeTp<0||current.sat.id!=observedSatellite||
      !current.key().equals(observedFrequency)){
   toast(tx("ابدأ البحث وحقق القفل على نفس التردد أولاً",
     "Lock the exact transponder before reading SIDs"));return;
  }
  if(!busy.compareAndSet(false,true))return;
  final int expected=observedRuntimeTp;
  services.setText(tx("قراءة معرّفات الخدمات المرصودة…","Reading published service IDs…"));
  io.execute(()->{
   String text;
   try{
    ReceiverSignalReading lock=DvbTuner.read();
    if(lock.locked!=1||lock.tp!=expected)throw new Exception("TUNER_CHANGED");
    JSONArray list=ReceiverDvbBridge.readSidList().optJSONArray("stEPGCHSIDList");
    TreeSet<Integer> ids=new TreeSet<>();
    if(list!=null)for(int i=0;i<Math.min(512,list.length());i++){
     JSONObject tp=list.optJSONObject(i);
     if(tp==null||tp.optInt("tp_id",-1)!=expected)continue;
     String source=tp.optString("aucSIDList","");
     if(source.length()>20000)continue;
     for(String value:source.split(":")){
      String item=value.trim();
      if(item.matches("[0-9]{1,5}")){
       int sid=Integer.parseInt(item);
       if(sid>0&&sid<=65535&&ids.size()<256)ids.add(sid);
      }
     }
    }
    text=ids.isEmpty()?tx("قفل الإشارة ناجح، لكن خدمة DVB لم تنشر معرّفات SID لهذا التردد.",
       "TP locked but the receiver did not publish SIDs for this TP."):
       tx("SID المرصودة من DVB: ","Observed DVB service IDs: ")+ids.size()+"\n"+ids.toString()+
       tx("\nهذه معرّفات فقط؛ لم تُستخرج أسماء أو PIDs للقنوات.",
          "\nOnly SIDs — not new playable channel data.");
   }catch(Exception err){
    text=tx("تعذر الحصول على SID: ","SID query unavailable: ")+
      (err.getMessage()==null?"UNKNOWN":err.getMessage());
   }
   String result=text;
   runOnUiThread(()->{if(!closed)services.setText(result);busy.set(false);});
  });
 }
 private EditText input(LinearLayout section,String hint,String init){
  EditText field=new EditText(this);
  field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setSingleLine(true);
  field.setHint(hint);field.setText(init);
  section.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));
  return field;
 }
 @Override protected void onDestroy(){
  closed=true;cancelDirectScan();io.shutdownNow();super.onDestroy();
 }
}
