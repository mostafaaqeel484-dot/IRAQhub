package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authorised LAN ADB setup: user enters receiver IP:5556 explicitly.
 * TCP probe performs RSA ADB authentication and a fixed shell:id, not a
 * misleading port-open check. DNS-over-TLS is NOT ADB-over-TLS.
 */
public final class ReceiverHardwareBridgeActivity extends HubActivity {
 private TextView state,front,ipStatus;
 private EditText address;
 private final ExecutorService connectionWorker=Executors.newSingleThreadExecutor();
 private volatile boolean stopped,connecting;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout body=page(tx("إصلاح · ADB عبر IP:5556 ولوحة X5M",
     "Fix · ADB over IP:5556 and X5M panel"),
     tx("اتصال مصرح به وتشخيص USB؛ بدون افتراض أن 127.0.0.1 هو IP الرسيفر",
       "Authorised ADB and USB diagnostics; do not confuse 127.0.0.1 with the receiver's LAN IP"));
  LinearLayout adb=card(body);
  adb.addView(text("USB ADB / Developer Bridge",20,true));
  state=text("",14,false);adb.addView(state);

  LinearLayout network=card(body);
  network.addView(text(tx("اتصال ADB عبر عنوان IP","ADB connection over receiver IP"),20,true));
  note(network,tx("اكتب عنوان IP الحقيقي للجهاز والمنفذ، مثال 192.168.0.214:5556. لن نستعمل 127.0.0.1 تلقائياً.",
    "Enter the real receiver IP and port, for example 192.168.0.214:5556. No implicit localhost fallback."));
  address=field(network,tx("عنوان IP:PORT · عادةً 5556","Receiver IP:PORT · typically 5556"),
    prefs().getString("adb_ip_target",""));
  address.setSingleLine(true);
  address.setHint("192.168.0.214:5556");
  ipStatus=text("",14,false);network.addView(ipStatus);
  action(network,tx("الاتصال بـADB وفحص تفويض shell:id",
    "Connect to ADB and verify authorised shell:id"),this::connectIp);
  CheckBox automatic=new CheckBox(this);
  automatic.setText(tx("التحقق تلقائياً عند فتح اللانشر (بعد التفويض)",
     "Check ADB on launcher start (after authorisation)"));
  automatic.setChecked(prefs().getBoolean("adb_ip_auto_connect",false));
  automatic.setOnCheckedChangeListener((button,value)->{
   String raw=address.getText().toString().trim();
   if(value){
    try{
     AdbTcpEndpoint.parse(raw,5556);
     prefs().edit().putString("adb_ip_target",raw).apply();
    }catch(Exception invalid){
     automatic.setChecked(false);
     ipStatus.setText(tx("احفظ عنوان IP:5556 صحيحاً أولاً",
       "Enter a valid receiver IP:5556 first"));
     return;
    }
   }
   prefs().edit().putBoolean("adb_ip_auto_connect",value).apply();
  });
  network.addView(automatic);
  note(network,tx("النظام يطلب الموافقة على بصمة RSA إن كان المفتاح جديداً. إظهار 'متصل' يتطلب رد ADB مصادقاً، وليس نجاح TCP فقط. ADB TCP القديم على 5556 غير مشفر.",
    "Authorise the RSA fingerprint on the receiver. A connected status requires real ADB authentication, not an open TCP port. Legacy 5556 transport is NOT encrypted."));

  LinearLayout dns=card(body);
  dns.addView(text("DNS-over-TLS / ADB TLS",19,true));
  note(dns,tx("DNS الخاص (DNS-over-TLS) يخص أسماء النطاقات، ولا يشفّر ADB 5556. إذا تريد ADB مشفراً فعلياً، استخدم Wireless debugging مع اقتران TLS ومنفذ الاتصال المعلن؛ هذا العميل يدعم legacy ADB فقط.",
    "Android Private DNS (DNS-over-TLS) only protects DNS lookups. It does not encrypt ADB TCP 5556. Actual TLS ADB uses Wireless debugging pairing with its advertised port; this client supports legacy ADB only."));
  action(dns,tx("فتح إعدادات DNS الخاص (DNS-over-TLS)",
    "Open Android Private DNS settings"),this::privateDns);
  action(dns,tx("فتح خيارات المطوّر لاقتران TLS",
    "Developer options for ADB TLS pairing"),()->UsbAdbSetup.openDeveloperSettings(this));

  action(adb,tx("فتح إعدادات تصحيح USB","Open USB debugging setup"),
    ()->UsbAdbSetup.show(this));
  action(adb,tx("فتح خيارات المطوّر مباشرة","Open Developer options"),
    ()->UsbAdbSetup.openDeveloperSettings(this));

