package com.smarthub.plus;
import android.content.Context;import android.os.SystemClock;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;

/** Direct, read-only vendor SDK transport. No INFO/OCR, retune, motor or listener registration. */
final class DirectReceiverSignal implements AutoCloseable {
 private static final class Frame{final DirectReceiverSignal owner;final String channel;final long seen;final ReceiverSignalReading data;Frame(DirectReceiverSignal owner,String channel,ReceiverSignalReading data){this.owner=owner;this.channel=channel;this.data=data;seen=SystemClock.elapsedRealtime();}}
 private static volatile Frame latest;private final Context context;private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean closed,busy,enabled;private volatile DatagramSocket active;private volatile long next;private int failures;private String requested="";private volatile long successes;
 DirectReceiverSignal(Context c){context=c.getApplicationContext();}
 static String key(SatelliteState s){return s.channelId+"|"+s.channelName;}
 synchronized void refresh(SatelliteState observed){if(closed)return;if(context.getSharedPreferences("smarthub",0).getBoolean("dvb_tuning",false)){pause();return;}enabled=true;if(busy||observed.source.startsWith("IPTV")||observed.channelId.startsWith("IPTV:"))return;String key=key(observed);long now=SystemClock.elapsedRealtime();if(!requested.equals(key)){requested=key;next=0;Frame old=latest;if(old!=null&&old.owner==this)latest=null;}if(now<next)return;next=now+1000;busy=true;io.execute(()->{try{ReceiverSignalReading data=read(0);if(!closed&&enabled&&key.equals(key(SatelliteState.read(context)))){latest=new Frame(this,key,data);successes++;failures=0;}}catch(Exception unavailable){if(!closed&&enabled){Frame f=latest;if(f!=null&&f.owner==this)latest=null;if(++failures>=3)next=SystemClock.elapsedRealtime()+5000;}}finally{busy=false;}});}
 private ReceiverSignalReading read(int tuner)throws Exception{try(DatagramSocket socket=new DatagramSocket()){active=socket;socket.setSoTimeout(250);byte[] query=("Tuner\n0003\n{\"iTunerIndex\":"+tuner+"}").getBytes(StandardCharsets.UTF_8);socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("127.0.0.1"),51021));long until=SystemClock.elapsedRealtime()+1500;while(!closed&&enabled&&!Thread.currentThread().isInterrupted()&&SystemClock.elapsedRealtime()<until){byte[] bytes=new byte[8193];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);try{socket.receive(packet);}catch(SocketTimeoutException wait){continue;}if(!packet.getAddress().isLoopbackAddress()||packet.getLength()>8192)continue;try{ReceiverSignalReading data=ReceiverSignalReading.packet(new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8),tuner);if(data!=null)return data;}catch(Exception malformed){}}throw new SocketTimeoutException("DIRECT_SIGNAL_TIMEOUT");}finally{active=null;}}
 void pause(){enabled=false;DatagramSocket socket=active;if(socket!=null)socket.close();Frame f=latest;if(f!=null&&f.owner==this)latest=null;next=0;}
 long successes(){return successes;}
 private static Frame current(SatelliteState s){Frame f=latest;return f!=null&&!s.source.startsWith("IPTV")&&!s.channelId.startsWith("IPTV:")&&ReceiverSignalReading.fresh(key(s),f.channel,SystemClock.elapsedRealtime(),f.seen)?f:null;}
 static String value(SatelliteState s,String field){Frame f=current(s);return f==null?"":f.data.value(field);}
 static boolean live(SatelliteState s){return current(s)!=null;}
 static long timestamp(SatelliteState s){Frame f=current(s);return f==null?0:f.seen;}
 static ReceiverSignalReading reading(SatelliteState s){Frame f=current(s);return f==null?null:f.data;}
 static String format(SatelliteState s){Frame f=current(s);return f==null||f.data.locked!=1?"":f.data.format;}
 static String sourceLabel(SatelliteState s,boolean ar){Frame f=current(s);return f==null?(ar?"إشارة DVB غير متاحة":"DVB signal unavailable"):f.data.locked==0?(ar?"DVB مباشر · غير مقفل":"DVB LIVE · Unlocked"):(ar?"DVB مباشر":"DVB LIVE");}
 @Override public void close(){closed=true;DatagramSocket socket=active;if(socket!=null)socket.close();io.shutdownNow();pause();}
}
