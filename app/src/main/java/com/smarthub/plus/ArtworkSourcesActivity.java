package com.smarthub.plus;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fix 12 — actual online logo/poster controls: exact TVmaze shows/episodes,
 * TheSportsDB free V1 team artwork and licensed Commons File: selections.
 */
public final class ArtworkSourcesActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private TextView output,credit;
 private ImageView preview;
 private EditText tvTitle,teamTitle,commonsFile;
 private JSONObject selectedCommons;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=page(tx("إصلاح 12 · مصادر الشعارات والبوسترات",
      "Fix 12 · Logos and posters"),
    tx("صور موثقة للقنوات والبرامج والمباريات. لا جلب ثقيل أثناء 4:2:2.",
      "Verified channel, programme and sport artwork; no heavy network calls during 4:2:2."));
  LinearLayout switches=card(page);
  switches.addView(text(tx("تشغيل وإيقاف مصادر البوسترات","Enable / disable poster providers"),20,true));
  note(switches,tx("الإيقاف يمنع الطلبات الجديدة ولا يحذف الصور المحفوظة. التغيير يُحفظ تلقائياً.",
    "Off stops new lookups without deleting saved images. Changes save automatically."));
  switches.addView(text(tx("الأفلام والمسلسلات","Films and series"),16,true));
  sourceSwitch(switches,PosterSourceSwitches.TMDB,
    tx("TMDB · الأفلام والمسلسلات","TMDB · movies and series"));
  sourceSwitch(switches,PosterSourceSwitches.OPDB,
    tx("OpenPosterDB (OPDB) · بوسترات IMDb","OpenPosterDB (OPDB) · IMDb posters"));
  sourceSwitch(switches,PosterSourceSwitches.RPDB,
    tx("RatingPosterDB (RPDB) · بوسترات بالتقييم","RatingPosterDB (RPDB) · rated posters"));
  sourceSwitch(switches,PosterSourceSwitches.BETTER,
    tx("BetterPosters · بوسترات إضافية","BetterPosters · extra artwork"));
  sourceSwitch(switches,PosterSourceSwitches.ARCHIVE,
    tx("Archive.org · بوسترات أرشيفية بترخيص","Archive.org · licensed archive posters"));
  note(switches,tx("OPDB وRPDB يحتاجان IMDb ID مؤكداً. OMDb يحتاج مفتاح Poster API منفصلاً؛ مفاتيح RPDB لا تعمل معه.",
    "OPDB/RPDB require an exact IMDb ID. OMDb Poster API needs its own patron key, not RPDB's key."));
  switches.addView(text(tx("المسلسلات والرياضة والصور الحرة","TV, sports and licensed images"),16,true));
  CheckBox tv=check(switches,tx("TVmaze · صور المسلسلات والحلقات SxxExx",
    "TVmaze · shows and exact SxxExx episode posters"),
    prefs().getBoolean("art_source_tvmaze_enabled",true));
  CheckBox sports=check(switches,tx("TheSportsDB · صور الأحداث وشعارات الفرق والبطولات",
    "TheSportsDB · event posters and team/league badges"),
    prefs().getBoolean("art_source_sportsdb_enabled",true));
  CheckBox commons=check(switches,tx("Wikimedia Commons · صور برخصة ونسبة محفوظة",
    "Wikimedia Commons · credited and licensed images"),
    prefs().getBoolean("art_source_commons_enabled",true));
  tv.setOnCheckedChangeListener((v,on)->PosterSourceSwitches.set(
    this,PosterSourceSwitches.TVMAZE,on));
  sports.setOnCheckedChangeListener((v,on)->PosterSourceSwitches.set(
    this,PosterSourceSwitches.SPORTS,on));
  commons.setOnCheckedChangeListener((v,on)->PosterSourceSwitches.set(
    this,PosterSourceSwitches.COMMONS,on));

  LinearLayout tvBox=card(page);
  tvBox.addView(text("1. TVmaze · https://www.tvmaze.com/api",18,true));
  note(tvBox,tx("الاسم يُطابق حرفياً بعد التطبيع؛ صيغة S01E02 تجلب صورة الحلقة إذا توفرت. النتائج لا تخلط مسلسلاً آخر.",
    "Unique normalised show match only; S01E02 chooses verified episode art when available."));
  tvTitle=field(tvBox,tx("اسم المسلسل أو S01E02","Exact series title / S01E02"),"");
  action(tvBox,tx("اختبار جلب بوستر TVmaze","Test TVmaze poster"),()->{
   if(!tv.isChecked()){show(tx("فعّل TVmaze أولاً","Enable TVmaze first"));return;}
   runCheck(()->new TvmazeProvider(this).resolve(tvTitle.getText().toString().trim(),""),
      "TVmaze","poster");
  });

  LinearLayout sportBox=card(page);
  sportBox.addView(text("2. TheSportsDB · free V1 /123/",18,true));
  note(sportBox,tx("المفتاح 123 عام، 30 طلباً بالدقيقة كحد أعلى، والبحث المجاني محدود بنتيجة واحدة؛ لا يدعم Premium V2.",
    "Public V1 key 123, at most 30 requests/minute and one free team-search result. No Premium V2."));
  teamTitle=field(sportBox,tx("اسم الفريق الدقيق، مثال Arsenal","Exact team, e.g. Arsenal"),"Arsenal");
  action(sportBox,tx("اختبار شعار الفريق","Test official team badge"),()->{
   if(!sports.isChecked()){show(tx("فعّل المصدر الرياضي أولاً","Enable sports source first"));return;}
   runCheck(()->new TheSportsDBProvider(this).findTeamBadge(
     teamTitle.getText().toString().trim()),"TheSportsDB","logo");
  });

  LinearLayout playerBox=card(page);
  playerBox.addView(text("TheSportsDB · Player photos",18,true));
  note(playerBox,tx("صور اللاعبين من API المجاني؛ البحث محدود بنتيجة واحدة ويشترط مطابقة الاسم.",
    "Player photo lookup through free V1; exactly matched name, one-result limit."));
  EditText playerTitle=field(playerBox,tx("اسم اللاعب","Exact player name"),"Danny Welbeck");
  action(playerBox,tx("اختبار صورة اللاعب","Test verified player photo"),()->{
   if(!sports.isChecked()){show(tx("فعّل المصدر الرياضي أولاً","Enable sports source first"));return;}
   runCheck(()->new TheSportsDBProvider(this).findPlayerPortrait(
     playerTitle.getText().toString().trim()),"TheSportsDB","poster");
  });

  LinearLayout commonsBox=card(page);
  commonsBox.addView(text("3. Wikimedia Commons · Commons API",18,true));
  note(commonsBox,tx("أدخل اسم ملف الصورة الكامل File: فقط؛ لا نخمن صوراً من عناوين البرامج. تُحفظ بيانات المؤلف والرخصة والرابط.",
    "Enter an exact File: title. No fuzzy artwork associations; save author, license and source."));
  commonsFile=field(commonsBox,tx("اسم ملف Wikimedia Commons (File:...)",
    "Exact Commons filename (File:...)"),"");
  action(commonsBox,tx("فحص الترخيص وتحميل المعاينة",
    "Verify file license and preview"),()->{
   if(!commons.isChecked()){show(tx("فعّل Wikimedia Commons أولاً","Enable Commons first"));return;}
   selectedCommons=null;
   String title=commonsFile.getText().toString().trim();
   runCheck(()->CommonsArtworkProvider.exactFile(this,title),"Wikimedia Commons","poster");
  });
  action(commonsBox,tx("إصلاح بوستر البرنامج الحالي من الملف المحدد",
    "Assign selected file to NOW programme poster"),()->assignProgramme(false));
  action(commonsBox,tx("إصلاح بوستر البرنامج القادم من الملف المحدد",
    "Assign selected file to NEXT programme poster"),()->assignProgramme(true));
  action(commonsBox,tx("إصلاح شعار قناة DVB الحالية من الملف المحدد",
    "Assign selected file to the exact current DVB channel logo"),this::assignLogo);

  LinearLayout results=card(page);
  results.addView(text(tx("نتيجة الجلب والتحقق","Source verification"),18,true));
  preview=new ImageView(this);
  preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
  results.addView(preview,new LinearLayout.LayoutParams(-1,dp(165)));
  output=text(tx("اختر المصدر واضغط اختبار.","Choose a source and test it."),14,false);
  results.addView(output);
  credit=text("",12,false);results.addView(credit);
  action(results,tx("فتح إعدادات صور EPG الرئيسية",
    "Open EPG provider credentials"),()->startActivity(
      new Intent(this,ProviderSetupActivity.class)));
 }

 private CheckBox sourceSwitch(LinearLayout group,String key,String title){
  CheckBox item=check(group,title,PosterSourceSwitches.enabled(this,key));
  item.setOnCheckedChangeListener((v,on)->{
   PosterSourceSwitches.set(this,key,on);
   if(output!=null)output.setText(tx("تم حفظ التفضيلات. طلبات الصور الجديدة تحترم الاختيارات.",
     "Preferences saved. New image requests follow your choices."));
  });
  return item;
 }
 private interface Lookup {JSONObject fetch()throws Exception;}

 private void runCheck(Lookup job,String name,String imageKey){
  if(PlaybackResourceMode.active()||prefs().getBoolean("software_dvb_422_active",false)){
   show(tx("الجلب مؤجل أثناء تشغيل 4:2:2؛ استخدم الكاش أو جرب بعد المشاهدة",
     "Network fetch deferred while software 4:2:2 is decoding"));return;
  }
  show(tx("جاري البحث والتحقق…","Looking up exact identity and image…"));
  io.execute(()->{
   try{
    JSONObject result=job.fetch();
    String status=result.optString("status");
    if(!"matched".equals(status)&&!"verified".equals(status)){
     show(name+": "+status);return;
    }
    String address=result.optString(imageKey);
    // Check source-host integrity even when the metadata API is successful.
    URI uri=new URI(address);
    if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||
       uri.getFragment()!=null)throw new java.io.IOException("INVALID_HTTPS_ARTWORK");
    String host=uri.getHost();
    if(!("TVmaze".equals(name)&&"static.tvmaze.com".equalsIgnoreCase(host)||
       "TheSportsDB".equals(name)&&
         ("www.thesportsdb.com".equalsIgnoreCase(host)||
          "r2.thesportsdb.com".equalsIgnoreCase(host)||
          "thesportsdb.com".equalsIgnoreCase(host))||
       "Wikimedia Commons".equals(name)&&"upload.wikimedia.org".equalsIgnoreCase(host)))
     throw new java.io.IOException("ARTWORK_HOST_NOT_VERIFIED");
    byte[] bytes=ArtworkCache.fetch(this,uri.toURL(),5*1024*1024);
    BitmapFactory.Options bounds=new BitmapFactory.Options();
    bounds.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
    if(bounds.outWidth<24||bounds.outHeight<24||bounds.outWidth>8192||bounds.outHeight>8192)
     throw new java.io.IOException("IMAGE_DECODE_UNAVAILABLE");
    BitmapFactory.Options opts=new BitmapFactory.Options();
    opts.inSampleSize=1;
    while(bounds.outWidth/opts.inSampleSize>512||
          bounds.outHeight/opts.inSampleSize>512)opts.inSampleSize*=2;
    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
    if(bitmap==null)throw new java.io.IOException("IMAGE_DECODE_FAILED");
    String caption=name+": "+result.optString("title",result.optString("team",
      result.optString("player",result.optString("file","Verified"))));
    String attribution="Wikimedia Commons".equals(name)
      ?"© "+result.optString("artist")+" · "+result.optString("license")+
        "\n"+result.optString("source")+"\n"+result.optString("licenseUrl")
      :result.optString("source","");
    runOnUiThread(()->{
     if(closed)return;
     preview.setImageBitmap(bitmap);
     output.setText(caption);
     credit.setText(attribution);
     selectedCommons="Wikimedia Commons".equals(name)?result:null;
    });
   }catch(Exception failure){
    show(name+": "+failure.getClass().getSimpleName()+" · "+failure.getMessage());
   }
  });
 }

 private void assignProgramme(boolean next){
  if(selectedCommons==null){
   show(tx("تحقق من ملف Wikimedia Commons أولاً","Verify the Commons File: first"));return;
  }
  JSONObject source=selectedCommons;
  SatelliteState state=ReceiverInfoRepository.get(this).enrich(SatelliteState.read(this));
  try{
   if(!CommonsArtworkSelection.assignProgramme(this,state,next,source)){
    show(tx("لا توجد هوية EPG مؤكدة مع وقت بداية للبرنامج",
      "No exact NOW/NEXT EPG identity and start time available"));
   }else show(tx("تم حفظ البوستر للبرنامج المطابق فقط، مع النسبة والترخيص",
     "Saved artwork for this exact programme event with credit and license"));
  }catch(Exception failure){show(failure.getMessage());}
 }
 private void assignLogo(){
  if(selectedCommons==null){
   show(tx("تحقق من الملف أولاً","Verify a Commons file first"));return;
  }
  JSONObject chosen=selectedCommons;
  SatelliteState current=SatelliteState.read(this);
  if(!current.channelId.startsWith("DVB:")){
   show(tx("يلزم تشغيل قناة DVB ذات هوية محددة",
     "An exact physical DVB channel is required"));return;
  }
  show(tx("جاري حفظ شعار القناة الحالية…","Saving verified channel logo…"));
  io.execute(()->{
   try{
    boolean ok=CommonsArtworkSelection.assignChannelLogo(this,current,chosen);
    show(ok?tx("تم إصلاح شعار القناة مع حفظ المصدر والرخصة",
      "Channel logo saved with attribution"):tx("تعذر ربط الشعار لهذه القناة",
      "Cannot bind logo to this channel"));
   }catch(Exception error){show(error.getMessage());}
  });
 }
 private void show(String text){
  runOnUiThread(()->{if(!closed&&output!=null)output.setText(text==null?"":text);});
 }
 @Override protected void onDestroy(){
  closed=true;io.shutdownNow();super.onDestroy();
 }
}
