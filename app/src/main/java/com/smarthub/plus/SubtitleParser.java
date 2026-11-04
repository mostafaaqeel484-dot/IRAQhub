package com.smarthub.plus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Plain Java SRT/WebVTT parser shared by the player, translator and overlay. */
public final class SubtitleParser {
    private SubtitleParser() { }
    public static final class Cue {
        public final long startMs, endMs;
        public final String text;
        public Cue(long startMs, long endMs, String text) {
            if (startMs < 0 || endMs <= startMs) throw new IllegalArgumentException("Invalid cue interval");
            this.startMs = startMs; this.endMs = endMs; this.text = text == null ? "" : text;
        }
    }
    public static List<Cue> parse(String source) {
        if (source == null || source.trim().isEmpty()) return Collections.emptyList();
        if(source.contains("[Events]")&&source.contains("Dialogue:"))return parseAss(source);
        String[] lines = source.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<Cue> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("NOTE") || line.startsWith("NOTE ") || line.equals("STYLE") || line.equals("REGION")) {
                while (i + 1 < lines.length && !lines[i + 1].trim().isEmpty()) i++;
                continue;
            }
            int arrow = line.indexOf("-->");
            if (arrow < 0) continue;
            String right = line.substring(arrow + 3).trim();
            String[] endAndSettings = right.split("\\s+", 2);
            long start, end;
            try { start = timestamp(line.substring(0, arrow).trim()); end = timestamp(endAndSettings[0]); }
            catch (IllegalArgumentException invalid) { continue; }
            StringBuilder text = new StringBuilder();
            while (i + 1 < lines.length && !lines[i + 1].trim().isEmpty() && !lines[i + 1].contains("-->")) {
                if (text.length() > 0) text.append('\n');
                text.append(lines[++i]);
            }
            String plain = plainText(text.toString()).trim();
            if (end > start && !plain.isEmpty()) result.add(new Cue(start, end, plain));
            if (result.size() >= 50000) throw new IllegalArgumentException("Subtitle contains more than 50,000 cues");
        }
        result.sort(Comparator.comparingLong(c -> c.startMs));
        return result;
    }
    static long timestamp(String raw) {
        String[] clock = raw.split(":", -1);
        if (clock.length != 2 && clock.length != 3) throw new IllegalArgumentException("Invalid timestamp");
        String[] seconds = clock[clock.length - 1].split("[.,]", -1);
        if (seconds.length != 2 || !seconds[0].matches("\\d{2}") || !seconds[1].matches("\\d{1,3}")) throw new IllegalArgumentException("Invalid seconds");
        String minuteRaw = clock[clock.length - 2];
        if (!minuteRaw.matches("\\d{2}")) throw new IllegalArgumentException("Invalid minutes");
        long hours = 0;
        try {
            if (clock.length == 3) { if (!clock[0].matches("\\d{2,6}")) throw new IllegalArgumentException("Invalid hours"); hours = Long.parseLong(clock[0]); }
            int minutes = Integer.parseInt(minuteRaw), secs = Integer.parseInt(seconds[0]);
            if (minutes > 59 || secs > 59) throw new IllegalArgumentException("Invalid clock");
            int millis = Integer.parseInt((seconds[1] + "00").substring(0, 3));
            return ((hours * 60 + minutes) * 60 + secs) * 1000L + millis;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid timestamp", error); }
    }
    private static List<Cue> parseAss(String input){
        List<Cue> result=new ArrayList<>();String[] format={"Layer","Start","End","Style","Name","MarginL","MarginR","MarginV","Effect","Text"};boolean events=false;
        for(String line:input.replace("\uFEFF","").replace("\r","").split("\n")){
            line=line.trim();if(line.startsWith("[")){events=line.equalsIgnoreCase("[Events]");continue;}if(!events)continue;
            if(line.regionMatches(true,0,"Format:",0,7)){format=line.substring(7).trim().split(",");continue;}
            if(!line.regionMatches(true,0,"Dialogue:",0,9))continue;String[] values=line.substring(9).trim().split(",",format.length);if(values.length!=format.length)continue;
            try{String start="",end="",text="";for(int i=0;i<format.length;i++){String name=format[i].trim();if(name.equalsIgnoreCase("Start"))start=values[i].trim();if(name.equalsIgnoreCase("End"))end=values[i].trim();if(name.equalsIgnoreCase("Text"))text=values[i];}if(text.matches("(?s).*\\{[^}]*\\\\p[1-9][^}]*}.*"))continue;long a=assTime(start),b=assTime(end);text=text.replaceAll("\\{[^}]*}","").replace("\\N","\n").replace("\\n","\n").replace("\\h"," ").trim();if(b>a&&!text.isEmpty())result.add(new Cue(a,b,text));}catch(Exception ignored){}
            if(result.size()>50000)throw new IllegalArgumentException("Too many ASS cues");
        }result.sort(Comparator.comparingLong(c->c.startMs));return result;
    }
    private static long assTime(String text){String[] t=text.split(":");if(t.length!=3)throw new IllegalArgumentException();String[] s=t[2].split("\\.");if(s.length!=2||!s[1].matches("[0-9]{1,3}"))throw new IllegalArgumentException();int h=Integer.parseInt(t[0]),m=Integer.parseInt(t[1]),seconds=Integer.parseInt(s[0]);if(h<0||m<0||m>59||seconds<0||seconds>59)throw new IllegalArgumentException();return ((h*60L+m)*60+seconds)*1000+Integer.parseInt((s[1]+"00").substring(0,3));}
    private static String plainText(String text) {
        return text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }
    public static Cue activeCue(List<Cue> cues, long positionMs) {
        if (cues == null) return null;
        for (Cue cue : cues) { if (cue.startMs > positionMs) break; if (positionMs < cue.endMs) return cue; }
        return null;
    }
    public static String textAt(List<Cue> cues, long positionMs) {
        StringBuilder out = new StringBuilder();
        if (cues != null) for (Cue cue : cues) {
            if (cue.startMs > positionMs) break;
            if (positionMs < cue.endMs) { if (out.length() > 0) out.append('\n'); out.append(cue.text); }
        }
        return out.toString();
    }
    public static String toSrt(List<Cue> cues) { return toSrt(cues, 0); }
    public static String toSrt(List<Cue> cues, long offsetMs) {
        StringBuilder out = new StringBuilder(); int index = 1;
        for (Cue cue : cues) {
            long start = Math.max(0, cue.startMs + offsetMs), end = cue.endMs + offsetMs;
            if (end <= start) continue;
            out.append(index++).append('\n').append(format(start)).append(" --> ").append(format(end)).append('\n').append(cue.text).append("\n\n");
        }
        return out.toString();
    }
    private static String format(long time) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", time / 3600000L, time / 60000L % 60, time / 1000L % 60, time % 1000L);
    }
}
