package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Official OpenSubtitles REST API. Download-file requests never carry API credentials. */
final class OpenSubtitlesClient {
    static final String CONSUMER_URL="https://www.opensubtitles.com/en/consumers";
    private static final String USER_AGENT="SmartHubPlus v1.0";
    private String base="https://api.opensubtitles.com/api/v1",token="";
    private final String apiKey;
    private final boolean loopbackTest;
    private volatile HttpURLConnection active;private volatile okhttp3.Call nativeCall;
    private volatile boolean cancelled;private SubsourceClient subsource;
    OpenSubtitlesClient(String key)throws IOException{this.apiKey=cleanKey(key);loopbackTest=false;}
    private OpenSubtitlesClient(ProviderSecrets keys)throws IOException{apiKey=keys.opensubtitles;loopbackTest=false;if(!keys.subsource.isEmpty())subsource=new SubsourceClient(keys.subsource);if(apiKey.isEmpty()&&subsource==null)throw new IOException("Enter at least one subtitle provider key in settings");}
    static OpenSubtitlesClient fromPreferences(android.content.Context context)throws IOException{try{return new OpenSubtitlesClient(ProviderSecrets.read(context));}catch(Exception e){throw new IOException("Configure the built-in subtitle provider keys first",e);}}
    private void restoreSession(String value,String host)throws IOException{
        if(value.contains("\r")||value.contains("\n")||(!host.equals("api.opensubtitles.com")&&!host.equals("vip-api.opensubtitles.com")))throw new IOException("Invalid saved OpenSubtitles session. Sign in again.");
        token=value;base="https://"+host+"/api/v1";
    }
    String sessionToken(){return token;}
    String sessionHost(){return URI.create(base).getHost();}
    /** Package-private loopback-only transport for deterministic JVM wire tests. */
    OpenSubtitlesClient(String key,URI testBase)throws IOException{
        apiKey=cleanKey(key);if(!"http".equals(testBase.getScheme())||!"127.0.0.1".equals(testBase.getHost())||testBase.getUserInfo()!=null||testBase.getQuery()!=null||testBase.getFragment()!=null)throw new IOException("Tests require loopback HTTP");base=testBase.toString();loopbackTest=true;
    }
    private static String cleanKey(String value)throws IOException{if(value==null||value.trim().isEmpty())throw new IOException("Set your OpenSubtitles application API key first");if(value.contains("\r")||value.contains("\n"))throw new IOException("Invalid API key");return value.trim();}
    JSONObject login(String username,String password)throws Exception{
        JSONObject result=api("login",new JSONObject().put("username",username).put("password",password));
        String received=result.optString("token","");if(received.isEmpty())throw new IOException("Login returned no token");
        String host=result.optString("base_url","api.opensubtitles.com");if(!host.equals("api.opensubtitles.com")&&!host.equals("vip-api.opensubtitles.com"))throw new IOException("Unsupported API host returned by login");
        token=received;if(!loopbackTest)base="https://"+host+"/api/v1";return result;
    }
    static String searchQuery(String title,String imdb,String language,int season,int episode)throws Exception{
        TreeMap<String,String> values=new TreeMap<>();String id=imdb==null?"":imdb.trim().toLowerCase(Locale.ROOT);
        if(!id.isEmpty()){id=id.replaceFirst("^tt","").replaceFirst("^0+(?!$)","");if(!id.matches("[0-9]{1,12}"))throw new IOException("IMDb ID must look like tt0133093 or 133093");values.put(season>0||episode>0?"parent_imdb_id":"imdb_id",id);}
        else{String query=title==null?"":title.trim().toLowerCase(Locale.ROOT);if(query.isEmpty())throw new IOException("Enter a film title or IMDb ID");values.put("query",query);}
        String lang=language==null?"":language.trim().toLowerCase(Locale.ROOT);if(!lang.matches("[a-z]{2,3}(-[a-z]{2})?"))throw new IOException("Choose a subtitle language");values.put("languages",lang);
        if(season>0)values.put("season_number",Integer.toString(season));if(episode>0)values.put("episode_number",Integer.toString(episode));
        StringBuilder query=new StringBuilder();for(Map.Entry<String,String> entry:values.entrySet()){if(query.length()>0)query.append('&');query.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(),"UTF-8"));}return query.toString();
    }
    List<SubtitleFile> search(String title,String imdb,String language,int season,int episode)throws Exception{if(subsource==null)return parse(api("subtitles?"+searchQuery(title,imdb,language,season,episode),null));List<SubtitleFile> result=new ArrayList<>();Exception failure=null;int success=0;if(!apiKey.isEmpty())try{result.addAll(parse(api("subtitles?"+searchQuery(title,imdb,language,season,episode),null)));success++;}catch(Exception e){failure=e;}check();try{result.addAll(subsource.search(title,imdb,language,season,episode));success++;}catch(Exception e){failure=e;}if(success==0)throw failure;return result;}
    static List<SubtitleFile> parse(JSONObject response)throws Exception{
        JSONArray data=response.optJSONArray("data");if(data==null)throw new IOException("Search returned no subtitle data array");List<SubtitleFile> output=new ArrayList<>();
        for(int i=0;i<data.length()&&output.size()<100;i++){JSONObject item=data.optJSONObject(i);if(item==null)continue;JSONObject attributes=item.optJSONObject("attributes");if(attributes==null)continue;JSONArray files=attributes.optJSONArray("files");if(files==null)continue;JSONObject feature=attributes.optJSONObject("feature_details");
            for(int j=0;j<files.length()&&output.size()<100;j++){JSONObject file=files.optJSONObject(j);if(file==null||file.optLong("file_id",0)<=0)continue;SubtitleFile parsed=new SubtitleFile(file.optLong("file_id"),file.optString("file_name",""),attributes.optString("release",""),attributes.optString("language",""),attributes.optDouble("fps",0),feature==null?"":feature.optString("title",""),feature==null?0:feature.optInt("season_number",0),feature==null?0:feature.optInt("episode_number",0),attributes.optBoolean("ai_translated",false),attributes.optBoolean("machine_translated",false));parsed.imdbId=feature==null?"":feature.optString("parent_imdb_id",feature.optString("imdb_id",""));parsed.downloadCount=attributes.optLong("download_count",0);output.add(parsed);}}
        return output;
    }
    Download download(long fileId)throws Exception{
        if(fileId>=SubsourceClient.FILE_BASE){if(subsource==null)throw new IOException("Subsource is not configured");return new Download("subsource:"+(fileId-SubsourceClient.FILE_BASE),"Subsource.zip",-1,"","");}if(fileId<=0)throw new IOException("Select a subtitle file first");JSONObject info=api("download",new JSONObject().put("file_id",fileId).put("sub_format","srt"));
        String url=info.optString("link","");validateFileUri(URI.create(url));return new Download(url,info.optString("file_name","subtitles.srt"),info.optInt("remaining",-1),info.optString("reset_time_utc",""),info.optString("message",""));
    }
    String fetch(Download download)throws Exception{
        if(download.url.matches("subsource:[0-9]+")){if(subsource==null)throw new IOException("Subsource is not configured");return subsource.download(Long.parseLong(download.url.substring(10)));}
        URI uri=URI.create(download.url);if(!loopbackTest){for(int i=0;i<4;i++){validateFileUri(uri);DirectHttp.Reply r=DirectHttp.request(uri.toString(),Collections.emptyMap(),null,5*1024*1024,c->nativeCall=c);if(r.code>=300&&r.code<400&&r.location!=null){uri=uri.resolve(r.location);continue;}if(r.code<200||r.code>=300)throw new IOException("Subtitle file HTTP "+r.code);return decodeSubtitle(r.bytes);}throw new IOException("Too many subtitle redirects");}
        for(int redirects=0;redirects<4;redirects++){
            validateFileUri(uri);HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();active=connection;
            try{check();configure(connection);int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing subtitle redirect location");uri=uri.resolve(location);continue;}if(code<200||code>=300)throw new IOException("Subtitle file HTTP "+code);return read(connection,5*1024*1024);}finally{connection.disconnect();if(active==connection)active=null;}
        }throw new IOException("Too many subtitle file redirects");
    }
    private void validateFileUri(URI uri)throws IOException{
        if(uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getHost()==null)throw new IOException("Invalid subtitle file URL");
        if(loopbackTest&&"http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost()))return;
        String host=uri.getHost().toLowerCase(Locale.ROOT);boolean trusted=host.equals("opensubtitles.com")||host.endsWith(".opensubtitles.com")||host.equals("osdb.link")||host.endsWith(".osdb.link");
        if(!"https".equalsIgnoreCase(uri.getScheme())||!trusted)throw new IOException("Subtitle download must use an official HTTPS host");
    }
    private JSONObject api(String path,JSONObject body)throws Exception{
        check();if(apiKey.isEmpty())throw new IOException("OpenSubtitles is not configured");URI uri=URI.create(base+"/"+path);if(!loopbackTest){Map<String,String> h=new HashMap<>();h.put("Api-Key",apiKey);h.put("Accept","application/json");if(!token.isEmpty())h.put("Authorization","Bearer "+token);DirectHttp.Reply r=DirectHttp.request(uri.toString(),h,body==null?null:body.toString(),2*1024*1024,c->nativeCall=c);if(r.code<200||r.code>=300)throw new IOException("OpenSubtitles HTTP "+r.code+(r.code==429?" · request limit":""));return new JSONObject(new String(r.bytes,StandardCharsets.UTF_8));}
        for(int redirects=0;redirects<4;redirects++){
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();active=connection;
            try{check();configure(connection);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Api-Key",apiKey);if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
                if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
                int code=connection.getResponseCode();
                if(body==null&&(code==301||code==302||code==307||code==308)){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing API redirect");URI next=uri.resolve(location);if(!sameOrigin(uri,next)||next.getUserInfo()!=null)throw new IOException("Cross-host API redirect refused");uri=next;continue;}
                if(code==429)throw new IOException("OpenSubtitles rate limit (429). Wait before trying again.");
                if(code==401||code==403)throw new IOException("OpenSubtitles HTTP "+code+": check application API key, optional login and service access.");
                if(code==406)throw new IOException("OpenSubtitles download quota exhausted (406).");
                if(code<200||code>=300)throw new IOException("OpenSubtitles HTTP "+code);
                return new JSONObject(read(connection,2*1024*1024));
            }finally{connection.disconnect();if(active==connection)active=null;}
        }throw new IOException("Too many API redirects");
    }
    static boolean sameOrigin(URI a,URI b){return Objects.equals(a.getScheme(),b.getScheme())&&Objects.equals(a.getHost(),b.getHost())&&port(a)==port(b);}
    private static int port(URI uri){return uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();}
    private void configure(HttpURLConnection connection){connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(7000);connection.setReadTimeout(15000);connection.setRequestProperty("User-Agent",USER_AGENT);}
    private void check()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    private String read(HttpURLConnection connection,int limit)throws Exception{try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){check();if(out.size()+count>limit)throw new IOException("Response too large");out.write(buffer,0,count);}return decodeSubtitle(out.toByteArray());}}
    static String decodeSubtitle(byte[] bytes)throws Exception{
        if(bytes.length>1&&bytes[0]=='P'&&bytes[1]=='K'){
            List<ArchiveFile> choices=new ArrayList<>();int entries=0,total=0;
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(++entries>32)throw new IOException("Subtitle archive has too many entries");boolean usable=!entry.isDirectory()&&entry.getName().toLowerCase(Locale.ROOT).matches(".*\\.(srt|ass|ssa|vtt)$");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=zip.read(b))!=-1){if((total+=n)>5*1024*1024)throw new IOException("Expanded subtitle archive exceeds limit");if(usable)out.write(b,0,n);}if(usable){byte[] candidate=out.toByteArray();String text=decodeText(candidate);boolean duplicate=false;for(ArchiveFile old:choices)if(old.text.equals(text))duplicate=true;if(!duplicate)choices.add(new ArchiveFile(entry.getName(),text));}}}
            if(choices.isEmpty())throw new IOException("Archive has no SRT, ASS or VTT subtitle");if(choices.size()>1)throw new ArchiveChoice(choices);return choices.get(0).text;
        }
        return decodeText(bytes);
    }
    private static String decodeText(byte[] bytes)throws Exception{
        if(bytes.length>1&&((bytes[0]&255)==255&&(bytes[1]&255)==254||(bytes[0]&255)==254&&(bytes[1]&255)==255))return new String(bytes,java.nio.charset.StandardCharsets.UTF_16);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    static final class ArchiveFile{final String name,text;ArchiveFile(String name,String text){this.name=name.substring(0,Math.min(240,name.length()));this.text=text;}}
    static final class ArchiveChoice extends IOException{final List<ArchiveFile> files;ArchiveChoice(List<ArchiveFile> files){super("Choose the subtitle release inside this archive");this.files=Collections.unmodifiableList(files);}}
    void cancel(){cancelled=true;if(nativeCall!=null)nativeCall.cancel();if(subsource!=null)subsource.cancel();HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
    static final class SubtitleFile{
        String imdbId="";long downloadCount;
        final long id;final String filename,release,language,title;final double fps;final int season,episode;final boolean ai,machine;
        SubtitleFile(long id,String filename,String release,String language,double fps,String title,int season,int episode,boolean ai,boolean machine){this.id=id;this.filename=filename;this.release=release;this.language=language;this.fps=fps;this.title=title;this.season=season;this.episode=episode;this.ai=ai;this.machine=machine;}
        String label(){return title+" · "+language+(season>0?" · S"+season+" E"+episode:"")+"\n"+release+" · "+fps+" FPS"+(ai?" · AI":"")+(machine?" · machine":"")+"\n"+filename+" · file #"+id+" · downloads: "+downloadCount;}
    }
    static final class Download{final String url,name,reset,message;final int remaining;Download(String url,String name,int remaining,String reset,String message){this.url=url;this.name=name;this.remaining=remaining;this.reset=reset;this.message=message;}}
}
