package com.smarthub.plus;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;

/** Offline synthetic DVB packet checks: never interprets a stored BISS key as decoded media. */
public final class LiveTsVideoClearTest {
 @Rule public TemporaryFolder tmp=new TemporaryFolder();
 private File capture(int encrypted,int clear)throws Exception{
  File stream=tmp.newFile("observed.ts");
  try(FileOutputStream out=new FileOutputStream(stream)){
   for(int i=0;i<encrypted+clear;i++){
    byte[] block=new byte[188];
    block[0]=0x47;
    block[1]=0x01;block[2]=0; // PID 0x100
    block[3]=(byte)(i<encrypted?0x90:0x10); // scrambled vs clear, payload present
    out.write(block);
   }
  }
  return stream;
 }
 @Test public void failsWhenRecentVideoStillScrambled()throws Exception{
  assertEquals(-1,LiveTsRelay.videoClearState(capture(35,0),256));
 }
 @Test public void acceptsOnlyRecentlyClearVideo()throws Exception{
  assertEquals(1,LiveTsRelay.videoClearState(capture(40,40),256));
 }
 @Test public void incompleteOrWrongVideoPidDoesNotClaimDecode()throws Exception{
  assertEquals(0,LiveTsRelay.videoClearState(capture(0,8),256));
  assertEquals(0,LiveTsRelay.videoClearState(capture(0,40),260));
 }
}
