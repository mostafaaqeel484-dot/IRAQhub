package com.smarthub.plus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.AtomicFile
import java.io.File

/** Compact channel logos: no full-size raster retained for a 48dp Tiger icon. */
class ChannelLogoCache(context:Context){
 private val dir=File(context.filesDir,"resolved-logos")
 private val ram=object:LruCache<String,Bitmap>(3*1024*1024){
  override fun sizeOf(k:String,b:Bitmap)=b.allocationByteCount
 }
 fun peek(key:String):Bitmap?=ram.get(key)
 fun read(key:String):Bitmap?{
  peek(key)?.let{return it}
  val file=File(dir,digest(key)+".png")
  if(!file.isFile)return null
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeFile(file.path,bounds)
  if(bounds.outWidth !in 1..8192||bounds.outHeight !in 1..8192)return null
  val opts=BitmapFactory.Options().apply{
   inSampleSize=LogoImageSizing.sample(bounds.outWidth,bounds.outHeight,512)
   inPreferredConfig=Bitmap.Config.ARGB_8888
  }
  return BitmapFactory.decodeFile(file.path,opts)?.also{ram.put(key,it)}
 }
 fun clearRam(){ram.evictAll()}
 fun remember(key:String,bitmap:Bitmap){ram.put(key,bitmap)}
 fun save(key:String,bitmap:Bitmap){
  dir.mkdirs()
  val ratio=512f/kotlin.math.max(bitmap.width,bitmap.height)
  val output=if(ratio>=1f)bitmap.copy(Bitmap.Config.ARGB_8888,false) else Bitmap.createScaledBitmap(bitmap,
   kotlin.math.max(1,kotlin.math.round(bitmap.width*ratio).toInt()),
   kotlin.math.max(1,kotlin.math.round(bitmap.height*ratio).toInt()),true)
  val file=AtomicFile(File(dir,digest(key)+".png"))
  val out=file.startWrite()
  try{
   check(output.compress(Bitmap.CompressFormat.PNG,100,out)){"LOGO_CACHE_ENCODE_FAILED"}
   file.finishWrite(out)
   ram.put(key,output)
  }catch(e:Exception){file.failWrite(out);throw e}
 }
 companion object{
  fun digest(s:String)=java.security.MessageDigest.getInstance("SHA-256")
   .digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
 }
}
