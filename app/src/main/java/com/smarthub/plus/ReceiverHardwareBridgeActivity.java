package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Realistic receiver tools. ADB is an external authorised shell, NOT APK root. */
public final class ReceiverHardwareBridgeActivity extends HubActivity {
 private TextView state,front;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("إصلاح · USB ADB ولوحة X5M",
     "Fix · USB ADB and X5M front panel"),
     tx("فحص اتصال المطوّر ومخارج اللوحة واستيراد لوغوات هوية DVB",
       "Developer state, front-panel detection and exact DVB logo import"));
  LinearLayout adb=card(body);
  adb.addView(text("USB ADB / Developer Bridge",20,true));
  state=text("",14,false);adb.addView(state);
  action(adb,tx("فتح نافذة إعداد تصحيح USB","Open USB debugging setup"),
    ()->UsbAdbSetup.show(this));
  action(adb,tx("فتح إعدادات المطوّر مباشرة","Open Developer options"),
    ()->UsbAdbSetup.openDeveloperSettings(this));
  note(adb,tx("التطبيق لا يرث صلاحية adb shell بمجرد تفعيل التصحيح؛ يلزم كمبيوتر أو هاتف مصرح له، ولا يتطلب هذا الاتصال عبر الواي فاي.",
   "Turning on USB debugging does not grant the APK shell or root. Authorise a wired ADB host; Wi-Fi is not required."));

  LinearLayout panel=card(body);
  panel.addView(text(tx("ربط اللوحة الأمامية","Front-panel interface"),20,true));
  front=text("",13,false);panel.addView(front);
  action(panel,tx("إعادة فحص جهاز X5M واللوحة","Recheck X5M panel compatibility"),
    this::refresh);
  note(panel,tx("عند نجاح استقبال أمر تشغيل DVB، يكتب IRAQhub اسم القناة وهويتها في ملف state.json الخارجي. أدوات ADB المصرح بها يمكنها قراءته. التحكم الفعلي بالـ VFD يحتاج اكتشاف بروتوكول OEM، لذلك لم نخمن أوامر قد تضر الجهاز.",
   "When DVB play is accepted, IRAQhub publishes channel identity in state.json. An authorised ADB host may read it. Writing to VFD requires a verified vendor protocol and permissions."));

  LinearLayout logos=card(body);
  logos.addView(text(tx("استيراد لوغوات USB ADB","USB ADB logo import"),20,true));
  note(logos,tx("صدّر الصور المسموح قراءتها من الرسيفر إلى الكمبيوتر عبر USB، وجهّز ZIP يحتوي manifest.json ومجلد logos/، ثم اختر الملف داخل IRAQhub. لن ننسخ ملفات تطبيقات أخرى المحمية.",
   "Export only accessible receiver artwork using wired ADB. Make a ZIP with manifest.json and logos/. Import with the system picker. App-private protected images cannot be bypassed."));
  action(logos,tx("فتح استيراد لوغوات USB ADB (ZIP)","Open ADB logo ZIP importer"),
    ()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(logos,tx("إصلاح مصادر بوسترات EPG والشعارات","Fix EPG posters and logos"),
    ()->startActivity(new Intent(this,ArtworkSourcesActivity.class)));
  refresh();
 }
 @Override protected void onResume(){super.onResume();refresh();}
 private void refresh(){
  if(state==null||front==null)return;
  state.setText(UsbAdbSetup.status(this)+"\n"+
    tx("المستخدم الحالي للتطبيق: صلاحيات Android العادية؛ adb shell ليس مخولاً للتطبيق",
       "APK is a regular Android UID, not an authorised adb shell session"));
  front.setText(FrontPanelBridge.diagnostics(this)+"\n"+
    tx("آخر تسليم: ","Last handoff: ")+
    prefs().getLong("front_panel_handoff_at",0));
 }
}
