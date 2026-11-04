package com.smarthub.plus;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;

/** Local personal-playlist library. No channel URLs or credentials are logged. */
public final class PlaylistRepository {
    public static final int MAX_CHANNELS=10000, MAX_PROGRAMMES=30000, MAX_BYTES=24*1024*1024;
    public static final class Channel {
        public String id="",name="",originalName="",uri="",logo="",group="",tvgId="",catchup="";
        public boolean favorite,hidden;
        public int position,originalPosition;
        public Channel copy(){Channel c=new Channel();c.id=id;c.name=name;c.originalName=originalName;c.uri=uri;c.logo=logo;c.group=group;c.tvgId=tvgId;c.catchup=catchup;c.favorite=favorite;c.hidden=hidden;c.position=position;c.originalPosition=originalPosition;return c;}
    }
    public static final class Programme {
        public String channelId="",title="",description="",category="",image="";
        public long startMs,endMs;
        public boolean repeat;
    }
    private final File directory;
    private final List<Channel> channels=new ArrayList<>();
    private final List<Programme> programmes=new ArrayList<>();
    private final Map<String,List<Programme>> epg=new HashMap<>();
    public PlaylistRepository(Context context) { directory=new File(context.getFilesDir(),"personal_tv");directory.mkdirs(); try {loadChannels(readFile(new File(directory,"channels.json")));}catch(Exception ignored){} try {loadProgrammes(new JSONArray(readFile(new File(directory,"epg.json"))));}catch(Exception ignored){} }

