package com.smarthub.plus;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Uses the OEM Fpanel display message on the receiver's local DVB service.
 * The state file remains available for diagnostics.
 */
final class FrontPanelBridge {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private static final AtomicLong REVISION=new AtomicLong();
 private FrontPanelBridge(){}
 static String diagnostics(Context c){
  File vfd=new File("/dev/vfd");
  File fp=new File("/dev/led");
  File leds=new File("/sys/class/leds");
  File external=c.getExternalFilesDir(null);
  File export=external==null?null:new File(external,"frontpanel/state.json");
  return "ADB="+(UsbAdbSetup.enabled(c)?"ON":"OFF")+
   "\n/dev/vfd="+(vfd.exists()?"present":"absent")+
   " app_write="+vfd.canWrite()+
   "\n/dev/led="+(fp.exists()?"present":"absent")+
   " app_write="+fp.canWrite()+
   "\n/sys/class/leds="+(leds.exists()?"present":"absent")+
   "\nADB handoff="+(export==null?"UNAVAILABLE":export.getAbsolutePath())+
   "\nOEM Fpanel: "+c.getSharedPreferences("smarthub",0)
    .getString("front_panel_status","WAITING_FOR_CHANNEL");
 }
 static void publish(Context context,ReceiverCatalog.Channel channel){
  publish(context,channel,0);
 }
 static void publish(Context context,ReceiverCatalog.Channel channel,int number){
  if(channel==null||!context.getSharedPreferences("smarthub",0)
      .getBoolean("front_panel_export_enabled",true))return;
  Context app=context.getApplicationContext();
  String name=channel.name,physical="DVB:"+channel.physical();
  int displayNumber=app.getSharedPreferences("smarthub",0)
    .getBoolean("front_panel_export_number",true)?Math.max(0,number):0;
  long revision=REVISION.incrementAndGet();
  IO.execute(()->{
   if(revision!=REVISION.get())return;
   String status=showName(name,displayNumber,channel.tv==1);
   if(revision!=REVISION.get())return;
   app.getSharedPreferences("smarthub",0).edit()
    .putString("front_panel_status",status).apply();
   try{
    File base=app.getExternalFilesDir(null);
    if(base==null)return;
    File directory=new File(base,"frontpanel");
    if(!directory.isDirectory()&&!directory.mkdirs())return;
    File current=new File(directory,"state.json"),tmp=new File(directory,"state.tmp");
    JSONObject state=new JSONObject()
       .put("channel",name).put("physical",physical)
       .put("scrambled",channel.ca>0)
       .put("channelNumber",displayNumber)
       .put("displayMode","channel")
       .put("clockFormat","HH:mm")
       .put("oemDriverWrites",false)
       .put("updatedAt",System.currentTimeMillis());
    try(FileOutputStream out=new FileOutputStream(tmp)){
      out.write(state.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
    }
    if(!tmp.renameTo(current))tmp.delete();
    app.getSharedPreferences("smarthub",0).edit()
      .putLong("front_panel_handoff_at",System.currentTimeMillis()).apply();
   }catch(Exception ignored){}
  });
 }
 private static String showName(String name,int number,boolean radio){
  try(DatagramSocket socket=new DatagramSocket()){
   JSONObject body=new JSONObject().put("iType0",2).put("iNameNum",number)
     .put("aucBuffer",name).put("iType1",4).put("iInfo",(radio?8:16)|4)
     .put("bKeepPre",0).put("iType2",-1);
   byte[] request=("Fpanel\n0002\n"+body).getBytes(StandardCharsets.UTF_8);
   socket.connect(InetAddress.getByName("127.0.0.1"),51021);
   socket.setSoTimeout(300);
   socket.send(new DatagramPacket(request,request.length));
   byte[] bytes=new byte[1024];
   DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
   socket.receive(packet);
   String reply=new String(bytes,0,packet.getLength(),StandardCharsets.UTF_8);
   return reply.startsWith("Fpanel\n0002\nSUCCESS:")?"COMMAND_ACCEPTED":"DISPLAY_UNCONFIRMED";
  }catch(java.net.SocketTimeoutException timeout){return "SENT_NO_ACK";}
  catch(Exception unavailable){return "UNAVAILABLE";}
 }
}
