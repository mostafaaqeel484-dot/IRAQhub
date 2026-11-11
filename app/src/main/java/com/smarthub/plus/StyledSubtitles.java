package com.smarthub.plus;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.*;
import androidx.media3.extractor.text.ssa.SsaParser;
import androidx.media3.extractor.text.subrip.SubripParser;
import java.util.*;
/** Retains Media3 ASS positions, colours and spans while shifting only the lookup clock. */
@androidx.media3.common.util.UnstableApi
final class StyledSubtitles {
 private final List<CuesWithTiming> timeline=new ArrayList<>();private final long[] maxEnd;
 StyledSubtitles(String content){byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);androidx.media3.extractor.text.SubtitleParser p=content.contains("[Events]")?new SsaParser():content.trim().startsWith("WEBVTT")?new androidx.media3.extractor.text.webvtt.WebvttParser():new SubripParser();p.parse(bytes,androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues(),timeline::add);timeline.sort(Comparator.comparingLong(c->c.startTimeUs));maxEnd=new long[timeline.size()];long end=0;for(int n=0;n<timeline.size();n++){CuesWithTiming cue=timeline.get(n);end=Math.max(end,cue.startTimeUs+cue.durationUs);maxEnd[n]=end;}}
 List<Cue> at(long ms){List<Cue> result=new ArrayList<>();long us=ms*1000;int low=0,high=timeline.size();while(low<high){int mid=(low+high)>>>1;if(timeline.get(mid).startTimeUs<=us)low=mid+1;else high=mid;}for(int n=low-1;n>=0&&maxEnd[n]>us;n--){CuesWithTiming item=timeline.get(n);if(us<item.startTimeUs+item.durationUs)result.addAll(0,item.cues);}return result;}
}
