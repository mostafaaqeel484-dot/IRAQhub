package com.smarthub.plus;

import android.os.Bundle;
import android.graphics.Bitmap;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV-friendly film and series reference library backed by real open encyclopedia data. */
public final class MediaActivity extends HubActivity {
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private MediaRepository repository;
    private EditText query;
    private LinearLayout results;
    private TextView status;
    private final List<MediaRepository.Item> lastResults=new ArrayList<>();
    private MediaRepository.SearchPage lastPage;
    private String lastQuery="", mode="all", scene="home";
    private volatile long generation;

    @Override public void onCreate(Bundle state){super.onCreate(state);repository=new MediaRepository(this);lastQuery=prefs().getString("media_query","");String programme=getIntent().getStringExtra("title");boolean supplied=programme!=null&&programme.trim().length()>=2&&programme.trim().length()<=100;if(supplied)lastQuery=programme.trim();home();if(supplied)search(false);}
    private void home(){
        generation++;scene="home";
        LinearLayout body=page(tx("موسوعة الشاشة","Screen encyclopedia"),tx("اكتشف الأفلام والمسلسلات • بحث عربي وإنجليزي • مفضلة تعمل دون إنترنت","Films & series • Arabic and English search • Offline favorites"));
        LinearLayout search=card(body);search.addView(text(tx("ابحث عن عنوان","Find a title"),20,true));
        query=field(search,tx("اسم فيلم أو مسلسل…","Film or series title…"),lastQuery);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search(false);return true;}return false;});
        LinearLayout controls=row();Button find=button(tx("بحث","Search"));find.setOnClickListener(v->search(false));Button favorites=button(tx("★ قائمتي المحفوظة","★ Saved library"));favorites.setOnClickListener(v->favorites());
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(48),1);half.setMargins(0,dp(4),dp(8),dp(6));controls.addView(find,half);controls.addView(favorites,new LinearLayout.LayoutParams(0,dp(48),1));search.addView(controls);
        LinearLayout filters=row();filter(filters,"all",tx("الكل","All"));filter(filters,"film",tx("أفلام","Films"));filter(filters,"series",tx("مسلسلات","Series"));search.addView(filters);
        status=text(tx("اكتب عنواناً لعرض نتائج موثقة من Wikidata.","Search a title to retrieve verified Wikidata entries."),14,false);status.setTextColor(MUTED);status.setPadding(0,dp(10),0,dp(8));search.addView(status);
        results=col();body.addView(results);if(lastPage!=null)renderResults();
        note(body,tx("Wikidata: بيانات CC0. النبذات من ويكيبيديا وفق CC BY-SA؛ الصور المرتبطة بالعمل من Wikimedia Commons بترخيص حر موثّق فقط. اختر العمل المطابق؛ لا نختار تلقائياً بين عناوين متشابهة. البيانات قد تكون ناقصة.","Wikidata: CC0. Wikipedia summaries: CC BY-SA. Images linked to the work come from Wikimedia Commons with a verified open license. Choose the matching work; similar titles are never selected automatically. Metadata may be incomplete."));
    }
    private void filter(LinearLayout box,String key,String label){Button b=button((key.equals(mode)?"● ":"")+label);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),1);p.setMargins(0,dp(8),dp(8),0);box.addView(b,p);b.setOnClickListener(v->{mode=key;lastQuery=query.getText().toString();home();});}
    private void search(boolean refresh){
        String value=query.getText().toString().trim();if(value.length()<2||value.length()>100){toast(tx("اكتب من حرفين إلى 100 حرف.","Enter 2 to 100 characters."));return;}
        lastQuery=value;prefs().edit().putString("media_query",value).apply();long token=++generation;status.setText(tx("جارٍ البحث والتحقق من نوع العناوين…","Searching and verifying title types…"));results.removeAllViews();
        jobs.execute(()->{if(token!=generation)return;try{MediaRepository.SearchPage page=repository.search(BroadcastTitle.parse(value).title,ar?"ar":"en",true);runOnUiThread(()->{if(!active(token))return;lastPage=page;lastResults.clear();lastResults.addAll(page.items);String wanted=getIntent().getStringExtra("imdb_id");if(wanted!=null&&!wanted.isEmpty())for(MediaRepository.Item match:page.items)if(wanted.equals(match.imdbId)){getIntent().removeExtra("imdb_id");details(match.id,true);return;}renderResults();});}catch(Exception error){runOnUiThread(()->{if(!active(token))return;status.setText(tx("تعذر الاتصال بالمصدر. افتح قائمتك المحفوظة للعمل دون إنترنت.","The source could not be reached. Open your saved library for offline entries."));action(results,tx("إعادة المحاولة","Try again"),()->search(true));});}});
    }
    private void renderResults(){
        results.removeAllViews();int count=0;for(MediaRepository.Item item:lastResults){if(!"all".equals(mode)&&!mode.equals(item.kind))continue;count++;resultCard(results,item);}
        if(count==0)note(results,tx("لم نجد أفلاماً أو مسلسلات مطابقة. جرّب الاسم الأصلي أو أضف سنة الإصدار؛ النتائج الأخرى تُستبعد بعد التحقق.","No matching film or series was found. Try its original title or add the release year; other entity types are excluded after verification."));
        String source=lastPage.cached?tx("من الذاكرة المحلية","From local cache"):tx("من المصدر","From source");status.setText(count+tx(" نتيجة · "," results · ")+source+"\n"+tx("جُلبت: ","Fetched: ")+time(lastPage.fetchedAt)+(lastPage.warning.isEmpty()?"":"\n"+tx("انقطع الاتصال؛ نعرض آخر نتائج محفوظة.","Connection unavailable; showing saved results.")));
        action(results,tx("تحديث البحث من المصدر","Refresh search from source"),()->search(true));
    }
    private void resultCard(LinearLayout box,MediaRepository.Item item){
        LinearLayout card=card(box);LinearLayout top=row();TextView title=text(item.title,21,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView kind=text(kind(item.kind),13,true);kind.setTextColor(ACCENT);kind.setPadding(dp(12),dp(5),dp(12),dp(5));kind.setBackground(shape(TILE,7));top.addView(kind);card.addView(top);
        if(!item.description.isEmpty()){TextView description=text(item.description,15,false);description.setTextColor(MUTED);description.setPadding(0,dp(5),0,dp(8));card.addView(description);}
        if(!item.release.isEmpty())note(card,tx("أول إصدار مسجل: ","Earliest recorded release: ")+item.release);
        action(card,(repository.isFavorite(item.id)?"★ ":"")+tx("عرض المعلومات","View details"),()->{if("favorites".equals(scene)){long token=++generation;scene="detail";showDetail(item,token);}else details(item.id,false);});
    }
    private void favorites(){
        generation++;scene="favorites";LinearLayout body=page(tx("★ قائمتي المحفوظة","★ Saved library"),tx("معلومات وصور محفوظة محلياً للرجوع إليها دون إنترنت","Locally saved information and images, available offline"));List<MediaRepository.Item> items=repository.favorites();
        if(items.isEmpty())note(body,tx("قائمتك فارغة. ابحث عن عنوان وافتح معلوماته ثم اختر «حفظ في قائمتي».","Your library is empty. Find a title, open its details and choose Save to library."));
        for(MediaRepository.Item item:items){resultCard(body,item);}
        action(body,tx("العودة إلى البحث","Return to search"),this::home);
    }
    private void details(String id,boolean refresh){
        long token=++generation;scene="detail";LinearLayout body=page(tx("معلومات العنوان","Title details"),tx("بيانات موسوعية مفتوحة","Open encyclopedia metadata"));LinearLayout loading=card(body);loading.addView(text(tx("جارٍ تحميل المعلومات…","Loading metadata…"),20,true));note(loading,tx("نسترجع فقط الحقول التي يوفرها المصدر.","Retrieving only the fields available from the source."));
        jobs.execute(()->{if(token!=generation)return;try{MediaRepository.Item item=repository.detail(id,ar?"ar":"en",refresh);runOnUiThread(()->{if(active(token))showDetail(item,token);});}catch(Exception error){runOnUiThread(()->{if(!active(token))return;body.removeAllViews();note(body,tx("تعذر جلب معلومات هذا العنوان، ولا توجد نسخة محلية صالحة.","Could not load this title and no valid offline copy is available."));action(body,tx("إعادة المحاولة","Try again"),()->details(id,true));action(body,tx("العودة إلى البحث","Return to search"),this::home);});}});
    }
    private void showDetail(MediaRepository.Item item,long token){
        bindSatelliteSelection(item);
        LinearLayout body=page(item.title,kind(item.kind)+(item.release.isEmpty()?"":"  •  "+item.release));LinearLayout hero=card(body);LinearLayout heroRow=row();heroRow.setGravity(Gravity.TOP);
        LinearLayout art=col();ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(tx("صورة العنوان من المصدر","Title image from source"));image.setBackground(shape(BG,9));art.addView(image,new LinearLayout.LayoutParams(dp(170),dp(230)));TextView imageStatus=text(tx("جارٍ تحميل الصورة…","Loading image…"),12,false);imageStatus.setTextColor(MUTED);imageStatus.setPadding(0,dp(6),0,0);art.addView(imageStatus,new LinearLayout.LayoutParams(dp(170),-2));heroRow.addView(art);
        LinearLayout facts=col();LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.setMargins(dp(20),0,0,0);heroRow.addView(facts,fp);hero.addView(heroRow);
        if(!item.description.isEmpty())facts.addView(text(item.description,18,true));
        fact(facts,tx("النوع","Format"),kind(item.kind));fact(facts,tx("أول إصدار مسجل","Earliest release"),item.release);fact(facts,tx("المخرج","Director"),item.directors);fact(facts,tx("التصنيف الفني","Genres"),item.genres);fact(facts,tx("المدة","Runtime"),item.runtime);fact(facts,tx("المواسم","Seasons"),item.seasons);fact(facts,tx("الحلقات","Episodes"),item.episodes);fact(facts,tx("اللغة الأصلية","Original language"),item.languages);fact(facts,tx("بلد الإنتاج","Country of origin"),item.countries);fact(facts,tx("تصنيف المحتوى بحسب الجهة","Content rating by authority"),item.ratings);
        LinearLayout synopsis=card(body);synopsis.addView(text(tx("نبذة موسوعية","Encyclopedia summary"),20,true));note(synopsis,item.summary.isEmpty()?tx("لا تتوفر نبذة موسوعية لهذا العنوان في المصدر.","No encyclopedia summary is available for this title."):item.summary);
        if(!item.cast.isEmpty()){LinearLayout cast=card(body);cast.addView(text(tx("من طاقم التمثيل","Selected cast"),20,true));note(cast,item.cast);}
        LinearLayout actions=card(body);Button save=action(actions,repository.isFavorite(item.id)?tx("★ إزالة من قائمتي","★ Remove from library"):tx("☆ حفظ في قائمتي","☆ Save to library"),()->{try{repository.toggleFavorite(item);toast(repository.isFavorite(item.id)?tx("حُفظت المعلومات في قائمتك.","Information saved to your library."):tx("أُزيل من قائمتك.","Removed from your library."));showDetail(item,token);}catch(Exception error){toast(tx("تعذر حفظ العنوان.","Could not save this title."));}});
        action(actions,tx("ترجمة هذا العمل عبر OpenSubtitles","Find this work's subtitles on OpenSubtitles"),()->subtitles(item));
        if(!item.imdbId.isEmpty()){fact(actions,"IMDb",item.imdbId);action(actions,tx("فتح صفحة IMDb الموثقة من Wikidata","Open IMDb linked by Wikidata"),()->open("https://www.imdb.com/title/"+item.imdbId+"/"));}
        action(actions,tx("تحديث المعلومات من المصدر","Refresh metadata from source"),()->details(item.id,true));
        if(!item.article.isEmpty())action(actions,tx("قراءة المقال في ويكيبيديا","Read the Wikipedia article"),()->open(item.article));action(actions,tx("مصدر البيانات: Wikidata","Data source: Wikidata"),()->open(item.sourceUrl()));
        String source=(item.cached?tx("نسخة محلية · ","Local copy · "):tx("من المصدر · ","From source · "))+tx("جُلبت: ","Fetched: ")+time(item.fetchedAt);
        if("OFFLINE".equals(item.warning))source+="\n"+tx("تعذر الاتصال؛ تظهر آخر معلومات محفوظة.","Connection unavailable; showing the last saved information.");else if("SUMMARY_UNAVAILABLE".equals(item.warning))source+="\n"+tx("تعذر تحميل النبذة من ويكيبيديا؛ بقية البيانات من Wikidata.","The Wikipedia summary could not be loaded; other facts come from Wikidata.");
        note(actions,source);if(!item.imagePage.isEmpty()){note(actions,tx("ترخيص الصورة: ","Image license: ")+item.imageLicense+(item.imageCredit.isEmpty()?"":"\n"+item.imageCredit));action(actions,tx("مصدر الصورة ونسبتها في Wikimedia Commons","Image source and attribution on Wikimedia Commons"),()->open(item.imagePage));}
        note(body,tx("تُحدّث الذاكرة خلال عشر دقائق، ويمكن التحديث يدوياً. الحقول غير المتوفرة لا تُعرض. Wikidata: CC0 • نص ويكيبيديا: CC BY-SA • الصورة وفق الترخيص والنسبة الموضّحين. لا يُعرض ملصق بديل مختلق عند غياب صورة حرة.","Cache refreshes within ten minutes; manual refresh is available. Unavailable fields are omitted. Wikidata: CC0 • Wikipedia text: CC BY-SA • Images use their displayed license and attribution. No substitute poster is invented when no open image is available."));action(body,tx("العودة إلى البحث","Return to search"),this::home);
        jobs.execute(()->{if(token!=generation)return;Bitmap bitmap=repository.image(item);runOnUiThread(()->{if(!active(token))return;if(bitmap==null)imageStatus.setText(tx("لا تتوفر صورة حرة موثقة","No verified open image available"));else{image.setImageBitmap(bitmap);imageStatus.setText(("poster".equals(item.imageKind)?tx("ملصق الفيلم","Film poster"):tx("صورة مرتبطة بالعمل","Related work image"))+"\nWikimedia Commons\n"+item.imageLicense);}});});
        LinearLayout gallery=card(body);gallery.addView(text(tx("صور العمل والممثلين من المصدر","Work and cast imagery from source"),20,true));TextView galleryStatus=text(tx("البحث عبر الإنترنت عن صور مرخّصة مرتبطة بالعمل وطاقمه…","Looking online for licensed images linked to the work and its cast…"),14,false);gallery.addView(galleryStatus);
        jobs.execute(()->{try{List<MediaRepository.Item> pictures=repository.gallery(item.id,ar?"ar":"en");int count=0;for(MediaRepository.Item picture:pictures){if(token!=generation)return;Bitmap bitmap=repository.image(picture);if(bitmap==null)continue;count++;runOnUiThread(()->{if(!active(token))return;LinearLayout tile=card(gallery);tile.addView(text(picture.title+" · "+("portrait".equals(picture.imageKind)?tx("من طاقم التمثيل","Cast member"):tx("صورة مرتبطة بالعمل","Related work image")),17,true));ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(bitmap);tile.addView(view,new LinearLayout.LayoutParams(-1,dp("portrait".equals(picture.imageKind)?240:300)));note(tile,picture.imageLicense+"\n"+picture.imageCredit);action(tile,tx("مصدر الصورة وترخيصها","Image source and license"),()->open(picture.imagePage));});}final int found=count;runOnUiThread(()->{if(active(token))galleryStatus.setText(found==0?tx("لا توجد صور إضافية مرخّصة في المصدر لهذا العمل.","No additional licensed imagery is available for this work."):tx("صور مرتبطة بهوية العمل وطاقمه؛ لا تُستخدم صورة ممثل كملصق للفيلم.","Images are linked to the verified work and cast identities."));});}catch(Exception e){runOnUiThread(()->{if(active(token))galleryStatus.setText(tx("تعذر تحديث الصور الإضافية عبر الإنترنت.","Could not update additional images online."));});}});
    }
    private void subtitles(MediaRepository.Item item){Intent intent=new Intent(this,TranslationActivity.class).putExtra("title",item.title).putExtra("imdb_id",item.imdbId);if(satelliteSelectionMatches(item)){Map<String,?> current=prefs().getAll();intent.putExtra("channel_id",string(current.get("satellite_channel_id"))).putExtra("event_start_ms",storedStart(current));}startActivity(intent);}
    private void bindSatelliteSelection(MediaRepository.Item item){if(satelliteSelectionMatches(item))prefs().edit().putString("satellite_imdb_id",item.imdbId).putString("satellite_imdb_title",prefs().getString("satellite_title","")).putString("satellite_wikidata_id",item.id).putString("satellite_wikidata_title",item.title).apply();}
    private boolean satelliteSelectionMatches(MediaRepository.Item item){Map<String,?> current=prefs().getAll();String programme=normalizedTitle(string(current.get("satellite_title"))),channel=string(current.get("satellite_channel_id"));long start=storedStart(current);String requestedChannel=getIntent().getStringExtra("channel_id");long requestedStart=getIntent().getLongExtra("event_start_ms",0);boolean sameTitle=!programme.isEmpty()&&programme.equals(normalizedTitle(item.title));boolean sameEvent=!channel.isEmpty()&&channel.equals(requestedChannel)&&start>0&&start==requestedStart;return sameTitle||sameEvent;}
    private static String normalizedTitle(String title){return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private static String string(Object value){return value==null?"":String.valueOf(value);}
    private static long storedStart(Map<String,?> values){Object value=values.get("satellite_event_start_ms");if(value==null)value=values.get("satellite_start");if(value==null)value=values.get("satellite_start_ms");if(value instanceof Number)return((Number)value).longValue();try{return Long.parseLong(string(value));}catch(Exception ignored){return 0;}}
    private void fact(LinearLayout box,String label,String value){if(value.isEmpty())return;TextView t=text(label+"  ·  "+value,15,false);t.setPadding(0,dp(5),0,dp(5));box.addView(t);}
    private String kind(String value){return "film".equals(value)?tx("فيلم","Film"):tx("مسلسل","Series");}
    private String time(long timestamp){return timestamp>0?DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(timestamp)):tx("غير معروف","Unknown");}
    private boolean active(long token){return token==generation&&!isFinishing()&&!isDestroyed();}
    private void open(String address){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}catch(Exception unavailable){toast(tx("لا يوجد متصفح مثبت لفتح الرابط.","No installed browser can open this link."));}}
    @Override public void finish(){if(!"home".equals(scene)){home();return;}super.finish();}
    @Override public void onBackPressed(){finish();}
    @Override protected void onDestroy(){generation++;jobs.shutdownNow();super.onDestroy();}
}
