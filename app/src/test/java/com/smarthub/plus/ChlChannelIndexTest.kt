package com.smarthub.plus
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray

class ChlChannelIndexTest {
 private val base="""{"Type":"index","Ver":1}{"Type":"sat","Index":0,"Angle":130,"Band":"KU"}{"Type":"tp","Index":2,"SatIndex":0,"Freq":11294,"Pol":"H","SR":27500}"""
 private fun channel(name:String="Love Nature 4K",sid:Int=2229,provider:String="iKOMG")="""{"Type":"ch","Name":${org.json.JSONObject.quote(name)},"Provider":"$provider","TPIndex":2,"SID":$sid,"Index":99,"CA":11,"VideoType":"H265"}"""
 private fun parse(s:String=base+channel())=ChlChannelIndex.parse(s.reader())
 @Test fun manufacturerConcatenatedObjectsResolvePhysicalIdentity(){val i=parse();val s=i.unique("LOVE NATURE 4K")!!;assertEquals("130:KU:11294:H:27500:2229",s.physicalKey);assertEquals("iKOMG",i.provider("Love Nature 4K"))}
 @Test fun compactIndexPreservesPhysicalIdentityAndMetadata(){val i=parse();val bytes=java.io.ByteArrayOutputStream();i.write(bytes);val saved=ChlChannelIndex.read(bytes.toByteArray().inputStream());assertEquals(i.services,saved.services);assertEquals(i.satellites,saved.satellites);assertEquals(i.unique("Love Nature 4K"),saved.unique("Love Nature 4K"))}
 @Test fun corruptTruncatedOrExtendedCompactIndexIsRejected(){val bytes=java.io.ByteArrayOutputStream();parse().write(bytes);val good=bytes.toByteArray();for(b in listOf(good.copyOf(12),good+byteArrayOf(1),ByteArray(good.size))){try{ChlChannelIndex.read(b.inputStream());fail()}catch(_:Exception){}}}
 @Test fun bracesAndEscapesInsideNamesDoNotSplitRecords(){val i=parse(base+channel("Test }{ \"Quoted\""));assertEquals(1,i.services.size);assertEquals("Test }{ \"Quoted\"",i.services.single().name)}
 @Test fun arrayFormIsAlsoAccepted(){val i=parse("["+base.replace("}{","},{")+","+channel()+"]");assertEquals(1,i.services.size)}
 @Test fun duplicatedNamesNeverChooseFirstPhysicalService(){val i=parse(base+channel()+channel(sid=2230));assertNull(i.unique("Love Nature 4K"));assertEquals("iKOMG",i.provider("Love Nature 4K"))}
 @Test fun duplicatedNamesNowSelectARepeatableRandomMatchingLogo(){val i=parse(base+channel()+channel(sid=2230));val chosen=i.choose("Love Nature 4K","installation-a");assertTrue(chosen in i.candidates("Love Nature 4K"));repeat(20){assertEquals(chosen,i.choose("Love Nature 4K","installation-a"))};assertNull(i.choose("Different channel","installation-a"))}
 @Test fun randomSeedsCanChooseBothDuplicateServices(){val i=parse(base+channel()+channel(sid=2230));assertEquals(2,(0..100).map{i.choose("Love Nature 4K","seed-$it")?.sid}.toSet().size)}
 @Test fun inconsistentProvidersAreNotInferred(){assertEquals("",parse(base+channel()+channel(sid=2230,provider="Other")).provider("Love Nature 4K"))}
 @Test fun channelQualitySuffixIsPartOfChlIdentity(){assertNull(parse().unique("Love Nature HD"));assertNull(parse().unique("Love Nature"))}
 @Test fun unreferencedTransponderIsRejected(){try{parse((base+channel()).replace("\"TPIndex\":2","\"TPIndex\":9"));fail()}catch(_:IllegalArgumentException){}}
 @Test fun savedIndexOmitsExportIdsFavoritesAndSnapshotDecoderClaims(){val json=parse().json();for(k in listOf("Index","CA","VideoType","FAV","Audio","Lock"))assertFalse(json.contains("\"$k\""));assertEquals(parse().unique("Love Nature 4K"),ChlChannelIndex.fromSaved(json).unique("Love Nature 4K"))}
 @Test fun truncatedOrTrailingGarbageIsRejected(){for(s in listOf(base+channel().dropLast(1),base+channel()+"garbage","["+base.replace("}{","},{")+","+channel()+",]")){try{parse(s);fail()}catch(_:Exception){}}}
 @Test fun onlyExactServiceAndPublishedImagePathIsAccepted(){val s=parse().services.single();val rows=JSONArray("""[{"sat":"130","tps":[{"tp":"11294:H:27500","chs":[{"sid":"2229","iconUrl":"gchsicon.datasver.com/icon/130_11296_2229"}]}]}]""");assertEquals("https://gchsicon.datasver.com/icon/130_11296_2229.png",ChlLogoAddon.select(rows,s));assertEquals("",ChlLogoAddon.select(rows,s.copy(sid=1)));assertEquals("",ChlLogoAddon.select(rows,s.copy(frequency=11296)))}
 @Test fun imageUrlsNeverAllowCredentialsHttpOtherHostsOrPathTraversal(){for(s in listOf("http://gchsicon.datasver.com/icon/1","https://evil.test/icon/1","https://secret@gchsicon.datasver.com/icon/1","gchsicon.datasver.com/icon/../1","gchsicon.datasver.com/icon/1?token=abc"))assertEquals("",ChlLogoAddon.imageUrl(s))}
 @Test fun invalidDuplicateSatelliteCannotReplaceEarlierReference(){try{parse(base+"""{"Type":"sat","Index":0,"Angle":260,"Band":"KU"}"""+channel());fail()}catch(_:IllegalArgumentException){}}
}
