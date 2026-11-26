package com.iraqbiss.glass;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeedData {
    static final class Item {
        String title="فيد جديد", sat="غير محدد", freq="", pol="", sr="", video="", category="", crypt="غير معروف", source="", link="";
        long time=0, fetched=0;
        String key() { return sat.toLowerCase(Locale.ROOT).replace(" ","")+"|"+freq+"|"+pol+"|"+sr; }
        String tp() { return freq+" "+pol+" "+sr; }
        boolean sport() {
            String t=(title+" "+category).toLowerCase(Locale.ROOT);
            return t.matches("(?s).*(football|sport|match|cup|soccer|league|tennis|basket|nba|fifa|uefa|vs|رياض|مباراة|كأس|دوري|بطولة).*");
        }
        boolean fourTwoTwo() { return video.contains("4:2:2"); }
        JSONObject json() {
            JSONObject o=new JSONObject();
            try {o.put("title",title);o.put("sat",sat);o.put("freq",freq);o.put("pol",pol);o.put("sr",sr);
                o.put("video",video);o.put("category",category);o.put("crypt",crypt);o.put("source",source);
                o.put("link",link);o.put("time",time);o.put("fetched",fetched);} catch(Exception ignored){}
            return o;
        }
        static Item from(JSONObject o) {
            Item i=new Item(); i.title=o.optString("title",i.title);i.sat=o.optString("sat",i.sat);
            i.freq=o.optString("freq");i.pol=o.optString("pol");i.sr=o.optString("sr");
            i.video=o.optString("video");i.category=o.optString("category");
            i.crypt=o.optString("crypt",i.crypt);i.source=o.optString("source");
            i.link=o.optString("link");i.time=o.optLong("time");i.fetched=o.optLong("fetched");return i;
        }
    }
    private static final Pattern TP=Pattern.compile("(?i)(?<!\\d)(\\d{4,5})\\s*[-/:]?\\s*([HVLR])\\s*[-/:]?\\s*(\\d{3,6})(?!\\d)");
    private static final Pattern TP_LABEL=Pattern.compile("(?i)Frequency\\s*:\\s*(\\d{4,5})\\s*[-,]*\\s*Pol\\s*:\\s*([HVLR])\\s*[-,]*\\s*SR\\s*:\\s*(\\d{3,6})");
    private static final Pattern ORBIT=Pattern.compile("(?i)(\\d{1,3}(?:[.,]\\d+)?)\\s*[°º]?\\s*([EW])\\b");
    static final String[][] BUILTIN={
      {"Live-Feed", "https://live-feed.net/", "html"},
      {"Satelliweb","https://www.satelliweb.com/live-feeds-en.html","html"},
      {"Feed Hunter","https://t.me/s/feed_hunter","telegram"},
      {"LiveFeeds1","https://t.me/s/livefeeds1","telegram"}
    };
    private FeedData(){}
    private static SharedPreferences db(Context c){return c.getSharedPreferences("iraqbiss_cache",Context.MODE_PRIVATE);}
    private static SharedPreferences settings(Context c){return c.getSharedPreferences("iraqbiss_settings",Context.MODE_PRIVATE);}
    static List<Item> cached(Context c) {
        List<Item> out=new ArrayList<>();
        try {JSONArray a=new JSONArray(db(c).getString("items","[]"));
            for (int n=0;n<a.length();n++) out.add(Item.from(a.getJSONObject(n)));
        } catch(Exception ignored){}
        return out;
    }
    static long lastSync(Context c){return db(c).getLong("updated",0);}
    static String statuses(Context c){return db(c).getString("status","لم يبدأ الاتصال بالمصادر");}
    static boolean favorite(Context c,Item i){return settings(c).getBoolean("fav_"+i.key(),false);}
    static void flipFavorite(Context c,Item i){settings(c).edit().putBoolean("fav_"+i.key(),!favorite(c,i)).apply();}
    static List<Item> refresh(Context c,boolean force) {
        long now=System.currentTimeMillis();
        LinkedHashMap<String,Item> items=new LinkedHashMap<>();
        for (Item i:cached(c)) { if(now-i.fetched<86400000L) items.put(i.key(),i); }
        StringBuilder status=new StringBuilder();
        List<String[]> sources=new ArrayList<>();
        for(String[] s:BUILTIN) if(settings(c).getBoolean("source_"+s[0],true)) sources.add(s);
        String custom=settings(c).getString("rss","");
        int customCount=0;
        for(String line:custom.split("\\r?\\n")) {
            line=line.trim();
            if(line.startsWith("https://") && customCount++<15) sources.add(new String[]{"RSS "+customCount,line,"rss"});
        }
        for(String[] src:sources){
            String source=src[0], url=src[1], kind=src[2];
            long last=db(c).getLong("check_"+source,0);
            if(!force && now-last<60000L) continue;
            try {
                String html=download(url);
                List<Item> parsed=parse(html,kind,source,url);
                if(parsed.isEmpty()) {status.append(source).append(": لا توجد ترددات قابلة للقراءة\n");}
                else {status.append(source).append(": ").append(parsed.size()).append(" فيد\n");}
                for(Item next:parsed) {
                    next.fetched=now;
                    Item prev=items.get(next.key());
                    if(prev!=null) {
                        if(next.title.equals("فيد جديد")) next.title=prev.title;
                        if(next.video.isEmpty()) next.video=prev.video;
                        if(next.crypt.equals("غير معروف")) next.crypt=prev.crypt;
                        if(!prev.source.contains(source)) next.source=prev.source+" + "+source;
                    }
                    items.put(next.key(),next);
                }
            } catch(Exception e) {
                status.append(source).append(": تعذّر الجلب (").append(e.getClass().getSimpleName()).append(")\n");
            }
            db(c).edit().putLong("check_"+source,now).apply();
        }
        List<Item> list=new ArrayList<>(items.values());
        list.sort((a,b)->Long.compare(b.fetched,a.fetched));
        if(list.size()>350) list=new ArrayList<>(list.subList(0,350));
        JSONArray json=new JSONArray();for(Item i:list)json.put(i.json());
        SharedPreferences.Editor editor=db(c).edit().putString("items",json.toString()).putLong("updated",now);
        if(status.length()>0) editor.putString("status",status.toString().trim());
        editor.apply();
        return list;
    }
    static List<Item> newItems(Context c,List<Item> list) {
        Set<String> seen=new LinkedHashSet<>();
        String old=db(c).getString("seen",""); if(!old.isEmpty())Collections.addAll(seen,old.split("\\n"));
        boolean initialized=db(c).getBoolean("seen_ready",false);
        List<Item> fresh=new ArrayList<>();
        for(Item i:list) if(!seen.contains(i.key())) {
            if(initialized && i.fetched>0 && System.currentTimeMillis()-i.fetched<300000L) fresh.add(i);
            seen.add(i.key());
        }
        if(seen.size()>1300) {
            List<String> s=new ArrayList<>(seen);
            seen=new LinkedHashSet<>(s.subList(s.size()-1100,s.size()));
        }
        db(c).edit().putString("seen",android.text.TextUtils.join("\n",seen)).putBoolean("seen_ready",true).apply();
        return fresh;
    }
    private static String download(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setInstanceFollowRedirects(true); connection.setConnectTimeout(9000);connection.setReadTimeout(9000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; IraqBISS Glass/0.2)");
        connection.setRequestProperty("Accept","text/html,application/rss+xml,application/xml;q=0.8,*/*;q=0.5");
        try {
            if(connection.getResponseCode()!=200) throw new Exception("HTTP "+connection.getResponseCode());
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n,total=0;
                while((n=input.read(buf))!=-1){total+=n;if(total>2097152)throw new Exception("source too large");output.write(buf,0,n);}
                return output.toString("UTF-8");
            }
        } finally {connection.disconnect();}
    }
    private static List<Item> parse(String html,String kind,String name,String url) {
        List<Item> out=new ArrayList<>();
        if(kind.equals("telegram")) {
            Document d=Jsoup.parse(html,url);
            for(Element message:d.select(".tgme_widget_message")){
                Element body=message.selectFirst(".tgme_widget_message_text");
                if(body==null)continue;
                String text=body.wholeText();
                String link=url;Element anchor=message.selectFirst(".tgme_widget_message_date");
                if(anchor!=null)link=anchor.attr("abs:href");
                addMatches(out,text,name,link,120);
                if(out.size()>=200)break;
            }
        } else if(kind.equals("rss")) {
            Document d=Jsoup.parse(html,"",Parser.xmlParser());
            for(Element e:d.select("item, entry")){
                String text=e.select("title").text()+" "+e.select("description, summary, content").text();
                String link=e.select("link").text(); if(link.isEmpty())link=e.select("link[href]").attr("href");
                addMatches(out,text,name,link.isEmpty()?url:link,140);
                if(out.size()>=200)break;
            }
        } else {
            String text=Jsoup.parse(html).body().text();
            addMatches(out,text,name,url,250);
        }
        return out;
    }
    private static void addMatches(List<Item> out,String text,String name,String link,int limit) {
        Matcher m=TP.matcher(text);
        while(m.find() && out.size()<limit) create(out,m,text,name,link);
        m=TP_LABEL.matcher(text);
        while(m.find() && out.size()<limit) create(out,m,text,name,link);
    }
    private static void create(List<Item> out,Matcher m,String text,String name,String link) {
        int f=Integer.parseInt(m.group(1)),sr=Integer.parseInt(m.group(3));
        if(f<3000 || f>45000 || sr<100 || sr>70000)return;
        String before=text.substring(Math.max(0,m.start()-145),m.start()).trim();
        String after=text.substring(m.end(),Math.min(text.length(),m.end()+70)).trim();
        Item i=new Item();i.freq=m.group(1);i.pol=m.group(2).toUpperCase(Locale.ROOT);i.sr=m.group(3);
        Matcher orbit=ORBIT.matcher(before+" "+after);
        String last="";
        while(orbit.find())last=orbit.group(1)+orbit.group(2).toUpperCase(Locale.ROOT);
        if(!last.isEmpty())i.sat=last;
        else if(before.toLowerCase(Locale.ROOT).contains("eutelsat"))i.sat="Eutelsat";
        else if(before.toLowerCase(Locale.ROOT).contains("badr"))i.sat="Badr";
        else if(before.toLowerCase(Locale.ROOT).contains("astra"))i.sat="Astra";
        String candidate=before.replaceAll("(?s).*?(?:Reported by\\s+\\S+\\s+on\\s+|Feed\\s*:)", "").trim();
        if(candidate.length()>92)candidate=candidate.substring(candidate.length()-92);
        if(candidate.length()>3 && candidate.length()<93)i.title=candidate;
        i.video=(before+" "+after).contains("4:2:2")?"4:2:2":((before+" "+after).contains("4:2:0")?"4:2:0":"");
        String info=(before+" "+after).toLowerCase(Locale.ROOT);
        if(info.matches("(?s).*(?:\\bfta\\b|\\bclear\\b|clair).*"))i.crypt="FTA";
        else if(info.contains("crypt")||info.contains("biss"))i.crypt="مشفر";
        i.category=i.sport()?"رياضة":"عام";
        i.source=name;i.link=link;
        out.add(i);
    }
}
