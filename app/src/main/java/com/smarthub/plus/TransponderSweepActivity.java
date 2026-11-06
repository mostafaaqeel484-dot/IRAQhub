package com.smarthub.plus;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Reads real tuner lock and observed SIDs for known TPs; never invents DVB services. */
public final class TransponderSweepActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile Future<?> task;
 private volatile boolean cancelled,closed;
 private List<ReceiverCatalog.Sat> sats=Collections.emptyList();
 private Spinner selection;
 private TextView progress,results;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("فحص ترددات القمر","Satellite transponder sweep"),
   tx("فحص الترددات المحفوظة وقراءة قفل الإشارة وSID الفعليين",
      "Scan saved transponders for real tuner lock and published service IDs"));
  LinearLayout box=card(body);
  selection=new Spinner(this);box.addView(selection,new LinearLayout.LayoutParams(-1,dp(52)));
  note(box,tx("هذا فحص ترددات وليس Blind Scan أو استخراج قنوات جديدة قابلة للمشاهدة. يتم حفظ تقرير القياس فقط.",
    "This is a tuner lock/SID sweep, not blind scan or playable-channel discovery. It saves a measured report."));
  progress=text("",17,true);box.addView(progress);
  results=text("",13,false);box.addView(results);
  action(box,tx("بدء فحص كل الترددات المحفوظة","Sweep all saved transponders"),this::confirm);
  action(box,tx("إيقاف الفحص والموتور","STOP sweep / motor"),this::cancel);
  action(box,tx("عرض آخر نتائج محفوظة","Last report"),this::showReport);
  action(box,tx("إدارة الأقمار والترددات","Edit satellites and TPs"),
    ()->startActivity(new Intent(this,InstallationEditorActivity.class)));
  io.execute(()->{
   ReceiverCatalog catalog=ReceiverCatalog.get(this);catalog.ensure();
   List<ReceiverCatalog.Sat> rows=catalog.satellites();
   runOnUiThread(()->{if(closed)return;sats=rows;String[] names=new String[rows.size()];
    for(int i=0;i<rows.size();i++)names[i]=rows.get(i).label();
    selection.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    long requested=getIntent().getLongExtra("satellite",-1);
    for(int i=0;i<rows.size();i++)if(rows.get(i).id==requested){selection.setSelection(i);break;}
   });
  });
 }
 private void confirm(){
  int n=selection.getSelectedItemPosition();
  if(n<0||n>=sats.size())return;
  if(task!=null&&!task.isDone()){toast(tx("الفحص جارٍ","Sweep already running"));return;}
  ReceiverCatalog.Sat sat=sats.get(n);
  boolean motor=DvbTuner.usalsEnabled(this,sat.angle,sat.band);
  new AlertDialog.Builder(this).setTitle(sat.label())
   .setMessage(tx("الفحص يغيّر تردد التونر، لذلك أوقف المشاهدة أولاً.",
       "Sweep retunes the tuner. Stop playback first.")+(motor?
     tx("\nUSALS مفعّل وقد يتحرك الطبق؛ تحقق من حدود الحركة.",
       "\nUSALS is enabled; verify dish movement limits."):""))
   .setPositiveButton(tx("ابدأ","Start"),(d,w)->runSweep(sat))
   .setNegativeButton(tx("رجوع","Back"),null).show();
 }
 private void runSweep(ReceiverCatalog.Sat sat){
  cancelled=false;results.setText("");
  task=io.submit(()->{
   try{
    if(prefs().getBoolean("dvb_player_visible",false)||prefs().getBoolean("software_dvb_422_active",false))
      throw new IllegalStateException("CLOSE_PLAYER_FIRST");
    List<ReceiverCatalog.Tp> rows=ReceiverCatalog.get(this).tpRows(sat.id);
    if(rows.isEmpty())throw new IllegalStateException("NO_TRANSPONDERS");
    JSONArray saved=new JSONArray();
    int done=0,locked=0;
    for(ReceiverCatalog.Tp tp:rows){
     if(cancelled||Thread.currentThread().isInterrupted())break;
     int index=done+1;
     runOnUiThread(()->{if(!closed)progress.setText(index+"/"+rows.size()+" · "+tp.label());});
     JSONObject item=new JSONObject().put("freq",tp.frequency).put("pol",tp.polarity)
       .put("sr",tp.symbolRate).put("time",System.currentTimeMillis());
     try{
      ReceiverSignalReading signal=DvbTuner.manualTune(this,sat,tp.frequency,tp.polarity,tp.symbolRate);
      if(cancelled)break;
      item.put("lock",signal.locked==1).put("quality",signal.value("quality"))
       .put("strength",signal.value("strength")).put("sid",knownSids(signal.tp));
      if(signal.locked==1)locked++;
     }catch(Exception ex){
      if(cancelled||Thread.currentThread().isInterrupted())break;
      item.put("lock",false).put("error",ex.getMessage()==null?"NO_LOCK":ex.getMessage());
     }
     saved.put(item);done++;
     String message=tp.label()+" · "+(item.optBoolean("lock")?"LOCK":"NO LOCK")+" · Q "+
      item.optString("quality","—")+" · SID "+item.optJSONArray("sid").length();
     int found=locked,checked=done;
     runOnUiThread(()->{if(!closed){results.append(message+"\n");
      progress.setText(checked+"/"+rows.size()+" · "+tx("ناجحة: ","Locked: ")+found);}});
     prefs().edit().putString("installation_last_sweep",new JSONObject()
       .put("satellite",sat.label()).put("saved",saved).put("locked",locked)
       .put("checked",done).put("total",rows.size()).toString()).apply();
    }
    int count=done,success=locked;
    runOnUiThread(()->{if(!closed)progress.setText(
     (cancelled?tx("تم الإيقاف","Stopped"):tx("اكتمل","Completed"))+" · "+
     count+"/"+rows.size()+" · "+tx("قفل: ","Locked: ")+success);});
   }catch(Exception error){
    String reason=error.getMessage()==null?"SCAN_FAILED":error.getMessage();
    runOnUiThread(()->{if(!closed)progress.setText(reason);});
   }
  });
 }
 private static JSONArray knownSids(int tpId){
  JSONArray ids=new JSONArray();
  try{
   JSONArray list=ReceiverDvbBridge.readSidList().optJSONArray("stEPGCHSIDList");
   if(list==null)return ids;
   for(int i=0;i<Math.min(list.length(),512);i++){
    JSONObject tp=list.optJSONObject(i);
    if(tp==null||tp.optInt("tp_id",-1)!=tpId)continue;
    String raw=tp.optString("aucSIDList","");
    if(raw.length()>10000)break;
    Set<Integer> unique=new HashSet<>();
    for(String id:raw.split(":")){
     if(!id.trim().matches("[0-9]{1,5}"))continue;
     int val=Integer.parseInt(id.trim());
     if(val<=65535&&unique.add(val))ids.put(val);
     if(ids.length()>=512)return ids;
    }
   }
  }catch(Exception ignored){}
  return ids;
 }
 private void showReport(){
  String raw=prefs().getString("installation_last_sweep","");
  if(raw.isEmpty()){toast(tx("لا يوجد تقرير بعد","No report saved"));return;}
  try{
   JSONObject obj=new JSONObject(raw);JSONArray rows=obj.optJSONArray("saved");
   StringBuilder text=new StringBuilder(obj.optString("satellite")).append("\n");
   if(rows!=null)for(int i=0;i<Math.min(rows.length(),128);i++){
    JSONObject row=rows.getJSONObject(i);
    text.append(row.optInt("freq")).append(" ").append(row.optString("pol"))
      .append("/").append(row.optInt("sr")).append("  ")
      .append(row.optBoolean("lock")?"LOCK":"—").append("  Q:")
      .append(row.optString("quality","—")).append("\n");
   }
   new AlertDialog.Builder(this).setTitle(tx("نتيجة الفحص","Sweep result"))
     .setMessage(text.toString()).setPositiveButton("OK",null).show();
  }catch(Exception damaged){toast(tx("التقرير غير صالح","Invalid report"));}
 }
 private void cancel(){
  cancelled=true;Future<?> active=task;if(active!=null)active.cancel(true);
  new Thread(DvbTuner::stopMotor,"iraqhub-stop-dish").start();
  progress.setText(tx("تم إرسال طلب الإيقاف","Stop requested"));
 }
 @Override protected void onDestroy(){closed=true;cancelled=true;
  Future<?> active=task;if(active!=null)active.cancel(true);io.shutdownNow();super.onDestroy();}
}
