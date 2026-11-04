package com.smarthub.plus;
import java.util.regex.*;
/** Removes only explicit programme labels/quality markers; identity still needs a unique match. */
final class BroadcastTitle {
 final String title;final int year,season,episode;
 private BroadcastTitle(String t,int y,int s,int e){title=t;year=y;season=s;episode=e;}
 static BroadcastTitle parse(String raw){String text=raw==null?"":raw.trim();StringBuilder ascii=new StringBuilder();for(char c:text.toCharArray()){int n=Character.digit(c,10);ascii.append(n>=0?(char)('0'+n):c);}text=ascii.toString().replaceFirst("(?i)^(?:movie|film|series|فيلم(?:\\s+(?:أجنبي|اجنبي|عربي))?|مسلسل)\\s*[:：-]\\s*","").replaceFirst("(?i)\\s+(?:HD|FHD|UHD|4K)$","").trim();int year=0,season=0,episode=0;Matcher ep=Pattern.compile("(?i)\\s+S([0-9]{1,2})E([0-9]{1,3})$").matcher(text);if(ep.find()){season=Integer.parseInt(ep.group(1));episode=Integer.parseInt(ep.group(2));text=text.substring(0,ep.start()).trim();}if(episode==0){Matcher only=Pattern.compile("(?i)\\s*[:：-]?\\s*(?:episode|ep\\.?|الحلقة)\\s*([0-9]{1,3})$").matcher(text);if(only.find()){episode=Integer.parseInt(only.group(1));text=text.substring(0,only.start()).trim();}}Matcher y=Pattern.compile("\\s*[\\[(]((?:19|20)[0-9]{2})[\\])]$").matcher(text);if(y.find()){year=Integer.parseInt(y.group(1));text=text.substring(0,y.start()).trim();}return new BroadcastTitle(text,year,season,episode);}
 static String normalized(String s){return PlaylistRepository.normalize(s);}
 boolean matches(MediaRepository.Item item){if(year>0&&"film".equals(item.kind)&&!item.release.startsWith(Integer.toString(year)))return false;if(normalized(title).equals(normalized(item.title)))return true;for(String alias:item.matchTitles)if(normalized(title).equals(normalized(alias)))return true;return false;}
}
