package com.smarthub.plus;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;

public final class ArtworkCachePrunerTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 private File add(File dir,int id,int bytes,long modified)throws Exception{
  String name=String.format(java.util.Locale.ROOT,"%064x",id);
  File file=new File(dir,name);
  try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[bytes]);}
  assertTrue(file.setLastModified(modified));
  return file;
 }
 @Test public void removesOldestWhenMoreThan24Cached()throws Exception{
  File dir=temp.newFolder("cache");
  for(int i=1;i<=30;i++)add(dir,i,128,10000L+i*1000);
  ArtworkCachePruner.prune(dir,null);
  for(int i=1;i<=6;i++)assertFalse(new File(dir,
    String.format(java.util.Locale.ROOT,"%064x",i)).exists());
  for(int i=7;i<=30;i++)assertTrue(new File(dir,
    String.format(java.util.Locale.ROOT,"%064x",i)).exists());
 }
 @Test public void protectsNewImageAndAtomicFiles()throws Exception{
  File dir=temp.newFolder("art");
  File protectedFile=add(dir,1,80,1000L);
  for(int i=2;i<=26;i++)add(dir,i,80,1000L+i);
  File inProgress=new File(dir,"atomic.new");
  try(FileOutputStream out=new FileOutputStream(inProgress)){out.write(1);}
  ArtworkCachePruner.prune(dir,protectedFile);
  assertTrue(protectedFile.exists());
  assertTrue(inProgress.exists());
 }
 @Test public void neverDeletesRandomFilesOrDirectories()throws Exception{
  File dir=temp.newFolder("random");
  File unrelated=new File(dir,"receiver-catalog.db");
  try(FileOutputStream out=new FileOutputStream(unrelated)){out.write(5);}
  File folder=new File(dir,"recordings");assertTrue(folder.mkdir());
  for(int i=1;i<=26;i++)add(dir,i,64,10000+i);
  ArtworkCachePruner.prune(dir,null);
  assertTrue(unrelated.exists());assertTrue(folder.exists());
 }
}
