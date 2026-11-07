package com.smarthub.plus;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

/** Pure parser tests. Stream URLs are fictitious and must not be logged. */
public class ImportedAudioCatalogTest {
 @Test public void parsesFoxCfgAndSkipsCommentsAndBadSchemes()throws Exception{
  String cfg="# ipaudio: 0|k|\n"+
   "0|Arabic Sport|https://example.com/sport/audio.m3u8?token=sample\n"+
   "0|Arabic Sport|https://example.com/sport/audio.m3u8?token=sample\n"+
   "1|News|http://audio.example.org:8080/live\n"+
   "2|Invalid|file:///data/private\n"+
   "nonsense\n";
  List<ImportedAudioCatalog.Entry> entries=ImportedAudioCatalog.parse(cfg);
  assertEquals(2,entries.size());
  assertEquals("Arabic Sport",entries.get(0).label);
  assertEquals("News",entries.get(1).label);
  assertTrue(entries.get(0).url.startsWith("https://"));
 }
 @Test public void rejectsEmptyCatalog(){
  try{ImportedAudioCatalog.parse("# only comments\n0|X|file:///private");fail();}
  catch(Exception valid){assertTrue(valid.getMessage().contains("CFG_NO_AUDIO_STREAMS"));}
 }
 @Test public void handlesUtf8Names()throws Exception{
  List<ImportedAudioCatalog.Entry> list=ImportedAudioCatalog.parse("0|صوت عربي|https://example.org/audio");
  assertEquals("صوت عربي",list.get(0).label);
 }
}
