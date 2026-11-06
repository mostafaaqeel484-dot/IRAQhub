package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Original 3.7.0 motor controls integrated directly in the IRAQhub interface. */
public final class MotorCenterActivity extends HubActivity {
 private final ExecutorService work=Executors.newSingleThreadExecutor();
 private List<ReceiverCatalog.Sat> satellites=Collections.emptyList();
 private Spinner satellite;
 private EditText latitude,longitude;
 private CheckBox enabled;
 private TextView state;
 private volatile boolean closed;

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("مركز الموتور وUSALS","Motor and USALS centre"),
      tx("إعدادات الموتور داخل IRAQhub 3.7.0","Motor controls inside IRAQhub 3.7.0"));
  LinearLayout panel=card(body);
  panel.addView(text(tx("إحداثيات موقع الطبق","Dish location"),21,true));
  note(panel,tx("الإحداثيات بالنظام العشري: شمال وشرق موجب، جنوب وغرب سالب. لا تُدخل إحداثيات تقريبية إذا كان الطبق قد يصطدم بحدود الحركة.",
      "Decimal degrees: north/east positive, south/west negative. Use verified coordinates and physical travel limits."));
  latitude=edit(panel,tx("خط العرض Latitude (مثل 30.50)","Latitude, e.g. 30.50"));
  longitude=edit(panel,tx("خط الطول Longitude (مثل 47.80)","Longitude, e.g. 47.80"));
  satellite=new Spinner(this);
  panel.addView(text(tx("القمر المستهدف","Target satellite"),16,true));
  panel.addView(satellite,new LinearLayout.LayoutParams(-1,dp(54)));
  enabled=new CheckBox(this);
  enabled.setText(tx("تفعيل USALS لهذا القمر عند اختيار إحدى قنواته","Enable USALS for this satellite when tuning one of its channels"));
  panel.addView(enabled);
  state=text("",15,false);
  panel.addView(state);

  String location=prefs().getString("own_usals_location","");
  try{
   if(!location.isEmpty()){
    UsalsLocation savedLocation=UsalsLocation.parse(location);
    latitude.setText(Double.toString(savedLocation.latitude));
    longitude.setText(Double.toString(savedLocation.longitude));
   }
  }catch(Exception bad){state.setText(tx("إحداثيات قديمة غير صالحة؛ أعد الإدخال","Invalid saved coordinates; re-enter them"));}
  satellite.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onNothingSelected(AdapterView<?> parent){}
   @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){refresh(position);}
  });

  action(panel,tx("حفظ موقع الطبق وخيار USALS","Save dish position and USALS option"),()->{
   if(satellites.isEmpty())return;
   final UsalsLocation position;
   try{
    position=new UsalsLocation(Double.parseDouble(latitude.getText().toString().trim()),
      Double.parseDouble(longitude.getText().toString().trim()));
   }catch(Exception invalid){toast(tx("الإحداثيات غير صالحة","Invalid geographic coordinates"));return;}
   int index=satellite.getSelectedItemPosition();
   if(index<0||index>=satellites.size())return;
   ReceiverCatalog.Sat target=satellites.get(index);
   Runnable save=()->{
    try{
     prefs().edit().putString("own_usals_location",position.json())
      .putBoolean("own_usals_enabled:"+target.angle+":"+target.band,enabled.isChecked())
      .putInt("own_dvb_mapping_version",0).remove("verified_native_tuner_tp").remove("verified_native_tuner_rf").remove("own_dvb_profile_tag")
      .putString("motor_status",enabled.isChecked()?"CONFIGURED_NOT_MOVED":"DISABLED")
      .apply();
     refresh(index);
     toast(tx("تم الحفظ. لن يتحرك الطبق حتى تختار قناة من هذا القمر.",
       "Saved. The dish won't move until you tune a channel on this satellite."));
    }catch(Exception error){toast(tx("تعذر حفظ USALS","Could not save USALS")); }
   };
   if(enabled.isChecked()){
    new AlertDialog.Builder(this)
     .setTitle(tx("تفعيل تحريك الطبق","Enable motorized tuning"))
     .setMessage(tx("عند اختيار قناة من هذا القمر، سيحاول التونر تحريك الطبق حسب الموقع المدخل. تأكد من صحة الإحداثيات والحدود الميكانيكية.",
        "Future tuning to this satellite may move your dish. Confirm your location and mechanical limits are safe."))
     .setPositiveButton(tx("تفعيل وحفظ","Enable and save"),(d,w)->save.run())
     .setNegativeButton(tx("إلغاء","Cancel"),null).show();
   }else save.run();
  });

  LinearLayout controls=card(body);
  controls.addView(text(tx("التحكم الآمن","Safe controls"),21,true));
  action(controls,tx("إيقاف الموتور فوراً","STOP motor"),()->{
   work.execute(()->{
    boolean stopped=DvbTuner.stopMotor();
    runOnUiThread(()->{if(closed)return;
     prefs().edit().putString("motor_status",stopped?"STOP_CONFIRMED":"STOP_NOT_CONFIRMED").apply();
     state.setText(tx(stopped?"تم إرسال أمر الإيقاف وتأكيد الخدمة":"تعذر تأكيد إيقاف الموتور؛ استخدم زر STOP في الريموت إذا لزم",
       stopped?"Motor stop acknowledged":"Motor stop not acknowledged; use receiver emergency stop if needed"));
    });
   });
  });
  action(controls,tx("افتح قنوات IRAQhub لاختيار قمر وضبط الإشارة","Select an IRAQhub channel to tune the satellite"),
   ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)));
  action(controls,tx("قراءة إشارة التونر","Read tuner signal"),
   ()->startActivity(new Intent(this,SignalInformationActivity.class)));
  note(controls,tx("حفظ الموقع لا يحرك الطبق. بعد التفعيل تُرسَل حقول USALS عبر أمر Tuner/0002 عند تشغيل القناة، ثم ينتظر IRAQhub حتى دقيقتين لقفل الإشارة.",
    "Saving never moves the dish. Enabled USALS values are sent in the native Tuner/0002 tune request, with a lock wait up to two minutes."));

  work.execute(()->{
   ReceiverCatalog catalog=ReceiverCatalog.get(this);catalog.ensure();
   List<ReceiverCatalog.Sat> found=catalog.satellites();
   runOnUiThread(()->{
    if(closed)return;
    satellites=found;
    String[] options=new String[found.size()];
    int chosen=0;
    long requested=getIntent().getLongExtra("satellite",-1);
    for(int i=0;i<found.size();i++){
     options[i]=found.get(i).label();
     if(requested==found.get(i).id)chosen=i;
    }
    satellite.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
    if(found.size()>0)satellite.setSelection(chosen);
   });
  });
 }

 private EditText edit(LinearLayout panel,String hint){
  EditText input=new EditText(this);
  input.setSingleLine(true);
  input.setHint(hint);
  input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
  panel.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));
  return input;
 }

 private void refresh(int index){
  if(index<0||index>=satellites.size())return;
  ReceiverCatalog.Sat sat=satellites.get(index);
  enabled.setChecked(DvbTuner.usalsEnabled(this,sat.angle,sat.band));
  state.setText(tx("حالة الموتور: ","Motor state: ")+prefs().getString("motor_status","DISABLED")
      +"\n"+tx("الزاوية المدارية: ","Orbital position: ")+sat.angle/10.0+"°");
 }
 @Override protected void onDestroy(){closed=true;work.shutdownNow();super.onDestroy();}
}
