package com.smarthub.plus;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
/** Direct, key-free BetterPosters Stremio catalogue and metadata provider. */
final class BetterPostersProvider {
 private static final String MANIFEST_URL="https://btttr.cc/VZAxbsMwDEXv8mf1ArpAkaFL063IQDiEasAWXZJCEBi-eyA5jp3t871PDpzRkdMgyRB_MZCz-ceIsEVDgMuV7g2uaWeEgE7GPqemn9H2WAuunK9b5TXU0iRTGUib2PJ6fMIlYGQnxHmp0pwVcW4qgopL3VFJymaIroWfx38ovcB_oaH3-xHZn9w-OSsfwTd5n9NGKPE7MFFHROYbm7ffVAdSBGhrnqVox4g4fWFZHg/manifest.json";
 private final Context context;private boolean offline;private long fetchedAt=System.currentTimeMillis();private JSONObject loadedManifest;
 BetterPostersProvider(Context context){this.context=context.getApplicationContext();}
 static String titleTokens(String value){String s=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("['’]s\\b","").replaceAll("[^\\p{L}\\p{N}]+"," ");List<String> words=new ArrayList<>();Set<String> skip=new HashSet<>(Arrays.asList("the","a","an","of","and"));for(String w:s.trim().split("\\s+")){if(w.isEmpty()||skip.contains(w))continue;words.add(w.length()>4&&w.endsWith("s")?w.substring(0,w.length()-1):w);}Collections.sort(words);return String.join(" ",words);}
 private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20");} 
 private JSONObject manifest(boolean force)throws Exception{
  if(!force&&loadedManifest!=null)return loadedManifest;
  android.content.SharedPreferences prefs=context.getSharedPreferences("poster_sources",0);
  String cached=prefs.getString("betterposters_manifest","");
  long cachedAt=prefs.getLong("betterposters_manifest_at",0),now=System.currentTimeMillis();
  if(!force&&!cached.isEmpty()&&now-cachedAt<4*3600000L){try{JSONObject value=new JSONObject(cached);validateManifest(value);loadedManifest=value;fetchedAt=cachedAt;return value;}catch(Exception malformedCache){/* fetch a fresh manifest below */}}
  try{
   JSONObject value=new JSONObject(HubHttp.get(MANIFEST_URL,512*1024));validateManifest(value);
   prefs.edit().putString("betterposters_manifest",value.toString()).putLong("betterposters_manifest_at",now).putString("betterposters_manifest_status","OK").apply();
   loadedManifest=value;fetchedAt=now;offline=false;return value;
  }catch(Exception error){
   if(!cached.isEmpty()&&now-cachedAt<7L*24*3600000L){JSONObject value=new JSONObject(cached);validateManifest(value);loadedManifest=value;fetchedAt=cachedAt;offline=true;prefs.edit().putString("betterposters_manifest_status","STALE_CACHE").apply();return value;}
   prefs.edit().putString("betterposters_manifest_status","UNAVAILABLE_"+error.getClass().getSimpleName()).apply();throw error;
  }
 }
 private static void validateManifest(JSONObject m)throws IOException{
  if(!"community.betterposters".equals(m.optString("id"))||!"1.0.0".equals(m.optString("version")))throw new IOException("BetterPosters manifest identity/version mismatch");
  JSONArray types=m.optJSONArray("types"),resources=m.optJSONArray("resources"),catalogs=m.optJSONArray("catalogs");
  if(types==null||resources==null||catalogs==null||!contains(types,"movie")||!contains(types,"series")||!contains(resources,"meta")||!contains(resources,"catalog"))throw new IOException("BetterPosters manifest capabilities incomplete");
 }
 private static boolean contains(JSONArray a,String value){for(int i=0;i<a.length();i++)if(value.equals(a.optString(i)))return true;return false;}
 private static boolean supportsSearch(JSONObject manifest,String type){
  JSONArray catalogs=manifest.optJSONArray("catalogs");if(catalogs==null)return false;
  for(int i=0;i<catalogs.length();i++){JSONObject c=catalogs.optJSONObject(i);if(c!=null&&"bp-search".equals(c.optString("id"))&&type.equals(c.optString("type")))return true;}
  return false;
 }
 JSONObject refreshManifest()throws Exception{return manifest(true);}

