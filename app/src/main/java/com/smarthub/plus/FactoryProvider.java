package com.smarthub.plus;
import android.content.*;import android.graphics.*;import org.json.*;import java.net.URL;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import java.util.concurrent.*;
/** Factory CHMAX channel artwork + current/next online EPG, fetched outside the rendering thread. */
final class FactoryProvider implements AutoCloseable {
 private static final ExecutorService BEFORE_ZAP_IO=Executors.newSingleThreadExecutor();
 private static final ExecutorService AUTO_LOGO_IO=Executors.newSingleThreadExecutor();
 private static final ConcurrentHashMap<String,Long> AUTO_LOGO_RETRY=new ConcurrentHashMap<>();
 private static final ConcurrentHashMap<String,Long> BEFORE_ZAP_RETRY=new ConcurrentHashMap<>();
 private final Context context;private final SharedPreferences prefs;private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean closed,busy;private String requestKey="";private long retry,blockedUntil;private volatile SatelliteState cached;private volatile String cachedIdentity="";
 FactoryProvider(Context c){context=c.getApplicationContext();prefs=c.getSharedPreferences("smarthub",0);}
 void enrich(SatelliteState state){if(!prefs.getBoolean("factory_enabled",true)||state.channelName.isEmpty()||closed)return;String key=CHMAXIntegration.identity(state);SatelliteState data=cached;if(data!=null&&key.equals(cachedIdentity)){SatelliteState copy=data.copy();copy.expire(System.currentTimeMillis());if(!copy.title.isEmpty()&&copy.startMs<=System.currentTimeMillis()&&copy.endMs>System.currentTimeMillis()){state.title=copy.title;state.startMs=copy.startMs;state.endMs=copy.endMs;state.image=copy.image;state.source="Factory CHMAX / EPG";}if(!copy.nextTitle.isEmpty()&&copy.nextStartMs>System.currentTimeMillis()){state.nextTitle=copy.nextTitle;state.nextStartMs=copy.nextStartMs;state.nextEndMs=copy.nextEndMs;state.nextImage=copy.nextImage;}}// Keep previously verified CHMAX EPG visible, but never start a fresh
 // manufacturer EPG/network request during high-priority 4:2:2 decoding.
 if(PlaybackResourceMode.active()||prefs.getBoolean("software_dvb_422_active",false))return;
 long now=System.currentTimeMillis();String contextKey=key+"|"+state.startMs+"|"+state.title;if(busy||now<blockedUntil||!FactoryEpgGuard.canRequest(prefs,now)||
    contextKey.equals(requestKey)&&now<retry)return;busy=true;requestKey=contextKey;retry=now+60000;SatelliteState snapshot=state.copy();io.execute(()->{try{fetch(snapshot,key);FactoryEpgGuard.success(prefs);prefs.edit().putString("factory_status","FACTORY_EPG_CONNECTED").putLong("factory_last_success",System.currentTimeMillis()).apply();}catch(Exception e){retry=System.currentTimeMillis()+60000;String failure=FactoryClient.safe(e);FactoryEpgGuard.failure(prefs,failure,System.currentTimeMillis());if(!failure.contains("CHANNEL")&&!failure.contains("PROGRAMME")&&!failure.contains("NOT_PUBLISHED"))blockedUntil=System.currentTimeMillis()+300000;prefs.edit().putString("factory_status",failure).apply();}finally{busy=false;}});}
 private void fetch(SatelliteState state,String identity)throws Exception{fetch(state,identity,false);}
 private void fetch(SatelliteState state,String identity,boolean preZapping)throws Exception{FactoryClient client=new FactoryClient(context);ZoneId zone=ZoneId.of(prefs.getString("tv_epg_timezone","Asia/Baghdad"));long now=System.currentTimeMillis();Map<String,String> p=new LinkedHashMap<>();p.put("cid",Integer.toString(client.identity().optInt("cid",-1)));p.put("timezone",ZonedDateTime.now(zone).getOffset().getId());p.put("language","en");DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyy-MM-dd");p.put("start",Instant.ofEpochMilli(now-3600000).atZone(zone).format(f));p.put("end",Instant.ofEpochMilli(now+24*3600000L).atZone(zone).format(f));p.put("pageSize","25");p.put("pageNum","1");String nativeKey=SportsCache.key(identity);String channel=prefs.getString("factory_channel:"+nativeKey,"");if(!FactoryProtocol.reference(channel)){// CHMAX channel search is about SERVICE identity, never the now-playing programme.
 p.put("searchKey",state.channelName.trim());JSONObject search=client.get("/epgapi/v1/schedule/search",p);channel=FactoryProtocol.channelKey(search,state.channelName,state.title,now,zone);if(channel.isEmpty())throw new Exception("FACTORY_CHANNEL_NOT_MATCHED");String satellite="";JSONArray rows=search.optJSONObject("data").optJSONArray("channelList");if(rows!=null)for(int i=0;i<Math.min(50,rows.length());i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&channel.equals(row.optString("id")))satellite=row.optString("satellite");}String reference=FactoryProtocol.resolveReference(search,channel,null);if(reference.isEmpty()&&!satellite.isEmpty()&&satellite.length()<100)reference=FactoryProtocol.resolveReference(search,channel,client.get("/epgapi/v1/satellite/channel/list",Collections.singletonMap("querySats",satellite)));if(!FactoryProtocol.reference(reference))throw new Exception("FACTORY_CHANNEL_REFERENCE_UNAVAILABLE");channel=reference;prefs.edit().putString("factory_channel:"+nativeKey,channel).apply();}
  Map<String,String> query=new LinkedHashMap<>();query.put("sn",client.identity().optString("sn"));query.put("sat",channel);query.put("start",p.get("start"));query.put("end",p.get("end"));query.put("timezone",p.get("timezone"));query.put("language","en");query.put("simple","0");JSONObject reply=client.get("/epgapi/v1/schedule/list",query);JSONObject data=reply.optJSONObject("data");
  if(data==null||!FactoryProtocol.compatibleChannelName(state.channelName,data.optString("name")))
    throw new Exception("FACTORY_CHANNEL_MISMATCH");
  // The logo is channel-scoped. A missing or mismatched EPG programme must
  // not suppress a correctly identified channel logo.
  rememberLogo(identity,data.optString("cimg"));
  List<FactoryProtocol.Event> events=FactoryProtocol.schedule(reply,zone);FactoryProtocol.Event current=null,next=null;for(FactoryProtocol.Event e:events){if(e.start<=now&&e.end>now)current=e;else if(e.start>now&&next==null)next=e;}if(current==null)throw new Exception("FACTORY_EPG_NOT_PUBLISHED");// The service identity and compatible channel name were already verified.
  // A differing native EIT title is not grounds to suppress the requested
  // factory-first EPG; its own NOW/NEXT time window is independently checked.
  SatelliteState resolved=state.copy();resolved.title=current.title;resolved.startMs=current.start;resolved.endMs=current.end;resolved.image=current.pic;if(next!=null){resolved.nextTitle=next.title;resolved.nextStartMs=next.start;resolved.nextEndMs=next.end;resolved.nextImage=next.pic;}resolved.source="Factory CHMAX / EPG";resolved.observedAt=now;if(closed)return;
  { // Persist verified factory EPG for both live refresh and pre-zap.
   // Don't change the now-playing SatelliteState before the tuner switches.
   JSONObject ready=new JSONObject().put("identity",identity)
    .put("name",state.channelName).put("loadedAt",now)
    .put("nowTitle",resolved.title).put("nowStart",resolved.startMs)
    .put("nowEnd",resolved.endMs).put("nowImage",resolved.image)
    .put("nextTitle",resolved.nextTitle).put("nextStart",resolved.nextStartMs)
    .put("nextEnd",resolved.nextEndMs).put("nextImage",resolved.nextImage);
   prefs.edit().putString("factory_before_zap:"+nativeKey,ready.toString())
    .putLong("epg_art_changed_at",now).apply();
   if(preZapping)return;
  }
  SatelliteState active=SatelliteState.read(context);if(!CHMAXIntegration.identity(active).equals(identity))return;cached=resolved;cachedIdentity=identity;resolved.save(context);SharedPreferences.Editor edit=prefs.edit().putLong("epg_art_changed_at",now).putString("factory_current_subtitle_metadata",current.subtitle);edit.apply();}
 private void rememberLogo(String identity,String raw)throws Exception{
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
 /** Queue a manufacturer EPG Pro lookup just before the native DVB tune.
  * The receiver alone still controls the tuner, EIT and CA permissions. */
 static void prepareBeforeZap(Context c,ReceiverCatalog.Channel channel){
  if(channel==null||channel.name==null||channel.name.trim().isEmpty())return;
  Context app=c.getApplicationContext();SharedPreferences prefs=app.getSharedPreferences("smarthub",0);
  if(!prefs.getBoolean("factory_enabled",true)||PlaybackResourceMode.active()||
    prefs.getBoolean("software_dvb_422_active",false))return;
  requestAutomaticLogo(c,targetFrom(channel));
  if(!FactoryEpgGuard.canRequest(prefs,System.currentTimeMillis()))return;
  SatelliteState target=new SatelliteState();
  target.channelName=channel.name;target.channelId="DVB:"+channel.physical();
  String identity=CHMAXIntegration.identity(target);
  long now=System.currentTimeMillis();
  Long last=BEFORE_ZAP_RETRY.get(identity);
  if(last!=null&&now-last<90000)return;
  BEFORE_ZAP_RETRY.put(identity,now);
  BEFORE_ZAP_IO.execute(()->{
   FactoryProvider provider=new FactoryProvider(app);
   try{provider.fetch(target,identity,true);FactoryEpgGuard.success(prefs);}
   catch(Exception unavailable){String status=FactoryClient.safe(unavailable);
    FactoryEpgGuard.failure(prefs,status,System.currentTimeMillis());
    prefs.edit().putString("factory_before_zap_status",status).apply();}
   finally{provider.close();}
  });
 }
 private static SatelliteState targetFrom(ReceiverCatalog.Channel channel){
  SatelliteState target=new SatelliteState();
  target.channelName=channel.name;target.channelId="DVB:"+channel.physical();
  return target;
 }
 /** Download and retain CHMAX artwork without requiring the manual logo page.
  * 500ms UI checks NEVER turn into 500ms factory-network requests. */
 static void requestAutomaticLogo(Context c,SatelliteState channel){
  if(channel==null||channel.channelName==null||channel.channelName.isEmpty()||
      channel.channelId==null||!channel.channelId.startsWith("DVB:"))return;
  Context app=c.getApplicationContext();
  SharedPreferences prefs=app.getSharedPreferences("smarthub",0);
  if(!prefs.getBoolean("factory_enabled",true)||
     !prefs.getBoolean("osd_chmax_logos",true)||
     PlaybackResourceMode.active()||prefs.getBoolean("software_dvb_422_active",false))return;
  final SatelliteState wanted=channel.copy();
  String identity=CHMAXIntegration.identity(wanted);
  long now=System.currentTimeMillis();
  Long before=AUTO_LOGO_RETRY.putIfAbsent(identity,now);
  if(before!=null){
   if(now-before<120000)return;
   AUTO_LOGO_RETRY.put(identity,now);
  }
  AUTO_LOGO_IO.execute(()->{
   try{
    if(CHMAXIntegration.logo(app,wanted,false)!=null)return;
    // This MUST use the manufacturer's separate CHMAX channel-icon API.
    // Factory EPG Pro can reject code 50000 without blocking channel logos.
    Bitmap logo=FactoryProvider.cachedLogo(app,wanted.channelName,wanted.channelId);
    if(logo==null){
     ChlLogoAddon addon=ChlLogoAddon.get(app);
     if(addon.enabled()&&addon.supportedSession()){
      logo=addon.resolvePhysical(wanted.channelId,
        new ChannelLogoResolver(app,new ChannelAliasDatabase(app)));
      if(logo!=null)CHMAXIntegration.saveObservedLogo(
         app,wanted.channelId,wanted.channelName,logo);
     }
    }
    prefs.edit().putString("chmax_auto_logo_status",
       logo==null?"CHMAX_AUTO_PENDING":"CHMAX_AUTO_SAVED").apply();
   }catch(Exception failed){
    prefs.edit().putString("chmax_auto_logo_status",FactoryClient.safe(failed)).apply();
   }
  });
 }
 /** Use only preloaded manufacturer events attached to the exact native DVB service. */
 static boolean applyPrepared(Context c,SatelliteState state,boolean upcoming){
  if(state==null||state.channelName.isEmpty()||!state.channelId.startsWith("DVB:"))return false;
  try{
   String identity=CHMAXIntegration.identity(state);
   String text=c.getSharedPreferences("smarthub",0).getString(
      "factory_before_zap:"+SportsCache.key(identity),"");
   if(text.isEmpty())return false;
   JSONObject ready=new JSONObject(text);
   long now=System.currentTimeMillis();
   if(!identity.equals(ready.optString("identity"))||
      !ChannelCatalogue.normalized(state.channelName).equals(
         ChannelCatalogue.normalized(ready.optString("name")))||
      now-ready.optLong("loadedAt",0)>120000||now<ready.optLong("loadedAt",0))return false;
   String title=ready.optString(upcoming?"nextTitle":"nowTitle");
   long start=ready.optLong(upcoming?"nextStart":"nowStart",0);
   long end=ready.optLong(upcoming?"nextEnd":"nowEnd",0);
   if(title.isEmpty()||end<=start||end<=now||
      (!upcoming&&(start>now||end<=now))||(upcoming&&start<=now))return false;
   if(upcoming){state.nextTitle=title;state.nextStartMs=start;state.nextEndMs=end;
    state.nextImage=ready.optString("nextImage");}
   else{state.title=title;state.startMs=start;state.endMs=end;
    state.image=ready.optString("nowImage");state.source="Factory EPG Pro · prepared";}
   return true;
  }catch(Exception ignored){return false;}
 }
 static Bitmap logo(Context c,String name,String id){return logo(c,name,id,false);}
 /** UI-safe cached-only path; opening the channel list never blocks on CHMAX network IO. */
 static Bitmap cachedLogo(Context c,String name,String id){return logo(c,name,id,true);}
 private static Bitmap logo(Context c,String name,String id,boolean cachedOnly){
  try{
   SatelliteState s=new SatelliteState();s.channelName=name;s.channelId=id;
   String key=SportsCache.key(CHMAXIntegration.identity(s));
   SharedPreferences p=c.getSharedPreferences("smarthub",0);
   // Permanent original CHMAX artwork must work offline and after reboot.
   Bitmap retained=CHMAXIntegration.logo(c,s,false);
   if(retained!=null)return retained;
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
   Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
   if(decoded!=null)try{CHMAXIntegration.saveObservedLogo(c,id,name,decoded);}
     catch(Exception ignored){/* Display still succeeds; disk IO can retry. */}
   return decoded;
  }catch(Exception e){return null;}
 }
 public void close(){closed=true;io.shutdownNow();}
}
