package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BroadcastVideoClassTest {
 @Test public void classifiesActualDimensionsIntoReceiverProfiles(){
  assertEquals("1080",BroadcastVideoClass.resolution(1920,1080));
  assertEquals("720",BroadcastVideoClass.resolution(1280,720));
  assertEquals("560",BroadcastVideoClass.resolution(720,576));
  assertEquals("360",BroadcastVideoClass.resolution(640,360));
  assertEquals("240",BroadcastVideoClass.resolution(426,240));
  assertEquals("UNKNOWN",BroadcastVideoClass.resolution(-1,-1));
 }
 @Test public void classifiesOnlyDecoderPixelFormats(){
  assertEquals("4:2:2",BroadcastVideoClass.chroma("yuv422p10le"));
  assertEquals("4:2:0",BroadcastVideoClass.chroma("yuv420p"));
  assertEquals("4:2:0",BroadcastVideoClass.chroma("nv12"));
  assertEquals("UNKNOWN",BroadcastVideoClass.chroma("rgb0"));
 }
}
