package com.smarthub.plus

/** Decode bounds are checked before allocating a raster. Pure functions for JVM tests. */
object LogoImageSizing {
 @JvmStatic fun sample(width:Int,height:Int,target:Int):Int {
  require(width>0&&height>0&&target>0)
  var scale=1
  while(width/scale>target||height/scale>target)scale*=2
  return scale
 }
}
