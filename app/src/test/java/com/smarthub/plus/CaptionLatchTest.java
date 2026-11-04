package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptionLatchTest {
 @Test public void punctuationNoiseDoesNotRestartPendingTranslation(){CaptionLatch l=new CaptionLatch();long t=l.offer("A","Hello, world!",1000);assertEquals(t,l.offer("A","Hello world",1600));assertTrue(l.current(t,2000));}
 @Test public void speakerLineBreaksArePreserved(){CaptionLatch l=new CaptionLatch();l.offer("A","First speaker\nSecond speaker",1000);assertEquals("First speaker\nSecond speaker",l.text());}
 @Test public void languageChangeRejectsPreviousTranslation(){CaptionLatch l=new CaptionLatch();long t=l.offer("A|en|ar","Hello",1000);long changed=l.offer("A|en|fr","Hello",1300);assertFalse(l.current(t,1400));assertTrue(l.current(changed,1400));}
 @Test public void slowTranslationRemainsValidWhileOriginalCueIsStillObserved(){CaptionLatch l=new CaptionLatch();long t=l.offer("channel|film","Hello world",1000);for(long now=1500;now<20000;now+=500)assertEquals(t,l.offer("channel|film","Hello world",now));assertTrue(l.current(t,20000));}
 @Test public void changingCueOrChannelRejectsOldTranslation(){CaptionLatch l=new CaptionLatch();long old=l.offer("A","First sentence",1000);long next=l.offer("A","Next sentence",1500);assertFalse(l.current(old,1600));assertTrue(l.current(next,1600));long changed=l.offer("B","Next sentence",1700);assertFalse(l.current(next,1800));assertTrue(l.current(changed,1800));}
 @Test public void blankFramesExpireCueAndItsPendingTranslation(){CaptionLatch l=new CaptionLatch();long t=l.offer("A","Hello",1000);l.offer("A","",1300);assertTrue(l.current(t,1300));l.offer("A","",1900);assertFalse(l.current(t,1900));assertEquals("",l.text());}
 @Test public void stalledCaptureCannotKeepAnOldCaptionVisible(){CaptionLatch l=new CaptionLatch();long t=l.offer("A","Hello",1000);assertFalse(l.current(t,3500));}
}
