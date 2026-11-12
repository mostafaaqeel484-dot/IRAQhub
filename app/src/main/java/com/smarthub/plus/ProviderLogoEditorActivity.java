package com.smarthub.plus;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.*;

/** Independent from CHMAX and internet channel-logo switches. */
public final class ProviderLogoEditorActivity extends HubActivity {
 private static final int PICK_IMAGE=9194;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private EditText name,country;
 private CheckBox currentChannel;
 private TextView status;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("إضافة مزوّد وشعاره","Add provider and logo"),
    tx("شعار المزوّد يظهر بمكانه بالـInfoBar. لا يُضاف كشعار قناة ولا يُرسل إلى CHMAX.",
      "Provider badge is separate from channel artwork and CHMAX storage."));
  SatelliteState playing=SatelliteState.read(this);
  name=field(body,tx("اسم المزوّد","Provider name"),"");
  country=field(body,tx("رمز الدولة ISO: مثال AL أو PL أو IQ","Two-letter country code"),"");
  currentChannel=check(body,tx("اربط المزوّد بالقناة الحالية فقط","Assign to currently playing DVB channel only"),
    false);
  body.addView(text(tx("القناة الحالية: ","Current channel: ")+
    (playing.channelName.isEmpty()?tx("غير محددة","None"):playing.channelName),14,false));
  action(body,tx("اختيار شعار PNG أو JPG أو WEBP","Choose provider PNG/JPG/WEBP"),()->{
   Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");
   launchDocument(intent,PICK_IMAGE);
  });
  action(body,tx("إزالة الربط اليدوي المحدد","Remove the selected manual provider logo"),()->{
   String label=name.getText().toString().trim();
   String iso=country.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
   String physical=currentChannel.isChecked()?playing.channelId:"";
   try{
    ProviderLogoOverrides.remove(this,label,iso,physical);
    status.setText(tx("أُزيل الربط اليدوي","Manual provider art unlinked"));
   }catch(Exception failed){status.setText(tx("فشل الحذف","Unable to remove override"));}
  });
  status=text(tx("الشعارات اليدوية المسجلة: ","Saved provider overrides: ")+
    ProviderLogoOverrides.count(this),15,false);
  body.addView(status);
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  if(code!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;
  String label=name.getText().toString().trim();
  String iso=country.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
  SatelliteState playing=SatelliteState.read(this);
  String physical=currentChannel.isChecked()?playing.channelId:"";
  Uri image=data.getData();
  status.setText(tx("جارٍ حفظ الصورة محلياً…","Saving provider logo locally…"));
  io.execute(()->{
   String message;
   try{
    ProviderLogoOverrides.add(this,label,iso,physical,image);
    message=tx("تم حفظ شعار المزوّد بنجاح","Provider logo saved")+
      " · "+label+" · "+iso;
   }catch(Exception invalid){message=tx("لم يُقبل الشعار: ","Logo rejected: ")+
      invalid.getClass().getSimpleName();}
   final String output=message;
   runOnUiThread(()->{if(!closed)status.setText(output);});
  });
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
