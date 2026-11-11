package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ChannelNumberInputTest {
 @Test public void digitsSelectRealOrdinal(){
  ChannelNumberInput b=new ChannelNumberInput();
  b.add(5);b.add(5);
  assertEquals("55",b.display());assertEquals(55,b.number());
  b.clear();assertFalse(b.hasInput());
 }
 @Test public void invalidZeroDoesNotSelectChannel(){
  ChannelNumberInput b=new ChannelNumberInput();
  b.add(0);assertEquals(-1,b.number());
  b.add(1);assertEquals(1,b.number());
 }
 @Test public void overflowDoesNotSilentlyNavigateAnotherChannel(){
  ChannelNumberInput b=new ChannelNumberInput();
  for(int n=1;n<=6;n++)b.add(n);
  assertEquals("6",b.display());
  assertEquals(6,b.number());
 }
}