 private JSONObject json(String url)throws Exception{String key=Integer.toHexString(url.hashCode());File dir=new File(context.getCacheDir(),"poster-metadata");dir.mkdirs();File file=new File(dir,key+".json");try{String text=HubHttp.get(url,2*1024*1024);JSONObject value=new JSONObject(text);try(FileOutputStream out=new FileOutputStream(file)){out.write(new JSONObject().put("url",url).put("at",System.currentTimeMillis()).put("data",value).toString().getBytes(StandardCharsets.UTF_8));}File[] all=dir.listFiles();if(all!=null&&all.length>80){Arrays.sort(all,Comparator.comparingLong(File::lastModified));for(int n=0;n<all.length-80;n++)all[n].delete();}return value;}catch(Exception error){if(file.isFile())try(InputStream in=new FileInputStream(file)){JSONObject old=new JSONObject(PlaylistRepository.readBounded(in));if(url.equals(old.optString("url"))){offline=true;fetchedAt=Math.min(fetchedAt,old.optLong("at"));return old.getJSONObject("data");}}throw error;}}
 private static String image(String address){try{URI u=new URI(address);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&Arrays.asList("btttr.cc","image.tmdb.org").contains(u.getHost())?address:"";}catch(Exception e){return "";}}
 private static String imdb(JSONObject meta){for(String v:new String[]{meta.optString("imdb_id"),meta.optString("imdbId"),meta.optJSONObject("behaviorHints")==null?"":meta.optJSONObject("behaviorHints").optString("defaultVideoId"),meta.optString("id")})if(v.matches("tt[0-9]{7,10}"))return v;JSONArray links=meta.optJSONArray("links");if(links!=null)for(int n=0;n<links.length();n++)try{URI u=new URI(links.getJSONObject(n).optString("url"));if("https".equals(u.getScheme())&&Arrays.asList("imdb.com","www.imdb.com").contains(u.getHost())){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^/title/(tt[0-9]{7,10})(?:/|$)").matcher(u.getPath());if(m.find())return m.group(1);}}catch(Exception ignored){}return "";}
 private static int year(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:19|20)[0-9]{2}").matcher(value);return m.find()?Integer.parseInt(m.group()):0;}
 private JSONObject meta(String type,String id)throws Exception{JSONObject mf=manifest(false);if(!contains(mf.optJSONArray("types"),type)||!contains(mf.optJSONArray("resources"),"meta"))throw new IOException("BetterPosters meta resource not advertised");if(!Arrays.asList("movie","series").contains(type)||!id.matches("(?:tt[0-9]{7,10}|tmdb:[1-9][0-9]{0,10})"))throw new IOException("Invalid programme identity");String source="https://btttr.cc/meta/"+type+"/"+enc(id)+".json";JSONObject m=json(source).optJSONObject("meta");if(m==null||!id.equals(m.optString("id"))||!type.equals(m.optString("type")))throw new IOException("Programme identity mismatch");String identity=imdb(m);if(id.startsWith("tt")&&!id.equals(identity))throw new IOException("IMDb identity mismatch");JSONArray cast=new JSONArray(),directors=new JSONArray(),links=m.optJSONArray("links");if(links!=null)for(int n=0;n<links.length();n++){JSONObject link=links.optJSONObject(n);if(link==null)continue;if("Cast".equals(link.optString("category"))&&cast.length()<12)cast.put(link.optString("name"));if("Directors".equals(link.optString("category"))&&directors.length()<4)directors.put(link.optString("name"));}return new JSONObject().put("status","matched").put("id",id).put("type",type).put("imdbId",identity).put("title",m.optString("name")).put("year",year(m.optString("releaseInfo"))).put("poster",image(m.optString("poster"))).put("background",image(m.optString("background"))).put("description",m.optString("description")).put("runtime",m.optString("runtime")).put("rating",m.optString("imdbRating")).put("genres",m.optJSONArray("genres")).put("cast",cast).put("directors",directors).put("source",source).put("provider","BetterPosters").put("cached",offline).put("fetchedAt",fetchedAt);}
 JSONObject resolve(String raw,String chosenId,String chosenType,String identity,double duration)throws Exception{BroadcastTitle wanted=BroadcastTitle.parse(raw);if(!chosenId.isEmpty())return meta(chosenType,chosenId);if(identity.matches("tt[0-9]{7,10}")){for(String t:wanted.season>0||wanted.episode>0?new String[]{"series"}:new String[]{"movie","series"})try{return meta(t,identity);}catch(Exception ignored){}}
  List<JSONObject> candidates=new ArrayList<>(),exact=new ArrayList<>();int success=0;Exception failure=null;JSONObject mf=manifest(false);for(String type:wanted.season>0?new String[]{"series"}:new String[]{"movie","series"})try{if(!supportsSearch(mf,type))continue;JSONArray rows=json("https://btttr.cc/catalog/"+type+"/bp-search/search="+enc(wanted.title)+".json").optJSONArray("metas");if(rows==null)throw new IOException("Invalid BetterPosters search");success++;for(int n=0;n<Math.min(50,rows.length());n++){JSONObject m=rows.optJSONObject(n);if(m==null||!type.equals(m.optString("type"))||!m.optString("id").matches("(?:tt[0-9]{7,10}|tmdb:[1-9][0-9]{0,10})"))continue;int y=year(m.optString("releaseInfo"));if(wanted.year>0&&!type.equals("series")&&wanted.year!=y)continue;JSONObject item=new JSONObject().put("id",m.optString("id")).put("type",type).put("title",m.optString("name")).put("year",y).put("releaseDate",m.optString("released")).put("poster",image(m.optString("poster")));candidates.add(item);if(titleTokens(wanted.title).equals(titleTokens(m.optString("name"))))exact.add(item);}}catch(Exception e){failure=e;}if(success==0)throw failure==null?new IOException("BetterPosters unavailable"):failure;JSONObject newest=ProgrammeChoice.first(exact,wanted.year,wanted.season>0||wanted.episode>0,java.time.LocalDate.now().toString());if(newest!=null)return meta(newest.getString("type"),newest.getString("id"));
  if(exact.isEmpty()&&candidates.size()==1){JSONObject selected=candidates.get(0),found=meta(selected.getString("type"),selected.getString("id"));Set<String> generic=new HashSet<>(Arrays.asList("movie","film","episode","series","season","story","secret","world","night","legend"));boolean anchor=false;for(String token:titleTokens(wanted.title).split(" "))if(token.length()>=5&&!generic.contains(token)&&Arrays.asList(titleTokens(found.optString("title")).split(" ")).contains(token))anchor=true;java.util.regex.Matcher r=java.util.regex.Pattern.compile("^[0-9]+(?:\\.[0-9]+)?").matcher(found.optString("runtime"));double minutes=r.find()?Double.parseDouble(r.group()):0;if(anchor&&duration>=15&&duration<=300&&minutes>0&&Math.abs(duration-minutes)<=Math.max(4,minutes*.08))return found;}
  return new JSONObject().put("status",candidates.isEmpty()?"not_found":"ambiguous").put("candidates",new JSONArray(exact.isEmpty()?candidates:exact));
 }
}
