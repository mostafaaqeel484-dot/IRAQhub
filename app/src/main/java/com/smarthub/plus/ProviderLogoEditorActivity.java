package com.smarthub.plus;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Provider-only artwork editor. Long-press entrypoint from the catalogue.
 * Manual channel associations persist across channel list refreshes because
 * each selected service is keyed by its physical DVB identity. */
public final class ProviderLogoEditorActivity extends HubActivity {
 private static final int PICK_IMAGE=9194;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final LinkedHashSet<String> selectedDvb=new LinkedHashSet<>();
 private volatile boolean closed;
 private EditText name,country,remote;
 private CheckBox currentChannel;
 private TextView status;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("تخصيص شعار المزوّد وقنواته","Edit provider logo and channel assignments"),
    tx("تغيير شعار المزوّد لا يبدّل شعار CHMAX للقناة أو بيانات DVB.",
      "Provider badge and channel image are independent."));
  SatelliteState playing=SatelliteState.read(this);
  name=field(body,tx("اسم المزوّد","Provider name"),
    getIntent().getStringExtra("provider_name")==null?"":getIntent().getStringExtra("provider_name"));
  country=field(body,tx("رمز الدولة (مثال PL / IQ / AL)","ISO country code"),
    getIntent().getStringExtra("provider_country")==null?"":getIntent().getStringExtra("provider_country"));
  currentChannel=check(body,tx("طبّق الصورة على القناة الحالية فقط",
     "Artwork only for the currently playing channel"),false);
  body.addView(text(tx("القناة الحالية: ","Current: ")+
    (playing.channelName.isEmpty()?tx("غير محددة","None"):playing.channelName),14,false));
  remote=field(body,tx("رابط HTTPS مباشر لصورة الشعار","Direct HTTPS provider image URL"),"");
  action(body,tx("حفظ شعار المزوّد من رابط الإنترنت","Import provider logo from URL"),
    ()->importLink());
  action(body,tx("اختيار صورة شعار من USB أو الجهاز","Choose provider image from USB/storage"),()->{
   Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   picker.addCategory(Intent.CATEGORY_OPENABLE);
   picker.setType("image/*");
   launchDocument(picker,PICK_IMAGE);
  });
  action(body,tx("إضافة قنوات للمزوّد","Select multiple associated DVB channels"),
    ()->chooseChannels());
  action(body,tx("حفظ انتماء القنوات المحددة للمزوّد","Save chosen channel assignments"),
    ()->saveChannels());
  action(body,tx("إلغاء ربط القنوات المحددة والعودة للتلقائي",
     "Unassign selected channels and restore automatic provider"),()->{
   final ArrayList<String> selected=new ArrayList<>(selectedDvb);
   if(selected.isEmpty()){result(tx("حدد قنوات أولاً","Select DVB services first"));return;}
   io.execute(()->{
    try{
     int undone=ProviderLogoOverrides.clearChannels(this,selected);
     runOnUiThread(()->selectedDvb.removeAll(selected));
     result(tx("أُلغي ربط قنوات: ","Unassigned channels: ")+undone);
    }catch(Exception e){result(tx("تعذر إلغاء الربط","Cannot remove assignments"));}
   });
  });
  action(body,tx("حذف التخصيص اليدوي المحدد","Remove this manual provider override"),()->{
   String provider=name.getText().toString().trim();
   String iso=country.getText().toString().trim().toUpperCase(Locale.ROOT);
   String physical=currentChannel.isChecked()?playing.channelId:"";
   io.execute(()->{
    String message;
    try{
     ProviderLogoOverrides.remove(this,provider,iso,physical);
     message=tx("أُزيل الربط اليدوي","Manual assignment removed");
    }catch(Exception error){message=tx("فشل حذف الربط","Unable to remove override");}
    result(message);
   });
  });
  status=text(tx("الشعارات والتخصيصات المحلية: ","Manual provider entries: ")+
    ProviderLogoOverrides.count(this),15,false);
  body.addView(status);
 }
 private String provider(){return name.getText().toString().trim();}
 private String iso(){return country.getText().toString().trim().toUpperCase(Locale.ROOT);}
 private String channelScope(){
  return currentChannel.isChecked()?SatelliteState.read(this).channelId:"";
 }
 private void result(String message){runOnUiThread(()->{
  if(!closed&&status!=null)status.setText(message);
 });}
 private void importLink(){
  String url=remote.getText().toString().trim();
  if(url.isEmpty()){status.setText(tx("أدخل رابطاً مباشراً للصورة","Paste a direct image URL"));return;}
  final String label=provider(),cc=iso(),physical=channelScope();
  status.setText(tx("جارٍ جلب الشعار من HTTPS…","Importing HTTPS artwork…"));
  io.execute(()->{
   try{
    ProviderImageUrlImport.add(this,label,cc,physical,url);
    result(tx("حُفظ شعار المزوّد من الرابط","Provider logo imported from URL"));
   }catch(Exception rejected){
    result(tx("فشل جلب الصورة: ","Image import failed: ")+rejected.getClass().getSimpleName());
   }
  });
 }
 private void chooseChannels(){
  io.execute(()->{
   try{
    List<ReceiverCatalog.Sat> satellites=ReceiverCatalog.get(this).satellites();
    runOnUiThread(()->{
     if(closed||isFinishing())return;
     if(satellites.isEmpty()){
      status.setText(tx("لا توجد أقمار في فهرس الرسيفر","No satellites in receiver catalog"));
      return;
     }
     String[] labels=new String[satellites.size()];
     for(int i=0;i<labels.length;i++)labels[i]=satellites.get(i).label();
     new android.app.AlertDialog.Builder(this).setTitle(tx("قائمة الأقمار","Satellite list"))
      .setItems(labels,(dialog,index)->loadChannels(satellites.get(index),"",0))
      .setNegativeButton(tx("إلغاء","Cancel"),null).show();
    });
   }catch(Exception failure){result(tx("تعذرت قراءة الأقمار","Cannot read satellites"));}
  });
 }
 private void loadChannels(ReceiverCatalog.Sat satellite,String query,int offset){
  io.execute(()->{
   try{
    ReceiverCatalog catalog=ReceiverCatalog.get(this);
    List<ReceiverCatalog.Channel> page=catalog.channels(query,satellite.id,-1,false,false,offset);
    Set<String> ambiguous=catalog.ambiguousPhysicalKeys();
    ArrayList<ReceiverCatalog.Channel> eligible=new ArrayList<>();
    for(ReceiverCatalog.Channel row:page)if(!ambiguous.contains(row.physical()))eligible.add(row);
    runOnUiThread(()->{
     if(!closed&&!isFinishing())showChannelList(satellite,query,offset,page.size()==120,eligible);
    });
   }catch(Exception failure){result(tx("تعذرت قراءة القنوات","Cannot read channels"));}
  });
 }
 private void showChannelList(ReceiverCatalog.Sat satellite,String query,int offset,boolean more,
   List<ReceiverCatalog.Channel> rows){
  String[] labels=new String[rows.size()];
  boolean[] checked=new boolean[rows.size()];
  for(int i=0;i<rows.size();i++){
   ReceiverCatalog.Channel row=rows.get(i);
   labels[i]=row.name+" · "+row.frequency+" "+row.pol;
   checked[i]=selectedDvb.contains("DVB:"+row.physical());
  }
  Runnable keep=()->{
   for(int i=0;i<rows.size();i++){
    String key="DVB:"+rows.get(i).physical();
    if(checked[i])selectedDvb.add(key);else selectedDvb.remove(key);
   }
   status.setText(tx("القنوات المحددة: ","Selected services: ")+selectedDvb.size());
  };
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this)
   .setTitle(satellite.label()+" · "+(offset/120+1)+" · "+selectedDvb.size())
   .setPositiveButton(tx("اعتماد التحديد","Keep selection"),(dialog,which)->keep.run())
   .setNeutralButton(tx("بحث","Search"),(dialog,which)->{
    keep.run();
    EditText input=new EditText(this);
    input.setSingleLine(true);
    input.setText(query);
    input.setHint(tx("اسم القناة أو التردد","Channel name or frequency"));
    new android.app.AlertDialog.Builder(this).setTitle(satellite.label()).setView(input)
     .setPositiveButton(tx("عرض القنوات","Show channels"),
       (d,w)->loadChannels(satellite,input.getText().toString().trim(),0))
     .setNegativeButton(tx("رجوع","Back"),(d,w)->loadChannels(satellite,query,offset)).show();
   });
  if(rows.isEmpty())builder.setMessage(tx("لا توجد قنوات قابلة للربط في هذه الصفحة",
    "No assignable channels on this page"));
  else builder.setMultiChoiceItems(labels,checked,(dialog,index,on)->{
   checked[index]=on;
   keep.run();
   ((android.app.AlertDialog)dialog).setTitle(satellite.label()+" · "+(offset/120+1)+" · "+selectedDvb.size());
  });
  if(more)builder.setNegativeButton(tx("التالي","Next page"),(dialog,which)->{
   keep.run();
   loadChannels(satellite,query,offset+120);
  });
  else if(offset>0)builder.setNegativeButton(tx("السابق","Previous page"),(dialog,which)->{
   keep.run();
   loadChannels(satellite,query,offset-120);
  });
  builder.show();
 }
 private void saveChannels(){
  final String provider=provider(),iso=iso();
  final ArrayList<String> choices=new ArrayList<>(selectedDvb);
  if(choices.isEmpty()){status.setText(tx("حدد قنوات أولاً","Select channels first"));return;}
  status.setText(tx("جارٍ حفظ الربط…","Saving provider channel associations…"));
  io.execute(()->{
   try{
    int count=ProviderLogoOverrides.assignChannels(this,provider,iso,choices);
    result(tx("تم ربط قنوات بالمزوّد: ","Assigned DVB services: ")+count);
   }catch(Exception rejected){result(tx("فشل الربط: ","Assignment rejected: ")+
     rejected.getClass().getSimpleName());}
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;
  final String label=provider(),iso=iso(),physical=channelScope();
  final Uri image=data.getData();
  status.setText(tx("جارٍ حفظ صورة الشعار…","Saving provider image…"));
  io.execute(()->{
   try{
    ProviderLogoOverrides.add(this,label,iso,physical,image);
    result(tx("تم حفظ شعار المزوّد","Provider logo saved")+" · "+label);
   }catch(Exception rejected){result(tx("لم تُقبل الصورة","Image rejected: ")+
     rejected.getClass().getSimpleName());}
  });
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
