package com.smarthub.plus

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import java.io.File
import java.io.Reader
import org.json.JSONArray
import java.net.URI

/** Explicit optional source. Independent logo resolvers do not call CHMAX. */
class ChlLogoAddon private constructor(private val context:Context){
 @Volatile private var index:ChlChannelIndex?=null
 @Volatile private var loaded=false
 @Volatile private var selectionSeed=""
 private val prefs=context.getSharedPreferences("smarthub",0)
 @Synchronized fun load(){if(loaded)return;selectionSeed=LogoSelection.seed(context);index=try{val f=File(context.filesDir,"chl-logo-index.bin");if(f.isFile&&f.length()<=8*1024*1024)f.inputStream().use{ChlChannelIndex.read(it)}else null}catch(_:Exception){null};loaded=true}
 @Synchronized fun importFile(reader:Reader):ChlChannelIndex {val raw=ReceiverCatalog.readText(reader);val parsed=ChlChannelIndex.parse(java.io.StringReader(raw));val catalog=ReceiverCatalog.get(context);try{catalog.importText(raw)}catch(_:Exception){catalog.replaceCompact(parsed)};prefs.edit().putString("receiver_catalog_format","CHL").apply();val atomic=AtomicFile(File(context.filesDir,"chl-logo-index.bin"));val out=atomic.startWrite();try{parsed.write(out);atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e};File(context.filesDir,"chl-logo-index.json").delete();selectionSeed=LogoSelection.seed(context);val edit=prefs.edit();prefs.all.keys.filter{it.startsWith("chl_provider:")}.forEach{edit.remove(it)};edit.apply();index=parsed;loaded=true;changed();return parsed}
 /** After importing a native DB, rebuild the logo source once from indexed SQLite.
  * Keeps physical CHMAX lookups without reparsing CHL at every process start.
  */
 @Synchronized fun databaseImported(){
  val parsed=try{ChlChannelIndex.fromReceiverDatabase(context)}catch(_:Exception){null}
  val file=AtomicFile(File(context.filesDir,"chl-logo-index.bin"))
  var saved=false
  if(parsed!=null){
   var out:java.io.FileOutputStream?=null
   try{
    out=file.startWrite()
    parsed.write(out)
    file.finishWrite(out)
    saved=true
   }catch(_:Exception){
    if(out!=null)file.failWrite(out)
   }
  }
  if(!saved)file.delete()
  AtomicFile(File(context.filesDir,"chl-logo-index.json")).delete()
  index=if(saved)parsed else null
  loaded=true
  selectionSeed=LogoSelection.seed(context)
  val edit=prefs.edit()
  prefs.all.keys.filter{it.startsWith("chl_provider:")}.forEach{edit.remove(it)}
  edit.apply()
  changed()
 }
 @Synchronized fun clear(){ReceiverCatalog.get(context).clear();AtomicFile(File(context.filesDir,"chl-logo-index.bin")).delete();AtomicFile(File(context.filesDir,"chl-logo-index.json")).delete();index=null;loaded=true;val edit=prefs.edit();prefs.all.keys.filter{it.startsWith("chl_provider:")}.forEach{edit.remove(it)};edit.apply();prefs.edit().putBoolean("chl_chmax_enabled",false).remove("receiver_catalog_format").remove("receiver_catalog_count").apply();changed()}
 fun snapshot():ChlChannelIndex?{load();return index}
 fun provider(name:String)=index?.provider(name)?:prefs.getString("chl_provider:"+ChlChannelIndex.normalize(name),"")?:""
 fun rememberProvider(name:String){val value=index?.provider(name)?:return;val key="chl_provider:"+ChlChannelIndex.normalize(name);if(prefs.getString(key,"")!=value)prefs.edit().putString(key,value).apply()}
 fun choice(name:String)=index?.choose(name,selectionSeed)
 fun resolve(name:String,loader:ChannelLogoResolver):Bitmap?{load();rememberProvider(name);for(service in index?.choices(name,selectionSeed)?.take(3)?:emptyList())resolve(service,loader)?.let{return it};return null}
 fun unique(name:String)=index?.unique(name)
 /** Bind CHMAX logos to the exact DVB transport/service identity, not the name. */
 @Synchronized fun resolvePhysical(physical:String,loader:ChannelLogoResolver):Bitmap?{
  load()
  val actual=physical.removePrefix("DVB:")
  if(!physical.startsWith("DVB:")||actual.length>100||!enabled())return null
  val single=index?.byPhysical(actual)?:return null
  if(!supportedSession())return null
  return resolve(single,loader)
 }

