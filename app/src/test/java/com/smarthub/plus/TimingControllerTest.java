package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class TimingControllerTest {
 @Test public void programmeClockUsesOnlyEpgWallTime(){ProgrammeClock p=new ProgrammeClock(10000,110000,35000);assertEquals(25000,p.elapsedMs);assertEquals(75000,p.remainingMs);assertEquals(250,p.progress);long subtitleShift=SubtitleSyncRepository.clamp(2500);assertEquals(2500,subtitleShift);assertEquals(75000,new ProgrammeClock(10000,110000,35000).remainingMs);}
 @Test public void unknownEndDoesNotInventRemainingTime(){ProgrammeClock p=new ProgrammeClock(10000,0,35000);assertFalse(p.known);assertEquals(0,p.remainingMs);}
 @Test public void elapsedIsClampedAtProgrammeBoundaries(){assertEquals(0,new ProgrammeClock(10000,110000,5000).progress);assertEquals(1000,new ProgrammeClock(10000,110000,130000).progress);assertEquals(0,new ProgrammeClock(10000,110000,130000).remainingMs);}
 @Test public void sameFilmDifferentFilesAndProvidersHaveIndependentKeys(){String a=SubtitleSyncRepository.identity("tt1234567","file1","OpenSubtitles");assertNotEquals(a,SubtitleSyncRepository.identity("tt1234567","file2","OpenSubtitles"));assertNotEquals(a,SubtitleSyncRepository.identity("tt1234567","file1","Subsource"));assertEquals(a,SubtitleSyncRepository.identity("tt1234567","file1","OpenSubtitles"));}
 @Test public void subtitleOffsetHasSymmetricBoundaries(){assertEquals(-3600000,SubtitleSyncRepository.clamp(Long.MIN_VALUE));assertEquals(3600000,SubtitleSyncRepository.clamp(Long.MAX_VALUE));assertEquals(-1234,SubtitleSyncRepository.clamp(-1234));}
 @Test public void existingArabicIsNotSentToMachineTranslation(){assertTrue(SubtitleSyncRepository.isArabic("هذه ترجمة عربية بالفعل."));assertFalse(SubtitleSyncRepository.isArabic("This is an English subtitle."));assertFalse(SubtitleSyncRepository.isArabic("123 !"));}
 @Test public void subtitlePreviewShowsMilliseconds(){assertEquals("00:01:01.234",ProgrammeClock.format(61234,true));assertEquals("00:01:01",ProgrammeClock.format(61234,false));}
 @Test public void tmdbImagePathsCannotInjectAnotherHost(){assertEquals("",TmdbProvider.image("https://bad.example/image.jpg"));assertEquals("https://image.tmdb.org/t/p/original/test.jpg",TmdbProvider.image("/test.jpg"));assertEquals("",TmdbProvider.image("/../key?secret"));}
}
