package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Software422SourceTest {
 @Test public void acceptExplicitTransportStreams(){
  assertTrue(Software422Source.validNetwork("https://example.net/live.ts"));
  assertTrue(Software422Source.validNetwork("rtsp://192.168.1.10:554/stream"));
  assertTrue(Software422Source.validNetwork("udp://239.1.2.3:1234"));
 }
 @Test public void rejectLocalAndUnsafeSchemes(){
  assertFalse(Software422Source.validNetwork("file:///sdcard/test.ts"));
  assertFalse(Software422Source.validNetwork("content://secrets/file"));
  assertFalse(Software422Source.validNetwork("javascript:alert(1)"));
  assertFalse(Software422Source.validNetwork("https://user:password@example.net/a"));
  assertFalse(Software422Source.validNetwork("https://example.net/a#fragment"));
  assertFalse(Software422Source.validNetwork("https://example.net/one\nPlay"));
 }
}
