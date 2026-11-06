package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reconstructed from IRAQhub 3.7.0 bytecode, not an OEM privileged API.
 * Captures an FTA service to bounded app-owned MPEG-TS segments then exposes
 * a private 127.0.0.1 MPEG-TS HTTP stream to the in-app LibVLC decoder.
 */
final class LiveTsRelay implements AutoCloseable,Runnable {
 interface Listener {void ready(String url);void error(String cause);}
 private static final long ROTATE=24L*1024*1024;
 private static final long RESERVED_DISK=128L*1024*1024;
 private static final long MAX_DURATION=8L*60*60*1000;
 private static final int TS=188;
 private final Context context;
 private final ReceiverCatalog.Channel channel;
 private final JSONObject body;
 private final Listener callback;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Object lock=new Object();
 private final AtomicInteger clients=new AtomicInteger();
 private final java.util.concurrent.ConcurrentHashMap<Socket,Integer> cursors=new java.util.concurrent.ConcurrentHashMap<>();
 private final String session=UUID.randomUUID().toString();
 private final List<File> files=new ArrayList<>();
 private long retiredSegmentBytes; // total bytes in successfully deleted old TS segments
 private final int firstId,secondId;
 private volatile boolean closed,started;
 private volatile ServerSocket server;
 private volatile File current;
 private volatile int currentId;
 private volatile Thread worker;
 private File root;
 private String token;

