package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Best-effort isolation of the OEM satellite decoder's AUDIO path while
 * LibVLC plays an independently captured MPEG-TS service.
 *
 * Play/0117 is already used by ReceiverPlayback.nativeAudio(...); Play/0006
 * is the decoder volume control. These commands target the firmware player,
 * NOT Android's AudioManager, so LibVLC's AudioTrack remains audible.
 * No Play/0003 STOP, tuner retune or motor command is ever issued here:
 * the tuner and PVR/TS relay MUST remain alive for software playback.
 * UDP send is NOT proof that the OEM service applied either command.
 */
final class NativeSatelliteAudioGate {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"iraqhub-native-audio-gate");t.setDaemon(true);return t;
 });
 private static final Object LOCK=new Object();
 private static long serial,active;
 private NativeSatelliteAudioGate(){}

 static long suppress(Context c){
  final Context context=c.getApplicationContext();final long token;
  synchronized(LOCK){token=++serial;active=token;}
  IO.execute(()->{if(isActive(token))apply(context,false);});
  return token;
 }
 static void enforce(Context c,long token){
  final Context context=c.getApplicationContext();
  if(token==0)return;
  IO.execute(()->{if(isActive(token))apply(context,false);});
 }
 static void restore(Context c,long token){
  if(token==0)return;
  final Context context=c.getApplicationContext();
  IO.execute(()->{
   synchronized(LOCK){if(active!=token)return;active=0;}
   apply(context,true);
  });
 }
 private static boolean isActive(long token){synchronized(LOCK){return token!=0&&active==token;}}
 private static void apply(Context context,boolean enabled){
  SharedPreferences p=context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);
  int volume=p.getBoolean("dvb_muted",false)?0:Math.max(0,Math.min(100,p.getInt("dvb_volume",20)));
  boolean sentGate=send(117,enabled?"1":"0");
  boolean sentVolume=send(6,enabled?Integer.toString(volume):"0");
  p.edit().putString("software_422_native_audio",!sentGate&&!sentVolume?
    "NATIVE_AUDIO_COMMAND_UNAVAILABLE":enabled?"RESTORE_SENT_UNVERIFIED":"MUTE_SENT_UNVERIFIED").apply();
 }
 private static boolean send(int command,String data){
  try(DatagramSocket socket=new DatagramSocket()){
   byte[] packet=("Play\n"+String.format(Locale.ROOT,"%04d",command)+"\n"+data)
     .getBytes(StandardCharsets.UTF_8);
   socket.send(new DatagramPacket(packet,packet.length,
     InetAddress.getByName("127.0.0.1"),51021));
   return true;
  }catch(Exception unavailable){return false;}
 }
}
