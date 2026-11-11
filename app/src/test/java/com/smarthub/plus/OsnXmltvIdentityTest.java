package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class OsnXmltvIdentityTest {
 @Test public void knownOsnMovieAliasesRemainExact(){
  assertEquals("osnmoviesaction",StreamingXmlTv.channelKey("OSN TV Movies Action"));
  assertEquals("osnmoviesaction",StreamingXmlTv.channelKey("OSN Movies Action HD"));
  assertEquals("osnmoviesaction",StreamingXmlTv.channelKey("OSN Movie Action"));
  assertEquals("osnshowcase",StreamingXmlTv.channelKey("OSN TV Showcase"));
  assertEquals("osnmoviespremiere",StreamingXmlTv.channelKey("OSN Movies Premiere"));
 }
 @Test public void neverCollapseDistinctOsnChannels(){
  assertNotEquals(StreamingXmlTv.channelKey("OSN Movies Action"),
    StreamingXmlTv.channelKey("OSN Movies Comedy"));
  assertNotEquals(StreamingXmlTv.channelKey("OSN TV One"),
    StreamingXmlTv.channelKey("OSN TV Now"));
 }
 @Test public void osnUsesSaudiGuideFeed(){
  assertEquals("SA1",OnlineEpgGuide.feed("OSN Movies Action HD"));
 }
}
