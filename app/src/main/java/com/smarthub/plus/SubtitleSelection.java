package com.smarthub.plus;
import java.util.*;
/** Automatic selection requires the same film/episode identity and a human subtitle. */
final class SubtitleSelection {
 static OpenSubtitlesClient.SubtitleFile choose(List<OpenSubtitlesClient.SubtitleFile> files,MediaRepository.Item work,BroadcastTitle wanted,String language){
  if(work==null||work.imdbId.isEmpty())return null;List<OpenSubtitlesClient.SubtitleFile> suitable=new ArrayList<>();String id=identity(work.imdbId);
  for(OpenSubtitlesClient.SubtitleFile f:files){if(!identity(f.imdbId).equals(id)||f.ai||f.machine)continue;if(!Arrays.asList("ar","en").contains(f.language))continue;if("series".equals(work.kind)&&(wanted.season<=0||wanted.episode<=0||f.season!=wanted.season||f.episode!=wanted.episode))continue;String release=(f.release+" "+f.filename).toLowerCase(Locale.ROOT);if(release.matches(".*(?:^|[ ._-])cd[1-9](?:[ ._-]|$).*"))continue;if((release.contains("extended")||release.contains("director's cut")||release.contains("directors cut"))&&!wanted.title.toLowerCase(Locale.ROOT).contains("extended")&&!wanted.title.toLowerCase(Locale.ROOT).contains("director"))continue;suitable.add(f);}
  suitable.sort(Comparator.comparingInt((OpenSubtitlesClient.SubtitleFile f)->f.language.equals(language)?0:1).thenComparing(Comparator.comparingLong((OpenSubtitlesClient.SubtitleFile f)->f.downloadCount).reversed()).thenComparingLong(f->f.id));return suitable.isEmpty()?null:suitable.get(0);
 }
 private static String identity(String value){return value.replaceFirst("^tt","").replaceFirst("^0+","");}
}