  LinearLayout panel=card(body);
  panel.addView(text(tx("ربط اللوحة الأمامية","Front-panel interface"),20,true));
  front=text("",13,false);panel.addView(front);
  action(panel,tx("إعادة فحص جهاز X5M واللوحة","Recheck X5M panel compatibility"),this::refresh);
  note(panel,tx("عند نجاح أمر تشغيل DVB، يُكتب اسم القناة في state.json. التحكم الفعلي بشاشة VFD يحتاج بروتوكول OEM مؤكد؛ ADB IP وحده لا يمنح تطبيق اللانشر صلاحيات shell.",
    "On accepted DVB play, channel identity is exported to state.json. OEM panel control requires its actual protocol; ADB connection does not change the APK's Android UID."));

  LinearLayout logos=card(body);
  logos.addView(text(tx("استيراد لوغوات USB ADB","USB ADB logo import"),20,true));
  note(logos,tx("المصدر المسموح الوصول له يمكن نسخه إلى حزمة ZIP مرتبطة بهويات DVB الدقيقة. لا نقرأ ملفات التطبيقات الأخرى المحمية تلقائياً.",
    "Accessible channel pictures can be packaged as a ZIP mapped to exact DVB identity. Protected app-private files are not bypassed."));
  action(logos,tx("فتح مستورد لوغوات ADB (ZIP)","Open ADB logo ZIP importer"),
    ()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(logos,tx("إصلاح مصادر البوسترات والشعارات","Fix poster and logo sources"),
    ()->startActivity(new Intent(this,ArtworkSourcesActivity.class)));
  refresh();
 }
 private void connectIp(){
  if(connecting)return;
  String target=address.getText().toString().trim();
  try{
   AdbTcpEndpoint.parse(target,5556);
  }catch(Exception invalid){
   ipStatus.setText(tx("عنوان IP أو المنفذ غير صحيح: ","Invalid IP/port: ")+
     invalid.getMessage());return;
  }
  prefs().edit().putString("adb_ip_target",target).apply();
  connecting=true;
  ipStatus.setText(tx("اتصال ADB قيد التحقق؛ وافق على طلب RSA من شاشة الرسيفر إذا ظهر…",
     "Authenticating ADB; approve the RSA trust prompt on receiver screen if shown…"));
  connectionWorker.execute(()->{
   AdbIpConnection.Result result=AdbIpConnection.test(this,target);
   prefs().edit().putString("adb_ip_status",result.status)
      .putString("adb_ip_detail",result.detail)
      .putString("adb_ip_verified_target",result.target)
      .putLong("adb_ip_checked_at",System.currentTimeMillis())
      .putBoolean("adb_ip_last_authorized",result.authorised).apply();
   runOnUiThread(()->{
    connecting=false;
    if(!stopped)refresh();
   });
  });
 }
 @Override protected void onResume(){super.onResume();refresh();}
 private void refresh(){
  if(state==null||front==null||ipStatus==null)return;
  state.setText(UsbAdbSetup.status(this)+"\n"+
    tx("USB debugging لا يشغّل تلقائياً المنفذ TCP 5556.",
       "USB debugging does not automatically start the ADB TCP 5556 listener."));
  String status=prefs().getString("adb_ip_status","NOT_TESTED");
  String detail=prefs().getString("adb_ip_detail","");
  long age=System.currentTimeMillis()-prefs().getLong("adb_ip_checked_at",0);
  String freshness=age>=0&&age<300000?
    tx(" · تم التحقق قبل أقل من 5 دقائق"," · checked within 5 minutes"):
    tx(" · نتيجة قديمة، أعد الفحص"," · old result, reconnect to verify");
  ipStatus.setText("ADB IP: "+status+freshness+"\n"+detail+"\n"+
    prefs().getString("adb_ip_verified_target",""));
  front.setText(FrontPanelBridge.diagnostics(this)+"\n"+
    tx("آخر تسليم: ","Last handoff: ")+
    prefs().getLong("front_panel_handoff_at",0));
 }
 private void privateDns(){
  try{startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));}
  catch(Exception unavailable){
   try{startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}
   catch(Exception ignored){ipStatus.setText(tx("إعداد DNS غير متاح في هذه الروم",
       "Private DNS screen unavailable on this firmware"));}
  }
 }
 @Override protected void onDestroy(){
  stopped=true;connectionWorker.shutdownNow();super.onDestroy();
 }
}
