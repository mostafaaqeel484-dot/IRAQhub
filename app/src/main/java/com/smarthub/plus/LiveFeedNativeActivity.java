package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Remote-first, native RX6 satellite feeds directory. This is NOT a browser.
 * Reads public frequencies / service labels only. No BISS keys are displayed,
 * cached or injected into the tuner. The independent site has no promised API.
 */
public class LiveFeedNativeActivity extends HubActivity {
 private static final String HOME="https://live-feed.net/";
 private static final String PUBLIC_LIST=HOME+"?show_off=1";
 private static final int MAX_DOWNLOAD=2_200_000;
 private static final int GOLD=0xffffc96c;
 private static final int MUTED_TEXT=0xff9aadc4;
 private static final OkHttpClient HTTP=new OkHttpClient.Builder()
   .connectTimeout(8,TimeUnit.SECONDS)
   .readTimeout(15,TimeUnit.SECONDS)
   .callTimeout(24,TimeUnit.SECONDS)
   .followRedirects(false).followSslRedirects(false).build();
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final ArrayList<LiveFeedTextParser.Entry> all=new ArrayList<>();
 private final ArrayList<LiveFeedTextParser.Entry> visible=new ArrayList<>();
 private final FeedAdapter adapter=new FeedAdapter();
 private ListView list;
 private EditText query;
 private TextView status,count;
 private Button refresh,live,only422,satellite;
 private boolean destroyed,loading,liveOnly,format422;
 private String satelliteFilter="";

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout root=col();
  root.setBackgroundColor(0xff070c13);
  root.setPadding(dp(20),dp(12),dp(20),dp(12));
  root.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);

  LinearLayout header=row();
  LinearLayout names=col();
  TextView title=text("IRAQhub  ·  FEEDS",25,true);
  title.setTextColor(GOLD);
  names.addView(title);
  TextView subtitle=text(tx("دليل الفيدات الفضائية · بيانات عامة من live-feed.net",
                            "Satellite feed directory · public data from live-feed.net"),12,false);
  subtitle.setTextColor(MUTED_TEXT);
  names.addView(subtitle);
  header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
  Button source=button(tx("المصدر","Source"));
  source.setOnClickListener(v->openSource());
  header.addView(source,new LinearLayout.LayoutParams(dp(116),dp(46)));
  Button back=button(tx("رجوع","Back"));
  back.setOnClickListener(v->finish());
  header.addView(back,new LinearLayout.LayoutParams(dp(105),dp(46)));
  root.addView(header);

  LinearLayout actions=row();
  actions.setPadding(0,dp(12),0,dp(9));
  refresh=button(tx("تحديث","Refresh"));
  refresh.setOnClickListener(v->refresh());
  actions.addView(refresh,new LinearLayout.LayoutParams(dp(118),dp(45)));
  live=button("");
  live.setOnClickListener(v->{liveOnly=!liveOnly;updateFilters();});
  actions.addView(live,new LinearLayout.LayoutParams(dp(144),dp(45)));
  only422=button("");
  only422.setOnClickListener(v->{format422=!format422;updateFilters();});
  actions.addView(only422,new LinearLayout.LayoutParams(dp(134),dp(45)));
  satellite=button(tx("كل الأقمار","Satellites"));
  satellite.setOnClickListener(v->chooseSatellite());
  actions.addView(satellite,new LinearLayout.LayoutParams(dp(172),dp(45)));
  root.addView(actions);

  LinearLayout searchBar=row();
  query=new EditText(this);
  query.setSingleLine(true);
  query.setTextSize(font(16));
  query.setTextColor(0xfff6f6f8);
  query.setHintTextColor(MUTED_TEXT);
  query.setHint(tx("بحث: اسم الفيد أو القمر أو التردد","Find feed, satellite or frequency"));
  query.setBackground(shape(0xff172234,12));
  query.setPadding(dp(16),dp(8),dp(16),dp(8));
  query.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){updateFilters();}
   public void afterTextChanged(Editable e){}
  });
  searchBar.addView(query,new LinearLayout.LayoutParams(0,dp(48),1));
  count=text("",13,true);
  count.setTextColor(GOLD);
  count.setPadding(dp(14),0,dp(8),0);
  searchBar.addView(count);
  root.addView(searchBar);

  status=text(tx("البيانات المخزنة تظهر فوراً؛ جارٍ التحقق من المصدر…",
                 "Showing saved entries while refreshing public data…"),13,false);
  status.setTextColor(MUTED_TEXT);
  status.setPadding(0,dp(10),0,dp(8));
  root.addView(status);
  list=new ListView(this);
  list.setBackgroundColor(0xff070c13);
  list.setDivider(null);
  list.setCacheColorHint(0xff070c13);
  list.setScrollingCacheEnabled(false);
  list.setAdapter(adapter);
  list.setSelector(RxSkin.focus(this));
  list.setOnItemClickListener((parent,view,position,id)->{
   if(position>=0&&position<visible.size())showDetails(visible.get(position));
  });
  list.setOnItemLongClickListener((parent,view,position,id)->{
   if(position>=0&&position<visible.size()){copy(visible.get(position));return true;}
   return false;
  });
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);
  updateFilters();
  readSaved();
  refresh();
 }

 private void chooseSatellite(){
  LinkedHashSet<String> satellites=new LinkedHashSet<>();
  for(LiveFeedTextParser.Entry f:all)if(!f.orbital.isEmpty())satellites.add(f.orbital);
  ArrayList<String> names=new ArrayList<>(satellites);
  java.util.Collections.sort(names);
  names.add(0,tx("كل الأقمار","All satellites"));
  new AlertDialog.Builder(this)
    .setTitle(tx("اختر القمر","Choose satellite"))
    .setItems(names.toArray(new String[0]),(dialog,which)->{
     satelliteFilter=which==0?"":names.get(which);
     updateFilters();
    }).show();
 }
 private void updateFilters(){
  if(query==null||list==null)return;
  live.setText(liveOnly?tx("● الشغالة فقط","● ON only"):tx("الكل ON/OFF","All ON/OFF"));
  only422.setText(format422?"● 4:2:2":"4:2:2");
  satellite.setText(satelliteFilter.isEmpty()?tx("كل الأقمار","All satellites"):satelliteFilter);
  String q=query.getText().toString().trim().toLowerCase(Locale.ROOT);
  visible.clear();
  for(LiveFeedTextParser.Entry f:all){
   if(liveOnly&&!f.isLive())continue;
   if(format422&&!f.is422())continue;
   if(!satelliteFilter.isEmpty()&&!satelliteFilter.equals(f.orbital))continue;
   String hay=(f.satellite+" "+f.orbital+" "+f.name+" "+f.frequencyLabel()+" "+f.codec)
     .toLowerCase(Locale.ROOT);
   if(!q.isEmpty()&&!hay.contains(q))continue;
   visible.add(f);
  }
  count.setText(visible.size()+" / "+all.size());
  adapter.notifyDataSetChanged();
 }
 private void openSource(){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(HOME)));}
  catch(ActivityNotFoundException e){toast(tx("لا يوجد متصفح على الجهاز","No browser available"));}
 }
 private void showDetails(LiveFeedTextParser.Entry f){
  String body=tx("القمر: ","Satellite: ")+(f.satellite.isEmpty()?"—":f.satellite)+
    "\n"+tx("المدار: ","Orbital: ")+f.orbital+
    "\n"+tx("التردد: ","Frequency: ")+f.frequencyLabel()+
    "\n"+tx("الفيديو: ","Video: ")+(f.codec.isEmpty()?"—":f.codec)+
    "\n"+tx("نظام الألوان: ","Chroma: ")+(f.sampling.isEmpty()?"—":f.sampling)+
    "\n"+tx("الحالة المنشورة: ","Reported state: ")+(f.status.isEmpty()?"—":f.status)+
    "\n\n"+tx("المعلومات من موقع مستقل وليست تأكيداً لقفل الموالف أو تشغيل 4:2:2.",
          "External metadata, not confirmation of tuner lock or 4:2:2 playback.");
  new AlertDialog.Builder(this).setTitle(f.name.isEmpty()?f.frequencyLabel():f.name)
    .setMessage(body)
    .setPositiveButton(tx("نسخ بيانات التردد","Copy RF"),(d,n)->copy(f))
    .setNegativeButton(tx("إغلاق","Close"),null).show();
 }
 private void copy(LiveFeedTextParser.Entry f){
  ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
  if(manager!=null)manager.setPrimaryClip(ClipData.newPlainText("Feed RF",f.frequencyLabel()));
  toast(tx("تم نسخ بيانات التردد","Frequency copied"));
 }
 private File saved(){return new File(getFilesDir(),"live-feed-public-directory.json");}
 private void readSaved(){
  io.execute(()->{
   ArrayList<LiveFeedTextParser.Entry> loaded=new ArrayList<>();
   try{
    File file=saved();
    if(file.isFile()&&file.length()>0&&file.length()<150_000){
     byte[] bytes=new byte[(int)file.length()];
     try(InputStream in=new java.io.FileInputStream(file)){
      int n=0,k;while(n<bytes.length&&(k=in.read(bytes,n,bytes.length-n))>0)n+=k;
      if(n!=bytes.length)throw new java.io.IOException("PARTIAL_FEEDS_CACHE");
     }
     JSONArray data=new JSONArray(new String(bytes,StandardCharsets.UTF_8));
     for(int i=0;i<Math.min(320,data.length());i++){
      JSONObject x=data.optJSONObject(i);if(x==null)continue;
      LiveFeedTextParser.Entry f=new LiveFeedTextParser.Entry();
      f.satellite=x.optString("s");f.orbital=x.optString("o");
      f.name=x.optString("n");f.frequency=x.optString("f");
      f.polarity=x.optString("p");f.symbolRate=x.optString("r");
      f.codec=x.optString("c");f.sampling=x.optString("m");
      f.status=x.optString("t");
      if(f.frequency.matches("[0-9]{4,5}")&&f.symbolRate.matches("[0-9]{3,5}"))loaded.add(f);
     }
    }
   }catch(Exception ignored){}
   runOnUiThread(()->{
    if(destroyed)return;
    if(all.isEmpty()&&!loaded.isEmpty()){
     all.addAll(loaded);updateFilters();
     status.setText(tx("عرض آخر قائمة محفوظة على الجهاز","Showing last saved feed list"));
    }
   });
  });
 }
 private void persist(List<LiveFeedTextParser.Entry> loaded){
  try{
   JSONArray data=new JSONArray();
   for(LiveFeedTextParser.Entry f:loaded){
    JSONObject x=new JSONObject();
    x.put("s",f.satellite).put("o",f.orbital).put("n",f.name)
     .put("f",f.frequency).put("p",f.polarity).put("r",f.symbolRate)
     .put("c",f.codec).put("m",f.sampling).put("t",f.status);
    data.put(x);
   }
   byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>150_000)return;
   AtomicFile file=new AtomicFile(saved());
   FileOutputStream out=file.startWrite();
   try{out.write(bytes);file.finishWrite(out);}
   catch(Exception failed){file.failWrite(out);}
  }catch(Exception ignored){}
 }
 private void refresh(){
  if(loading||destroyed)return;
  loading=true;refresh.setEnabled(false);
  status.setText(tx("جارٍ تحميل بيانات الفيدات العامة…","Refreshing public feed metadata…"));
  io.execute(()->{
   List<LiveFeedTextParser.Entry> parsed=new ArrayList<>();
   String error="";
   try{
    Request request=new Request.Builder().url(PUBLIC_LIST)
      .header("Accept","text/html").header("User-Agent","IRAQhub-Turbo/4.0").build();
    try(Response response=HTTP.newCall(request).execute()){
     if(!response.isSuccessful()||response.body()==null)
      throw new java.io.IOException("HTTP "+response.code());
     ByteArrayOutputStream out=new ByteArrayOutputStream();
     try(InputStream input=response.body().byteStream()){
      byte[] buffer=new byte[16384];int n;
      while((n=input.read(buffer))!=-1){
       if(out.size()+n>MAX_DOWNLOAD)throw new java.io.IOException("FEED_PAGE_TOO_LARGE");
       out.write(buffer,0,n);
      }
     }
     String plain=Html.fromHtml(out.toString("UTF-8"),Html.FROM_HTML_MODE_LEGACY).toString();
     parsed=LiveFeedTextParser.parse(plain);
     if(parsed.isEmpty())throw new java.io.IOException("SITE_FORMAT_CHANGED_OR_EMPTY");
     persist(parsed); // sanitized data only; never original HTML / BISS keys
    }
   }catch(Exception e){error=e.getMessage()==null?"NETWORK_UNAVAILABLE":e.getMessage();}
   final ArrayList<LiveFeedTextParser.Entry> newData=new ArrayList<>(parsed);
   final String failure=error;
   runOnUiThread(()->{
    if(destroyed)return;
    loading=false;refresh.setEnabled(true);
    if(!newData.isEmpty()){
     all.clear();all.addAll(newData);updateFilters();
     status.setText(tx("الفيدات المعلنة من المصدر · اضغط OK للتفاصيل",
                       "Public feeds · press OK for metadata"));
    }else{
     status.setText(tx("تعذر تحديث المصدر، البيانات المحفوظة باقية: ",
                       "Update unavailable; saved entries kept: ")+failure);
    }
   });
  });
 }
 private static final class Holder{
  LinearLayout outer;
  TextView name,satellite,rf,technical,state;
 }
 private final class FeedAdapter extends BaseAdapter {
  public int getCount(){return visible.size();}
  public Object getItem(int i){return visible.get(i);}
  public long getItemId(int i){return i;}
  public View getView(int position,View old,ViewGroup parent){
   Holder h;
   if(old!=null&&old.getTag() instanceof Holder)h=(Holder)old.getTag();
   else{
    h=new Holder();
    h.outer=col();h.outer.setPadding(dp(12),dp(11),dp(12),dp(11));
    h.outer.setBackground(cardBackground());
    LinearLayout top=row();
    h.name=text("",18,true);h.name.setTextColor(0xfff2f4f8);
    h.name.setSingleLine(true);
    top.addView(h.name,new LinearLayout.LayoutParams(0,-2,1));
    h.state=text("",13,true);h.state.setGravity(Gravity.CENTER);
    top.addView(h.state,new LinearLayout.LayoutParams(dp(86),-2));
    h.outer.addView(top);
    h.satellite=text("",13,false);h.satellite.setTextColor(0xffb9c8d8);
    h.outer.addView(h.satellite);
    LinearLayout lower=row();
    h.rf=text("",19,true);h.rf.setTextColor(GOLD);
    lower.addView(h.rf,new LinearLayout.LayoutParams(0,-2,1));
    h.technical=text("",14,true);h.technical.setGravity(Gravity.END);
    lower.addView(h.technical,new LinearLayout.LayoutParams(dp(230),-2));
    h.outer.addView(lower);
    LinearLayout wrapper=col();wrapper.setPadding(0,dp(3),0,dp(4));
    wrapper.addView(h.outer,new LinearLayout.LayoutParams(-1,-2));
    wrapper.setTag(h);
    old=wrapper;
   }
   LiveFeedTextParser.Entry f=visible.get(position);
   h.name.setText(f.name.isEmpty()?tx("فيد فضائي","Satellite Feed"):f.name);
   h.state.setText(f.status.isEmpty()?"—":f.status);
   h.state.setTextColor(f.isLive()?0xff62f2b2:0xffdf8798);
   h.satellite.setText("◈  "+(f.satellite.isEmpty()?tx("القمر غير محدد","Satellite not specified"):f.satellite)+
      (f.orbital.isEmpty()?"":"  ·  "+f.orbital));
   h.rf.setText(f.frequencyLabel());
   h.technical.setText((f.codec.isEmpty()?"":f.codec+"  ·  ")+
      (f.sampling.isEmpty()?"—":f.sampling));
   h.technical.setTextColor(f.is422()?GOLD:0xff9ac7dc);
   return old;
  }
  private GradientDrawable cardBackground(){
   GradientDrawable d=new GradientDrawable();
   d.setColor(0xff131e30);d.setCornerRadius(dp(10));
   d.setStroke(dp(1),0xff725c3b);
   return d;
  }
 }
 @Override protected void onDestroy(){
  destroyed=true;io.shutdownNow();super.onDestroy();
 }
}
