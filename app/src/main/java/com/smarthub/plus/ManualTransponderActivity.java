package com.smarthub.plus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Manual DVB-S transponder lock screen, within IRAQhub. Does not fabricate scanned channels. */
public final class ManualTransponderActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private Spinner satellite,polarity;
 private EditText frequency,symbolRate;
 private TextView result;
 private List<ReceiverCatalog.Sat> list=Collections.emptyList();
 private boolean closed;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout root=page(tx("بحث التردد اليدوي","Manual transponder search"),
   tx("ضبط تردد وقراءة إشارة حقيقية من خدمة DVB داخل IRAQhub",
      "Tune a transponder and read real receiver signal data in IRAQhub"));
  LinearLayout panel=card(root);
  panel.addView(text(tx("القمر والتردد","Satellite and frequency"),22,true));
  note(panel,tx("هذا ضبط تردد وفحص إشارة، وليس بعدُ بحث خدمات وتخزين قنوات؛ بروتوكول استخراج البرامج من التردد لم يُربط.",
    "This is a real transponder tune/lock operation, not a service scan. Service enumeration and database insertion are not implemented."));
  satellite=new Spinner(this);
  panel.addView(satellite,new LinearLayout.LayoutParams(-1,dp(52)));
  frequency=input(panel,tx("التردد MHz (مثال 11747)","Frequency MHz, e.g. 11747"),"11747");
  symbolRate=input(panel,tx("معدل الترميز (مثال 27500)","Symbol rate, e.g. 27500"),"27500");
  polarity=new Spinner(this);
  polarity.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"H","V"}));
  int requestedFrequency=getIntent().getIntExtra("frequency",-1),requestedRate=getIntent().getIntExtra("symbol_rate",-1);
  if(requestedFrequency>0)frequency.setText(Integer.toString(requestedFrequency));
  if(requestedRate>0)symbolRate.setText(Integer.toString(requestedRate));
  if("V".equals(getIntent().getStringExtra("polarity")))polarity.setSelection(1);
  panel.addView(polarity,new LinearLayout.LayoutParams(-1,dp(52)));
  result=text("",16,false);
  panel.addView(result);
  action(panel,tx("ضبط التردد وقراءة الإشارة","Tune and read signal"),()->{
   if(list.isEmpty())return;
   int pos=satellite.getSelectedItemPosition();
   if(pos<0||pos>=list.size())return;
   int f,sr;
   try{
    f=Integer.parseInt(frequency.getText().toString());
    sr=Integer.parseInt(symbolRate.getText().toString());
    if(f<100||f>100000||sr<1||sr>100000)throw new NumberFormatException();
   }catch(NumberFormatException invalid){
    toast(tx("التردد أو معدل الترميز غير صالح","Invalid frequency or symbol rate"));return;
   }
   ReceiverCatalog.Sat sat=list.get(pos);
   String pol=(String)polarity.getSelectedItem();
   new AlertDialog.Builder(this).setTitle(tx("الضبط اليدوي","Manual tuning"))
    .setMessage(tx("سيُضبط التونر على ","Receiver will tune to ")+f+" "+pol+" / "+sr+
      "\n"+sat.label()+"\n"+
      tx("إذا كان USALS مفعلاً لهذا القمر قد يتحرك الطبق، تأكد من حدوده.",
         "If USALS is enabled, the dish may move. Confirm physical motor limits."))
    .setPositiveButton(tx("ضبط","Tune"),(dialog,which)->{
     result.setText(tx("جاري ضبط التردد والتحقق من قفل الإشارة…","Tuning and verifying signal lock…"));
     io.execute(()->{
      try{
       ReceiverSignalReading measurement=DvbTuner.manualTune(this,sat,f,pol,sr);
       runOnUiThread(()->{if(closed)return;result.setText(
        tx("قفل التردد: ناجح\nالقوة: ","Locked transponder\nStrength: ")+measurement.value("strength")+
        tx("\nالجودة: ","\nQuality: ")+measurement.value("quality")+
        "\nSNR: "+measurement.value("snr")+"\nBER: "+measurement.value("ber"));});
      }catch(Exception failure){
       String code=failure.getMessage()==null?"MANUAL_TUNE_FAILED":failure.getMessage();
       runOnUiThread(()->{if(!closed)result.setText(tx("تعذر ضبط التردد: ","Manual tune failed: ")+code);});
      }
     });
    }).setNegativeButton(tx("إلغاء","Cancel"),null).show();
  });

  action(card(root),tx("إعداد LNB وDiSEqC","LNB / DiSEqC profiles"),
    ()->startActivity(new android.content.Intent(this,TunerSettingsActivity.class)));
  io.execute(()->{
   ReceiverCatalog catalog=ReceiverCatalog.get(this);catalog.ensure();
   List<ReceiverCatalog.Sat> satellites=catalog.satellites();
   runOnUiThread(()->{
    if(closed)return;
    list=satellites;
    String[] names=new String[satellites.size()];
    for(int i=0;i<names.length;i++)names[i]=satellites.get(i).label();
    satellite.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    long target=getIntent().getLongExtra("satellite",-1L);
    for(int i=0;i<satellites.size();i++)if(satellites.get(i).id==target){satellite.setSelection(i);break;}
   });
  });
 }
 private EditText input(LinearLayout section,String hint,String init){
  EditText field=new EditText(this);field.setInputType(InputType.TYPE_CLASS_NUMBER);
  field.setSingleLine(true);field.setHint(hint);field.setText(init);
  section.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));return field;
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