    public synchronized List<Channel> all(){List<Channel> out=new ArrayList<>();for(Channel c:channels)out.add(c.copy());out.sort(Comparator.comparingInt(c->c.position));return out;}
    public synchronized Channel find(String id){for(Channel c:channels)if(c.id.equals(id))return c.copy();return null;}
    public synchronized int epgCount(){return programmes.size();}
    public synchronized int merge(List<Channel> imported)throws IOException {int count=0;Set<String> known=new HashSet<>();for(Channel c:channels)known.add(c.id);for(Channel item:imported){if(channels.size()>=MAX_CHANNELS)break;if(!known.add(item.id))continue;Channel c=item.copy();c.position=channels.size();c.originalPosition=c.position;if(c.originalName.isEmpty())c.originalName=c.name;channels.add(c);count++;}saveChannels();return count;}
    public synchronized void update(Channel value)throws IOException{for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(value.id)){channels.set(i,value.copy());break;}saveChannels();}
    public synchronized void move(String id,int step)throws IOException{List<Channel> sorted=all();int index=-1;for(int i=0;i<sorted.size();i++)if(sorted.get(i).id.equals(id))index=i;int target=index+step;if(index<0||target<0||target>=sorted.size())return;Collections.swap(sorted,index,target);channels.clear();for(int i=0;i<sorted.size();i++){sorted.get(i).position=i;channels.add(sorted.get(i));}saveChannels();}
    public synchronized void restoreOriginal()throws IOException{for(Channel c:channels){c.name=c.originalName;c.position=c.originalPosition;c.hidden=false;}saveChannels();}
    public synchronized void saveEpg(List<Programme> imported)throws IOException{programmes.clear();programmes.addAll(imported.subList(0,Math.min(imported.size(),MAX_PROGRAMMES)));indexEpg();try{writeFile(new File(directory,"epg.json"),programmeJson(programmes).toString());}catch(Exception e){throw new IOException("Could not save EPG",e);}}
    public synchronized List<Programme> schedule(Channel channel){List<Programme> found=epg.get(channel.tvgId.toLowerCase(Locale.ROOT));if(found==null)found=epg.get(channel.name.toLowerCase(Locale.ROOT));return found==null?new ArrayList<>():new ArrayList<>(found);}
    public synchronized Programme now(Channel channel,long now){for(Programme p:schedule(channel))if(p.startMs<=now&&p.endMs>now)return p;return null;}
    public synchronized List<Channel> search(String query,boolean favorites,boolean hidden){List<Channel> out=new ArrayList<>();for(Channel c:all()){if(!hidden&&c.hidden||favorites&&!c.favorite)continue;boolean match=matches(c.name+" "+c.group+" "+c.tvgId,query);if(!match)for(Programme p:schedule(c)){String times=DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(p.startMs));if(matches(p.title+" "+p.category+" "+times+(p.repeat?" repeat replay إعادة":""),query)){match=true;break;}}if(!match&&!c.catchup.isEmpty())match=matches("catchup replay إعادة",query);if(match)out.add(c);}return out;}
    public synchronized String exportJson()throws IOException{try{return new JSONObject().put("format","SmartHubPlusLibrary").put("version",1).put("channels",channelJson(channels)).put("programmes",programmeJson(programmes)).toString(2);}catch(Exception e){throw new IOException("Could not export library",e);}}
    public synchronized int importJson(String json)throws IOException{try{bounded(json);JSONObject root=new JSONObject(json);if(!"SmartHubPlusLibrary".equals(root.optString("format"))||root.optInt("version")!=1)throw new IOException("Unsupported library format");List<Channel> incoming=decodeChannels(root.optJSONArray("channels"));incoming.sort(Comparator.comparingInt(c->c.position));List<Channel> existing=all(),restored=new ArrayList<>();Set<String> oldIds=new HashSet<>(),seenIds=new HashSet<>();for(Channel c:existing)oldIds.add(c.id);Set<String> union=new HashSet<>(oldIds);for(Channel c:incoming)union.add(c.id);if(union.size()>MAX_CHANNELS)throw new IOException("Library capacity exceeded; existing channels retained");int added=0;for(Channel c:incoming){if(!oldIds.contains(c.id))added++;c.position=restored.size();restored.add(c);seenIds.add(c.id);}for(Channel c:existing)if(!seenIds.contains(c.id)){c.position=restored.size();c.originalPosition=restored.size();restored.add(c);}channels.clear();channels.addAll(restored);saveChannels();JSONArray rows=root.optJSONArray("programmes");if(rows!=null){List<Programme> merged=new ArrayList<>(programmes);Set<String> seen=new HashSet<>();for(Programme p:merged)seen.add(p.channelId+"|"+p.startMs);for(Programme p:decodeProgrammes(rows))if(seen.add(p.channelId+"|"+p.startMs)&&merged.size()<MAX_PROGRAMMES)merged.add(p);saveEpg(merged);}return added;}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid library JSON",e);}}

    public static List<Channel> parseM3u(String data,String source)throws IOException {
        bounded(data);String input=data.replace("\uFEFF","");List<Channel> out=new ArrayList<>();
        if(input.contains("#EXT-X-")){if(source==null||!(source.startsWith("http://")||source.startsWith("https://"))||!validStream(source))throw new IOException("A local HLS manifest needs its original stream URL");Channel c=new Channel();c.uri=source;c.id=id(source);c.name="HLS stream";c.originalName=c.name;out.add(c);return out;}
        if(!input.trim().startsWith("#EXTM3U")&&!input.contains("#EXTINF"))throw new IOException("Not an M3U playlist");
        Map<String,String> attrs=new HashMap<>();String title="",extGroup="";Set<String> seen=new HashSet<>();
        for(String raw:input.split("\\r?\\n")){String line=raw.trim();if(line.isEmpty())continue;if(line.startsWith("#EXTINF:")){int comma=infoComma(line);title=comma<0?"":line.substring(comma+1).trim();attrs=attributes(comma<0?line:line.substring(0,comma));continue;}if(line.startsWith("#EXTGRP:")){extGroup=line.substring(8).trim();continue;}if(line.startsWith("#"))continue;
            String uri=line;if(!validStream(uri)&&source!=null&&(source.startsWith("http://")||source.startsWith("https://")))try{uri=URI.create(source).resolve(line).toString();}catch(Exception ignored){}
            if(validStream(uri)&&seen.add(uri)){Channel c=new Channel();c.uri=uri;c.id=id(uri);c.name=title.isEmpty()?attrs.getOrDefault("tvg-name","Channel "+(out.size()+1)):title;c.originalName=c.name;c.tvgId=attrs.getOrDefault("tvg-id","");c.logo=webUrl(attrs.getOrDefault("tvg-logo",""));c.group=attrs.getOrDefault("group-title",extGroup);c.catchup=attrs.getOrDefault("catchup-source","");if(c.catchup.isEmpty()&&!attrs.getOrDefault("catchup","").isEmpty())c.catchup=attrs.get("catchup");c.position=out.size();c.originalPosition=c.position;out.add(c);if(out.size()>=MAX_CHANNELS)break;}title="";attrs=new HashMap<>();extGroup="";
        }if(out.isEmpty())throw new IOException("No valid stream entries found");return out;
    }
    private static int infoComma(String value){char quote=0;for(int i=0;i<value.length();i++){char c=value.charAt(i);if((c=='\"'||c=='\'')&&(quote==0||quote==c))quote=quote==0?c:0;else if(c==','&&quote==0)return i;}return -1;}
    private static Map<String,String> attributes(String info){Map<String,String> out=new HashMap<>();Matcher m=Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))").matcher(info);while(m.find())out.put(m.group(1).toLowerCase(Locale.ROOT),m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4));return out;}
    public static boolean validStream(String value){if(value==null||value.contains("\n")||value.contains("\r"))return false;try{URI uri=new URI(value.trim());String s=uri.getScheme();if(s==null||uri.getHost()==null||uri.getHost().isEmpty())return false;return s.equalsIgnoreCase("http")||s.equalsIgnoreCase("https")||s.equalsIgnoreCase("rtsp")||s.equalsIgnoreCase("content")||s.equalsIgnoreCase("udp")||s.equalsIgnoreCase("rtp");}catch(Exception e){return false;}}
    private static String webUrl(String value){return value.startsWith("https://")||value.startsWith("http://")?value:"";}

    public static List<Programme> parseXmlTv(String xml)throws IOException {
        bounded(xml);String upper=xml.toUpperCase(Locale.ROOT);if(upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new IOException("DTD and external entities are not allowed");
        final List<Programme> out=new ArrayList<>();
        try{SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(false);factory.setValidating(false);try{factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);}catch(Exception ignored){}
            org.xml.sax.XMLReader reader=factory.newSAXParser().getXMLReader();reader.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
            reader.setContentHandler(new DefaultHandler(){Programme current;String tag="";StringBuilder chars=new StringBuilder();int depth;
                public void startElement(String uri,String local,String q,Attributes a)throws SAXException{if(++depth>64)throw new SAXException("XML nesting too deep");if(depth==1&&!q.equals("tv"))throw new SAXException("Expected XMLTV root");tag=q;chars.setLength(0);if(q.equals("programme")){if(out.size()>=MAX_PROGRAMMES)throw new SAXException("Too many programmes");current=new Programme();current.channelId=safe(a.getValue("channel"));current.startMs=parseXmlTime(a.getValue("start"));current.endMs=parseXmlTime(a.getValue("stop"));}else if(q.equals("icon")&&current!=null&&current.image.isEmpty())current.image=epgImage(a.getValue("src"));else if(q.equals("previously-shown")&&current!=null)current.repeat=true;}
                public void characters(char[] c,int start,int length)throws SAXException{if(chars.length()+length>100000)throw new SAXException("XML value too long");chars.append(c,start,length);}
                public void endElement(String uri,String local,String q){if(current!=null){String value=chars.toString().trim();if(q.equals("title")&&current.title.isEmpty())current.title=value;else if(q.equals("desc")&&current.description.isEmpty())current.description=value;else if(q.equals("category"))current.category+=(current.category.isEmpty()?"":", ")+value;else if(q.equals("programme")){if(!current.channelId.isEmpty()&&!current.title.isEmpty()&&current.startMs>0&&current.endMs>current.startMs)out.add(current);current=null;}}chars.setLength(0);depth--;}
            });reader.parse(new InputSource(new StringReader(xml)));return out;
        }catch(Exception e){throw new IOException("Invalid or unsafe XMLTV: "+(e.getMessage()==null?"parse failed":e.getMessage()),e);}
    }
    static String epgImage(String value){if(value==null||value.length()>4096)return "";try{URI uri=new URI(value);String scheme=uri.getScheme();return uri.getUserInfo()==null&&("https".equals(scheme)||"http".equals(scheme)||"content".equals(scheme)||"android.resource".equals(scheme))?value:"";}catch(Exception e){return "";}}
    public static long parseXmlTime(String value){if(value==null)return 0;Matcher m=Pattern.compile("^(\\d{12}|\\d{14})(?:\\s*([+-]\\d{4}|Z))?$").matcher(value.trim());if(!m.matches())return 0;try{String date=m.group(1),offset=m.group(2);DateTimeFormatter f=DateTimeFormatter.ofPattern(date.length()==14?"uuuuMMddHHmmss":"uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);return LocalDateTime.parse(date,f).toInstant(offset==null||offset.equals("Z")?ZoneOffset.UTC:ZoneOffset.of(offset)).toEpochMilli();}catch(Exception invalid){return 0;}}
    public static boolean matches(String value,String query){String q=normalize(query);if(q.isEmpty())return true;String text=normalize(value);if(q.matches("[0-9]+"))return t9(text).contains(q);return text.contains(q);}
    public static String t9(String input){String text=normalize(input),out="";String[] latin={"abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"},arabic={"ابتث","جحخ","دذرز","سشصض","طظعغ","فقك","لمن","هوي"};StringBuilder digits=new StringBuilder();for(char c:text.toCharArray()){if(c>='0'&&c<='9'){digits.append(c);continue;}for(int i=0;i<8;i++)if(latin[i].indexOf(c)>=0||arabic[i].indexOf(c)>=0){digits.append(i+2);break;}}return digits.toString();}
    public static String normalize(String value){String s=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه').replace("ـ","");StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>='٠'&&c<='٩'?(char)('0'+c-'٠'):c>='۰'&&c<='۹'?(char)('0'+c-'۰'):c);return b.toString().trim();}
    private static String id(String uri){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public static String readBounded(InputStream in)throws IOException{try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("File exceeds 24 MB limit");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static void bounded(String value)throws IOException{if(value==null||value.length()>MAX_BYTES)throw new IOException("Import exceeds size limit");}
    private static String safe(String s){return s==null?"":s;}
    private void loadChannels(String data)throws Exception{channels.clear();channels.addAll(decodeChannels(new JSONArray(data)));}
    private void loadProgrammes(JSONArray data){programmes.clear();programmes.addAll(decodeProgrammes(data));indexEpg();}
    private void indexEpg(){epg.clear();for(Programme p:programmes)epg.computeIfAbsent(p.channelId.toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(p);for(List<Programme> list:epg.values())list.sort(Comparator.comparingLong(p->p.startMs));}
    private void saveChannels()throws IOException{try{writeFile(new File(directory,"channels.json"),channelJson(channels).toString());}catch(Exception e){throw new IOException("Could not save channels",e);}}
    private static JSONArray channelJson(List<Channel> channels)throws Exception{JSONArray rows=new JSONArray();for(Channel c:channels)rows.put(new JSONObject().put("id",c.id).put("name",c.name).put("originalName",c.originalName).put("uri",c.uri).put("logo",c.logo).put("group",c.group).put("tvgId",c.tvgId).put("catchup",c.catchup).put("favorite",c.favorite).put("hidden",c.hidden).put("position",c.position).put("originalPosition",c.originalPosition));return rows;}
    private static List<Channel> decodeChannels(JSONArray rows){List<Channel> out=new ArrayList<>();if(rows==null)return out;Set<String> seen=new HashSet<>();for(int i=0;i<Math.min(rows.length(),MAX_CHANNELS);i++){JSONObject o=rows.optJSONObject(i);if(o==null||!validStream(o.optString("uri")))continue;Channel c=new Channel();c.uri=o.optString("uri");c.id=id(c.uri);if(!seen.add(c.id))continue;c.name=o.optString("name","Channel "+(i+1));c.originalName=o.optString("originalName",c.name);c.logo=webUrl(o.optString("logo"));c.group=o.optString("group");c.tvgId=o.optString("tvgId");c.catchup=o.optString("catchup");c.favorite=o.optBoolean("favorite");c.hidden=o.optBoolean("hidden");c.position=o.optInt("position",i);c.originalPosition=o.optInt("originalPosition",i);out.add(c);}return out;}
    private static JSONArray programmeJson(List<Programme> list)throws Exception{JSONArray rows=new JSONArray();for(Programme p:list)rows.put(new JSONObject().put("channel",p.channelId).put("title",p.title).put("description",p.description).put("image",p.image).put("category",p.category).put("start",p.startMs).put("end",p.endMs).put("repeat",p.repeat));return rows;}
    private static List<Programme> decodeProgrammes(JSONArray rows){List<Programme> out=new ArrayList<>();for(int i=0;i<Math.min(rows.length(),MAX_PROGRAMMES);i++){JSONObject o=rows.optJSONObject(i);if(o==null)continue;Programme p=new Programme();p.channelId=o.optString("channel");p.title=o.optString("title");p.description=o.optString("description");p.image=epgImage(o.optString("image"));p.category=o.optString("category");p.startMs=o.optLong("start");p.endMs=o.optLong("end");p.repeat=o.optBoolean("repeat");if(!p.channelId.isEmpty()&&p.endMs>p.startMs)out.add(p);}return out;}
    private static String readFile(File file)throws IOException{if(!file.isFile())return "[]";try(InputStream in=new FileInputStream(file)){return readBounded(in);}}
    private static void writeFile(File file,String value)throws IOException{File tmp=new File(file.getParentFile(),file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!tmp.renameTo(file)){try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));}tmp.delete();}}
}
