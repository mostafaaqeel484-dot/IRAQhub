package com.smarthub.plus;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

/** A long 4:2:2 capture prunes TS files: their null slots must not stop playback. */
public final class LiveTsRelayMemoryTest {
 @Test public void prunedSegmentDoesNotCrashBitrateMeter()throws Exception{
  File active=File.createTempFile("iraqhub-ts-meter",".ts");
  try{
   byte[] data=new byte[188*10];
   try(FileOutputStream out=new FileOutputStream(active)){out.write(data);}
   assertEquals(12345L+1880L,LiveTsRelay.measuredBytes(
       Arrays.asList(null,null,active),12345L));
   assertEquals(12345L,LiveTsRelay.measuredBytes(
       Arrays.asList(null,null),12345L));
  }finally{active.delete();}
 }
}
