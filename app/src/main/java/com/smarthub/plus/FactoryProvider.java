package com.smarthub.plus;
import android.content.*;import android.graphics.*;import org.json.*;import java.net.URL;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import java.util.concurrent.*;
/** Factory CHMAX channel artwork + current/next online EPG, fetched outside the rendering thread. */
final class FactoryProvider implements AutoCloseable {
 private final Context context;private final SharedPreferences prefs;private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean closed,busy;private String requestKey="";private long retry,blockedUntil;private volatile SatelliteState cached;private volatile String cachedIdentity="";
 FactoryProvider(Context c){context=c.getApplicationContext();prefs=c.getSharedPreferences("smarthub",0);}
 void enrich(SatelliteState state){if(!prefs.getBoolean("factory_enabled",true)||state.channelName.isEmpty()||closed)return;String key=CHMAXIntegration.identity(state);SatelliteState data=cached;if(data!=null&&key.equals(cachedIdentity)){SatelliteState copy=data.copy();copy.expire(System.currentTimeMillis());if(!copy.title.isEmpty()&&(state.title.isEmpty()||FactoryProtocol.sameTitle(state.title,copy.title))){state.title=copy.title;state.startMs=copy.startMs;state.endMs=copy.endMs;state.image=copy.image;state.source="Factory CHMAX / EPG";}if(!copy.nextTitle.isEmpty()&&(state.nextTitle.isEmpty()||FactoryProtocol.sameTitle(state.nextTitle,copy.nextTitle))){state.nextTitle=copy.nextTitle;state.nextStartMs=copy.nextStartMs;state.nextEndMs=copy.nextEndMs;state.nextImage=copy.nextImage;}}long now=System.currentTimeMillis();String contextKey=key+"|"+state.startMs+"|"+state.title;if(busy||now<blockedUntil||contextKey.equals(requestKey)&&now<retry)return;busy=true;requestKey=contextKey;retry=now+60000;SatelliteState snapshot=state.copy();io.execute(()->{try{fetch(snapshot,key);prefs.edit().putString("factory_status","FACTORY_EPG_CONNECTED").putLong("factory_last_success",System.currentTimeMillis()).apply();}catch(Exception e){retry=System.currentTimeMillis()+60000;String failure=FactoryClient.safe(e);if(!failure.contains("CHANNEL")&&!failure.contains("PROGRAMME")&&!failure.contains("NOT_PUBLISHED"))blockedUntil=System.currentTimeMillis()+300000;prefs.edit().putString("factory_status",failure).apply();}finally{busy=false;}});}
 private void fetch(SatelliteState state,String identity)throws Exception{FactoryClient client=new FactoryClient(context);ZoneId zone=ZoneId.of(prefs.getString("tv_epg_timezone","Asia/Baghdad"));long now=System.currentTimeMillis();Map<String,String> p=new LinkedHashMap<>();p.put("cid",Integer.toString(client.identity().optInt("cid",-1)));p.put("timezone",ZonedDateTime.now(zone).getOffset().getId());p.put("language","en");DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyy-MM-dd");p.put("start",Instant.ofEpochMilli(now-3600000).atZone(zone).format(f));p.put("end",Instant.ofEpochMilli(now+24*3600000L).atZone(zone).format(f));p.put("pageSize","25");p.put("pageNum","1");String nativeKey=SportsCache.key(identity);String channel=prefs.getString("factory_channel:"+nativeKey,"");if(!FactoryProtocol.reference(channel)){// CHMAX channel search is about SERVICE identity, never the now-playing programme.
 p.put("searchKey",state.channelName.trim());JSONObject search=client.get("/epgapi/v1/schedule/search",p);channel=FactoryProtocol.channelKey(search,state.channelName,state.title,now,zone);if(channel.isEmpty())throw new Exception("FACTORY_CHANNEL_NOT_MATCHED");String satellite="";JSONArray rows=search.optJSONObject("data").optJSONArray("channelList");if(rows!=null)for(int i=0;i<Math.min(50,rows.length());i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&channel.equals(row.optString("id")))satellite=row.optString("satellite");}String reference=FactoryProtocol.resolveReference(search,channel,null);if(reference.isEmpty()&&!satellite.isEmpty()&&satellite.length()<100)reference=FactoryProtocol.resolveReference(search,channel,client.get("/epgapi/v1/satellite/channel/list",Collections.singletonMap("querySats",satellite)));if(!FactoryProtocol.reference(reference))throw new Exception("FACTORY_CHANNEL_REFERENCE_UNAVAILABLE");channel=reference;prefs.edit().putString("factory_channel:"+nativeKey,channel).apply();}
  Map<String,String> query=new LinkedHashMap<>();query.put("sn",client.identity().optString("sn"));query.put("sat",channel);query.put("start",p.get("start"));query.put("end",p.get("end"));query.put("timezone",p.get("timezone"));query.put("language","en");query.put("simple","0");JSONObject reply=client.get("/epgapi/v1/schedule/list",query);JSONObject data=reply.optJSONObject("data");
  if(data==null||!PublishedEpgArtwork.matchesChannel(state.channelName,data.optString("name")))
    throw new Exception("FACTORY_CHANNEL_MISMATCH");
  // The logo is channel-scoped. A missing or mismatched EPG programme must
  // not suppress a correctly identified channel logo.
  rememberLogo(identity,data.optString("cimg"));
  List<FactoryProtocol.Event> events=FactoryProtocol.schedule(reply,zone);FactoryProtocol.Event current=null,next=null;for(FactoryProtocol.Event e:events){if(e.start<=now&&e.end>now)current=e;else if(e.start>now&&next==null)next=e;}if(current==null)throw new Exception("FACTORY_EPG_NOT_PUBLISHED");if(!state.title.isEmpty()&&!FactoryProtocol.sameTitle(state.title,current.title))throw new Exception("FACTORY_PROGRAMME_MISMATCH");if(state.startMs>0&&Math.abs(state.startMs-current.start)>120000||state.endMs>0&&Math.abs(state.endMs-current.end)>120000)throw new Exception("FACTORY_PROGRAMME_MISMATCH");SatelliteState resolved=state.copy();resolved.title=current.title;resolved.startMs=current.start;resolved.endMs=current.end;resolved.image=current.pic;if(next!=null){resolved.nextTitle=next.title;resolved.nextStartMs=next.start;resolved.nextEndMs=next.end;resolved.nextImage=next.pic;}resolved.source="Factory CHMAX / EPG";resolved.observedAt=now;if(closed)return;SatelliteState active=SatelliteState.read(context);if(!CHMAXIntegration.identity(active).equals(identity))return;cached=resolved;cachedIdentity=identity;resolved.save(context);SharedPreferences.Editor edit=prefs.edit().putLong("epg_art_changed_at",now).putString("factory_current_subtitle_metadata",current.subtitle);edit.apply();}
 private void rememberLogo(String identity,String raw){
  String logo=FactoryProtocol.image(raw);
  if(!logo.startsWith("https://")){
    prefs.edit().putString("factory_logo_status",logo.isEmpty()?
      "FACTORY_CHANNEL_LOGO_NOT_PUBLISHED":"FACTORY_LOGO_REQUIRES_HTTPS").apply();
    return;
  }
  String key=SportsCache.key(identity);
  prefs.edit().putString("factory_logo:"+key,logo)
    .putLong("factory_logo_time:"+key,System.currentTimeMillis())
    .putLong("native_logos_changed_at",System.currentTimeMillis())
    .putString("factory_logo_status","FACTORY_LOGO_URL_READY").apply();
 }
 static Bitmap logo(Context c,String name,String id){return logo(c,name,id,false);}
 /** UI-safe cached-only path; opening the channel list never blocks on CHMAX network IO. */
 static Bitmap cachedLogo(Context c,String name,String id){return logo(c,name,id,true);}
 private static Bitmap logo(Context c,String name,String id,boolean cachedOnly){
  try{
   SatelliteState s=new SatelliteState();s.channelName=name;s.channelId=id;
   String key=SportsCache.key(CHMAXIntegration.identity(s));
   SharedPreferences p=c.getSharedPreferences("smarthub",0);
   if(System.currentTimeMillis()-p.getLong("factory_logo_time:"+key,0)>86400000)return null;
   String url=p.getString("factory_logo:"+key,"");
   if(!url.startsWith("https://"))return null;
   URL link=new URL(url);
   byte[] bytes=cachedOnly?ArtworkCache.cached(c,link,4*1024*1024):
     ArtworkCache.fetch(c,link,4*1024*1024);
   if(bytes==null)return null;
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
   if(o.outWidth<1||o.outHeight<1||o.outWidth>10000||o.outHeight>10000)return null;
   o.inSampleSize=1;
   while(o.outWidth/o.inSampleSize>1024||o.outHeight/o.inSampleSize>1024)o.inSampleSize*=2;
   o.inJustDecodeBounds=false;
   return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
  }catch(Exception e){return null;}
 }
 public void close(){closed=true;io.shutdownNow();}
}
