package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public class Auto422PolicyTest {
 @Test public void thermalAndMemoryPressurePreferSmooth(){
  assertEquals("smooth",Auto422Policy.choose(10,8,true,-1));
  assertEquals("smooth",Auto422Policy.choose(10,8,false,3));
 }
 @Test public void observedLoadControlsChoice(){
  assertEquals("balanced",Auto422Policy.choose(150,8,false,-1));
  assertEquals("smooth",Auto422Policy.choose(600,8,false,-1));
  assertEquals("smooth",Auto422Policy.choose(200,2,false,-1));
 }
 @Test public void leavesCpuHeadroom(){
  assertEquals(3,Auto422Policy.decoderThreads("smooth",8));
  assertEquals(4,Auto422Policy.decoderThreads("balanced",8));
  assertEquals(1,Auto422Policy.decoderThreads("smooth",2));
 }
}