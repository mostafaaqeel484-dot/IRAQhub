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
 @Test public void removesOldestWhenMoreThanDiskBudgetCached()throws Exception{
  File dir=temp.newFolder("cache");
  for(int i=1;i<=ArtworkCachePruner.MAX_IMAGES+6;i++)add(dir,i,128,10000L+i*1000);
  ArtworkCachePruner.prune(dir,null);
  for(int i=1;i<=6;i++)assertFalse(new File(dir,
    String.format(java.util.Locale.ROOT,"%064x",i)).exists());
  for(int i=7;i<=ArtworkCachePruner.MAX_IMAGES+6;i++)assertTrue(new File(dir,
    String.format(java.util.Locale.ROOT,"%064x",i)).exists());
 }
 @Test public void protectsNewImageAndAtomicFiles()throws Exception{
  File dir=temp.newFolder("art");
  File protectedFile=add(dir,1,80,1000L);
  for(int i=2;i<=ArtworkCachePruner.MAX_IMAGES+2;i++)add(dir,i,80,1000L+i);
  File inProgress=new File(dir,"atomic.new");
  try(FileOutputStream out=new FileOutputStream(inProgress)){out.write(1);}
  ArtworkCachePruner.prune(dir,protectedFile);
  assertTrue(protectedFile.exists());
  assertTrue(inProgress.exists());
  int remaining=0;
  for(File item:dir.listFiles())if(item.getName().matches("[0-9a-f]{64}"))remaining++;
  assertEquals(ArtworkCachePruner.MAX_IMAGES,remaining);
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
 @Test public void recentlyReadPosterSurvivesEvenIfDownloadedEarlier()throws Exception{
  File dir=temp.newFolder("lru");
  File first=add(dir,1,100,1000L);
  for(int id=2;id<=ArtworkCachePruner.MAX_IMAGES+1;id++)
   add(dir,id,100,1000L+id);
  ArtworkCachePruner.markRead(first);
  // Recency must not falsify the CDN freshness timestamp on flash storage.
  assertEquals(1000L,first.lastModified());
  ArtworkCachePruner.prune(dir,null);
  assertTrue(first.exists());
  assertFalse(new File(dir,String.format(java.util.Locale.ROOT,"%064x",2)).exists());
 }
}
