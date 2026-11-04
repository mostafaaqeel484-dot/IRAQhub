package com.smarthub.plus
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
class LogoMappingTest{
 private fun mapping()=ChannelAliasDatabase.validate(JSONObject(File("src/main/assets/logo-resolver/mapping.json").takeIf{it.exists()}?.readText()?:File("app/src/main/assets/logo-resolver/mapping.json").readText()))
 @Test fun aliasesResolveToSameVerifiedMbcChannel(){val m=mapping().first;assertEquals("MBC2.ae",m[ChannelAliasDatabase.normalize("MBC TWO")]?.id);assertEquals(m[ChannelAliasDatabase.normalize("MBC 2")],m[ChannelAliasDatabase.normalize("MBC2")]);assertEquals(m[ChannelAliasDatabase.normalize("MBC 2")],m[ChannelAliasDatabase.normalize("MBC 2 HD")])}
 @Test fun acceptanceProvidersAreExplicit(){val m=mapping().first;assertEquals("mbc",m[ChannelAliasDatabase.normalize("MBC ACTION")]?.provider);assertEquals("bein",m[ChannelAliasDatabase.normalize("beIN SPORTS 1")]?.provider);assertNull(m[ChannelAliasDatabase.normalize("MBC UNKNOWN 94")])}
 @Test fun conflictingAliasIsRejected(){val j=JSONObject("{providers:[],channels:[{id:'a',name:'A',aliases:['same']},{id:'b',name:'B',aliases:['same']}]} ");try{ChannelAliasDatabase.validate(j);fail()}catch(_:IllegalArgumentException){}}
 @Test fun decoderSizesDriveExactlyOneBadge(){assertEquals("SD",ResolutionBadge.dimensions(720,576));assertEquals("HD",ResolutionBadge.dimensions(1280,720));assertEquals("FULL HD",ResolutionBadge.dimensions(1920,1080));assertEquals("4K / UHD",ResolutionBadge.dimensions(3840,2160));assertEquals("",ResolutionBadge.dimensions(0,0))}
 @Test fun familyNamesNeverSelectANumberedChannel(){val m=mapping().first;val c=m[ChannelAliasDatabase.normalize("Abu Dhabi Sports")];assertEquals("abu-dhabi",c?.provider);assertEquals("",c?.url);assertNotEquals("AbuDhabiSports1.ae",c?.id)}
 @Test fun emptyProviderIdentifiersAreRejected(){try{ChannelAliasDatabase.validate(JSONObject("{providers:[{id:'',name:'Default'}],channels:[]}"));fail()}catch(_:IllegalArgumentException){}}
}
