package com.smarthub.plus
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.AtomicFile
import java.io.File
class ChannelLogoCache(context:Context){private val dir=File(context.filesDir,"resolved-logos");private val ram=object:LruCache<String,Bitmap>(12*1024*1024){override fun sizeOf(k:String,b:Bitmap)=b.allocationByteCount}
 fun peek(key:String):Bitmap?=ram.get(key)
 fun read(key:String):Bitmap?{peek(key)?.let{return it};val file=File(dir,digest(key)+".png");if(!file.isFile)return null;return BitmapFactory.decodeFile(file.path)?.also{ram.put(key,it)}}
 fun remember(key:String,bitmap:Bitmap){ram.put(key,bitmap)}
 fun save(key:String,bitmap:Bitmap){dir.mkdirs();val file=AtomicFile(File(dir,digest(key)+".png"));val out=file.startWrite();try{bitmap.compress(Bitmap.CompressFormat.PNG,100,out);file.finishWrite(out);ram.put(key,bitmap)}catch(e:Exception){file.failWrite(out);throw e}}
 companion object{fun digest(s:String)=java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}}
}
