package com.smarthub.plus;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SubtitleSelectionTest {
 private OpenSubtitlesClient.SubtitleFile file(long id,String imdb,String lang,long downloads,boolean machine){OpenSubtitlesClient.SubtitleFile f=new OpenSubtitlesClient.SubtitleFile(id,"film.srt","WEB",lang,23.976,"Film",0,0,false,machine);f.imdbId=imdb;f.downloadCount=downloads;return f;}
 private MediaRepository.Item work(){MediaRepository.Item w=new MediaRepository.Item();w.kind="film";w.imdbId="tt0133093";return w;}
 @Test public void popularityNeverOverridesIdentityOrRequestedLanguage(){OpenSubtitlesClient.SubtitleFile arabic=file(1,"133093","ar",3,false),wrong=file(2,"9999999","ar",100000,false),english=file(3,"tt0133093","en",90000,false);assertSame(arabic,SubtitleSelection.choose(Arrays.asList(wrong,english,arabic),work(),BroadcastTitle.parse("Film"),"ar"));}
 @Test public void automatedTranslationAndWrongEpisodesAreNotAutoPlayed(){assertNull(SubtitleSelection.choose(Arrays.asList(file(1,"133093","ar",3,true)),work(),BroadcastTitle.parse("Film"),"ar"));MediaRepository.Item series=work();series.kind="series";OpenSubtitlesClient.SubtitleFile noEpisode=file(1,"133093","ar",3,false);assertNull(SubtitleSelection.choose(Arrays.asList(noEpisode),series,BroadcastTitle.parse("Film S02E03"),"ar"));}
}
