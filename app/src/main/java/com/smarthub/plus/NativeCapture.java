package com.smarthub.plus;

import android.os.SystemClock;
import org.json.JSONObject;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Native 3.7.0 Pvr UDP protocol, recovered from the supplied APK.
 * Only app-owned temporary capture IDs may be stopped here.
 * This does not bypass CA or grant platform permissions.
 */
final class NativeCapture {
 static final Object CONTROL = new Object();
 private static final int MIN_ID=1_100_000_000, MAX_ID=1_103_000_000;
 private NativeCapture(){}

 static boolean validCaptureId(int id){return id>=MIN_ID&&id<=MAX_ID;}

 static boolean exchange(int command,JSONObject body) {
  if(body==null)return false;
  synchronized(CONTROL){
   DatagramSocket socket=null;
   try{
    socket=new DatagramSocket();
    socket.setSoTimeout(200);
    String prefix="Pvr\n"+String.format(Locale.ROOT,"%04d",command)+"\n";
    byte[] request=(prefix+body).getBytes(StandardCharsets.UTF_8);
    InetAddress loopback=InetAddress.getByName("127.0.0.1");
    socket.send(new DatagramPacket(request,request.length,loopback,51021));
    long until=SystemClock.elapsedRealtime()+2000L;
    while(SystemClock.elapsedRealtime()<until&&!Thread.currentThread().isInterrupted()){
     byte[] raw=new byte[4096];
     DatagramPacket response=new DatagramPacket(raw,raw.length);
     try{socket.receive(response);}catch(SocketTimeoutException timeout){continue;}
     if(!response.getAddress().isLoopbackAddress())continue;
     String packet=new String(raw,0,response.getLength(),StandardCharsets.UTF_8).replace("\u0000","");
     if(packet.startsWith(prefix))return packet.startsWith(prefix+"SUCCESS:");
    }
   }catch(Exception notAccessible){return false;}
   finally{if(socket!=null)socket.close();}
   return false;
  }
 }

 static boolean record(int id,JSONObject packet){
  if(!validCaptureId(id)||packet==null)return false;
  return exchange(2,packet);
 }
 static boolean stop(int id){
  if(!validCaptureId(id))return false;
  try{return exchange(3,new JSONObject().put("iProgID",id));}
  catch(Exception ignored){return false;}
 }
}
