package com.smarthub.plus;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Independent API key fields for real poster services. No API keys logged. */
public final class PosterApiSettingsActivity extends HubActivity {
 private final LinkedHashMap<String,EditText> controls=new LinkedHashMap<>();
 private TextView status;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private static final String[] KEYS={"rpdb","opdb","omdb","sportsdb"};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("مفاتيح API للبوسترات","Poster API credentials"),
    tx("كل مصدر له مفتاح مستقل، ولا يؤثر على EPG المصنع أو DVB.",
      "Separate keys per provider; native Factory/DVB EPG remains unchanged."));
  note(body,tx("المفاتيح تُشفّر باستخدام Android Keystore. اترك الخانة فارغة للاحتفاظ بالمفتاح السابق. لا تشارك مفاتيحك في المحادثة.",
     "Encrypted with Android Keystore. Blank keeps an existing key; never share your keys."));
  LinearLayout secretBox=card(body);
  add(secretBox,"rpdb","RatingPosterDB (RPDB) API Key");
  add(secretBox,"opdb","OpenPosterDB (OPDB) API Key");
  add(secretBox,"omdb","OMDb Poster API Patron Key");
  add(secretBox,"sportsdb","TheSportsDB V1 API Key (optional; free = 123)");
  note(secretBox,tx("OMDb Poster API يتطلب صلاحية Patron؛ مفتاح OMDb العادي قد لا يسمح بتحميل البوسترات.",
    "OMDb Poster API needs patron access; a normal metadata key may not authorize posters."));
  action(secretBox,tx("حفظ المفاتيح مشفّرة","Save keys encrypted"),()->{
   Map<String,String> changes=new LinkedHashMap<>();
   for(String id:KEYS)changes.put(id,controls.get(id).getText().toString().trim());
   try{
    PosterApiSecrets.update(this,changes);
    for(EditText field:controls.values())field.setText("");
    refresh();
   }catch(Exception invalid){status.setText(tx("صيغة مفتاح غير صالحة أو تعذر التشفير.",
      "Invalid key format or encrypted storage unavailable."));}
  });
  for(String id:KEYS){
   action(secretBox,tx("حذف مفتاح ","Remove ")+id.toUpperCase(Locale.ROOT),()->{
    try{PosterApiSecrets.remove(this,id);refresh();}
    catch(Exception ignored){status.setText(tx("تعذر الحذف","Could not clear key"));}
   });
  }
  status=text("",14,false);body.addView(status);refresh();
  LinearLayout anime=card(body);
  anime.addView(text(tx("الأنمي: AniList / Kitsu / AniDB","Anime: AniList / Kitsu / AniDB"),18,true));
  note(anime,tx("AniList وKitsu للمعلومات والصور العامة بدون مفتاح خاص. AniDB هنا معرّف من Kitsu؛ الاتصال المباشر بـAniDB يتطلب تسجيل عميل رسمي.",
    "AniList and anime Kitsu support public metadata searches without user keys. AniDB identity comes from Kitsu mappings; direct AniDB integration needs a registered client."));
  animeSwitch(anime,PosterSourceSwitches.ANILIST,"AniList · public anime metadata");
  animeSwitch(anime,PosterSourceSwitches.KITSU,"Kitsu · public anime metadata");
  animeSwitch(anime,PosterSourceSwitches.ANIDB,"AniDB identifiers · via Kitsu only");
  EditText animeName=field(anime,tx("الاسم الدقيق للأنمي","Exact anime title"),"One Piece");
  action(anime,tx("اختبار بحث الأنمي","Test anime source"),()->{
    String name=animeName.getText().toString().trim();
    status.setText(tx("جارٍ البحث…","Searching anime services…"));
    io.execute(()->{
      JSONObject found=AnimeMetadataProvider.resolve(getApplicationContext(),name);
      runOnUiThread(()->{if(!closed)status.setText(found==null?
        tx("لم توجد مطابقة دقيقة؛ جرّب اسماً رسمياً","No exact title match or source unavailable"):
        found.optString("provider")+" · "+found.optString("title")+" · "+found.optString("id"));});
    });
  });
  action(anime,tx("اختبار ربط معرّف AniDB","Test AniDB ID mapping"),()->{
    String name=animeName.getText().toString().trim();
    status.setText("Checking Kitsu / AniDB mapping…");
    io.execute(()->{
      String message;
      try{
        JSONObject match=AnimeMetadataProvider.kitsu(name,true);
        message=match==null?"NO_EXACT_KITSU_MATCH":
          match.optString("anidbId","NO_VERIFIED_ANIDB_ID");
      }catch(Exception e){message="KITSU_UNAVAILABLE";}
      String answer=message;
      runOnUiThread(()->{if(!closed)status.setText("AniDB: "+answer);});
    });
  });
  LinearLayout others=card(body);
  others.addView(text(tx("مصادر أخرى","Other sources"),17,true));
  action(others,tx("TMDB + OpenSubtitles · إعداد المفاتيح","TMDB + OpenSubtitles · configure and test keys"),
   ()->startActivity(new android.content.Intent(this,ProviderSetupActivity.class)));
  note(others,tx("TVmaze وWikimedia Commons وArchive.org تعمل بواجهات عامة بدون مفتاح خاص في هذا المسار. BetterPosters: لا أضيف خانة وهمية دون API موثقة.",
   "TVmaze, Commons and Archive use public endpoints here. BetterPosters has no verified private-key interface in this integration."));
  action(others,tx("تشغيل وإيقاف المصادر","Enable / disable sources"),
   ()->startActivity(new android.content.Intent(this,ArtworkSourcesActivity.class)));
 }
 private void animeSwitch(LinearLayout box,String key,String label){
  CheckBox enabled=check(box,label,PosterSourceSwitches.enabled(this,key));
  enabled.setOnCheckedChangeListener((view,on)->PosterSourceSwitches.set(this,key,on));
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
 private void add(LinearLayout box,String id,String label){
  EditText input=field(box,label,"");
  input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  input.setTextDirection(View.TEXT_DIRECTION_LTR);
  controls.put(id,input);
 }
 private void refresh(){
  StringBuilder statusText=new StringBuilder();
  for(String key:KEYS){
   if(statusText.length()>0)statusText.append("  ·  ");
   statusText.append(key.toUpperCase(Locale.ROOT)).append(": ")
     .append(PosterApiSecrets.configured(this,key)?"✓":"—");
  }
  try{
   ProviderSecrets keys=ProviderSecrets.read(this);
   statusText.append("  ·  TMDB: ").append(keys.tmdb.isEmpty()?"—":"✓");
   statusText.append("  ·  OpenSubtitles: ").append(keys.opensubtitles.isEmpty()?"—":"✓");
  }catch(Exception ignored){
   statusText.append("  ·  TMDB/OpenSubtitles: unavailable");
  }
  statusText.append("  ·  AniList: ").append(PosterSourceSwitches.enabled(this,PosterSourceSwitches.ANILIST)?"ON":"OFF");
  statusText.append("  ·  Kitsu: ").append(PosterSourceSwitches.enabled(this,PosterSourceSwitches.KITSU)?"ON":"OFF");
  statusText.append("  ·  AniDB mapping: ").append(PosterSourceSwitches.enabled(this,PosterSourceSwitches.ANIDB)?"ON":"OFF");
  status.setText(statusText.toString());
 }
}
