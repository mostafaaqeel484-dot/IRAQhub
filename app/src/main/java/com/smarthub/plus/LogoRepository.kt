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
 private fun channelKey(name:String,e:ChannelAliasDatabase.Entry?)="channel:"+(e?.let{db.key(it)}?:if(addon.enabled())"chl-name:"+ChlChannelIndex.normalize(name) else ChannelAliasDatabase.normalize(name))
 fun request(name:String,listener:Listener){
  val known=db.channel(name);val channelCached=cache.peek(channelKey(name,known))
  val network=network(name,known);val providerCached=network?.let{cache.peek("provider:"+db.key(it))}
  if(channelCached!=null)listener.channel(channelCached)
  if(network!=null&&providerCached!=null)listener.provider(network.name,providerCached)
  if(channelCached!=null&&(network==null||providerCached!=null)){if(network==null)listener.provider("",null);return}
  scope.launch{
   db.load();val entry=db.channel(name)
   val c=async{get(channelKey(name,entry)){
    if(entry==null&&addon.enabled())addon.resolve(name,channels)?:channels.resolve(name)
    else channels.resolve(name)
   }}
   launch{val image=c.await();withContext(Dispatchers.Main){listener.channel(image)}}
   launch{
    var provider=network(name,entry)
    if(entry==null&&provider==null){addon.load();addon.rememberProvider(name);provider=network(name,entry)}
    val selectedProvider=provider;val image=if(selectedProvider==null)null else get("provider:"+db.key(selectedProvider)){providers.resolve(selectedProvider.id)}
    withContext(Dispatchers.Main){listener.provider(selectedProvider?.name?:"",image)}
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
