package com.smarthub.plus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Ensures CHL logo lookups never confuse same-name services from different satellites. */
class ChlPhysicalLogoIndexTest {
 private fun service(sat:Int,sid:Int)=JSONObject()
  .put("name","News HD").put("provider","Example")
  .put("sat",sat).put("band","KU")
  .put("frequency",11800).put("polarity","H")
  .put("symbolRate",27500).put("sid",sid)

 @Test fun strictSatelliteTpAndSidLookup(){
  val root=JSONObject().put("version",1)
   .put("satellites",2).put("transponders",2)
   .put("services",JSONArray().put(service(192,101)).put(service(130,101)))
  val index=ChlChannelIndex.fromSaved(root.toString())
  assertEquals(2,index.candidates("News HD").size)
  assertNull(index.unique("News HD"))
  assertEquals(192,index.byPhysical("192:KU:11800:H:27500:101")!!.sat)
  assertEquals(130,index.byPhysical("130:KU:11800:H:27500:101")!!.sat)
  assertNull(index.byPhysical("192:KU:11800:H:27500:102"))
  assertNull(index.byPhysical("192:KU:11900:H:27500:101"))
 }
}
