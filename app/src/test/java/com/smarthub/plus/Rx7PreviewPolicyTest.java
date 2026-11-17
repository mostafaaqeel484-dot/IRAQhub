package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Rx7PreviewPolicyTest {
 @Test public void trustedUserSuppliedStreamUrlOnly(){
  assertTrue(Rx7PreviewPolicy.validStream("https://tv.example.org/live/stream.m3u8"));
  assertTrue(Rx7PreviewPolicy.validStream("http://192.168.1.90:8080/stream.ts"));
  assertFalse(Rx7PreviewPolicy.validStream("file:///data/data/foo.ts"));
  assertFalse(Rx7PreviewPolicy.validStream("rtsp://example.org/live"));
  assertFalse(Rx7PreviewPolicy.validStream("https://user:token@example.org/a.ts"));
  assertFalse(Rx7PreviewPolicy.validStream("https://example.org/a.ts#fragment"));
  assertFalse(Rx7PreviewPolicy.validStream("https://example.org\\evil"));
 }
 @Test public void neverPreviewWrongOrUnfocusedChannelOrWhileVideoBusy(){
  String url="https://tv.example.org/live/stream.m3u8";
  assertTrue(Rx7PreviewPolicy.allowed(true,true,true,false,"DVB:7:13:42","DVB:7:13:42",url));
  assertFalse(Rx7PreviewPolicy.allowed(true,true,true,false,"DVB:7:13:42","DVB:7:13:43",url));
  assertFalse(Rx7PreviewPolicy.allowed(true,false,true,false,"a","a",url));
  assertFalse(Rx7PreviewPolicy.allowed(true,true,true,true,"a","a",url));
  assertFalse(Rx7PreviewPolicy.allowed(true,true,false,false,"a","a",url));
  assertFalse(Rx7PreviewPolicy.allowed(false,true,true,false,"a","a",url));
 }
}
