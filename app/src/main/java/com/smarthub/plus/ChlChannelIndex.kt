package com.smarthub.plus

import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader
import java.io.InputStream
import java.io.OutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.Normalizer
import java.util.Locale

/** Read-only export metadata. Export Index is never a live receiver programme ID. */
class ChlChannelIndex private constructor(val services:List<Service>,val satellites:Int,val transponders:Int){
 data class Service(val name:String,val provider:String,val sat:Int,val band:String,val frequency:Int,val polarity:String,val symbolRate:Int,val sid:Int){
  val physicalKey:String get()="$sat:$band:$frequency:$polarity:$symbolRate:$sid"
  fun json()=JSONObject().put("name",name).put("provider",provider).put("sat",sat).put("band",band).put("frequency",frequency).put("polarity",polarity).put("symbolRate",symbolRate).put("sid",sid)
  fun query()=JSONArray().put(JSONObject().put("sat",sat.toString()).put("band",band).put("tps",JSONArray().put(JSONObject().put("tp","$frequency:$polarity:$symbolRate").put("chs",JSONArray().put(JSONObject().put("sid",sid.toString()))))))
 }
 private val names=services.groupBy{normalize(it.name)}
 fun candidates(name:String)=names[normalize(name)]?:emptyList()
 fun unique(name:String):Service? {val rows=candidates(name).distinctBy{it.physicalKey};return rows.singleOrNull()}
 fun choices(name:String,seed:String):List<Service>{val rows=candidates(name).distinctBy{it.physicalKey}.sortedBy{it.physicalKey};if(rows.isEmpty())return emptyList();val start=LogoSelection.index(seed,normalize(name),rows.size);return rows.drop(start)+rows.take(start)}
 fun choose(name:String,seed:String)=choices(name,seed).firstOrNull()
 fun provider(name:String):String {val values=candidates(name).map{it.provider}.distinct();return values.singleOrNull()?.takeIf{it.isNotBlank()}?:""}
 fun json():String=JSONObject().put("version",1).put("satellites",satellites).put("transponders",transponders).put("services",JSONArray().also{a->services.forEach{a.put(it.json())}}).toString()
 /** Compact local index avoids parsing thousands of JSON objects on each process start. */
 fun write(output:OutputStream){val d=DataOutputStream(output.buffered());d.writeInt(0x43484c31);d.writeInt(satellites);d.writeInt(transponders);d.writeInt(services.size);for(s in services){d.writeUTF(s.name);d.writeUTF(s.provider);d.writeInt(s.sat);d.writeUTF(s.band);d.writeInt(s.frequency);d.writeUTF(s.polarity);d.writeInt(s.symbolRate);d.writeInt(s.sid)};d.flush()}
 companion object {
  private val STRIP=Regex("[^\\p{L}\\p{N}]")
  /** Shared bounded CHL framing for the independent receiver catalogue. */
  @JvmStatic fun visit(reader:Reader,visitor:java.util.function.Consumer<JSONObject>){objects(reader){visitor.accept(it)}}
  @JvmStatic fun normalize(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKC).uppercase(Locale.ROOT).replace(STRIP,"")
  private fun label(j:JSONObject,k:String)=j.optString(k).trim().also{require(it.length<=200&&!it.any{c->c<' '})}
  private fun number(j:JSONObject,k:String,min:Int,max:Int)=j.getStringValue(k).toIntOrNull()?.takeIf{it in min..max}?:throw IllegalArgumentException("Invalid CHL number")
  private fun JSONObject.getStringValue(k:String)=get(k).toString()
  @JvmStatic fun read(input:InputStream):ChlChannelIndex{val d=DataInputStream(input.buffered());require(d.readInt()==0x43484c31);val sats=d.readInt();val tps=d.readInt();val count=d.readInt();require(sats in 1..100000&&tps in 1..100000&&count in 1..50000);val out=ArrayList<Service>(count);repeat(count){val s=Service(d.readUTF(),d.readUTF(),d.readInt(),d.readUTF(),d.readInt(),d.readUTF(),d.readInt(),d.readInt());require(s.name.isNotBlank()&&s.name.length<=200&&s.provider.length<=200&&!s.name.any{it<' '}&&!s.provider.any{it<' '}&&s.sat in 0..3599&&s.band in setOf("KU","C")&&s.frequency in 100..100000&&s.polarity in setOf("H","V","L","R")&&s.symbolRate in 1..100000&&s.sid in 0..65535);out.add(s)};require(d.read()==-1);return ChlChannelIndex(out,sats,tps)}
  @JvmStatic fun parse(reader:Reader):ChlChannelIndex {
   val sats=HashMap<Int,JSONObject>();val tps=HashMap<Int,JSONObject>();val channels=ArrayList<JSONObject>();var rows=0
   objects(reader){j->require(++rows<=65000);when(j.optString("Type")){
    "sat"->{val id=number(j,"Index",0,100000);require(sats.put(id,j)==null)}
    "tp"->{val id=number(j,"Index",0,100000);require(tps.put(id,j)==null)}
    "ch"->{require(channels.size<50000);channels.add(JSONObject().put("Name",j.get("Name")).put("Provider",j.optString("Provider")).put("TPIndex",j.get("TPIndex")).put("SID",j.get("SID")))}
   }}
   require(sats.isNotEmpty()&&tps.isNotEmpty()&&channels.isNotEmpty())
   val services=channels.map{c->val tp=tps[number(c,"TPIndex",0,100000)]?:throw IllegalArgumentException("Missing CHL TP");val sat=sats[number(tp,"SatIndex",0,100000)]?:throw IllegalArgumentException("Missing CHL satellite");val name=label(c,"Name");require(name.isNotBlank());val band=label(sat,"Band").uppercase(Locale.ROOT);val pol=label(tp,"Pol").uppercase(Locale.ROOT);require(band in setOf("KU","C")&&pol in setOf("H","V","L","R"));Service(name,label(c,"Provider"),number(sat,"Angle",0,3599),band,number(tp,"Freq",100,100000),pol,number(tp,"SR",1,100000),number(c,"SID",0,65535))}
   return ChlChannelIndex(services,sats.size,tps.size)
  }
  /** Bounded framing understands quoted braces and escapes; never regex-splits user strings. */
  private fun objects(reader:Reader,consume:(JSONObject)->Unit){
   val r=reader.buffered();var count=0;fun read():Int{val n=r.read();if(n>=0)require(++count<=16*1024*1024);return n};var ch=read();if(ch==0xfeff)ch=read();while(ch>=0&&ch.toChar().isWhitespace())ch=read();val array=ch=='['.code;if(array)ch=read();var first=true
   while(true){while(ch>=0&&ch.toChar().isWhitespace())ch=read();if(array&&ch==']'.code){ch=read();while(ch>=0&&ch.toChar().isWhitespace())ch=read();require(ch<0);return};if(ch<0){require(!array);return};if(array&&!first){require(ch==','.code);ch=read();while(ch>=0&&ch.toChar().isWhitespace())ch=read()};require(ch=='{'.code);val b=StringBuilder();var depth=0;var quoted=false;var escaped=false
    do{require(ch>=0&&b.length<128*1024);val c=ch.toChar();b.append(c);if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else{if(c=='"')quoted=true else if(c=='{'||c=='['){depth++;require(depth<=32)}else if(c=='}'||c==']')depth--};ch=read()}while(depth>0||quoted)
    require(depth==0);consume(JSONObject(b.toString()));first=false
   }
  }
  @JvmStatic fun fromSaved(text:String):ChlChannelIndex{require(text.length<=16*1024*1024);val root=JSONObject(text);require(root.getInt("version")==1);val rows=root.getJSONArray("services");require(rows.length() in 1..50000);val out=ArrayList<Service>();for(i in 0 until rows.length()){val j=rows.getJSONObject(i);val name=label(j,"name");val band=label(j,"band");val pol=label(j,"polarity");require(name.isNotBlank()&&band in setOf("KU","C")&&pol in setOf("H","V","L","R"));out.add(Service(name,label(j,"provider"),number(j,"sat",0,3599),band,number(j,"frequency",100,100000),pol,number(j,"symbolRate",1,100000),number(j,"sid",0,65535)))};return ChlChannelIndex(out,root.getInt("satellites"),root.getInt("transponders"))}
 }
}