 LiveTsRelay(Context context,ReceiverCatalog.Channel channel,JSONObject body,Listener callback)throws Exception {
  if(channel==null||body==null||callback==null||channel.ca<0||channel.tv!=0)
   throw new IOException("SOFTWARE_FTA_ONLY");
  if(channel.id<0||channel.id>1000000)throw new IOException("CAPTURE_ID_OUT_OF_RANGE");
  this.context=context.getApplicationContext();
  this.channel=channel;
  this.body=new JSONObject(body.toString());
  this.callback=callback;
  firstId=channel.programToken();
  secondId=firstId+2_000_000;
  if(!NativeCapture.validCaptureId(firstId)||!NativeCapture.validCaptureId(secondId))
   throw new IOException("CAPTURE_ID_OUT_OF_RANGE");
 }
 private SharedPreferences prefs(){return context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);}

 boolean running(){return started&&!closed&&server!=null&&!server.isClosed();}
 synchronized void start(){
  if(started)return;
  started=true;
  worker=new Thread(this,"iraqhub-live-ts");
  worker.start();
 }

 @Override public void run(){
  long begin=SystemClock.elapsedRealtime();
  try{
   File external=context.getExternalFilesDir(null);
   if(external==null)throw new IOException("CAPTURE_STORAGE_UNAVAILABLE");
   root=new File(external,"software-live-"+session);
   if(!root.mkdirs()&&!root.isDirectory())throw new IOException("CAPTURE_FOLDER_UNAVAILABLE");
   if(root.getUsableSpace()<RESERVED_DISK)throw new IOException("CAPTURE_SPACE_REQUIRED");
   synchronized(NativeCapture.CONTROL){
    // No stale session can be stopped by this instance.
    prefs().edit().putString("capture_session",session)
      .putInt("capture_id_a",firstId).putInt("capture_id_b",secondId)
      .putLong("capture_heartbeat",SystemClock.elapsedRealtime())
      .putBoolean("capture_active_a",false).putBoolean("capture_active_b",false)
      .remove("software_ts_channel").remove("software_ts_bitrate").remove("software_ts_bitrate_at").apply();
   }
   token=UUID.randomUUID().toString().replace("-","");
   server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
   server.setSoTimeout(800);
   capture(firstId);
   long deadline=SystemClock.elapsedRealtime()+12000;
   while(!closed&&SystemClock.elapsedRealtime()<deadline){
    File file=current;
    if(file!=null&&file.length()>=TS*384&&sync(file))break;
    Thread.sleep(150);
   }
   if(closed)return;
   if(current==null||current.length()<TS*384||!sync(current))
    throw new IOException("CAPTURE_NO_MPEG_TS");
   if(channel.ca!=0||BissKeyVault.has(context,channel)){
    // Only the OEM's already-authorised descrambler may clear DVB packets.
    // Never feed scrambled video to VLC or pretend a stored BISS key was applied.
    int videoPid=channel.data.optInt("VideoPID",-1),observed=0;
    long clearUntil=SystemClock.elapsedRealtime()+8000;
    while(!closed&&SystemClock.elapsedRealtime()<clearUntil){
     observed=videoClearState(current,videoPid);
     if(observed==1)break;
     Thread.sleep(230);
    }
    if(observed<0)throw new IOException("BISS_FIRMWARE_CAPTURE_STILL_SCRAMBLED");
    if(observed==0)throw new IOException("BISS_FIRMWARE_CLEAR_TS_NOT_VERIFIED");
   }
   String url="http://127.0.0.1:"+server.getLocalPort()+"/"+token+".ts";
   main.post(()->{if(!closed)callback.ready(url);});
   long lastSignal=SystemClock.elapsedRealtime();
   long priorBytes=0;
   long priorMeasure=lastSignal;
   while(!closed&&SystemClock.elapsedRealtime()-begin<MAX_DURATION){
    try{
     Socket client=server.accept();
     if(clients.incrementAndGet()>2){clients.decrementAndGet();client.close();continue;}
     new Thread(()->{try{serve(client);}finally{
      cursors.remove(client);
      try{client.close();}catch(IOException ignored){}
      clients.decrementAndGet();
     }},"iraqhub-ts-client").start();
    }catch(SocketTimeoutException ignored){}
    if(SystemClock.elapsedRealtime()-lastSignal>2500){
     lastSignal=SystemClock.elapsedRealtime();
     long bytes;
     synchronized(lock){
      bytes=retiredSegmentBytes;
      for(File segment:files)if(segment!=null)bytes+=Math.max(0,segment.length());
     }
     long elapsed=lastSignal-priorMeasure;
     if(elapsed>=1000&&bytes>priorBytes){
      long bps=Math.min(1000000000L,(bytes-priorBytes)*8000L/elapsed);
      prefs().edit().putString("software_ts_channel","DVB:"+channel.physical())
       .putLong("software_ts_bitrate",bps)
       .putLong("software_ts_bitrate_at",System.currentTimeMillis()).apply();
     }
     priorBytes=bytes;priorMeasure=lastSignal;
     prefs().edit().putLong("capture_heartbeat",lastSignal).apply();
     ReceiverSignalReading signal=DvbTuner.read();
     if(signal.locked!=1||signal.tp!=body.optInt("tp_id",-1))
      throw new IOException("CAPTURE_TUNER_CHANGED_OR_UNLOCKED");
     File file=current;
     if(file!=null&&file.length()>ROTATE){rotate();trimOldSegments();}
     if(root.getUsableSpace()<RESERVED_DISK)throw new IOException("CAPTURE_SPACE_REQUIRED");
    }
   }
  }catch(Exception error){
   if(!closed) {
    String message=error.getMessage()==null?"CAPTURE_UNAVAILABLE":error.getMessage();
    main.post(()->{if(!closed)callback.error(message);});
   }
  }finally{close();}
 }

 private void capture(int id)throws Exception {
  if(closed)throw new IOException("CAPTURE_CANCELLED");
  File file=new File(root,(id==firstId?"a":"b")+"-"+files.size()+".ts");
  if(file.exists()&&!file.delete())throw new IOException("CAPTURE_FILE_BUSY");
  JSONObject packet=new JSONObject(body.toString())
   .put("iProgId",id).put("path_dir",root.getAbsolutePath())
   .put("path",file.getAbsolutePath()).put("bTimeShift",false)
   .put("FsType",0).put("tp_record",0);
  if(!NativeCapture.record(id,packet))throw new IOException("CAPTURE_REJECTED");
  synchronized(lock){
   files.add(file);current=file;currentId=id;lock.notifyAll();
  }
  prefs().edit().putBoolean(id==firstId?"capture_active_a":"capture_active_b",true).apply();
 }

 private void rotate()throws Exception{
  int old=currentId,next=old==firstId?secondId:firstId;
  try{
   // Zero-gap switch if the DVB firmware permits concurrent PVR capture.
   capture(next);
   if(!NativeCapture.stop(old))throw new IOException("CAPTURE_STOP_NOT_CONFIRMED");
  }catch(IOException duplicate){
   if(!"CAPTURE_REJECTED".equals(duplicate.getMessage()))throw duplicate;
   // Some receivers allow just one PVR session. Continue with a short gap
   // instead of aborting software video after the first 24MB segment.
   if(!NativeCapture.stop(old))throw new IOException("CAPTURE_STOP_NOT_CONFIRMED");
   prefs().edit().putBoolean(old==firstId?"capture_active_a":"capture_active_b",false).apply();
   capture(next);
  }
  prefs().edit().putBoolean(old==firstId?"capture_active_a":"capture_active_b",false).apply();
 }

 /**
  * 1 = observed multiple clear video TS packets, -1 = scrambled video,
  * 0 = insufficient evidence. This is a media check, not a CA bypass.
  */
 static int videoClearState(File file,int videoPid){
  if(videoPid<16||videoPid>=8191||file==null)return 0;
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   long total=in.length()/TS;
   if(total<16)return 0;
   long from=Math.max(0,total-4096);
   in.seek(from*TS);
   byte[] packet=new byte[TS];
   // A BISS receiver may unlock mid-capture. Examine only the most recent
   // 24 observed video packets, not encrypted packets at recording startup.
   int[] recent=new int[24];
   int head=0,kept=0,clear=0,scrambled=0;
   for(long i=from;i<total;i++){
    in.readFully(packet);
    if(packet[0]!=(byte)0x47)break;
    int pid=((packet[1]&31)<<8)|(packet[2]&255);
    if(pid!=videoPid||(packet[3]&0x10)==0)continue;
    int flag=(packet[3]&0xc0)==0?0:1;
    if(kept==recent.length){
     if(recent[head]==0)clear--;else scrambled--;
    }else kept++;
    recent[head]=flag;
    head=(head+1)%recent.length;
    if(flag==0)clear++;else scrambled++;
   }
   if(clear>=12&&scrambled==0)return 1;
   if(scrambled>=8)return -1;
   return 0;
  }catch(Exception unavailable){return 0;}
 }
 private static boolean sync(File file){
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   if(in.length()<TS*3)return false;
   byte[] block=new byte[TS*3];
   in.readFully(block);
   return block[0]==0x47&&block[TS]==0x47&&block[TS*2]==0x47;
  }catch(Exception missing){return false;}
 }

 /**
  * A live TV relay must not fill the receiver storage. Keep recent TS segments
  * plus anything still being read by a client; disconnect clients that lag
  * farther than eight 24MB segments.
  */
 private void trimOldSegments(){
  synchronized(lock){
   int latest=files.size()-1;
   for(java.util.Map.Entry<Socket,Integer> client:cursors.entrySet()){
    if(client.getValue()<latest-8)try{client.getKey().close();}
     catch(IOException ignored){}
   }
   int keepFrom=Math.max(0,latest-3);
   for(Integer reading:cursors.values())
    keepFrom=Math.min(keepFrom,reading);
   for(int i=0;i<keepFrom;i++){
    File old=files.get(i);
    if(old!=null&&old!=current&&old.exists()){
     long size=old.length();
     if(old.delete()){retiredSegmentBytes+=size;files.set(i,null);}
    }
   }
  }
 }
 private void serve(Socket client){
  try{
   client.setSoTimeout(3500);
   client.setTcpNoDelay(true);
   BufferedReader reader=new BufferedReader(
      new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
   String first=reader.readLine();
   if(first==null)return;
   String target="/"+token+".ts";
   boolean get=first.equals("GET "+target+" HTTP/1.1")||first.equals("GET "+target+" HTTP/1.0");
   boolean head=first.equals("HEAD "+target+" HTTP/1.1")||first.equals("HEAD "+target+" HTTP/1.0");
   if(!get&&!head){respond(client,"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");return;}
   for(int i=0;i<48;i++){String line=reader.readLine();if(line==null||line.isEmpty())break;
    if(line.length()>2048)throw new IOException("INVALID_HTTP_HEADER");
   }
   OutputStream out=client.getOutputStream();
   out.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\n"+
       "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: http://127.0.0.1\r\n"+
       "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
       .getBytes(StandardCharsets.US_ASCII));
   if(head){out.flush();return;}
   stream(out,client);
  }catch(Exception ignored){}
 }
 private static void respond(Socket client,String headers)throws IOException{
  OutputStream stream=client.getOutputStream();
  stream.write(headers.getBytes(StandardCharsets.US_ASCII));stream.flush();
 }
 private void stream(OutputStream destination,Socket client)throws IOException {
  int fileIndex;
  synchronized(lock){fileIndex=Math.max(0,files.size()-2);}
  cursors.put(client,fileIndex);
  long offset=0,lastData=SystemClock.elapsedRealtime();
  final byte[] buffer=new byte[TS*128];
  final TsSplice.Continuity continuity=new TsSplice.Continuity();
  final OutputStream out=new BufferedOutputStream(destination,TS*256);
  RandomAccessFile input=null;
  File opened=null;
  try{
   while(!closed&&!client.isClosed()&&!Thread.currentThread().isInterrupted()){
    File source;
    boolean next;
    synchronized(lock){
     source=fileIndex<files.size()?files.get(fileIndex):null;
     next=fileIndex+1<files.size();
    }
    if(source==null){
     if(next){
      if(input!=null){input.close();input=null;opened=null;}
      fileIndex++;offset=0;cursors.put(client,fileIndex);continue;
     }
     if(SystemClock.elapsedRealtime()-lastData>14000)break;
     sleep(65);continue;
    }
    if(!source.equals(opened)){
     if(input!=null)input.close();
     try{input=new RandomAccessFile(source,"r");opened=source;}
     catch(FileNotFoundException missing){
      input=null;opened=null;
      if(next){fileIndex++;offset=0;cursors.put(client,fileIndex);continue;}
      sleep(65);continue;
     }
    }
    long available=input.length()-offset;
    int amount=(int)(Math.min(buffer.length,Math.max(0,available))/TS)*TS;
    if(amount>0){
     input.seek(offset);
     input.readFully(buffer,0,amount);
     continuity.rewrite(buffer,amount);
     out.write(Integer.toHexString(amount).getBytes(StandardCharsets.US_ASCII));
     out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
     out.write(buffer,0,amount);
     out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
     out.flush();
     offset+=amount;lastData=SystemClock.elapsedRealtime();
     continue;
    }
    if(next){
     if(input!=null){input.close();input=null;opened=null;}
     fileIndex++;offset=0;cursors.put(client,fileIndex);
     continue;
    }
    if(SystemClock.elapsedRealtime()-lastData>14000)break;
    sleep(65);
   }
   out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
   out.flush();
  }catch(IOException disconnected){
   if(!client.isClosed()&&!closed)throw disconnected;
  }finally{if(input!=null)try{input.close();}catch(IOException ignored){}}
 }
 private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}

 @Override public void close(){
  if(closed)return;
  closed=true;
  ServerSocket live=server;
  if(live!=null)try{live.close();}catch(IOException ignored){}
  synchronized(NativeCapture.CONTROL){
   SharedPreferences p=prefs();
   if(session.equals(p.getString("capture_session",""))){
    if(p.getBoolean("capture_active_a",false))NativeCapture.stop(firstId);
    if(p.getBoolean("capture_active_b",false))NativeCapture.stop(secondId);
    p.edit().remove("capture_session").remove("capture_id_a").remove("capture_id_b")
      .remove("software_ts_channel").remove("software_ts_bitrate").remove("software_ts_bitrate_at")
      .putBoolean("capture_active_a",false).putBoolean("capture_active_b",false).apply();
   }
  }
  if(root!=null&&root.getName().equals("software-live-"+session)){
   File[] contents=root.listFiles();
   if(contents!=null)for(File file:contents){
    if(file.isFile()&&file.getName().matches("[ab]-[0-9]+\\.ts"))file.delete();
   }
   root.delete();
  }
 }
}
