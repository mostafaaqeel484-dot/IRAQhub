package com.smarthub.plus;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded local poster/logo image cache; only our SHA-256-named image files.
 * Do not touch AtomicFile .new/.bak, unrelated app files, receiver DB, APKs,
 * recordings or any user-provided artwork.
 */
final class ArtworkCachePruner {
 static final int MAX_IMAGES=256;
 static final long MAX_TOTAL_BYTES=128L*1024*1024;
 // Count confirmed image reads as use without touching the on-disk download date.
 // Only 512 recent paths; no poster pixels or Bitmaps are held here.
 private static final Map<String,Long> RECENT=Collections.synchronizedMap(
  new LinkedHashMap<String,Long>(512,0.75f,true){
   @Override protected boolean removeEldestEntry(Map.Entry<String,Long> oldest){
    return size()>512;
   }
  });
 private ArtworkCachePruner(){}
 static void markRead(File file){
  if(imageFile(file))RECENT.put(file.getAbsolutePath(),System.currentTimeMillis());
 }
 private static long accessed(File file){
  Long time=RECENT.get(file.getAbsolutePath());
  return Math.max(file.lastModified(),time==null?0:time);
 }
 private static boolean imageFile(File f){
  if(f==null||!f.isFile())return false;
  String name=f.getName();
  if(name.length()!=64)return false;
  for(int n=0;n<64;n++){
   char c=name.charAt(n);
   if(c!='0'&&c!='1'&&c!='2'&&c!='3'&&c!='4'&&c!='5'&&
      c!='6'&&c!='7'&&c!='8'&&c!='9'&&
      c!='a'&&c!='b'&&c!='c'&&c!='d'&&c!='e'&&c!='f')
    return false;
  }
  return true;
 }
 static void prune(File directory,File justSaved){
  if(directory==null||!directory.isDirectory())return;
  File[] files=directory.listFiles(ArtworkCachePruner::imageFile);
  if(files==null||files.length==0)return;
  Arrays.sort(files,Comparator.comparingLong(ArtworkCachePruner::accessed).reversed());
  boolean protect=justSaved!=null&&imageFile(justSaved)&&
    directory.equals(justSaved.getParentFile());
  // Count the newly saved image BEFORE choosing LRU survivors. Otherwise
  // an old but protected object could become the 25th retained image.
  long size=protect?Math.max(0,justSaved.length()):0;
  int count=protect?1:0;
  for(File file:files){
   if(protect&&file.equals(justSaved))continue;
   long length=Math.max(0,file.length());
   if(count<MAX_IMAGES&&size+length<=MAX_TOTAL_BYTES){
    size+=length;count++;continue;
   }
   // Deletion failure is non-fatal and is retried on the next successful save.
   if(file.delete())RECENT.remove(file.getAbsolutePath());
  }
 }
}
