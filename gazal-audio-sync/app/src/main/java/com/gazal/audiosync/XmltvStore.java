package com.gazal.audiosync;

import android.content.Context;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/** Offline XMLTV parser and source name/tvg-id now-next lookup. */
final class XmltvStore {
    private static final String FILE_NAME = "gazal_epg_cache.json";
    static final class Entry {
        final String id,name,title,description;
        final long start,end;
        Entry(String id,String name,String title,String description,long start,long end){
            this.id=id;this.name=name;this.title=title;this.description=description;
            this.start=start;this.end=end;
        }
    }
    private static ArrayList<Entry> entries;
    static synchronized int importStream(Context ctx,InputStream raw) throws Exception {
        if(raw==null)throw new IllegalArgumentException("Empty EPG file");
        java.io.BufferedInputStream probe=new java.io.BufferedInputStream(raw);
        probe.mark(4);
        int b1=probe.read(),b2=probe.read();probe.reset();
        InputStream data=b1==0x1f&&b2==0x8b?new GZIPInputStream(probe):probe;
        XmlPullParser parser=Xml.newPullParser();
        parser.setInput(data,"UTF-8");
        ArrayList<Entry> fresh=new ArrayList<>();
        HashMap<String,String> channelNames=new HashMap<>();
        String channelId="",programId="",title="",description="";
        long from=0,to=0;
        int type;
        while((type=parser.next())!=XmlPullParser.END_DOCUMENT && fresh.size()<18000){
            if(type==XmlPullParser.START_TAG){
                String tag=parser.getName();
                if("channel".equals(tag)){
                    channelId=parser.getAttributeValue(null,"id");
                }else if("programme".equals(tag)){
                    programId=parser.getAttributeValue(null,"channel");
                    from=parseTime(parser.getAttributeValue(null,"start"));
                    to=parseTime(parser.getAttributeValue(null,"stop"));
                    title="";description="";
                }else if("display-name".equals(tag)&&channelId!=null&&!channelId.isEmpty()){
                    channelNames.put(channelId,parser.nextText());
                }else if("title".equals(tag)&&programId!=null&&!programId.isEmpty()){
                    title=parser.nextText();
                }else if("desc".equals(tag)&&programId!=null&&!programId.isEmpty()){
                    description=parser.nextText();
                }
            } else if(type==XmlPullParser.END_TAG) {
                String tag=parser.getName();
                if("channel".equals(tag))channelId="";
                if("programme".equals(tag)){
                    if(programId!=null&&!programId.isEmpty()&&from>0&&to>from){
                        String name=channelNames.containsKey(programId)?channelNames.get(programId):"";
                        fresh.add(new Entry(programId,name,title,description,from,to));
                    }
                    programId="";title="";description="";
                }
            }
        }
        JSONArray json=new JSONArray();
        for(Entry e:fresh){
            JSONObject item=new JSONObject();
            item.put("id",e.id);item.put("name",e.name);item.put("title",e.title);
            item.put("desc",e.description.length()>350?e.description.substring(0,350):e.description);
            item.put("start",e.start);item.put("end",e.end);
            json.put(item);
        }
        try(BufferedWriter out=new BufferedWriter(new OutputStreamWriter(
            ctx.openFileOutput(FILE_NAME,Context.MODE_PRIVATE),"UTF-8"))){
            out.write(json.toString());
        }
        entries=fresh;
        return fresh.size();
    }
    private static long parseTime(String input){
        if(input==null||input.length()<14)return 0;
        try{
            SimpleDateFormat df;
            if(input.length()>14){
                df=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
                Date date=df.parse(input.substring(0,14)+" "+input.substring(15).trim());
                return date==null?0:date.getTime();
            }
            df=new SimpleDateFormat("yyyyMMddHHmmss",Locale.US);
            Date d=df.parse(input.substring(0,14));return d==null?0:d.getTime();
        }catch(Exception ignored){return 0;}
    }
    private static String norm(String s) {
        if(s==null)return "";
        return s.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\p{L}]+","")
            .replace("hd","").replace("fhd","");
    }
    private static synchronized void ensure(Context ctx){
        if(entries!=null)return;
        entries=new ArrayList<>();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(
            ctx.openFileInput(FILE_NAME),"UTF-8"))){
            StringBuilder b=new StringBuilder();
            String line;
            while((line=in.readLine())!=null && b.length()<25000000)b.append(line);
            JSONArray ar=new JSONArray(b.toString());
            for(int i=0;i<ar.length();i++){
                JSONObject o=ar.optJSONObject(i);if(o==null)continue;
                entries.add(new Entry(o.optString("id"),o.optString("name"),
                    o.optString("title"),o.optString("desc"),
                    o.optLong("start"),o.optLong("end")));
            }
        }catch(Exception ignored){}
    }
    static synchronized Entry[] nowNext(Context ctx,PlaylistStore.Source src){
        ensure(ctx);
        if(src==null)return new Entry[]{null,null};
        long now=System.currentTimeMillis();
        String id=norm(src.tvgId),name=norm(src.name);
        Entry current=null,next=null;
        for(Entry e:entries){
            boolean matching=!id.isEmpty()&&norm(e.id).equals(id);
            if(!matching && !name.isEmpty())matching=norm(e.name).equals(name)||norm(e.id).equals(name);
            if(!matching)continue;
            if(e.start<=now&&e.end>now){
                if(current==null||e.start>current.start)current=e;
            }else if(e.start>now&&(next==null||e.start<next.start))next=e;
        }
        return new Entry[]{current,next};
    }
    static String display(Entry e){
        if(e==null)return "Programme unavailable";
        SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.getDefault());
        String result=fmt.format(new Date(e.start))+"–"+fmt.format(new Date(e.end))+" "+e.title;
        return e.description.isEmpty()?result:result+"\n"+e.description;
    }
}
