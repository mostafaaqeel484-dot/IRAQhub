package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact guide pages are references only; mismatched channels are not substituted. */
public final class OsnGuideReferenceTest {
 @Test public void knownOsnChannelsLinkToOwnGuide(){
  assertEquals("https://www.osn.com/en-iq/explore/channels/osntv-yahala-aflam",
    OsnGuideReference.urlFor("OSNtv Yahala Aflam HD"));
  assertEquals("https://www.osn.com/en-iq/explore/channels/osntv-now",
    OsnGuideReference.urlFor("OSNtv Now"));
 }
 @Test public void unknownChannelsAreNotGuessed(){
  assertEquals("",OsnGuideReference.urlFor("OSN Random Channel"));
  assertEquals("",OsnGuideReference.urlFor("OSNtv Yahala Aflam +2"));
  assertEquals("",OsnGuideReference.urlFor("Arena Sport"));
 }
}
