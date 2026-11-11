package com.smarthub.plus;

import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Parses only known channel-banner fields from the receiver's accessible UI. */
public final class SatelliteUiParser {
    public static final String DEFAULT_PACKAGE="com.tosmart.aurum";
    public static final Set<String> VIEW_IDS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "tv_channel_info_index","tv_channel_info_program_name","mtv_info_current_epg_time","mtv_info_current_epg","mtv_info_next_epg_time","mtv_info_next_epg")));
    private static final Pattern RANGE=Pattern.compile("(?<![0-9])([0-9]{1,2}):([0-9]{2})\\s*(?:[-–—~]|to|إلى)\\s*([0-9]{1,2}):([0-9]{2})(?![0-9])",Pattern.CASE_INSENSITIVE);
    private static final Pattern POINT=Pattern.compile("(?<![0-9])([0-9]{1,2}):([0-9]{2})(?![0-9])");
    private SatelliteUiParser(){}

    public static SatelliteState parse(Map<String,String> fields,long now,ZoneId zone){return parse(fields,now,zone,DEFAULT_PACKAGE);}
    public static SatelliteState parse(Map<String,String> fields,long now,ZoneId zone,String packageName){
        if(fields==null||zone==null||packageName==null)return null;
        String channel=clean(value(fields,packageName,"tv_channel_info_program_name"),160),number=digits(clean(value(fields,packageName,"tv_channel_info_index"),24));
        if(channel.isEmpty()||!number.matches("[0-9]{1,8}"))return null;
        number=number.replaceFirst("^0+(?!$)","");SatelliteState state=new SatelliteState();state.channelId="Aurum:"+number;state.channelName=channel;state.source="accessibility:"+packageName;state.observedAt=now;
        String current=clean(value(fields,packageName,"mtv_info_current_epg"),600),next=clean(value(fields,packageName,"mtv_info_next_epg"),600);
        if(!noEpg(current)){
            state.title=current;Window window=currentWindow(value(fields,packageName,"mtv_info_current_epg_time"),now,zone);
            if(window.stale)state.title="";else{state.startMs=window.start;state.endMs=window.end;}
        }
        if(!noEpg(next)){
            state.nextTitle=next;Window window=nextWindow(value(fields,packageName,"mtv_info_next_epg_time"),now,zone);state.nextStartMs=window.start;state.nextEndMs=window.end;
        }
        state.expire(now);return state;
    }
    public static boolean currentProvided(Map<String,String> fields,String pkg){return fields.containsKey("mtv_info_current_epg")||fields.containsKey(pkg+":id/mtv_info_current_epg");}
    public static boolean nextProvided(Map<String,String> fields,String pkg){return fields.containsKey("mtv_info_next_epg")||fields.containsKey(pkg+":id/mtv_info_next_epg");}
    public static boolean sameChannel(SatelliteState a,SatelliteState b){if(a==null||b==null)return false;boolean sameName=!a.channelName.isEmpty()&&normalized(a.channelName).equals(normalized(b.channelName));if(!a.channelId.isEmpty()&&a.channelId.equals(b.channelId))return a.channelName.isEmpty()||b.channelName.isEmpty()||sameName;return sameName;}
    public static boolean noEpg(String value){
        String text=normalized(value);if(text.isEmpty()||text.matches("[-–— _.:]+"))return true;
        return text.contains("لا يوجد دليل")||text.contains("لايوجد دليل")||text.contains("لا توجد معلومات")||text.contains("لاتوجد معلومات")||text.contains("لا توجد برامج")||text.contains("لا يوجد برنامج")||text.contains("no epg")||text.contains("no programme")||text.contains("no program")||text.contains("no event")||text.contains("no information")||text.equals("n/a");
    }
    public static String normalized(String value){return Normalizer.normalize(clean(value,2000),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه').trim();}
    private static String value(Map<String,String> map,String pkg,String id){String value=map.get(pkg+":id/"+id);return value!=null?value:map.getOrDefault(id,"");}
    private static String clean(String text,int limit){if(text==null)return "";String value=text.replace("ـ","").replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","").replaceAll("\\s+"," ").trim();return value.length()>limit?value.substring(0,limit):value;}
    private static String digits(String value){StringBuilder out=new StringBuilder();for(char c:value.toCharArray())out.append(c>='٠'&&c<='٩'?(char)('0'+c-'٠'):c>='۰'&&c<='۹'?(char)('0'+c-'۰'):c);return out.toString();}
    private static Window currentWindow(String value,long now,ZoneId zone){
        Matcher m=RANGE.matcher(digits(clean(value,120)));if(!m.find())return new Window();
        LocalTime start=time(m.group(1),m.group(2)),end=time(m.group(3),m.group(4));if(start==null||end==null||start.equals(end))return new Window();
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for(int day=-1;day<=0;day++){LocalDate date=today.plusDays(day);long a=date.atTime(start).atZone(zone).toInstant().toEpochMilli(),b=date.plusDays(end.isBefore(start)?1:0).atTime(end).atZone(zone).toInstant().toEpochMilli();if(a<=now&&b>now)return new Window(a,b);}
        Window missing=new Window();missing.stale=true;return missing;
    }
    private static Window nextWindow(String value,long now,ZoneId zone){
        String text=digits(clean(value,120));Matcher range=RANGE.matcher(text);LocalTime start,end=null;
        if(range.find()){start=time(range.group(1),range.group(2));end=time(range.group(3),range.group(4));if(end==null||start==null||start.equals(end))return new Window();}
        else{Matcher point=POINT.matcher(text);if(!point.find())return new Window();start=time(point.group(1),point.group(2));if(start==null)return new Window();}
        LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();long a=date.atTime(start).atZone(zone).toInstant().toEpochMilli();if(a<now){date=date.plusDays(1);a=date.atTime(start).atZone(zone).toInstant().toEpochMilli();}
        long b=end==null?0:date.plusDays(end.isBefore(start)?1:0).atTime(end).atZone(zone).toInstant().toEpochMilli();return new Window(a,b);
    }
    private static LocalTime time(String hour,String minute){try{return LocalTime.of(Integer.parseInt(hour),Integer.parseInt(minute));}catch(Exception bad){return null;}}
    private static final class Window{long start,end;boolean stale;Window(){}Window(long a,long b){start=a;end=b;}}
}
