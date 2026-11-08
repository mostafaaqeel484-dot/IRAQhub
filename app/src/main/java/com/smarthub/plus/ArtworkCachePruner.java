package com.smarthub.plus;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Bounded local poster/logo image cache; only our SHA-256-named image files.
 * Do not touch AtomicFile .new/.bak, unrelated app files, receiver DB, APKs,
 * recordings or any user-provided artwork.
 */
final class ArtworkCachePruner {
 static final int MAX_IMAGES=24;
 static final long MAX_TOTAL_BYTES=40L*1024*1024;
 private ArtworkCachePruner(){}
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
  Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
  long size=0;
  int count=0;
  for(File file:files){
   boolean protectedFile=justSaved!=null&&file.equals(justSaved);
   long length=Math.max(0,file.length());
   if(protectedFile||count<MAX_IMAGES&&size+length<=MAX_TOTAL_BYTES){
    size+=length;count++;continue;
   }
   // Deletion failure is non-fatal and is retried on the next successful save.
   file.delete();
  }
 }
}
