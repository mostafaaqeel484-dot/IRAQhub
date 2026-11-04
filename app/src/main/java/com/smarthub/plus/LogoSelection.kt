package com.smarthub.plus
import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/** Random per installation, repeatable for the same name so navigation never flickers. */
object LogoSelection {
 @JvmStatic @Synchronized fun seed(context:Context):String{val p=context.getSharedPreferences("smarthub",0);return p.getString("logo_selection_seed",null)?:UUID.randomUUID().toString().also{p.edit().putString("logo_selection_seed",it).apply()}}
 @JvmStatic fun index(seed:String,name:String,count:Int):Int{require(count>0);val b=MessageDigest.getInstance("SHA-256").digest((seed+"|"+name).toByteArray(Charsets.UTF_8));val n=((b[0].toInt() and 127) shl 24) or ((b[1].toInt() and 255) shl 16) or ((b[2].toInt() and 255) shl 8) or (b[3].toInt() and 255);return n%count}
}
