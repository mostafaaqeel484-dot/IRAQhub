package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class ChannelNumberBufferTest {
 @Test public void remoteDigitsCreateDirectChannelNumbers(){
  ChannelNumberBuffer input=new ChannelNumberBuffer();
  assertTrue(input.push(5));assertTrue(input.push(5));
  assertEquals(55,input.value());assertEquals("55",input.display());
  input.clear();assertFalse(input.pending());
  assertFalse(input.push(0));assertFalse(input.push(-1));
  assertTrue(input.push(9));assertTrue(input.push(0));
  assertEquals(90,input.value());
 }
 @Test public void boundedInputDoesNotOverflow(){
  ChannelNumberBuffer input=new ChannelNumberBuffer();
  for(int d=1;d<=6;d++)input.push(d);
  assertEquals("23456",input.display());
 }
}
