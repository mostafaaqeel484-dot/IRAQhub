package com.smarthub.plus;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Native installation editor: satellites and transponders in IRAQhub's own database. */
public final class InstallationEditorActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private Spinner satellites;
 private LinearLayout transponders;
 private TextView status;
 private List<ReceiverCatalog.Sat> items=Collections.emptyList();
 private boolean closed;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("التركيب · الأقمار والترددات","Installation · Satellites & TPs"),
   tx("أضف وعدّل بيانات التركيب داخل IRAQhub. التغييرات على قائمة التطبيق وليس قاعدة بيانات المصنع.",
    "Manage satellites and transponders inside IRAQhub, without modifying the firmware database."));
  LinearLayout box=card(page);
  box.addView(text(tx("الأقمار","Satellites"),21,true));
  satellites=new Spinner(this);
  box.addView(satellites,new LinearLayout.LayoutParams(-1,dp(48)));
  status=text("",14,false);box.addView(status);
  action(box,tx("إضافة قمر جديد","Add satellite"),this::addSatellite);
  action(box,tx("تغيير اسم القمر","Rename satellite"),this::renameSatellite);
  action(box,tx("إعدادات LNB وDiSEqC للقمر","LNB / DiSEqC for satellite"),
   ()->withSatellite(s->startActivity(new Intent(this,TunerSettingsActivity.class).putExtra("satellite",s.id))));
  action(box,tx("إعدادات USALS والموتور","USALS / motor for satellite"),
   ()->withSatellite(s->startActivity(new Intent(this,MotorCenterActivity.class).putExtra("satellite",s.id))));
  satellites.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onNothingSelected(AdapterView<?> a){}
   @Override public void onItemSelected(AdapterView<?> a,View view,int index,long id){loadTransponders(index);}
  });

  LinearLayout tps=card(page);
  tps.addView(text(tx("قائمة الترددات","Transponder list"),21,true));
  action(tps,tx("إضافة تردد","Add transponder"),this::addTransponder);
  transponders=col();tps.addView(transponders);
  note(tps,tx("يمكن حذف التردد فقط إذا ما مرتبط بقنوات محفوظة. ضبْط التردد يدوي ويتحقق من الإشارة الحقيقية؛ لا يضيف قنوات وهمية.",
    "A TP can only be deleted if no saved channel uses it. Manual tuning reads real tuner lock; it does not invent services."));
  action(tps,tx("البحث عن الإشارة للتردد المختار","Manual signal tuning"),
    ()->withSatellite(s->startActivity(new Intent(this,ManualTransponderActivity.class).putExtra("satellite",s.id))));
  reload(-1);
 }

 private interface UseSat{void accept(ReceiverCatalog.Sat sat);}
 private void withSatellite(UseSat action){
  int pos=satellites.getSelectedItemPosition();
  if(pos<0||pos>=items.size()){toast(tx("أضف قمراً أولاً","Add a satellite first"));return;}
  action.accept(items.get(pos));
 }
 private static EditText field(InstallationEditorActivity host,LinearLayout parent,String hint,String value,boolean integer){
  EditText input=new EditText(host);
  input.setSingleLine(true);input.setHint(hint);input.setText(value);
  input.setInputType(integer?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);
  parent.addView(input,new LinearLayout.LayoutParams(-1,host.dp(49)));
  return input;
 }
 private void addSatellite(){
  LinearLayout pane=col();
  EditText name=field(this,pane,tx("اسم القمر","Satellite name"),"",false);
  EditText position=field(this,pane,tx("الزاوية × 10 (192 = 19.2E، 3300 = 30W)","Orbital angle tenths (192=19.2E, 3300=30W)"),"",true);
  Spinner band=new Spinner(this);
  band.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"KU","C"}));
  pane.addView(band);
  new AlertDialog.Builder(this).setTitle(tx("إضافة قمر","Add satellite"))
   .setView(pane).setPositiveButton(tx("حفظ","Save"),(d,w)->io.execute(()->{
    String msg;long created=-1;
    try{created=ReceiverCatalog.get(this).addSatelliteLocal(name.getText().toString(),
       Integer.parseInt(position.getText().toString()),(String)band.getSelectedItem());
       msg=tx("تمت إضافة القمر","Satellite added");}
    catch(Exception invalid){msg=tx("تعذر إضافة القمر: ","Could not add satellite: ")+invalid.getMessage();}
    long selected=created;String result=msg;
    runOnUiThread(()->{if(!closed){status.setText(result);reload(selected);}});
   })).setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void renameSatellite(){
  withSatellite(s->{
   EditText input=new EditText(this);input.setSingleLine(true);input.setText(s.name);
   new AlertDialog.Builder(this).setTitle(tx("تغيير اسم القمر","Rename satellite")).setView(input)
    .setPositiveButton(tx("حفظ","Save"),(d,w)->io.execute(()->{
     boolean ok=ReceiverCatalog.get(this).renameSatelliteLocal(s.id,input.getText().toString());
     runOnUiThread(()->{if(!closed){toast(ok?tx("تم تعديل الاسم","Renamed"):tx("الاسم غير صالح","Invalid name"));reload(s.id);}});
    })).setNegativeButton(tx("إلغاء","Cancel"),null).show();
  });
 }
 private void addTransponder(){
  withSatellite(s->{
   LinearLayout panel=col();
   EditText freq=field(this,panel,tx("التردد MHz","Frequency MHz"),"",true);
   EditText rate=field(this,panel,tx("معدل الترميز","Symbol rate"),"",true);
   Spinner polarity=new Spinner(this);
   polarity.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"H","V"}));
   panel.addView(polarity);
   new AlertDialog.Builder(this).setTitle(s.label()).setView(panel)
    .setPositiveButton(tx("إضافة","Add"),(d,w)->io.execute(()->{
     String result;
     try{ReceiverCatalog.get(this).addTransponderLocal(s.id,Integer.parseInt(freq.getText().toString()),
       (String)polarity.getSelectedItem(),Integer.parseInt(rate.getText().toString()));
       result=tx("تم حفظ التردد","Transponder saved");}
     catch(Exception err){result=tx("تعذر الحفظ: ","Save failed: ")+err.getMessage();}
     String display=result;
     runOnUiThread(()->{if(!closed){status.setText(display);reload(s.id);}});
    })).setNegativeButton(tx("إلغاء","Cancel"),null).show();
  });
 }
 private void reload(long target){
  io.execute(()->{
   ReceiverCatalog db=ReceiverCatalog.get(this);db.ensure();
   List<ReceiverCatalog.Sat> found=db.satellites();
   runOnUiThread(()->{
    if(closed)return;
    long existing=target>=0?target:selection();
    items=found;String[] names=new String[found.size()];
    int selected=0;
    for(int i=0;i<found.size();i++){names[i]=found.get(i).label();if(found.get(i).id==existing)selected=i;}
    satellites.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    if(!found.isEmpty())satellites.setSelection(selected);
    status.setText(tx("عدد الأقمار المحلية: ","Local satellites: ")+found.size());
    if(found.isEmpty())transponders.removeAllViews();
   });
  });
 }
 private long selection(){int index=satellites.getSelectedItemPosition();return index>=0&&index<items.size()?items.get(index).id:-1;}
 private void loadTransponders(int index){
  if(index<0||index>=items.size())return;
  ReceiverCatalog.Sat sat=items.get(index);
  io.execute(()->{
   List<ReceiverCatalog.Tp> found=ReceiverCatalog.get(this).tpRows(sat.id);
   runOnUiThread(()->{
    if(closed||selection()!=sat.id)return;
    transponders.removeAllViews();
    transponders.addView(text(tx("الترددات: ","Transponders: ")+found.size(),15,false));
    for(ReceiverCatalog.Tp tp:found){
     action(transponders,tp.label(),()->ReceiverPanelStyle.choose(this,tp.label(),new String[]{
       tx("ضبط التردد وقياس الإشارة","Tune and measure signal"),
       tx("حذف التردد إن لم ترتبط به قنوات","Delete TP if no channels use it")
      },0,pick->{
       if(pick==0)startActivity(new Intent(this,ManualTransponderActivity.class)
        .putExtra("satellite",sat.id).putExtra("frequency",tp.frequency)
        .putExtra("polarity",tp.polarity).putExtra("symbol_rate",tp.symbolRate));
       else new AlertDialog.Builder(this).setTitle(tx("حذف التردد؟","Delete transponder?"))
       .setMessage(tp.label()).setPositiveButton(tx("حذف","Delete"),(d,w)->io.execute(()->{
        boolean ok=ReceiverCatalog.get(this).removeEmptyTpLocal(tp.id);
        runOnUiThread(()->{if(!closed){toast(ok?tx("تم حذف التردد","Deleted"):tx("التردد يحتوي قنوات أو لم يعد موجوداً","TP has channels or is missing"));loadTransponders(index);}});
       })).setNegativeButton(tx("إلغاء","Cancel"),null).show();
      }));
    }
   });
  });
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
