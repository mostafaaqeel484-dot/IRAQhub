package com.smarthub.plus;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fix 12: separate, safe sources for TV episodes, sports badges and
 * explicitly selected Commons images with real author/licence metadata. */
public final class ArtworkSourcesActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private TextView status;
 private ImageView sportsPreview;
 private volatile boolean closed;

 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root=page(tx("إصلاح 12 · الشعارات والبوسترات",
    "Fix 12 · Logos & Posters"),
    tx("مصادر الصور الموثوقة فقط؛ لا تخمين لشعارات القنوات ولا بحث متكرر أثناء فك 4:2:2.",
      "Verified image sources only. No channel-logo guessing or network lookups during 4:2:2."));
  LinearLayout shows=card(root);
  shows.addView(text("TVmaze · https://www.tvmaze.com/api",19,true));
  note(shows,tx("بوسترات المسلسلات والحلقات: يطابق اسم المسلسل بشكل فريد؛ رقم الموسم والحلقة يحدد صورتها إن توفرت.",
    "Shows and episodes: require one exact show match. Numbered seasons/episodes use their own image if provided."));
  CheckBox tv=check(shows,tx("تفعيل صور TVmaze","Enable TVmaze artwork"),
    prefs().getBoolean("artwork_tvmaze_enabled",true));
  tv.setOnCheckedChangeListener((b,v)->prefs().edit().putBoolean("artwork_tvmaze_enabled",v).apply());
  action(shows,tx("فحص مسلسل البرنامج الحالي","Test TVmaze for current programme"),
    ()->testTvmaze());

  LinearLayout sports=card(root);
  sports.addView(text("TheSportsDB · Free API v1 / 123",19,true));
  note(sports,tx("شعارات الفرق والدوريات وصور المباريات؛ لا تستخدمها كلوغو قناة. المفتاح العام 123 محدود إلى 30 طلباً بالدقيقة وبعض النتائج فقط، ولا يمنح Premium v2.",
    "Team, league and match art, never TV channel logos. Public key 123: max 30/minute, restricted free endpoint results, no Premium v2."));
  CheckBox sp=check(sports,tx("تفعيل صور وأحداث TheSportsDB","Enable TheSportsDB art and events"),
    prefs().getBoolean("artwork_sportsdb_enabled",true));
  sp.setOnCheckedChangeListener((b,v)->prefs().edit().putBoolean("artwork_sportsdb_enabled",v).apply());
  action(sports,tx("فحص شعار Arsenal من API المجاني","Test public Arsenal team badge"),
    ()->testSports());
  EditText name=field(sports,tx("اسم الفريق أو اللاعب بالإنجليزية",
    "Team/player name"),"Arsenal");
  action(sports,tx("جلب شعار فريق مطابق بالضبط","Fetch exact team badge"),
    ()->lookupSports(name.getText().toString(),false));
  action(sports,tx("جلب صورة لاعب مطابق بالضبط","Fetch exact player image"),
    ()->lookupSports(name.getText().toString(),true));
  sportsPreview=new ImageView(this);
  sportsPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
  sports.addView(sportsPreview,new LinearLayout.LayoutParams(dp(260),dp(152)));

  LinearLayout commons=card(root);
  commons.addView(text("Wikimedia Commons · licensed exact File:",19,true));
  note(commons,tx("أدخل اسم صورة محددة من صفحة Commons (File:...)، ثم اختر ربطها بالقناة أو بوستر البرنامج الحالي/التالي. لا نختار صوراً عشوائية حسب الاسم. نحفظ المؤلف والترخيص ورابط صفحة المصدر.",
    "Enter the exact Commons File: title, select channel logo or current/next EPG poster. No unrelated name-based guesses; licence, creator and source page are retained."));
  CheckBox wm=check(commons,tx("السماح بصور Commons ذات الترخيص الواضح",
    "Allow licence-verified Commons images"),
    prefs().getBoolean("artwork_commons_enabled",true));
  wm.setOnCheckedChangeListener((b,v)->prefs().edit().putBoolean("artwork_commons_enabled",v).apply());
  EditText file=field(commons,"File:Example.jpg","");
  action(commons,tx("إصلاح 12 · ربط الصورة بشعار القناة الحالية",
    "Fix 12 · Set current channel logo"),()->useCommons(file.getText().toString(),0));
  action(commons,tx("ربط الصورة ببوستر البرنامج الحالي (أعلى اليسار)",
    "Set NOW poster (top left)"),()->useCommons(file.getText().toString(),1));
  action(commons,tx("ربط الصورة ببوستر البرنامج التالي (أعلى اليمين)",
    "Set NEXT poster (top right)"),()->useCommons(file.getText().toString(),2));
  action(commons,tx("عرض حقوق الصورة المختارة للقناة والبرنامج",
    "Show stored Commons credit and licence"),()->showCredit());

  LinearLayout diag=card(root);
  diag.addView(text(tx("حالة المصادر","Source status"),19,true));
  status=text(tx("اختر مصدراً للفحص","Choose a source to test"),15,false);
  diag.addView(status);
  note(diag,tx("تُحمّل الصور على خيط IO فقط. أثناء تشغيل 4:2:2 تتوقف عمليات البحث والتنزيل الجديدة وتُستخدم الصور المخزنة.",
    "Images download on a background thread only. During 4:2:2 new provider requests are deferred; cached artwork remains available."));
 }
 private boolean permitted(){
  if(PlaybackResourceMode.active()||prefs().getBoolean("software_dvb_422_active",false)||
     prefs().getBoolean("dvb_tuning",false)){
   show(tx("مؤجل: الفيديو 4:2:2 له الأولوية","Deferred: 4:2:2 decoding takes priority"));
   return false;
  }
  return true;
 }
 private void show(String message){
  runOnUiThread(()->{if(!closed&&status!=null)status.setText(message);});
 }
 private void testTvmaze(){
  if(!permitted())return;
  SatelliteState active=SatelliteState.read(this);
  if(active.title.isEmpty()){show(tx("لا توجد تسمية برنامج حالية","No current EPG title"));return;}
  show(tx("جاري فحص TVmaze…","Checking TVmaze…"));
  io.execute(()->{
   try{
    JSONObject x=new TvmazeProvider(getApplicationContext()).resolve(active.title,"");
    show("TVmaze · "+x.optString("status")+
      ("matched".equals(x.optString("status"))?" · "+x.optString("title")+
       (x.optString("poster").isEmpty()?" · NO_IMAGE":" · IMAGE_READY"):""));
   }catch(Exception e){show("TVmaze · "+error(e));}
  });
 }
 private void testSports(){
  if(!permitted())return;
  if(!prefs().getBoolean("artwork_sportsdb_enabled",true)){
   show("TheSportsDB · DISABLED");return;
  }
  show(tx("فحص API المجاني…","Checking public v1 API…"));
  io.execute(()->{
   try{
    JSONObject data=new SportsCache(getApplicationContext()).get(
      TheSportsDBProvider.API+"searchteams.php?t=Arsenal",86400000L)
      .getJSONObject("data");
    JSONArray rows=data.optJSONArray("teams");
    JSONObject first=rows==null?null:rows.optJSONObject(0);
    boolean found=first!=null&&"Arsenal".equalsIgnoreCase(first.optString("strTeam"))
      &&!first.optString("strBadge").isEmpty();
    show("TheSportsDB v1 · "+(found?"VERIFIED_TEAM_BADGE":"NO_VERIFIED_BADGE"));
   }catch(Exception e){show("TheSportsDB · "+error(e));}
  });
 }
 private void lookupSports(String name,boolean player){
  if(!permitted())return;
  show(tx("التحقق من تطابق الصورة الرياضية…","Checking exact sports identity…"));
  io.execute(()->{
   try{
    JSONObject result=TheSportsDBProvider.sportsImage(getApplicationContext(),name,player);
    String address=result.optString("image");
    if(!"matched".equals(result.optString("status"))||address.isEmpty()){
     show("TheSportsDB · "+result.optString("status")+" · NO_IMAGE");
     return;
    }
    byte[] bytes=ArtworkCache.fetch(getApplicationContext(),new URI(address).toURL(),
      4*1024*1024);
    if(bytes==null)throw new IOException("SPORTS_ART_NOT_CACHED");
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
    if(bounds.outWidth<24||bounds.outHeight<24||bounds.outWidth>8192||bounds.outHeight>8192)
     throw new IOException("SPORTS_ART_INVALID");
    bounds.inJustDecodeBounds=false;bounds.inSampleSize=1;
    while(bounds.outWidth/bounds.inSampleSize>384||bounds.outHeight/bounds.inSampleSize>384)
     bounds.inSampleSize*=2;
    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
    runOnUiThread(()->{
     if(closed)return;
     if(sportsPreview!=null)sportsPreview.setImageBitmap(bitmap);
     if(status!=null)status.setText("TheSportsDB v1 · "+result.optString("kind")+
       " · "+result.optString("name")+" · VERIFIED");
    });
   }catch(Exception e){show("TheSportsDB · "+error(e));}
  });
 }
 private void useCommons(String title,int target){
  if(!permitted())return;
  if(!prefs().getBoolean("artwork_commons_enabled",true)){
   show("Wikimedia Commons · DISABLED");return;
  }
  SatelliteState selected=SatelliteState.read(this);
  String eventTitle=target==2?selected.nextTitle:selected.title;
  String key=target==0?"":PosterSelection.key(selected,eventTitle,target==2);
  if(target==0&&(selected.channelId.isEmpty()||selected.channelName.isEmpty())){
   show(tx("اختر قناة فعلية أولاً","Select a channel first"));return;
  }
  if(target!=0&&key.isEmpty()){
   show(tx("لا توجد هوية EPG كاملة لهذا البرنامج","This EPG event has no exact identity"));return;
  }
  show(tx("جاري التحقق من ملف Commons والترخيص…","Checking Commons file and licence…"));
  io.execute(()->{
   try{
    JSONObject verified=CommonsArtworkProvider.exactFile(getApplicationContext(),title);
    URI url=new URI(verified.getString("poster"));
    byte[] bytes=ArtworkCache.fetch(getApplicationContext(),url.toURL(),5*1024*1024);
    if(bytes==null)throw new IOException("WIKIMEDIA_IMAGE_NOT_CACHED");
    BitmapFactory.Options o=new BitmapFactory.Options();
    o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
    if(o.outWidth<24||o.outHeight<24||o.outWidth>10000||o.outHeight>10000)
     throw new IOException("WIKIMEDIA_INVALID_DIMENSIONS");
    if(target==0){
     o.inJustDecodeBounds=false;o.inSampleSize=1;
     while(o.outWidth/o.inSampleSize>512||o.outHeight/o.inSampleSize>512)
      o.inSampleSize*=2;
     Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
     if(bitmap==null)throw new IOException("WIKIMEDIA_BAD_BITMAP");
     try{
      SatelliteState fresh=SatelliteState.read(getApplicationContext());
      if(!selected.channelId.equals(fresh.channelId)||
         !selected.channelName.equals(fresh.channelName))
       throw new IOException("CHANNEL_CHANGED");
      CHMAXIntegration.saveObservedLogo(getApplicationContext(),selected.channelId,
        selected.channelName,bitmap);
     }finally{bitmap.recycle();}
     String identity=CHMAXIntegration.identity(selected);
     prefs().edit().putString("native_logo_source:"+identity,"MANUAL_CHANNEL_IMAGE")
       .putString("commons_logo_credit:"+SportsCache.key(identity),verified.toString())
       .putLong("native_logos_changed_at",System.currentTimeMillis()).apply();
     LogoRepository.get(getApplicationContext()).invalidate();
    }else{
     SatelliteState fresh=SatelliteState.read(getApplicationContext());
     String currentKey=PosterSelection.key(fresh,target==2?fresh.nextTitle:fresh.title,target==2);
     if(!key.equals(currentKey))throw new IOException("EPG_EVENT_CHANGED");
     long end=target==2?selected.nextEndMs:selected.endMs;
     long until=Math.min(end+60000L,System.currentTimeMillis()+3L*86400000L);
     if(until<=System.currentTimeMillis())throw new IOException("EPG_EVENT_ENDED");
     prefs().edit().putString("verified_poster_url:"+key,verified.getString("poster"))
       .putString("verified_poster_source:"+key,"Wikimedia Commons · exact File")
       .putString("verified_poster_id:"+key,verified.optString("file"))
       .putString("commons_poster_credit:"+key,verified.toString())
       .putLong("verified_poster_until:"+key,until)
       .putLong("verified_poster_at:"+key,System.currentTimeMillis())
       .putLong("epg_art_changed_at",System.currentTimeMillis()).apply();
    }
    show(tx("تم الربط وحفظ الترخيص: ","Selected and credited: ")+
      verified.optString("license")+" · "+verified.optString("artist")+
      "\n"+verified.optString("source"));
   }catch(Exception e){show("Wikimedia Commons · "+error(e));}
  });
 }
 private void showCredit(){
  SatelliteState s=SatelliteState.read(this);
  try{
   String logo=prefs().getString("commons_logo_credit:"+
     SportsCache.key(CHMAXIntegration.identity(s)),"");
   String now=prefs().getString("commons_poster_credit:"+
     PosterSelection.key(s,s.title,false),"");
   String next=prefs().getString("commons_poster_credit:"+
     PosterSelection.key(s,s.nextTitle,true),"");
   StringBuilder result=new StringBuilder();
   for(String item:new String[]{logo,now,next}){
    if(item.isEmpty())continue;
    JSONObject j=new JSONObject(item);
    result.append(j.optString("file")).append("\n")
      .append(j.optString("artist")).append(" · ").append(j.optString("credit"))
      .append("\n").append(j.optString("license")).append(" · ")
      .append(j.optString("licenseUrl")).append("\n")
      .append(j.optString("source")).append("\n\n");
   }
   show(result.length()==0?tx("لا توجد صورة Commons مرتبطة بهذه القناة/البرامج",
      "No linked Commons image for these channel/event identities"):result.toString());
  }catch(Exception e){show("COMMONS_METADATA_UNAVAILABLE");}
 }
 private static String error(Exception e){
  String m=e.getMessage();
  return m!=null&&m.matches("[A-Z0-9_]+")?m:e.getClass().getSimpleName();
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
