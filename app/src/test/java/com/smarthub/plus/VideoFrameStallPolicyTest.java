package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public class VideoFrameStallPolicyTest {
 @Test public void detectsAudioOnlyFreezeAfterPicture(){
  assertTrue(VideoFrameStallPolicy.stalled(1000,10000,100000,120,0,0,true,false,true));
 }
 @Test public void identifiesRendererStallWhenDecodeContinues(){
  assertTrue(VideoFrameStallPolicy.rendererStalled(1000,10000,100000,120,0,100,true,false,true));
 }
 @Test public void ignoresBufferingAndInsufficientEvidence(){
  assertFalse(VideoFrameStallPolicy.stalled(1000,10000,100000,120,0,0,true,true,true));
  assertFalse(VideoFrameStallPolicy.stalled(1000,10000,0,120,0,0,true,false,true));
  assertFalse(VideoFrameStallPolicy.stalled(1000,10000,100000,120,1,0,true,false,true));
  assertFalse(VideoFrameStallPolicy.stalled(1000,10000,100000,120,0,0,true,false,false));
  assertFalse(VideoFrameStallPolicy.stalled(1000,5000,100000,120,0,0,true,false,true));
 }
}