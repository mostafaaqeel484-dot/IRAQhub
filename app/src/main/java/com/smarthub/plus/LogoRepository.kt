package com.smarthub.plus
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

class LogoRepository private constructor(context:Context){
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
 interface Listener{fun channel(bitmap:Bitmap?);fun provider(name:String,bitmap:Bitmap?)}
 init{scope.launch{db.load()};scope.launch{addon.load()}}
 private fun network(name:String,known:ChannelAliasDatabase.Entry?)=providerResolver.resolve(known)?:db.providerByName(addon.provider(name))
 private fun channelKey(name:String,e:ChannelAliasDatabase.Entry?,physical:String?)=if(physical?.startsWith("DVB:")==true)"channel:physical:$physical" else "channel:"+(e?.let{db.key(it)}?:if(addon.enabled())"chl-name:"+ChlChannelIndex.normalize(name) else ChannelAliasDatabase.normalize(name))
 fun request(name:String,listener:Listener){request(name,null,listener)}
 /**
  * A DVB picture is looked up by exact satellite/transponder/SID before any
  * name-only source. Duplicate CHL names never select a random satellite.
  */
 fun request(name:String,physical:String?,listener:Listener){
  val known=db.channel(name)
  val key=channelKey(name,known,physical)
  val cached=cache.peek(key)
  val network=network(name,known)
  val providerCached=network?.let{cache.peek("provider:"+db.key(it))}
  if(cached!=null)listener.channel(cached)
  if(network!=null&&providerCached!=null)listener.provider(network.name,providerCached)
  if(cached!=null&&(network==null||providerCached!=null)){
   if(network==null)listener.provider("",null)
   return
  }
  scope.launch{
   db.load();addon.load()
   val entry=db.channel(name)
   val image=async{get(channelKey(name,entry,physical)){
    val ambiguous=(addon.snapshot()?.candidates(name)?.map{it.physicalKey}?.distinct()?.size?:0)>1
    val exact=if(physical?.startsWith("DVB:")==true&&addon.enabled())
        addon.resolvePhysical(physical,channels) else null
    exact?:if(ambiguous)null else {
      if(entry==null&&addon.enabled())addon.unique(name)?.let{addon.resolve(it,channels)}?:channels.resolve(name)
      else channels.resolve(name)
    }
   }}
   launch{val bitmap=image.await();withContext(Dispatchers.Main){listener.channel(bitmap)}}
   launch{
    var provider=network(name,entry)
    if(entry==null&&provider==null){
     addon.rememberProvider(name)
     provider=network(name,entry)
    }
    val selected=provider
    val bitmap=if(selected==null)null else get("provider:"+db.key(selected)){providers.resolve(selected.id)}
    withContext(Dispatchers.Main){listener.provider(selected?.name?:"",bitmap)}
   }
  }
 }
 private suspend fun get(key:String,loader:()->Bitmap?):Bitmap?{
  cache.read(key)?.let{return it};if((failures[key]?:0)>System.currentTimeMillis())return null
  val task=synchronized(pending){pending[key]?:scope.async(start=CoroutineStart.LAZY){slots.withPermit{try{cache.read(key)?:loader()?.also{cache.save(key,it)}}catch(_:Exception){null}}}.also{pending[key]=it;it.start()}}
  return try{task.await().also{if(it==null)failures[key]=System.currentTimeMillis()+60000}}finally{pending.remove(key,task)}
 }
 fun providerName(name:String)=network(name,db.channel(name))?.name?:addon.provider(name).let{if(it.isEmpty())"" else "$it (CHL)"}
 fun invalidate(){failures.clear()}
 suspend fun replaceMapping(json:String,context:Context){withContext(Dispatchers.IO){val root=org.json.JSONObject(json);ChannelAliasDatabase.validate(root);val atomic=android.util.AtomicFile(java.io.File(context.filesDir,"logo-mapping.json"));val out=atomic.startWrite();try{out.write(json.toByteArray());atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e};db.reload();failures.clear();context.getSharedPreferences("smarthub",0).edit().putLong("logo_mapping_changed_at",System.currentTimeMillis()).apply()}}
 companion object{@Volatile private var instance:LogoRepository?=null;@JvmStatic fun get(context:Context):LogoRepository=instance?:synchronized(this){instance?:LogoRepository(context.applicationContext).also{instance=it}}}
}
