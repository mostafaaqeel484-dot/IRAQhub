package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ChannelNumberEntryTest {
 @Test public void acceptsChannelAndResets(){
  ChannelNumberEntry input=new ChannelNumberEntry();
  assertFalse(input.pending());
  assertEquals("1",input.append(1));
  assertEquals("12",input.append(2));
  assertEquals("125",input.append(5));
  assertTrue(input.pending());
  assertEquals(125,input.take());
  assertFalse(input.pending());
 }
 @Test public void ignoresBadDigitsAndRejectsZero(){
  ChannelNumberEntry n=new ChannelNumberEntry();
  assertEquals("0",n.append(0));
  assertFalse(n.pending());
  assertEquals(-1,n.take());
  n.append(-1);assertFalse(n.pending());
  n.append(5);n.append(0);n.append(0);n.append(0);n.append(0);
  assertEquals(50000,n.take());
 }
}
