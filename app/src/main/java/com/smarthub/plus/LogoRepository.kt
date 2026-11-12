package com.smarthub.plus
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

class LogoRepository private constructor(context:Context){
 private val app=context.applicationContext
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val db=ChannelAliasDatabase(context)
 private val cache=ChannelLogoCache(context)
 private val channels=ChannelLogoResolver(context,db)
 private val providers=ProviderLogoManager(db,channels)
 private val addon=ChlLogoAddon.get(context)
 private val slots=Semaphore(3)
 private val pending=ConcurrentHashMap<String,Deferred<Bitmap?>>()
 private val failures=ConcurrentHashMap<String,Long>()
 private val providerResolver=ProviderResolver(db)
 @Volatile private var videoPriority=false
 private var nativeRevision=0L
 @Synchronized private fun refreshObservedCache(){
  val revision=app.getSharedPreferences("smarthub",0).getLong("native_logos_changed_at",0)
  if(nativeRevision==revision)return
  nativeRevision=revision
  cache.clearRam()
 }
 /** Prioritize DVB/4:2:2: cancel app-owned download tasks and drop RAM images, never touch OEM services. */
 fun pauseForVideo(){
  videoPriority=true
  synchronized(pending){pending.values.forEach{it.cancel()};pending.clear()}
  cache.clearRam()
  failures.clear()
 }
 fun resumeAfterVideo(){videoPriority=false}
 /** Memory pressure only: release app-owned decoded logos; never force Java GC. */
 fun trimRamCache(){cache.clearRam()}
 interface Listener{fun channel(bitmap:Bitmap?);fun provider(name:String,bitmap:Bitmap?)}
 init{scope.launch{db.load()};scope.launch{addon.load()}}
 private fun network(name:String,known:ChannelAliasDatabase.Entry?)=providerResolver.resolve(known)?:db.providerByName(addon.provider(name))
 /**
  * Prefer a logo actually observed on the receiver's own infobar.
  * Its private /data/ChannelImgs directory is not readable by a normal APK.
  * A screenshot-derived logo is valid only for the same live channel ID/name;
  * an explicitly chosen manual logo remains usable while off that channel.
  */
 private fun observed(name:String,physical:String?,provider:Boolean=false):Bitmap?{
  if(provider||physical.isNullOrBlank()||name.isBlank())return null
  val prefs=app.getSharedPreferences("smarthub",0)
  val state=SatelliteState().apply{channelId=physical;channelName=name}
  val selected=prefs.getString("native_logo_source:"+CHMAXIntegration.identity(state),"")?:""
  val manual=selected=="MANUAL_CHANNEL_IMAGE"
  // Manual USB images are independent/local; captured OEM/CHMAX images are native.
  if(manual&&!ChannelLogoSourcePrefs.external(app))return null
  if(!manual&&!ChannelLogoSourcePrefs.chmax(app))return null
  val current=SatelliteState.read(app)
  val exact=current.channelId==physical&&current.channelName.isNotBlank()&&
    SatelliteUiParser.normalized(current.channelName)==SatelliteUiParser.normalized(name)
  if(exact||manual||physical.startsWith("DVB:"))
    CHMAXIntegration.logo(app,state,false)?.let{return it}
  return if(manual&&ChannelLogoSourcePrefs.external(app)&&
      prefs.getBoolean("adb_logo_imported:"+physical,false))
    cache.read("channel:physical:v2:"+physical) else null
 }
 private fun observedProviderName(name:String,physical:String?,fallback:String):String{
  if(physical.isNullOrBlank())return fallback
  val state=SatelliteState()
  state.channelId=physical
  state.channelName=name
  return CHMAXIntegration.provider(app,state).takeIf{it.isNotBlank()}?:fallback
 }
 /** Persist every newly verified receiver logo under the same exact-DVB key
  * used by channel-list rows and InfoBar. Revision changes replace old PNG. */
 private fun persistLogo(key:String,bitmap:Bitmap){
  val p=app.getSharedPreferences("smarthub",0)
  val stamp=Math.max(p.getLong("native_logos_changed_at",0),
    p.getLong("logo_mapping_changed_at",0))
  val marker="logo_file_revision:"+ChannelLogoCache.digest(key)
  try{
   if(p.getLong(marker,-1)!=stamp){
    cache.save(key,bitmap)
    p.edit().putLong(marker,stamp).apply()
   }else cache.remember(key,bitmap)
  }catch(_:Exception){cache.remember(key,bitmap)}
 }
 /** CHMAX ZIP logos live in a separate physical DVB namespace.
  * An external-only source cannot see these stored official package images. */
 private fun packageKey(physical:String?):String?=
  if(ChannelLogoSourcePrefs.chmax(app)&&physical?.startsWith("DVB:")==true)
    "chmax:package:channel:physical:v2:$physical" else null
 private fun bulkChmax(physical:String?):Bitmap?=packageKey(physical)?.let{cache.read(it)}
 private fun channelKey(name:String,e:ChannelAliasDatabase.Entry?,physical:String?)="rx6:channel:source-v4:"+ChannelLogoSourcePrefs.sourceId(app)+":"+(if(physical?.startsWith("DVB:")==true)"physical:$physical" else (e?.let{db.key(it)}?:if(addon.enabled())"chl-name:"+ChlChannelIndex.normalize(name) else ChannelAliasDatabase.normalize(name)))
 /** Only disk/RAM cache, used when the decoder needs all CPU bandwidth. */
 /** Show RAM hits instantly. Disk decode and verified receiver branding run
  * on the bounded IO pool, never on D-Pad scroll or InfoBar render threads. */
 /** Resolve CHANNEL images only from enabled sources. CHMAX has priority
  * over external artwork when both switches are on. Manual USB images are
  * treated as local independent artwork. No provider bitmap is used here. */
 private fun loadChannel(name:String,physical:String?,entry:ChannelAliasDatabase.Entry?):Bitmap?{
  val chmax=ChannelLogoSourcePrefs.chmax(app)
  val external=ChannelLogoSourcePrefs.external(app)
  if(!chmax&&!external)return null
  if(chmax)bulkChmax(physical)?.let{return it}
  observed(name,physical)?.let{return it}
  if(chmax){
   val factory=app.getSharedPreferences("smarthub",0).getBoolean("factory_enabled",true)
   if(factory&&physical!=null&&physical.isNotBlank()){
    FactoryProvider.cachedLogo(app,name,physical)?.let{return it}
   }
   if(physical?.startsWith("DVB:")==true&&addon.enabled()&&addon.supportedSession()){
    addon.resolvePhysical(physical,channels)?.let{return it}
   }
   if(factory&&physical!=null&&physical.isNotBlank()){
    FactoryProvider.logo(app,name,physical)?.let{return it}
   }
  }
  if(!external)return null
  // External includes imported USB, bundled map, direct verified URLs and
  // online logos. Native CHMAX snapshots are forbidden here.
  ReceiverLogoAssets.load(app,name,physical)?.let{return it}
  val published=ReceiverLogoAssets.remoteUrl(app,name,physical)
  if(published.isNotEmpty())try{channels.download(published)?.let{return it}}catch(_:Exception){}
  val ambiguous=(addon.snapshot()?.candidates(name)?.map{it.physicalKey}?.distinct()?.size?:0)>1
  if(ambiguous)return null
  // External-only lookups must never request CHMAX per-channel icons.
  val chosen=channels.resolve(name)
  if(chosen!=null)return chosen
  return try{OnlineChannelLogo.fetch(app,name,entry?.id?:"",physical?:"")}catch(_:Exception){null}
 }
 private fun providerRecord(name:String,physical:String?,entry:ChannelAliasDatabase.Entry?,
                            online:Boolean=false):Pair<String,Bitmap?>{
  if(!ChannelLogoSourcePrefs.providers(app))return Pair("",null)
  val label=network(name,entry)?.name?:addon.provider(name)
  val branding=ProviderBrandArt.get(app)
  val official=branding.provider(label,name,physical?:"")
  val display=official.ifBlank{label}
  val audited=branding.badge(label,name,physical?:"")
  // User-approved remote provider art is a third, independent image store.
  // A failed URL never influences CHMAX or the channel-logo resolver.
  val selected=if(audited!=null||display.isBlank())null else
    if(online)ProviderApprovedArtwork.resolve(app,display)
    else ProviderApprovedArtwork.cached(app,display)
  return Pair(display,audited?:selected)
 }
 /** Fast-path does not issue channel or provider network requests. */
 fun requestCached(name:String,physical:String?,listener:Listener){
  refreshObservedCache()
  val enabled=ChannelLogoSourcePrefs.chmax(app)||ChannelLogoSourcePrefs.external(app)
  val known=if(physical?.startsWith("DVB:")==true)null else db.channel(name)
  val key=channelKey(name,known,physical)
  listener.channel(if(enabled)packageKey(physical)?.let{cache.peek(it)}?:cache.peek(key) else null)
  // Only source name on D-pad thread; provider bitmap decode stays on IO.
  listener.provider(if(ChannelLogoSourcePrefs.providers(app))providerName(name) else "",null)
  if(!enabled&&!ChannelLogoSourcePrefs.providers(app))return
  scope.launch{
   val image=if(enabled)slots.withPermit{
    bulkChmax(physical)?:cache.read(key)?:observed(name,physical)?.also{persistLogo(key,it)}
     ?:if(ChannelLogoSourcePrefs.external(app))
       ReceiverLogoAssets.load(app,name,physical)?.also{persistLogo(key,it)}
       else null
   } else null
   val provider=providerRecord(name,physical,known,false)
   withContext(Dispatchers.Main){
    listener.channel(image)
    listener.provider(provider.first,provider.second)
   }
  }
 }
 fun request(name:String,listener:Listener){request(name,null,listener)}
 /** Source-aware keys prevent CHMAX images showing in internet-only mode
  * after changing a setting, and vice versa. Provider art is independent. */
 fun request(name:String,physical:String?,listener:Listener){
  refreshObservedCache()
  if(videoPriority){requestCached(name,physical,listener);return}
  val enabled=ChannelLogoSourcePrefs.chmax(app)||ChannelLogoSourcePrefs.external(app)
  val entry=db.channel(name)
  val key=channelKey(name,entry,physical)
  val ram=if(enabled)packageKey(physical)?.let{cache.peek(it)}?:cache.peek(key) else null
  listener.channel(ram)
  listener.provider(if(ChannelLogoSourcePrefs.providers(app))providerName(name) else "",null)
  scope.launch{
   db.load();addon.load()
   val updatedEntry=db.channel(name)
   val image=if(!enabled)null else bulkChmax(physical)?:if(ram!=null)ram else get(key){
    loadChannel(name,physical,updatedEntry)
   }
   val result=providerRecord(name,physical,updatedEntry,true)
   withContext(Dispatchers.Main){
    listener.channel(image)
    listener.provider(result.first,result.second)
   }
  }
 }
 private suspend fun get(key:String,loader:()->Bitmap?):Bitmap?{
  cache.read(key)?.let{return it};if((failures[key]?:0)>System.currentTimeMillis())return null
  val task=synchronized(pending){pending[key]?:scope.async(start=CoroutineStart.LAZY){slots.withPermit{try{cache.read(key)?:loader()?.also{cache.save(key,it)}}catch(_:Exception){null}}}.also{pending[key]=it;it.start()}}
  return try{task.await().also{if(it==null)failures[key]=System.currentTimeMillis()+60000}}finally{pending.remove(key,task)}
 }
 fun providerName(name:String):String {
  val playing=SatelliteState.read(app)
  if(playing.channelName.isNotEmpty()&&
     SatelliteUiParser.normalized(playing.channelName)==SatelliteUiParser.normalized(name)) {
   val verified=CHMAXIntegration.provider(app,playing)
   if(verified.isNotBlank())return verified
  }
  return network(name,db.channel(name))?.name?:addon.provider(name).let{
   if(it.isEmpty())"" else "$it (CHL)"
  }
 }
 fun invalidate(){cache.clearRam();failures.clear()}
 suspend fun replaceMapping(json:String,context:Context){withContext(Dispatchers.IO){val root=org.json.JSONObject(json);ChannelAliasDatabase.validate(root);val atomic=android.util.AtomicFile(java.io.File(context.filesDir,"logo-mapping.json"));val out=atomic.startWrite();try{out.write(json.toByteArray());atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e};db.reload();failures.clear();context.getSharedPreferences("smarthub",0).edit().putLong("logo_mapping_changed_at",System.currentTimeMillis()).apply()}}
 companion object{@Volatile private var instance:LogoRepository?=null;@JvmStatic fun get(context:Context):LogoRepository=instance?:synchronized(this){instance?:LogoRepository(context.applicationContext).also{instance=it}}}
}
