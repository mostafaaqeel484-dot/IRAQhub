package com.smarthub.plus
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale
class ChannelAliasDatabase(private val context:Context){
 data class Entry(val id:String,val name:String,val provider:String,val url:String,val asset:String){val cacheKey=id+"@"+ChannelLogoCache.digest(url+"|"+asset+if(asset.endsWith(".svg",true))"|svg-fit-v2" else "")}
 @Volatile private var entries=emptyMap<String,Entry>();@Volatile private var providers=emptyMap<String,Entry>();@Volatile private var loaded=false
 @Synchronized fun load(){if(loaded)return;val override=File(context.filesDir,"logo-mapping.json");val root=JSONObject(if(override.exists())override.readText() else context.assets.open("logo-resolver/mapping.json").bufferedReader().use{it.readText()});val maps=validate(root);entries=maps.first;providers=maps.second;loaded=true}
 fun channel(name:String)=entries[normalize(name)]
 fun provider(id:String)=providers[id]
 fun providerByName(name:String)=if(name.isBlank())null else providers.values.singleOrNull{normalize(it.name)==normalize(name)}
 fun key(e:Entry)=e.cacheKey
 fun reload(){loaded=false;load()}
 companion object{
 private val QUALITY=Regex("(?i)\\s+(?:HD|SD|FHD|UHD|4K)$")
 private val NAME=Regex("[^\\p{L}\\p{N}]")
 @JvmStatic fun normalize(raw:String)=Normalizer.normalize(raw,Normalizer.Form.NFKC).uppercase(Locale.ROOT).replace(QUALITY,"").replace(NAME,"")
 fun validate(root:JSONObject):Pair<Map<String,Entry>,Map<String,Entry>>{val ps=HashMap<String,Entry>();val cs=HashMap<String,Entry>();val providers=root.getJSONArray("providers");require(providers.length()<=500);for(i in 0 until providers.length()){val p=providers.getJSONObject(i);val e=entry(p,"");require(!ps.containsKey(e.id));ps[e.id]=e};val channels=root.getJSONArray("channels");require(channels.length()<=20000);for(i in 0 until channels.length()){val p=channels.getJSONObject(i);val e=entry(p,p.optString("provider"));require(e.provider.isEmpty()||ps.containsKey(e.provider));val aliases=p.getJSONArray("aliases");require(aliases.length()<=100);for(j in 0 until aliases.length()){val key=normalize(aliases.getString(j));require(key.isNotEmpty());require(cs[key]==null||cs[key]?.id==e.id){"Ambiguous alias"};cs[key]=e}};return cs to ps}
 private fun entry(p:JSONObject,provider:String):Entry{val url=p.optString("url");require(url.isEmpty()||java.net.URI(url).let{it.scheme=="https"&&it.host!=null&&it.userInfo==null});val asset=p.optString("asset");require(asset.isEmpty()||asset.matches(Regex("[A-Za-z0-9._-]+")));val id=p.getString("id");val name=p.getString("name");require(id.isNotBlank()&&id.length<=160&&name.isNotBlank()&&name.length<=160);return Entry(id,name,provider,url,asset)}
 }
}
