package com.smarthub.plus;

import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating in-player BISS editor, attached to the playback window (no draw-over-apps grant). */
final class BissKeyDialog {
 private BissKeyDialog(){}
 static void show(HubActivity host,ReceiverCatalog.Channel channel){
  if(channel==null){host.toast(host.tx("اختر قناة فضائية أولاً","Select a DVB channel first"));return;}
  LinearLayout form=host.col();
  form.setPadding(host.dp(16),host.dp(12),host.dp(16),host.dp(8));
  TextView title=host.text(channel.name+"\n"+channel.frequency+" "+channel.pol+
    " / "+channel.symbolRate+" · SID "+channel.sid,17,true);
  form.addView(title);
  TextView saved=host.text(BissKeyVault.has(host,channel)?
    host.tx("مفتاح BISS محفوظ ومشفّر لهذه القناة","An encrypted BISS key is stored for this channel"):
    host.tx("لا يوجد مفتاح BISS محفوظ لهذه القناة","No BISS key stored for this channel"),14,false);
  form.addView(saved);
  EditText input=new EditText(host);
  input.setSingleLine(true);
  input.setHint(host.tx("BISS-1 · 12 أو 16 رمز HEX","BISS-1 · 12 or 16 hexadecimal digits"));
  input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|
    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
  input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
  form.addView(input,new LinearLayout.LayoutParams(-1,host.dp(54)));
  TextView guide=host.text(host.tx(
    "قائمة عائمة داخل IRAQhub. تُخزَّن الشفرة محلياً بتشفير Android Keystore، ولا تُرسل إلى خادم. فتح القناة يحتاج أن تكون خدمة فك BISS مُفعّلة ومُصرّحاً بها على الرسيفر؛ LibVLC وحده لا يفك BISS.",
    "In-player menu. Keys are encrypted locally using Android Keystore and never sent to a server. Playback requires the receiver's authorised BISS descrambler; LibVLC alone cannot descramble BISS."),13,false);
  form.addView(guide);
  if(host instanceof DvbPlaybackActivity&&channel.tv==0){
   android.widget.Button trial=host.button(host.tx("تجربة تشغيل 4:2:2 باستخدام فك BISS الموجود بالرسيفر",
      "Try software 4:2:2 with receiver-authorised BISS"));
   form.addView(trial);
   trial.setOnClickListener(v->{
    new AlertDialog.Builder(host).setTitle(host.tx("تجربة 4:2:2","Try 4:2:2"))
     .setMessage(host.tx("هذه التجربة تعتمد على إخراج بث غير مشفر من جهاز الاستقبال. إذا بقيت حزم الفيديو مشفرة، لن يعمل LibVLC.",
       "This requires the receiver to output clear TS packets. LibVLC cannot decode still-scrambled DVB video."))
     .setPositiveButton(host.tx("تشغيل","Start"),(d,w)->{
      host.startActivity(new android.content.Intent(host,SoftwareVideoActivity.class)
        .putExtra("dvb_channel",channel.id));
      host.finish();
     }).setNegativeButton(host.tx("إلغاء","Cancel"),null).show();
   });
  }
  AlertDialog menu=new AlertDialog.Builder(host)
    .setTitle(host.tx("إدخال BISS · القناة الحالية","BISS key · current channel"))
    .setView(form)
    .setPositiveButton(host.tx("حفظ","Save"),null)
    .setNeutralButton(host.tx("حذف الشفرة","Delete key"),null)
    .setNegativeButton(host.tx("رجوع","Close"),null)
    .create();
  menu.setOnShowListener(ignored->{
    menu.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      try{
       BissKeyVault.save(host,channel,input.getText().toString().trim());
       input.setText("");
       host.toast(host.tx("حُفظت الشفرة مشفّرة لهذه القناة، ولم تُرسل إلى المصنع",
         "Key saved locally; it was not sent to firmware"));
       menu.dismiss();
      }catch(Exception invalid){
       String reason=invalid.getMessage()==null?"BISS_KEYSTORE_UNAVAILABLE":invalid.getMessage();
       input.setError(reason);
      }
    });
    menu.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->
      new AlertDialog.Builder(host).setTitle(host.tx("حذف شفرة BISS؟","Delete BISS key?"))
       .setMessage(channel.name)
       .setPositiveButton(host.tx("حذف","Delete"),(d,w)->{
        try{BissKeyVault.remove(host,channel);host.toast(host.tx("حُذفت الشفرة المحلية","Local key deleted"));menu.dismiss();}
        catch(Exception failure){host.toast("BISS_KEYSTORE_UNAVAILABLE");}
       }).setNegativeButton(host.tx("إلغاء","Cancel"),null).show());
  });
  menu.getWindow();menu.show();
  menu.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  input.clearFocus();
 }
}
