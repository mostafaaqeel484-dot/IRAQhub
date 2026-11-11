package com.smarthub.plus;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class LogoBackgroundCleanerTest {
 @Test public void ivoryBackgroundRemoved(){
  int w=32,h=24;int[] p=new int[w*h];Arrays.fill(p,0xfffff4df);
  for(int y=8;y<15;y++)for(int x=7;x<24;x++)p[y*w+x]=0xffb30a24;
  assertTrue(LogoBackgroundCleaner.removeMatte(p,w,h));
  assertEquals(0,p[1]);assertEquals(0xffb30a24,p[10*w+10]);
 }
 @Test public void darkArtworkUnchanged(){
  int[] p=new int[24*24];Arrays.fill(p,0xff202f38);int[] cp=p.clone();
  assertFalse(LogoBackgroundCleaner.removeMatte(p,24,24));assertArrayEquals(cp,p);
 }
 @Test public void interiorWhitePreserved(){
  int w=24,h=24;int[] p=new int[w*h];Arrays.fill(p,0xfffff4df);
  for(int y=3;y<21;y++)for(int x=3;x<21;x++)p[y*w+x]=0xffbf0f22;
  for(int y=10;y<14;y++)for(int x=10;x<15;x++)p[y*w+x]=0xfffff4df;
  assertTrue(LogoBackgroundCleaner.removeMatte(p,w,h));
  assertEquals(0,p[0]);assertEquals(0xfffff4df,p[11*w+11]);
 }
}
