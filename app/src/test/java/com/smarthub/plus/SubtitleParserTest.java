package com.smarthub.plus;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SubtitleParserTest {
    @Test public void assKeepsCentisecondsCommasAndArabicButRemovesFormattingAndDrawings(){String input="[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.25,0:00:03.50,Default,,0,0,0,,{\\b1}مرحبا، friend\\NSecond, line\nDialogue: 0,0:00:04.00,0:00:05.00,Default,,0,0,0,,{\\p1}m 0 0 l 1 1\n";List<SubtitleParser.Cue> cues=SubtitleParser.parse(input);assertEquals(1,cues.size());assertEquals(1250,cues.get(0).startMs);assertEquals(3500,cues.get(0).endMs);assertEquals("مرحبا، friend\nSecond, line",cues.get(0).text);}
    @Test public void readsBomCrLfSrtMultilineAndMarkup() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("\uFEFF1\r\n00:00:01,250 --> 00:00:03,500\r\n<b>Hello</b> &amp; world\r\nمرحبا\r\n\r\n");
        assertEquals(1,cues.size());assertEquals(1250,cues.get(0).startMs);assertEquals(3500,cues.get(0).endMs);assertEquals("Hello & world\nمرحبا",cues.get(0).text);
    }
    @Test public void readsVttSettingsIdentifiersAndSkipsNotes() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("WEBVTT\n\nNOTE private note\n00:00.000 --> 00:01.000\nignored\n\nSTYLE\n::cue { color: red; }\n\nopening\n00:01.500 --> 00:04.250 align:start position:10%\n<v narrator>Hello</v>\n\n");
        assertEquals(1,cues.size());assertEquals(1500,cues.get(0).startMs);assertEquals("Hello",cues.get(0).text);
    }
    @Test public void invalidAndReversedIntervalsDoNotBecomeCaptions() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("1\n00:00:05,000 --> 00:00:02,000\nreversed\n\n2\n00:99:01,000 --> 01:00:00,000\ninvalid\n\n3\n00:00:06,000 --> 00:00:07,000\nvalid\n");
        assertEquals(1,cues.size());assertEquals("valid",cues.get(0).text);
    }
    @Test public void activeCueUsesExclusiveEndAndOverlapsAreCombined() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("1\n00:00:01,000 --> 00:00:03,000\nA\n\n2\n00:00:02,000 --> 00:00:04,000\nB\n");
        assertNull(SubtitleParser.activeCue(cues,999));assertEquals("A\nB",SubtitleParser.textAt(cues,2500));assertEquals("B",SubtitleParser.textAt(cues,3000));assertEquals("",SubtitleParser.textAt(cues,4000));
    }
    @Test public void shiftedSrtClampsBeginningAndDropsFullyNegativeCue() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("1\n00:00:00,200 --> 00:00:00,500\nearly\n\n2\n00:00:01,000 --> 00:00:03,000\nvisible\n");
        String output=SubtitleParser.toSrt(cues,-1500);assertFalse(output.contains("early"));assertTrue(output.startsWith("1\n00:00:00,000 --> 00:00:01,500"));assertEquals("visible",SubtitleParser.parse(output).get(0).text);
    }
    @Test public void roundTripKeepsRealTimesAndUnicodeText() {
        List<SubtitleParser.Cue> cues=SubtitleParser.parse("WEBVTT\n\n01:02:03.456 --> 01:02:05.678\nالعراق — English\n");
        List<SubtitleParser.Cue> roundTrip=SubtitleParser.parse(SubtitleParser.toSrt(cues));assertEquals(cues.get(0).startMs,roundTrip.get(0).startMs);assertEquals(cues.get(0).endMs,roundTrip.get(0).endMs);assertEquals(cues.get(0).text,roundTrip.get(0).text);
    }
}
