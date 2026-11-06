package com.smarthub.plus;
import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;

/** App-owned tune operation, bounded lock verification and no cached-name inference. */
final class DvbTuner {
 private static String exchange(int command,JSONObject body,long timeout)throws Exception{
  try(DatagramSocket socket=new DatagramSocket()){socket.setSoTimeout(200);byte[] q=("Tuner\n"+String.format(java.util.Locale.ROOT,"%04d",command)+"\n"+body).getBytes(StandardCharsets.UTF_8);socket.send(new DatagramPacket(q,q.length,InetAddress.getByName("127.0.0.1"),51021));long end=SystemClock.elapsedRealtime()+timeout;String prefix="Tuner\n"+String.format(java.util.Locale.ROOT,"%04d",command)+"\n";while(SystemClock.elapsedRealtime()<end&&!Thread.currentThread().isInterrupted()){byte[] bytes=new byte[8192];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);try{socket.receive(packet);}catch(SocketTimeoutException idle){continue;}if(!packet.getAddress().isLoopbackAddress())continue;String text=new String(bytes,0,packet.getLength(),StandardCharsets.UTF_8).replace("\u0000","");if(text.startsWith(prefix))return text;}throw new IOException("DVB_TUNER_TIMEOUT");}
 }
 /** Emergency motor stop is a tuner command documented by IRAQhub 3.7.0 DEX. */
 static boolean stopMotor(){
  try{
   JSONObject packet=new JSONObject().put("iTunerIndex",0)
     .put("eCMD",0).put("iParabyte",0).put("usals",1);
   String reply=exchange(4,packet,1000);
   return reply.contains("\nSUCCESS:");
  }catch(Exception ignored){return false;}
 }
 static boolean usalsEnabled(Context context,int orbitalAngle,String band){
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  return p.getBoolean("own_usals_enabled:"+orbitalAngle+":"+band,
         p.getBoolean("own_usals_default:"+band,false));
 }
 static ReceiverSignalReading read()throws Exception{ReceiverSignalReading frame=ReceiverSignalReading.packet(exchange(3,new JSONObject().put("iTunerIndex",0),1500),0);if(frame==null)throw new IOException("DVB_TUNER_UNAVAILABLE");return frame;}
 /**
  * Manual transponder tune + real signal lock (not a channel/service scanner).
  * Uses the same original 3.7.0 Tuner 0002/0003 IPC as normal playback.
  */
 static ReceiverSignalReading manualTune(Context context,ReceiverCatalog.Sat satellite,
      int frequency,String polarity,int symbolRate)throws Exception{
  if(satellite==null||(!"H".equals(polarity)&&!"V".equals(polarity)))
    throw new IOException("INVALID_TRANSPONDER");
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  if(prefs.getBoolean("dvb_player_visible",false)||prefs.getBoolean("software_dvb_422_active",false)
     ||!prefs.getString("iraqhub_pvr_session","").isEmpty()
     ||!prefs.getString("capture_session","").isEmpty())
    throw new IOException("STOP_VIDEO_BEFORE_MANUAL_TUNE");
  LnbProfile profile=LnbProfile.forSatellite(context,satellite.angle,satellite.band);
  long hash=frequency*37L+symbolRate*11L+("H".equals(polarity)?1:2);
  int index=(int)(Math.abs(hash)%1000000L);
  JSONObject command=profile.tune(index,frequency,polarity,symbolRate);
  boolean motor=usalsEnabled(context,satellite.angle,satellite.band);
  if(motor){
   String rawLocation=prefs.getString("own_usals_location","");
   if(rawLocation.isEmpty())throw new IOException("USALS_LOCATION_REQUIRED");
   UsalsLocation.parse(rawLocation).apply(command,satellite.angle,false);
  }
  final int expected=command.getInt("iTpId");
  prefs.edit().putBoolean("dvb_tuning",true).apply();
  try{
   String reply=exchange(2,command,2000);
   if(!reply.contains("\nSUCCESS:"))throw new IOException("MANUAL_TUNE_REJECTED");
   int consecutive=0;
   long deadline=SystemClock.elapsedRealtime()+(motor?120000L:7000L);
   while(SystemClock.elapsedRealtime()<deadline&&!Thread.currentThread().isInterrupted()){
    ReceiverSignalReading result=read();
    if(result.locked==1&&result.tp==expected){
     if(++consecutive>=2)return result;
    }else consecutive=0;
    Thread.sleep(320);
   }
   throw new IOException("MANUAL_TUNE_NO_LOCK");
  }finally{prefs.edit().putBoolean("dvb_tuning",false).apply();}
 }
 static ReceiverSignalReading ensure(Context context,ReceiverCatalog.Channel channel)throws Exception{
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);int lease=prefs.getInt("verified_native_tuner_tp",-1);if(lease>=0&&channel.transponder().equals(prefs.getString("verified_native_tuner_rf",""))){ReceiverSignalReading frame=read();if(frame.locked==1&&frame.tp==lease){prefs.edit().putInt("own_dvb_mapping_version",3).putInt("own_dvb_runtime_tp",lease).putString("own_dvb_physical_tp",channel.transponder()).apply();return frame;}prefs.edit().remove("verified_native_tuner_tp").remove("verified_native_tuner_rf").apply();}LnbProfile profile=LnbProfile.forSatellite(context,channel.angle,channel.band);
  boolean motor=usalsEnabled(context,channel.angle,channel.band);
  String rawLocation=prefs.getString("own_usals_location","");
  if(motor&&rawLocation.isEmpty())throw new IOException("USALS_LOCATION_REQUIRED");
  // A changed dish location must invalidate the cached tune/lock.
  String tag=profile.json()+(motor?"|"+rawLocation:"");
  ReceiverSignalReading before=read();boolean trusted=prefs.getInt("own_dvb_mapping_version",0)==3&&before.tp>=1200000000&&before.locked==1&&before.tp==prefs.getInt("own_dvb_runtime_tp",-1)&&tag.equals(prefs.getString("own_dvb_profile_tag",""))&&channel.transponder().equals(prefs.getString("own_dvb_physical_tp",""));if(trusted)return before;
  JSONObject command=profile.tune(channel.tpId,channel.frequency,channel.pol,channel.symbolRate);
  if(motor){
   UsalsLocation location=UsalsLocation.parse(rawLocation);
   location.apply(command,channel.angle,false);
   prefs.edit().putString("motor_status","POSITION_REQUESTED").apply();
  }
  int requested=command.getInt("iTpId");prefs.edit().putBoolean("dvb_tuning",true).apply();ReceiverInfoRepository.get(context).invalidateSignal();String reply="";try{reply=exchange(2,command,1500);}catch(IOException timeout){if(!"DVB_TUNER_TIMEOUT".equals(timeout.getMessage()))throw timeout;}if(!reply.isEmpty()&&!reply.contains("\nSUCCESS:"))throw new IOException("DVB_TUNE_REJECTED");
  long deadline=SystemClock.elapsedRealtime()+(motor?120000L:6500L);int stable=0;ReceiverSignalReading frame=null;Thread.sleep(350);while(SystemClock.elapsedRealtime()<deadline&&!Thread.currentThread().isInterrupted()){try{frame=read();if(frame.tp==requested&&frame.locked==1){if(++stable>=2){prefs.edit().putInt("own_dvb_mapping_version",3).putInt("own_dvb_runtime_tp",requested)
      .putString("own_dvb_profile_tag",tag).putString("own_dvb_physical_tp",channel.transponder())
      .putString("motor_status",motor?"SIGNAL_LOCKED":"DISABLED").apply();return frame;}}else stable=0;}catch(IOException unavailable){stable=0;}Thread.sleep(250);}if(motor)prefs.edit().putString("motor_status","NO_SIGNAL_AFTER_POSITION_REQUEST").apply();throw new IOException("DVB_NOT_LOCKED");
 }
}
