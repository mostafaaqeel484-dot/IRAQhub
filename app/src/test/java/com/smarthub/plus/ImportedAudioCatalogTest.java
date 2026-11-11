package com.smarthub.plus;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;
public class ImportedAudioCatalogTest {
 private static List<ImportedAudioCatalog.Track> read(String text)throws Exception{
  return ImportedAudioCatalog.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
 }
 @Test public void parsesFoxCfgAndSkipsHeader()throws Exception{
  List<ImportedAudioCatalog.Track> tracks=read("# ipaudio: 0|k|\n0|Fox FM English|https://example.com/audio.m3u8\n0|Fox FM Arabic|http://example.com/live\n");
  assertEquals(2,tracks.size());assertEquals("Fox FM English",tracks.get(0).name);
 }
 @Test public void preservesOrderAndRemovesDuplicates()throws Exception{
  List<ImportedAudioCatalog.Track> tracks=read("0|News|https://example.com/1\n0|News|https://example.com/1\n0|Music|https://example.com/2\n");
  assertEquals(2,tracks.size());assertEquals("Music",tracks.get(1).name);
 }
 @Test public void dropsUnsupportedSchemes()throws Exception{
  assertEquals(1,read("0|Invalid|ftp://example.com/a\n0|Valid|https://example.com/b\n").size());
 }
 @Test(expected=IOException.class)public void refusesEmptyCatalog()throws Exception{
  read("# ipaudio: 0|k|\n0|Bad|file:///tmp/leak\n");
 }
}