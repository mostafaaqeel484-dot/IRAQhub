package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Native DVB service discovery from a genuine MPEG transport-stream recording.
 * Source file must correspond to the satellite/transponder explicitly selected.
 * Existing channels and all OEM data remain unchanged until user confirms import.
 */
public final class TsServiceImportActivity extends HubActivity {
 private static final int CHOOSE_TS=8201;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private Spinner satSelector,tpSelector;
 private TextView status,results;
 private List<ReceiverCatalog.Sat> sats=Collections.emptyList();
 private List<ReceiverCatalog.Tp> tps=Collections.emptyList();
 private volatile boolean closed,busy;
 private DvbPsiScan.Result pending;
 private long sourceSat=-1,sourceTp=-1;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("اكتشاف خدمات DVB وحفظها","DVB service discovery and import"),
    tx("استخراج برامج حقيقية من MPEG-TS: PAT وPMT وSDT، بدون واجهة Aurum",
       "Discover real MPEG-TS PAT, PMT and SDT services in IRAQhub; no OEM menu"));
  LinearLayout input=card(page);
  input.addView(text(tx("القمر والتردد الأصليان للتسجيل","Original recording satellite and transponder"),20,true));
  satSelector=new Spinner(this);tpSelector=new Spinner(this);
  input.addView(satSelector,new LinearLayout.LayoutParams(-1,dp(49)));
  input.addView(tpSelector,new LinearLayout.LayoutParams(-1,dp(49)));
  note(input,tx("الملف يجب أن يكون تسجيلاً حقيقياً من نفس القمر والتردد الذي تحدده. البرنامج لا يستنتج تردد الصحن من الفيديو ولا يخترع أسماء القنوات.",
   "Select the exact satellite and RF of a genuine DVB MPEG-TS recording. The file alone cannot prove the dish RF; programme names are never invented."));
  action(input,tx("اختيار ملف .ts من USB أو التخزين","Select DVB .ts recording from USB/storage"),this::choose);
  action(input,tx("حفظ البرامج المكتشفة داخل IRAQhub","Save verified services in IRAQhub"),this::confirm);
  status=text(tx("اختر القمر والتردد ثم ملف TS","Choose satellite, TP and TS file"),15,false);
  results=text("",14,false);input.addView(status);input.addView(results);
  LinearLayout note=card(page);
  note.addView(text(tx("ما الذي يكتشفه المحلل؟","What can be discovered?"),19,true));
  note(note,tx("يتحقق من CRC لجداول PSI ويستخرج SID واسم الخدمة والمزود وPMT/PCR/Video/Audio PID ونوع الكودك وعلامة التشفير. القنوات غير المكتملة لا تُضاف، والقديمة لا تُستبدل.",
    "Verifies PSI CRCs and extracts SID, service name, provider, PMT/PCR/Video/Audio PID, codec and CA metadata. Incomplete services are excluded; existing channels are not replaced."));
  note(note,tx("هذا بحث من بيانات TS متاحة، وليس Blind Scan مباشر. حفظ النتائج في قاعدة IRAQhub وليس قاعدة Aurum المحمية.",
    "This discovers services from an accessible TS capture, not direct Blind Scan. Results are saved to IRAQhub SQLite, not the protected Aurum database."));
  satSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onNothingSelected(AdapterView<?> p){}
   @Override public void onItemSelected(AdapterView<?> p,View view,int pos,long id){loadTps(pos);}
  });
  io.execute(()->{
   ReceiverCatalog catalog=ReceiverCatalog.get(this);catalog.ensure();
   List<ReceiverCatalog.Sat> available=catalog.satellites();
   runOnUiThread(()->{if(closed)return;
    sats=available;
    String[] labels=new String[available.size()];
    int chosen=0;long requested=getIntent().getLongExtra("satellite",-1);
    for(int i=0;i<available.size();i++){
     labels[i]=available.get(i).label();
     if(available.get(i).id==requested)chosen=i;
    }
    satSelector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    if(!available.isEmpty())satSelector.setSelection(chosen);
   });
  });
 }
 private void loadTps(int pos){
  if(pos<0||pos>=sats.size())return;
  ReceiverCatalog.Sat sat=sats.get(pos);
  pending=null;sourceSat=-1;sourceTp=-1;
  io.execute(()->{
   List<ReceiverCatalog.Tp> found=ReceiverCatalog.get(this).tpRows(sat.id);
   runOnUiThread(()->{if(closed||satSelector.getSelectedItemPosition()<0
       ||sats.get(satSelector.getSelectedItemPosition()).id!=sat.id)return;
    tps=found;String[] names=new String[found.size()];
    for(int i=0;i<found.size();i++)names[i]=found.get(i).label();
    tpSelector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    status.setText(tx("ترددات القمر: ","Available TPs: ")+found.size());
   });
  });
 }
 private void choose(){
  if(busy){toast(tx("التحليل ما زال يعمل","Scan still running"));return;}
  if(sats.isEmpty()||tps.isEmpty()){toast(tx("أضف قمراً وتردداً أولاً","Add satellite and transponder first"));return;}
  Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
    .addCategory(Intent.CATEGORY_OPENABLE);
  launchDocument(picker,CHOOSE_TS);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=CHOOSE_TS||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
  int satIndex=satSelector.getSelectedItemPosition(),tpIndex=tpSelector.getSelectedItemPosition();
  if(satIndex<0||satIndex>=sats.size()||tpIndex<0||tpIndex>=tps.size())return;
  ReceiverCatalog.Sat sat=sats.get(satIndex);
  ReceiverCatalog.Tp tp=tps.get(tpIndex);
  Uri uri=data.getData();
  if(!"content".equals(uri.getScheme())){toast(tx("اختر ملفاً عبر منتقي Android","Use Android document picker"));return;}
  pending=null;busy=true;results.setText("");
  status.setText(tx("قراءة TS والتحقق من PAT/PMT/SDT…","Validating MPEG-TS PSI tables…"));
  io.execute(()->{
   try(InputStream stream=getContentResolver().openInputStream(uri)){
    DvbPsiScan.Result parsed=DvbPsiScan.scan(stream);
    runOnUiThread(()->{if(closed)return;
     pending=parsed;sourceSat=sat.id;sourceTp=tp.id;
     status.setText(tx("الخدمات المكتشفة: ","Discovered services: ")+parsed.services.length()
       +" · TSID "+parsed.transportId+" · ONID "+parsed.networkId);
     StringBuilder preview=new StringBuilder();
     for(int i=0;i<Math.min(60,parsed.services.length());i++){
      org.json.JSONObject row=parsed.services.optJSONObject(i);
      if(row!=null)preview.append(row.optInt("sid")).append(" · ")
       .append(row.optString("name")).append(" · ")
       .append(row.optInt("ca")==0?"FTA":"CA").append(" · ")
       .append(row.optString("videoType")).append("\n");
     }
     if(parsed.services.length()>60)preview.append("…");
     results.setText(preview.toString());
     busy=false;
    });
   }catch(Exception err){
    String error=err.getMessage()==null?"PSI_SCAN_FAILED":err.getMessage();
    runOnUiThread(()->{if(!closed){status.setText(error);busy=false;}});
   }
  });
 }
 private void confirm(){
  if(busy||pending==null){toast(tx("حلّل ملف TS أولاً","Scan a TS file first"));return;}
  int si=satSelector.getSelectedItemPosition(),ti=tpSelector.getSelectedItemPosition();
  if(si<0||si>=sats.size()||ti<0||ti>=tps.size())return;
  ReceiverCatalog.Sat sat=sats.get(si);ReceiverCatalog.Tp tp=tps.get(ti);
  if(sat.id!=sourceSat||tp.id!=sourceTp){
   toast(tx("اختيار القمر أو التردد تغيّر؛ أعد قراءة الملف","Satellite/TP changed; re-scan source"));return;
  }
  DvbPsiScan.Result result=pending;
  new AlertDialog.Builder(this).setTitle(tx("تأكيد إدخال القنوات","Confirm DVB service import"))
   .setMessage(sat.label()+"\n"+tp.label()+"\n"+
    tx("هل تؤكد أن ملف TS من هذا التردد؟ إضافة القنوات ستكون داخل قاعدة IRAQhub فقط، بدون تعديل قنوات المصنع.",
      "Confirm the TS file really belongs to this RF. Only IRAQhub's catalogue will change; OEM channels remain untouched."))
   .setPositiveButton(tx("إضافة القنوات","Import services"),(d,w)->{
    busy=true;status.setText(tx("حفظ القنوات…","Saving services…"));
    io.execute(()->{
     try{
      ReceiverCatalog.PsiImportResult outcome=ReceiverCatalog.get(this).savePsiServices(
       sat.id,tp.frequency,tp.polarity,tp.symbolRate,result);
      runOnUiThread(()->{if(!closed){busy=false;pending=null;
       status.setText(tx("قنوات جديدة: ","Added: ")+outcome.added+
        tx(" · مكررة: "," · duplicates: ")+outcome.duplicates+
        tx(" · مرفوضة: "," · invalid: ")+outcome.invalid);
      }});
     }catch(Exception err){
      String message=err.getMessage()==null?"TS_SAVE_FAILED":err.getMessage();
      runOnUiThread(()->{if(!closed){busy=false;status.setText(message);}});
     }
    });
   }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
