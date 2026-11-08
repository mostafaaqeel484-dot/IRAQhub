package com.smarthub.plus;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receiver panel compatibility probe and optional USB-ADB handoff.
 *
 * We cannot safely guess an OEM proprietary /dev/vfd protocol or write to a
 * privileged sysfs device. Provide an exact channel-name state file for an
 * explicitly authorised ADB host companion. Never issue shell/root commands.
 */
final class FrontPanelBridge {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
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
   "\nPanel mode="+c.getSharedPreferences("smarthub",0).getString("front_panel_mode","channel")+
   "\nOEM panel display API: NOT YET IDENTIFIED (no unsafe writes)";
 }
 static void publish(Context context,ReceiverCatalog.Channel channel){
  if(channel==null)return;
  Context app=context.getApplicationContext();
  String name=channel.name,physical="DVB:"+channel.physical();
  String mode=app.getSharedPreferences("smarthub",0).getString("front_panel_mode","channel");
  if("off".equals(mode))return;
  IO.execute(()->{
   try{
    File base=app.getExternalFilesDir(null);
    if(base==null)return;
    File directory=new File(base,"frontpanel");
    if(!directory.isDirectory()&&!directory.mkdirs())return;
    File current=new File(directory,"state.json"),tmp=new File(directory,"state.tmp");
    JSONObject state=new JSONObject()
       .put("channel",name).put("physical",physical)
       .put("scrambled",channel.ca>0)
       .put("mode",mode)
       .put("clock",new java.text.SimpleDateFormat("HH:mm",java.util.Locale.ROOT)
           .format(new java.util.Date()))
       .put("updatedAt",System.currentTimeMillis())
       .put("nativePanelControl",false);
    try(FileOutputStream out=new FileOutputStream(tmp)){
      out.write(state.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
    }
    if(!tmp.renameTo(current))tmp.delete();
    app.getSharedPreferences("smarthub",0).edit()
      .putLong("front_panel_handoff_at",System.currentTimeMillis()).apply();
   }catch(Exception ignored){}
  });
 }
}