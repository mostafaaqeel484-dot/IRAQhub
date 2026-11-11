package com.smarthub.plus;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class OpenSubtitlesClientTest {
    @Test public void zipRequiresChoiceForDifferentReleasesAndDeduplicatesIdenticalCopies()throws Exception{String a="1\n00:00:01,000 --> 00:00:02,000\nمرحبا\n",b="1\n00:00:03,000 --> 00:00:04,000\nمرحبا\n";assertEquals(a,OpenSubtitlesClient.decodeSubtitle(zip(a,a)));try{OpenSubtitlesClient.decodeSubtitle(zip(a,b));fail();}catch(OpenSubtitlesClient.ArchiveChoice choice){assertEquals(2,choice.files.size());assertEquals(b,choice.files.get(1).text);}}
    private static byte[] zip(String a,String b)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(out)){int i=0;for(String value:new String[]{a,b}){zip.putNextEntry(new java.util.zip.ZipEntry("release"+(++i)+".srt"));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return out.toByteArray();}
    @Test public void canonicalSearchUsesPreciseIdOrEncodedTitleAndEpisodeFields()throws Exception{
        assertEquals("imdb_id=133093&languages=ar",OpenSubtitlesClient.searchQuery("ignored","tt0133093","AR",0,0));
        assertEquals("languages=ar&query=the+matrix",OpenSubtitlesClient.searchQuery("The Matrix","","ar",0,0));
        assertEquals("episode_number=3&languages=en&parent_imdb_id=123&season_number=2",OpenSubtitlesClient.searchQuery("","tt0000123","en",2,3));
        try{OpenSubtitlesClient.searchQuery("Title","bad-id","ar",0,0);fail();}catch(IOException expected){}
    }
    @Test public void missingKeysAndUnsafeTestEndpointsAreRefusedBeforeNetwork()throws Exception{
        for(String key:new String[]{""," ","abc\nsecret"})try{new OpenSubtitlesClient(key);fail();}catch(IOException expected){}
        try{new OpenSubtitlesClient("key",URI.create("http://example.com"));fail();}catch(IOException expected){}
        assertFalse(OpenSubtitlesClient.sameOrigin(URI.create("https://api.opensubtitles.com/a"),URI.create("https://evil.test/a")));
        assertFalse(OpenSubtitlesClient.sameOrigin(URI.create("https://api.opensubtitles.com/a"),URI.create("http://api.opensubtitles.com/a")));
    }
    @Test public void everyReturnedPhysicalFileIsSelectableWithReleaseAndOriginalFps()throws Exception{
        JSONObject data=new JSONObject("{\"data\":[{\"attributes\":{\"release\":\"BluRay\",\"language\":\"ar\",\"fps\":23.976,\"feature_details\":{\"title\":\"Film\",\"season_number\":2,\"episode_number\":3},\"files\":[{\"file_id\":12,\"file_name\":\"cd1.srt\"},{\"file_id\":13,\"file_name\":\"cd2.srt\"}]}}]}");
        List<OpenSubtitlesClient.SubtitleFile> files=OpenSubtitlesClient.parse(data);assertEquals(2,files.size());assertEquals(13,files.get(1).id);assertEquals("cd2.srt",files.get(1).filename);assertEquals(23.976,files.get(0).fps,0.001);assertEquals(3,files.get(0).episode);
        try{OpenSubtitlesClient.parse(new JSONObject("{}"));fail();}catch(IOException expected){}
    }
    @Test public void officialWireFlowPreservesArabicAndDoesNotLeakCredentialsToFileDownload()throws Exception{
        try(Server server=new Server()){
            server.handler=request->{
                if(request.path.equals("/api/v1/login"))return new Response(200,"{\"token\":\"user-token\",\"base_url\":\"vip-api.opensubtitles.com\",\"user\":{\"allowed_downloads\":10}}");
                if(request.path.startsWith("/api/v1/subtitles?"))return new Response(200,"{\"data\":[]}");
                if(request.path.equals("/api/v1/download"))return new Response(200,new JSONObject().put("link",server.url()+"/file.srt").put("file_name","ar.srt").put("remaining",4).put("reset_time_utc","2026-11-01T00:00:00Z").toString());
                return new Response(200,"1\n00:00:01,000 --> 00:00:02,000\nمرحبا بالعالم\n");
            };server.start();OpenSubtitlesClient client=new OpenSubtitlesClient("app-key",URI.create(server.url()+"/api/v1"));
            client.login("me","password-not-persisted");assertTrue(client.search("Film","","ar",0,0).isEmpty());OpenSubtitlesClient.Download download=client.download(12);assertEquals(4,download.remaining);assertTrue(client.fetch(download).contains("مرحبا بالعالم"));
            List<Request> requests=server.snapshot();assertEquals(4,requests.size());assertEquals("POST",requests.get(0).method);assertEquals("me",new JSONObject(requests.get(0).body).getString("username"));assertEquals("app-key",requests.get(0).header("Api-Key"));assertNull(requests.get(0).header("Authorization"));
            assertEquals("Bearer user-token",requests.get(1).header("Authorization"));assertEquals(12,new JSONObject(requests.get(2).body).getInt("file_id"));assertEquals("srt",new JSONObject(requests.get(2).body).getString("sub_format"));
            Request file=requests.get(3);assertNull(file.header("Api-Key"));assertNull(file.header("Authorization"));assertEquals("SmartHubPlus v1.0",file.header("User-Agent"));
        }
    }
    @Test public void rateLimitAndForbiddenDoNotRetryOrProduceFakeSearchResults()throws Exception{
        for(int status:new int[]{429,403})try(Server server=new Server()){
            server.handler=request->new Response(status,"{}");server.start();OpenSubtitlesClient client=new OpenSubtitlesClient("key",URI.create(server.url()+"/api/v1"));
            try{client.search("Film","","ar",0,0);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains(Integer.toString(status)));}assertEquals(1,server.snapshot().size());
        }
    }
    @Test public void apiRedirectCannotForwardApiKeyToAnotherHostOrPort()throws Exception{
        try(Server target=new Server();Server source=new Server()){
            target.handler=request->new Response(200,"{\"data\":[]}");target.start();source.handler=request->new Response(302,"",target.url()+"/stolen");source.start();
            try{new OpenSubtitlesClient("secret",URI.create(source.url()+"/api/v1")).search("Film","","ar",0,0);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("redirect"));}assertTrue(target.snapshot().isEmpty());
        }
    }
    @Test public void malformedLoginHostAndNonHttpsExternalDownloadAreRejected()throws Exception{
        try(Server server=new Server()){
            server.handler=request->new Response(200,request.path.endsWith("login")?"{\"token\":\"token\",\"base_url\":\"evil.test\"}":"{\"link\":\"http://evil.test/file\"}");server.start();OpenSubtitlesClient client=new OpenSubtitlesClient("key",URI.create(server.url()+"/api/v1"));
            try{client.login("u","p");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("host"));}
            try{client.download(1);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("HTTPS"));}
        }
    }
    private interface Handler{Response reply(Request request)throws Exception;}
    private static final class Response{final int status;final String body,location;Response(int status,String body){this(status,body,null);}Response(int status,String body,String location){this.status=status;this.body=body;this.location=location;}}
    private static final class Request{
        final String method,path,body;final Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Request(Socket socket)throws IOException{
            InputStream in=socket.getInputStream();ByteArrayOutputStream head=new ByteArrayOutputStream();int matched=0;byte[] boundary={13,10,13,10};
            while(matched<4){int value=in.read();if(value<0)throw new EOFException();head.write(value);if(head.size()>16384)throw new IOException("Header limit");matched=value==boundary[matched]?matched+1:value==13?1:0;}
            String[] lines=head.toString("US-ASCII").split("\r\n"),first=lines[0].split(" ");method=first[0];path=first[1];for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)headers.put(lines[i].substring(0,colon).trim(),lines[i].substring(colon+1).trim());}
            int length=Integer.parseInt(headers.getOrDefault("Content-Length","0"));if(length>1024*1024)throw new IOException("Body limit");byte[] bytes=in.readNBytes(length);body=new String(bytes,StandardCharsets.UTF_8);
        }
        String header(String name){return headers.get(name);}
    }
    private static final class Server implements AutoCloseable{
        final ServerSocket socket;final List<Request> requests=Collections.synchronizedList(new ArrayList<>());volatile boolean running;Thread worker;Handler handler;
        Server()throws IOException{socket=new ServerSocket();socket.bind(new InetSocketAddress("127.0.0.1",0));socket.setSoTimeout(200);}
        String url(){return "http://127.0.0.1:"+socket.getLocalPort();}
        List<Request> snapshot(){synchronized(requests){return new ArrayList<>(requests);}}
        void start(){running=true;worker=new Thread(()->{while(running)try(Socket accepted=socket.accept()){accepted.setSoTimeout(3000);Request request=new Request(accepted);requests.add(request);Response reply=handler.reply(request);byte[] body=reply.body.getBytes(StandardCharsets.UTF_8);String headers="HTTP/1.1 "+reply.status+" Test\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n"+(reply.location==null?"":"Location: "+reply.location+"\r\n")+"\r\n";accepted.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));accepted.getOutputStream().write(body);}catch(SocketTimeoutException ignored){}catch(Exception e){if(running)throw new RuntimeException(e);}},"opensubtitles-wire-test");worker.setDaemon(true);worker.start();}
        @Override public void close()throws IOException{running=false;socket.close();if(worker!=null)try{worker.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
}
