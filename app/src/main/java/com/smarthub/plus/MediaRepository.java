package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Open encyclopedia metadata, deliberately independent of playback services. */
public final class MediaRepository {
    private static final long CACHE_AGE = 10 * 60_000L;
    private static final int MEDIA_SCHEMA = 2;
    private static final String API = "https://www.wikidata.org/w/api.php?format=json&maxlag=5&";
    private static final Set<String> FILM = new HashSet<>(Arrays.asList("Q11424", "Q24869", "Q202866", "Q506240"));
    private static final Set<String> SERIES = new HashSet<>(Arrays.asList("Q5398426", "Q1259759", "Q581714"));
    private static final String[] FACT_PROPERTIES = {"P57", "P161", "P136", "P364", "P495", "P1657", "P1981", "P2629"};
    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, List<String>> ancestry = new HashMap<>();
    private long lastRequest;

    public MediaRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("smarthub", Context.MODE_PRIVATE);
    }
    public static final class Item {
        public String id = "", title = "", description = "", kind = "", release = "", runtime = "", seasons = "", episodes = "";
        public String directors = "", cast = "", genres = "", languages = "", countries = "", ratings = "";
        public String summary = "", image = "", article = "", wikiTitle = "", wikiLanguage = "", warning = "";
        public String imdbId = "", commonsFile = "", imageKind = "", imageLicense = "", imageLicenseUrl = "", imageCredit = "", imagePage = "";
        public String background="";public JSONArray people=new JSONArray();public final List<String> matchTitles=new ArrayList<>();public long fetchedAt;
        public boolean cached;
        public String sourceUrl() { if(id.startsWith("epg:"))return "";return (id.startsWith("subsource:")||id.startsWith("betterposters:")||id.startsWith("tmdb:"))?imagePage:"https://www.wikidata.org/wiki/" + id; }
        public JSONObject json() throws Exception {
            JSONObject j = new JSONObject();
            j.put("background",background).put("people",people);j.put("matchTitles",new JSONArray(matchTitles));j.put("id",id).put("title",title).put("description",description).put("kind",kind).put("release",release).put("runtime",runtime)
                .put("seasons",seasons).put("episodes",episodes).put("directors",directors).put("cast",cast).put("genres",genres)
                .put("languages",languages).put("countries",countries).put("ratings",ratings).put("summary",summary).put("image",image)
                .put("article",article).put("wikiTitle",wikiTitle).put("wikiLanguage",wikiLanguage).put("fetchedAt",fetchedAt)
                .put("imdbId",imdbId).put("commonsFile",commonsFile).put("imageKind",imageKind).put("imageLicense",imageLicense).put("imageLicenseUrl",imageLicenseUrl)
                .put("imageCredit",imageCredit).put("imagePage",imagePage).put("mediaSchema",MEDIA_SCHEMA);
            return j;
        }
        static Item fromJson(JSONObject j) {
            Item i = new Item();i.background=j.optString("background");i.people=j.optJSONArray("people")==null?new JSONArray():j.optJSONArray("people");
            JSONArray titles=j.optJSONArray("matchTitles");if(titles!=null)for(int n=0;n<Math.min(30,titles.length());n++)i.matchTitles.add(titles.optString(n));i.id=j.optString("id");i.title=j.optString("title");i.description=j.optString("description");i.kind=j.optString("kind");i.release=j.optString("release");i.runtime=j.optString("runtime");
            i.seasons=j.optString("seasons");i.episodes=j.optString("episodes");i.directors=j.optString("directors");i.cast=j.optString("cast");i.genres=j.optString("genres");
            i.languages=j.optString("languages");i.countries=j.optString("countries");i.ratings=j.optString("ratings");i.summary=j.optString("summary");i.image=j.optString("image");
            i.article=j.optString("article");i.wikiTitle=j.optString("wikiTitle");i.wikiLanguage=j.optString("wikiLanguage");i.fetchedAt=j.optLong("fetchedAt");
            i.imdbId=j.optString("imdbId");if(!i.imdbId.matches("tt[0-9]{7,10}"))i.imdbId="";
            i.commonsFile=j.optString("commonsFile");i.imageKind=j.optString("imageKind");i.imageLicense=j.optString("imageLicense");i.imageLicenseUrl=j.optString("imageLicenseUrl");i.imageCredit=j.optString("imageCredit");i.imagePage=j.optString("imagePage");
            if(!licensedImage(i)){i.image="";i.imageLicense="";i.imageLicenseUrl="";i.imageCredit="";i.imagePage="";}return i;
        }
    }
    public static final class SearchPage {
        public final List<Item> items = new ArrayList<>();
        public boolean cached;
        public final List<String> matchTitles=new ArrayList<>();public long fetchedAt;
        public String warning = "";
    }
    public SearchPage search(String query, String language, boolean refresh) throws Exception {
        String normalized = query.trim();
        if (normalized.length()<2 || normalized.length()>100) throw new IOException("SEARCH_LENGTH");
        File cache = cacheFile("search_" + language + "_" + digest(normalized.toLowerCase(Locale.ROOT)));
        JSONObject saved = read(cache);
        if (!refresh && fresh(saved)) return searchFromJson(saved, true);
        try {
            JSONObject hits = request(API+"action=wbsearchentities&type=item&limit=12&language="+language+"&uselang="+language+"&search="+encode(normalized));
            JSONArray list = hits.optJSONArray("search");
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (list!=null) for(int n=0;n<list.length();n++){JSONObject h=list.optJSONObject(n);if(h!=null&&validId(h.optString("id")))ids.add(h.optString("id"));}
            JSONObject entities = entities(ids,"labels|aliases|descriptions|claims|sitelinks",language);
            Set<String> classes = new LinkedHashSet<>();
            for(String id:ids)classes.addAll(entityValues(entities.optJSONObject(id),"P31",20));
            loadClasses(classes,language);
            SearchPage page=new SearchPage();page.fetchedAt=System.currentTimeMillis();
            for(String id:ids){JSONObject entity=entities.optJSONObject(id);String kind=kind(entityValues(entity,"P31",20),ancestry);if(kind.isEmpty())continue;
                Item item=basic(entity,language,kind);item.fetchedAt=page.fetchedAt;page.items.add(item);}
            JSONObject payload=new JSONObject().put("fetchedAt",page.fetchedAt).put("mediaSchema",MEDIA_SCHEMA);JSONArray out=new JSONArray();for(Item item:page.items)out.put(item.json());payload.put("items",out);write(cache,payload);
            return page;
        } catch(Exception failure) {
            if(saved!=null){SearchPage old=searchFromJson(saved,true);old.warning="OFFLINE";return old;}throw failure;
        }
    }
    public Item detail(String id,String language,boolean refresh) throws Exception {
        if(!validId(id))throw new IOException("INVALID_ENTITY");
        File cache=cacheFile("detail_"+language+"_"+id);JSONObject saved=read(cache);
        if(!refresh&&fresh(saved)){Item item=Item.fromJson(saved);item.cached=true;return item;}
        try {
            JSONObject entity=entities(Collections.singleton(id),"labels|aliases|descriptions|claims|sitelinks",language).optJSONObject(id);
            List<String> classes=entityValues(entity,"P31",20);loadClasses(new LinkedHashSet<>(classes),language);
            String kind=kind(classes,ancestry);if(kind.isEmpty())throw new IOException("NOT_FILM_OR_SERIES");
            Item item=basic(entity,language,kind);
            LinkedHashSet<String> factIds=new LinkedHashSet<>();for(String property:FACT_PROPERTIES)factIds.addAll(entityValues(entity,property,property.equals("P161")?12:5));
            JSONObject labels=entities(factIds,"labels",language);
            item.directors=names(entity,"P57",labels,language,5);item.cast=names(entity,"P161",labels,language,12);item.genres=names(entity,"P136",labels,language,5);
            item.languages=names(entity,"P364",labels,language,5);item.countries=names(entity,"P495",labels,language,5);
            List<String> ratings=new ArrayList<>();for(String property:new String[]{"P1657","P1981","P2629"}){String value=names(entity,property,labels,language,5);if(!value.isEmpty())ratings.add((property.equals("P1657")?"MPA: ":property.equals("P1981")?"FSK: ":"BBFC: ")+value);}item.ratings=String.join(" · ",ratings);
            item.runtime=runtime(entity);item.seasons=quantity(entity,"P2437");item.episodes=quantity(entity,"P1113");
            if(!item.wikiTitle.isEmpty())try {
                JSONObject summary=request("https://"+item.wikiLanguage+".wikipedia.org/api/rest_v1/page/summary/"+encode(item.wikiTitle).replace("+","%20"));
                applySummary(item,summary);
            }catch(Exception encyclopediaFailure){item.warning="SUMMARY_UNAVAILABLE";}
            for(Map.Entry<String,String> imageCandidate:imageCandidates(entity).entrySet()){item.commonsFile=imageCandidate.getKey();item.imageKind=imageCandidate.getValue();try{resolveCommonsImage(item);if(!item.image.isEmpty())break;}catch(Exception imageFailure){if(item.warning.isEmpty())item.warning="IMAGE_UNAVAILABLE";}}
            item.fetchedAt=System.currentTimeMillis();write(cache,item.json());return item;
        }catch(Exception failure){
            if(saved!=null){Item item=Item.fromJson(saved);item.cached=true;item.warning="OFFLINE";return item;}
            String offline=prefs.getString("media_saved_"+id,"");if(!offline.isEmpty()){Item item=Item.fromJson(new JSONObject(offline));item.cached=true;item.warning="OFFLINE";return item;}throw failure;
        }
    }
    public List<Item> favorites() {
        List<Item> items=new ArrayList<>();for(String id:prefs.getStringSet("media_favorites",Collections.emptySet()))try{String s=prefs.getString("media_saved_"+id,"");if(!s.isEmpty()){Item item=Item.fromJson(new JSONObject(s));item.cached=true;items.add(item);}}catch(Exception ignored){}
        items.sort(Comparator.comparing(i->i.title));return items;
    }
    public boolean isFavorite(String id){return prefs.getStringSet("media_favorites",Collections.emptySet()).contains(id);}
    public void toggleFavorite(Item item)throws Exception {
        Set<String> ids=new HashSet<>(prefs.getStringSet("media_favorites",Collections.emptySet()));SharedPreferences.Editor edit=prefs.edit();
        if(ids.remove(item.id))edit.remove("media_saved_"+item.id);else{ids.add(item.id);edit.putString("media_saved_"+item.id,item.json().toString());}edit.putStringSet("media_favorites",ids).apply();
    }
    public Bitmap image(Item item) {
        if(!licensedImage(item))return null;
        File file;try{file=new File(context.getFilesDir(),"media-cache/open_image_"+item.id+"_"+digest(item.image)+".png");}catch(Exception invalid){return null;}Bitmap cached=BitmapFactory.decodeFile(file.getAbsolutePath());if(cached!=null&&System.currentTimeMillis()-file.lastModified()<CACHE_AGE)return cached;
        HttpURLConnection c=null;
        try{pace();c=(HttpURLConnection)new URL(item.image).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","SmartHubPlus/1.0 (Android TV encyclopedia; personal use)");
            if(c.getResponseCode()!=200)return cached;byte[] data;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>3*1024*1024)return cached;out.write(b,0,n);}data=out.toByteArray();}
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>10000||bounds.outHeight>10000)return cached;
            BitmapFactory.Options opts=new BitmapFactory.Options();while(bounds.outWidth/Math.max(1,opts.inSampleSize)>800||bounds.outHeight/Math.max(1,opts.inSampleSize)>1200)opts.inSampleSize=Math.max(1,opts.inSampleSize)*2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(bitmap!=null){File folder=file.getParentFile();if(folder.isDirectory()||folder.mkdirs())try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}return bitmap;
        }catch(Exception ignored){return cached;}finally{if(c!=null)c.disconnect();}
    }
    /** Work imagery and portraits come only from the work's own claims and credited cast. */
    public List<Item> gallery(String id,String language)throws Exception{
        if(!validId(id))throw new IOException("INVALID_ENTITY");List<Item> result=new ArrayList<>();
        JSONObject work=entities(Collections.singleton(id),"labels|claims",language).optJSONObject(id);if(work==null)return result;
        List<String> images=stringClaims(work,"P18");if(!images.isEmpty()){Item still=new Item();still.id=id;still.title=label(work,"labels",language);still.commonsFile=images.get(0);still.imageKind="related";resolveCommonsImage(still);if(!still.image.isEmpty())result.add(still);}
        List<String> cast=entityValues(work,"P161",4);JSONObject actors=entities(cast,"labels|claims",language);
        for(String actor:cast){JSONObject entity=actors.optJSONObject(actor);List<String> portraits=stringClaims(entity,"P18");if(portraits.isEmpty())continue;Item portrait=new Item();portrait.id=actor;portrait.title=label(entity,"labels",language);portrait.commonsFile=portraits.get(0);portrait.imageKind="portrait";resolveCommonsImage(portrait);if(!portrait.image.isEmpty())result.add(portrait);}
        return result;
    }
    private JSONObject request(String url)throws Exception {pace();JSONObject j=new JSONObject(HubHttp.get(url,6*1024*1024));if(j.has("error"))throw new IOException("SOURCE_UNAVAILABLE");return j;}
    private void resolveCommonsImage(Item item)throws Exception {
        String expected="File:"+item.commonsFile.replace('_',' ');
        JSONObject response=request("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1920&titles="+encode(expected));
        JSONObject query=response.optJSONObject("query");JSONArray pages=query==null?null:query.optJSONArray("pages");
        if(pages==null)return;
        for(int n=0;n<pages.length();n++){JSONObject page=pages.optJSONObject(n);if(page==null||page.has("missing")||!normalizedFileTitle(expected).equals(normalizedFileTitle(page.optString("title"))))continue;JSONArray infos=page.optJSONArray("imageinfo");if(infos!=null&&infos.length()>0)applyCommonsImage(item,infos.optJSONObject(0));}
    }
    private static String normalizedFileTitle(String title){String value=title.replace('_',' ').trim();if(value.startsWith("File:")&&value.length()>5)value=value.substring(0,5)+value.substring(5,6).toUpperCase(Locale.ROOT)+value.substring(6);return value;}
    static boolean applyCommonsImage(Item item,JSONObject info){
        if(info==null)return false;JSONObject meta=info.optJSONObject("extmetadata");
        String license=metadata(meta,"LicenseShortName"),licenseUrl=metadata(meta,"LicenseUrl"),terms=metadata(meta,"UsageTerms");
        if(!openLicense(license,licenseUrl,terms))return false;
        String image=info.optString("thumburl",info.optString("url")),page=info.optString("descriptionurl");
        if(!safeImage(image)||!safeCommonsPage(page))return false;
        item.image=image;item.imagePage=page;item.imageLicense=license.isEmpty()?terms:license;item.imageLicenseUrl=licenseUrl;
        String artist=metadata(meta,"Artist"),credit=metadata(meta,"Credit");
        item.imageCredit=artist+(artist.isEmpty()||credit.isEmpty()?"":" · ")+credit;
        if("true".equalsIgnoreCase(metadata(meta,"AttributionRequired"))&&item.imageCredit.isEmpty())item.imageCredit="Wikimedia Commons · "+item.commonsFile;
        return true;
    }
    static boolean openLicense(String name,String address,String terms){
        String value=(name+" "+terms).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(value.matches(".*(fair.?use|non.?free|all rights reserved|non.?commercial|no.?derivatives|cc[^,;]*[- ]nc|cc[^,;]*[- ]nd).*"))return false;
        try{URL u=new URL(address);String p=u.getPath().toLowerCase(Locale.ROOT);if(("https".equals(u.getProtocol())||"http".equals(u.getProtocol()))&&("creativecommons.org".equals(u.getHost())||"www.creativecommons.org".equals(u.getHost()))&&(p.matches("/licenses/(by|by-sa)/[1-4]\\.[0-9]/.*")||p.matches("/publicdomain/(zero|mark)/1\\.0/.*")))return true;}catch(Exception ignored){}
        String shortName=name.trim().toLowerCase(Locale.ROOT);
        return shortName.equals("public domain")||shortName.equals("pdm")||shortName.matches("cc0(?: 1\\.0)?")||shortName.matches("cc by(?:-sa)? [1-4]\\.[0-9](?: [a-z -]+)?");
    }
    private static String metadata(JSONObject meta,String key){JSONObject entry=meta==null?null:meta.optJSONObject(key);return plain(entry==null?"":entry.optString("value"));}
    private static String plain(String value){return value.replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replaceAll("\\s+"," ").trim();}
    private static boolean safeCommonsPage(String address){try{URL u=new URL(address);return "https".equals(u.getProtocol())&&"commons.wikimedia.org".equals(u.getHost())&&URLDecoder.decode(u.getPath(),"UTF-8").startsWith("/wiki/File:");}catch(Exception ignored){return false;}}
    private static boolean licensedImage(Item item){return safeImage(item.image)&&safeCommonsPage(item.imagePage)&&openLicense(item.imageLicense,item.imageLicenseUrl,"");}
    private synchronized void pace()throws InterruptedException {long wait=1100-(System.currentTimeMillis()-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=System.currentTimeMillis();}
    private JSONObject entities(Collection<String> ids,String props,String lang)throws Exception {
        JSONObject result=new JSONObject();List<String> all=new ArrayList<>(ids);
        for(int start=0;start<all.size();start+=40){List<String> batch=all.subList(start,Math.min(start+40,all.size()));JSONObject chunk=request(API+"action=wbgetentities&ids="+encode(String.join("|",batch))+"&props="+encode(props)+"&languages="+encode(lang+"|en")+"&languagefallback=1").optJSONObject("entities");if(chunk==null)throw new IOException("INVALID_SOURCE_RESPONSE");Iterator<String> keys=chunk.keys();while(keys.hasNext()){String id=keys.next();result.put(id,chunk.get(id));}}
        return result;
    }
    private void loadClasses(Set<String> ids,String lang)throws Exception {
        Set<String> pending=new LinkedHashSet<>(ids);for(int depth=0;depth<3;depth++){pending.removeIf(id->FILM.contains(id)||SERIES.contains(id)||ancestry.containsKey(id));if(pending.isEmpty())break;
            List<String> batch=new ArrayList<>(pending);if(batch.size()>40)batch=batch.subList(0,40);JSONObject classes=entities(batch,"claims",lang);Set<String> next=new LinkedHashSet<>();for(String id:batch){List<String> parents=entityValues(classes.optJSONObject(id),"P279",12);ancestry.put(id,parents);next.addAll(parents);}pending=next;}
    }
    static String kind(List<String> classes,Map<String,List<String>> ancestry){for(String id:classes)if(reaches(id,FILM,ancestry,new HashSet<>(),0))return "film";for(String id:classes)if(reaches(id,SERIES,ancestry,new HashSet<>(),0))return "series";return "";}
    private static boolean reaches(String id,Set<String> bases,Map<String,List<String>> graph,Set<String> visited,int depth){if(bases.contains(id))return true;if(depth>5||!visited.add(id))return false;for(String parent:graph.getOrDefault(id,Collections.emptyList()))if(reaches(parent,bases,graph,visited,depth+1))return true;return false;}
    static List<String> entityValues(JSONObject entity,String property,int limit){List<String> ids=new ArrayList<>();for(JSONObject value:claimValues(entity,property)){String id=value.optString("id");if(validId(id)&&!ids.contains(id))ids.add(id);if(ids.size()>=limit)break;}return ids;}
    private static List<JSONObject> claimValues(JSONObject entity,String property){List<JSONObject> values=new ArrayList<>();JSONObject claims=entity==null?null:entity.optJSONObject("claims");JSONArray rows=claims==null?null:claims.optJSONArray(property);if(rows==null)return values;for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null||"deprecated".equals(row.optString("rank")))continue;JSONObject snak=row.optJSONObject("mainsnak");if(snak==null||!"value".equals(snak.optString("snaktype")))continue;JSONObject data=snak.optJSONObject("datavalue");JSONObject value=data==null?null:data.optJSONObject("value");if(value!=null)values.add(value);}return values;}
    static Item basic(JSONObject entity,String lang,String kind){Item i=new Item();if(entity==null)return i;i.id=entity.optString("id");i.title=label(entity,"labels",lang);if(i.title.isEmpty())i.title=i.id;JSONObject allLabels=entity.optJSONObject("labels"),allAliases=entity.optJSONObject("aliases");if(allLabels!=null){Iterator<String> keys=allLabels.keys();while(keys.hasNext()){JSONObject l=allLabels.optJSONObject(keys.next());if(l!=null)i.matchTitles.add(l.optString("value"));}}if(allAliases!=null){Iterator<String> keys=allAliases.keys();while(keys.hasNext()){JSONArray a=allAliases.optJSONArray(keys.next());if(a!=null)for(int n=0;n<Math.min(a.length(),15);n++){JSONObject alias=a.optJSONObject(n);if(alias!=null)i.matchTitles.add(alias.optString("value"));}}}i.description=label(entity,"descriptions",lang);i.kind=kind;i.release=release(entity);JSONObject links=entity.optJSONObject("sitelinks");JSONObject link=links==null?null:links.optJSONObject(lang+"wiki");i.wikiLanguage=lang;if(link==null&&links!=null){link=links.optJSONObject("enwiki");i.wikiLanguage="en";}if(link!=null)i.wikiTitle=link.optString("title");if("film".equals(kind)||"series".equals(kind)){for(String value:stringClaims(entity,"P345"))if(value.matches("tt[0-9]{7,10}")){i.imdbId=value;break;}for(Map.Entry<String,String> entry:imageCandidates(entity).entrySet()){i.commonsFile=entry.getKey();i.imageKind=entry.getValue();break;}}return i;}
    private static LinkedHashMap<String,String> imageCandidates(JSONObject entity){LinkedHashMap<String,String> files=new LinkedHashMap<>();for(String property:new String[]{"P3383","P18"}){int count=0;for(String file:stringClaims(entity,property)){String value=file.trim().replaceFirst("(?i)^File:","");if(value.isEmpty()||value.length()>=500)continue;files.putIfAbsent(value,property.equals("P3383")?"poster":"image");if(++count>=2)break;}}return files;}
    private static List<String> stringClaims(JSONObject entity,String property){List<String> values=new ArrayList<>();JSONObject claims=entity==null?null:entity.optJSONObject("claims");JSONArray rows=claims==null?null:claims.optJSONArray(property);if(rows==null)return values;for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null||"deprecated".equals(row.optString("rank")))continue;JSONObject snak=row.optJSONObject("mainsnak");JSONObject data=snak==null?null:snak.optJSONObject("datavalue");Object value=data==null?null:data.opt("value");if("value".equals(snak==null?"":snak.optString("snaktype"))&&value instanceof String)values.add((String)value);}return values;}
    static String label(JSONObject entity,String property,String lang){JSONObject object=entity==null?null:entity.optJSONObject(property);JSONObject value=object==null?null:object.optJSONObject(lang);if(value==null&&object!=null)value=object.optJSONObject("en");return value==null?"":value.optString("value","");}
    static String release(JSONObject entity){List<String> dates=new ArrayList<>();for(JSONObject v:claimValues(entity,"P577")){String time=v.optString("time");int precision=v.optInt("precision");if(time.startsWith("+")&&time.length()>=11){String date=time.substring(1,precision>=11?11:precision>=10?8:5);if(!dates.contains(date))dates.add(date);}}Collections.sort(dates);return dates.isEmpty()?"":dates.get(0);}
    static String runtime(JSONObject entity){for(JSONObject v:claimValues(entity,"P2047"))try{double amount=Double.parseDouble(v.optString("amount"));String unit=v.optString("unit");if(unit.endsWith("/Q11574"))amount/=60;else if(unit.endsWith("/Q25235"))amount*=60;else if(!unit.endsWith("/Q7727"))continue;if(amount>0&&amount<10000)return (amount==Math.rint(amount)?Long.toString(Math.round(amount)):String.format(Locale.ROOT,"%.1f",amount))+" min";}catch(Exception ignored){}return "";}
    private static String quantity(JSONObject entity,String property){for(JSONObject v:claimValues(entity,property))try{double n=Double.parseDouble(v.optString("amount"));if(n>=0&&n==Math.rint(n))return Long.toString(Math.round(n));}catch(Exception ignored){}return "";}
    private static String names(JSONObject entity,String property,JSONObject labels,String lang,int limit){List<String> names=new ArrayList<>();for(String id:entityValues(entity,property,limit)){String name=label(labels.optJSONObject(id),"labels",lang);if(!name.isEmpty())names.add(name);}return String.join("، ",names);}
    static void applySummary(Item item,JSONObject summary){if(!item.id.equals(summary.optString("wikibase_item"))||"disambiguation".equals(summary.optString("type")))return;item.summary=summary.optString("extract","");JSONObject urls=summary.optJSONObject("content_urls");JSONObject desktop=urls==null?null:urls.optJSONObject("desktop");String article=desktop==null?"":desktop.optString("page","");try{URL url=new URL(article);if("https".equals(url.getProtocol())&&url.getHost().endsWith(".wikipedia.org"))item.article=article;}catch(Exception ignored){}}
    static boolean safeImage(String image){try{URL u=new URL(image);return "https".equals(u.getProtocol())&&("upload.wikimedia.org".equals(u.getHost())||"thumb.wikimedia.org".equals(u.getHost()))&&u.getPath().startsWith("/wikipedia/commons/");}catch(Exception ignored){return false;}}
    static boolean validId(String id){return id!=null&&id.matches("Q[1-9][0-9]*");}
    private SearchPage searchFromJson(JSONObject j,boolean cached){SearchPage p=new SearchPage();p.cached=cached;p.fetchedAt=j.optLong("fetchedAt");JSONArray items=j.optJSONArray("items");if(items!=null)for(int n=0;n<items.length();n++){JSONObject row=items.optJSONObject(n);if(row!=null){Item i=Item.fromJson(row);i.cached=cached;p.items.add(i);}}return p;}
    private boolean fresh(JSONObject j){return j!=null&&j.optInt("mediaSchema")==MEDIA_SCHEMA&&j.optLong("fetchedAt")>0&&System.currentTimeMillis()-j.optLong("fetchedAt")<CACHE_AGE;}
    private File cacheFile(String name){return new File(context.getFilesDir(),"media-cache/"+name+".json");}
    private static JSONObject read(File file){try{if(!file.isFile()||file.length()>6*1024*1024)return null;ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception ignored){return null;}}
    private static void write(File file,JSONObject j){try{File parent=file.getParentFile();if(parent.isDirectory()||parent.mkdirs())try(FileOutputStream out=new FileOutputStream(file)){out.write(j.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
    private static String encode(String text)throws Exception{return URLEncoder.encode(text,"UTF-8");}
    private static String digest(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