 @Volatile private var sessionCheckedUntil=0L
 @Volatile private var sessionSupported=false
 /**
  * The signed CHMAX icon service requires an authorised compatibility profile.
  * Never send repeated remote requests with an absent profile on every scroll.
  */
 fun supportedSession():Boolean{
  val now=System.currentTimeMillis()
  if(now<sessionCheckedUntil)return sessionSupported
  synchronized(this){
   if(now<sessionCheckedUntil)return sessionSupported
   sessionSupported=try{
    val state=FactoryVault.read(context)
    val has=state.optString("chmaxProfile").length in 8..24&&
      state.optString("accessToken").isNotEmpty()
    if(!has)prefs.edit().putString("chl_chmax_status",
      "FACTORY_CHMAX_PROFILE_REQUIRED").apply()
    has
   }catch(_:Exception){false}
   sessionCheckedUntil=now+60_000L
   return sessionSupported
  }
 }
 fun invalidateSession(){sessionCheckedUntil=0L}
 fun enabled()=prefs.getBoolean("chl_chmax_enabled",prefs.getBoolean("factory_enabled",true)&&prefs.getBoolean("osd_chmax_logos",true))
 fun statusLabel():String=when(prefs.getString("chl_chmax_status","")){
  "CHMAX_LOGO_OK"->"تم جلب شعار مطابق من CHMAX"
  "CHMAX_LOGO_NOT_FOUND"->"لم ينشر CHMAX شعاراً مطابقاً لهذه القناة"
  "FACTORY_CHMAX_PROFILE_REQUIRED"->"ملف توافق CHMAX غير معدّ على هذا الجهاز"
  "FACTORY_IDENTITY_REQUIRED","FACTORY_AUTH_REQUIRED"->"تحتاج إضافة CHMAX مصادقة الرسيفر"
  ""->"لم يُختبر CHMAX بعد"
  else->"تعذر الاتصال بـCHMAX؛ يبقى المصدر المستقل متاحاً"
 }
 fun enable(value:Boolean){prefs.edit().putBoolean("chl_chmax_enabled",value).apply();changed()}
 private fun changed(){prefs.edit().putLong("logo_mapping_changed_at",System.currentTimeMillis()).apply()}
 fun resolve(service:ChlChannelIndex.Service,loader:ChannelLogoResolver):Bitmap? {
  val client=FactoryClient(context)
  return try{val rows=client.channelIcons(service.query());val url=select(rows,service);val image=if(url.isEmpty())null else loader.download(url);prefs.edit().putString("chl_chmax_status",if(image!=null)"CHMAX_LOGO_OK" else "CHMAX_LOGO_NOT_FOUND").apply();image}catch(e:Exception){prefs.edit().putString("chl_chmax_status",FactoryClient.safe(e)).apply();null}
 }
 companion object{
  @Volatile private var instance:ChlLogoAddon?=null
  @JvmStatic fun get(c:Context):ChlLogoAddon=instance?:synchronized(this){instance?:ChlLogoAddon(c.applicationContext).also{instance=it}}
  /** Only an exact physical service response is accepted; no default or transponder logo. */
  @JvmStatic fun select(rows:JSONArray,s:ChlChannelIndex.Service):String{var result="";for(i in 0 until minOf(100,rows.length())){val sat=rows.optJSONObject(i)?:continue;if(sat.optString("sat")!=s.sat.toString())continue;val tps=sat.optJSONArray("tps")?:continue;for(t in 0 until minOf(1000,tps.length())){val tp=tps.optJSONObject(t)?:continue;if(tp.optString("tp")!="${s.frequency}:${s.polarity}:${s.symbolRate}")continue;val chs=tp.optJSONArray("chs")?:continue;for(n in 0 until minOf(2000,chs.length())){val ch=chs.optJSONObject(n)?:continue;if(ch.optString("sid")!=s.sid.toString())continue;val url=imageUrl(ch.optString("iconUrl"));if(url.isEmpty())continue;if(result.isNotEmpty()&&result!=url)return "";result=url}}};return result}
  @JvmStatic fun imageUrl(value:String):String{if(value.isBlank())return "";return try{val raw=if(value.startsWith("https://"))value else if(value.contains(":")||value.startsWith("/"))return "" else "https://$value";val u=URI(raw);if(u.scheme!="https"||u.userInfo!=null||u.port !in setOf(-1,443)||u.query!=null||u.fragment!=null||u.host !in setOf("gchsicon.datasver.com")||!u.path.matches(Regex("/icon/[A-Za-z0-9_/-]+(?:\\.png)?")))return "";if(u.path.endsWith(".png"))raw else "$raw.png"}catch(_:Exception){""}}
 }
}
