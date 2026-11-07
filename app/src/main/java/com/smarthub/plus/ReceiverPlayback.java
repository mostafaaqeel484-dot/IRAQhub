package com.smarthub.plus;
import android.content.*;import android.os.*;import org.json.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.io.*;import java.util.concurrent.*;

/** Independent normal-UID DVB decoder client. No OEM Activities, JNI or database writes. */
final class ReceiverPlayback implements AutoCloseable {
 private static final Object CONTROL=new Object();private static volatile ReceiverPlayback activeOwner;boolean owns(){return activeOwner==this;}
 interface Listener{void accepted(ReceiverCatalog.Channel channel);void error(String code);void audio(int selected);default void pip(boolean accepted){}}
 private final DirectReceiverSignal sharedSignal;private final Context context;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private final LatestRequestQueue<ReceiverCatalog.Channel> zapQueue=new LatestRequestQueue<>();private volatile boolean closed,loading;private volatile Thread worker;private volatile DatagramSocket socket;private volatile ReceiverCatalog.Channel current;private volatile JSONObject request;private volatile int pendingAudio=-1;private volatile int generation;private volatile boolean pipPending,pipActive,pipIssued;private volatile long videoAccepted;private long decoderCheck,epgRefreshAt,zapStartedAt;private int recoveries;private String recoveredPhysical="";private final DvbVideoHealth frameHealth=new DvbVideoHealth();private boolean videoHealthWarned;
 ReceiverPlayback(Context c,Listener l){context=c.getApplicationContext();listener=l;sharedSignal=ReceiverInfoRepository.get(context).acquireSignal();}
 synchronized void play(ReceiverCatalog.Channel channel){if(closed||!zapQueue.offer(channel))return;loading=true;start(channel);}
 private void settled(int token,Runnable notify){main.post(()->{if(closed||token!=generation||!loading)return;ReceiverCatalog.Channel next=zapQueue.complete();loading=false;if(next!=null){loading=true;start(next);}else notify.run();});}
 private void start(ReceiverCatalog.Channel channel){int token=++generation;zapStartedAt=SystemClock.elapsedRealtime();io.execute(()->{try{
  if(channel.ca==-1)throw new IOException("DVB_METADATA_REQUIRED");
  JSONObject body=DvbPlayRequest.create(channel.data,(int)channel.id,channel.sid,channel.angle,channel.frequency,channel.pol,channel.symbolRate,0,volume(),channel.tv==1);FirmwareCaRequest.apply(body,channel.ca,channel.data.optJSONArray("CaSystemIdList"),ReceiverCatalog.get(context).transportServices(channel));worker=Thread.currentThread();synchronized(CONTROL){activeOwner=this;}context.getSharedPreferences("smarthub",0).edit().putBoolean("dvb_tuning",true).putBoolean("dvb_player_visible",false).putString("decoder_channel","").remove("decoder_fps").remove("decoder_bitrate").remove("decoder_video_health").remove("decoder_width").remove("decoder_height").putLong("zap_first_frame_counter_ms",-1).apply();ReceiverInfoRepository.get(context).invalidateSignal();ReceiverSignalReading tuner=DvbTuner.ensure(context,channel);body.put("tp_id",tuner.tp);SharedPreferences p=context.getSharedPreferences("smarthub",0);String active=channel.transponder();
  if(closed||token!=generation)return;if(socket!=null)socket.close();socket=new DatagramSocket();socket.setSoTimeout(400);final DatagramSocket channelSocket=socket;new Thread(()->receive(channelSocket,token),"iraqhub-dvb-decoder").start();
  if(pipActive||pipIssued){sendRaw(8,"null");pipActive=false;pipIssued=false;}int selectedAudio=p.getInt("dvb_audio_index:"+channel.physical(),0);JSONArray available=body.optJSONArray("stAudioInfo");if(available!=null&&selectedAudio>=0&&selectedAudio<available.length())body.put("iAudioSelected",selectedAudio);current=channel;request=body;p.edit().putString("own_dvb_physical_tp",active).putInt("own_dvb_runtime_tp",tuner.tp).putBoolean("own_dvb_ack",false).putLong("own_dvb_channel",channel.id).apply();send(2,body);main.postDelayed(()->{if(!closed&&token==generation&&!context.getSharedPreferences("smarthub",0).getBoolean("own_dvb_ack",false)){settled(token,()->{context.getSharedPreferences("smarthub",0).edit().putBoolean("dvb_tuning",false).apply();listener.error("DVB_PLAY_TIMEOUT");});}},4000);
 }catch(Exception failure){String code=failure.getMessage();settled(token,()->{context.getSharedPreferences("smarthub",0).edit().putBoolean("dvb_tuning",false).apply();listener.error(code==null?"DVB_CONNECTION_FAILED":code);});}});}
 private void send(int command,JSONObject body)throws Exception{sendRaw(command,String.valueOf(body));}
 private void sendRaw(int command,String body)throws Exception{DatagramSocket s=socket;if(s==null||s.isClosed())throw new IOException("DVB_CONNECTION_FAILED");byte[] data=("Play\n"+String.format(java.util.Locale.ROOT,"%04d",command)+"\n"+body).getBytes(StandardCharsets.UTF_8);s.send(new DatagramPacket(data,data.length,InetAddress.getByName("127.0.0.1"),51021));}
 private void receive(DatagramSocket input,int token){while(!closed&&token==generation){try{byte[] b=new byte[8192];DatagramPacket packet=new DatagramPacket(b,b.length);if(input.isClosed())return;input.receive(packet);if(!packet.getAddress().isLoopbackAddress())continue;String[] parts=new String(b,0,packet.getLength(),StandardCharsets.UTF_8).replace("\u0000","").split("\n",3);if(parts.length!=3||!parts[0].equals("Play"))continue;int command=Integer.parseInt(parts[1]);boolean ok=parts[2].startsWith("SUCCESS:");if(command==2){if(!loading)continue;ReceiverCatalog.Channel selected=current;settled(token,()->{if(ok&&selected!=null){videoAccepted=SystemClock.elapsedRealtime();
  context.getSharedPreferences("smarthub",0).edit()
   .putLong("zap_command_ack_ms",Math.max(0,videoAccepted-zapStartedAt))
   .putString("zap_target",selected.physical())
   .putLong("zap_accepted_at",System.currentTimeMillis()).apply();
  frameHealth.reset();videoHealthWarned=false;decoderCheck=0;context.getSharedPreferences("smarthub",0).edit().putString("own_ca_status",selected.ca==0?"FTA":"REQUEST_ACCEPTED").apply();context.getSharedPreferences("smarthub",0).edit().putBoolean("own_dvb_ack",true).apply();listener.accepted(selected);}else{context.getSharedPreferences("smarthub",0).edit().putBoolean("dvb_tuning",false).apply();listener.error("DVB_PLAY_REJECTED");}});}else if(command==7&&pipPending){pipPending=false;pipActive=ok;main.post(()->{if(!closed)listener.pip(ok);});}else if(command==4){int selected=pendingAudio;main.post(()->{if(!closed){if(!ok)listener.error("DVB_AUDIO_REJECTED");}});}else if(command==31||command==126){int begin=parts[2].indexOf('{');if(begin>=0&&current!=null){JSONObject v=new JSONObject(parts[2].substring(begin));if(v.optInt("id",-1)==current.programToken()){int width=v.optInt("width",0),height=v.optInt("height",0);if(width>0&&height>0&&width<=8192&&height<=8192){SharedPreferences.Editor edit=context.getSharedPreferences("smarthub",0).edit().putString("decoder_channel","DVB:"+current.physical()).putInt("decoder_width",width).putInt("decoder_height",height);int fps=v.optInt("framerate",0),bitrate=v.optInt("bitrate",0);if(fps>0&&fps<=240)edit.putInt("decoder_fps",fps);if(bitrate>0&&bitrate<=1000000000)edit.putInt("decoder_bitrate",bitrate);edit.apply();}}}}else if(command==200||command==201)main.post(()->{if(!closed)listener.error("DVB_DECODE_FAILED");});}catch(SocketTimeoutException idle){}catch(Exception failure){if(closed||input.isClosed()||token!=generation)return;}finally{if(!closed&&!loading&&token==generation&&current!=null){
  long now=SystemClock.elapsedRealtime();
  // EPG refresh is not part of the zapping hot path. Cache remains visible.
  if(now-epgRefreshAt>=1500){
   epgRefreshAt=now;
   SatelliteState state=SatelliteState.read(context);
   sharedSignal.refresh(state);
   ReceiverInfoRepository.get(context).refreshEpg(
     ReceiverInfoRepository.get(context).enrich(state));
  }
 }decoderMetadata(token);}}}

 private void decoderMetadata(int token){
  long now=SystemClock.elapsedRealtime();
  if(closed||loading||pipActive||pipPending||token!=generation||now-videoAccepted<2000||now-decoderCheck<1000)return;
  decoderCheck=now;
  ReceiverCatalog.Channel selected=current;
  if(selected==null||selected.tv!=0||request==null)return;
  SatelliteState state=SatelliteState.read(context);
  ReceiverSignalReading signal=DirectReceiverSignal.reading(state);
  if(signal==null||signal.locked!=1||signal.tp!=request.optInt("tp_id",-1)||!state.channelId.equals("DVB:"+selected.physical()))return;
  DecoderDimensions size=DecoderDimensions.read();
  DvbVideoHealth.State health=frameHealth.sample(now,DecoderFrames.read());
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  if(health==DvbVideoHealth.State.NO_TELEMETRY){
   // No sysfs access is not evidence of a failed decoder, bad key or incompatible chroma.
   prefs.edit().putString("decoder_video_health","TELEMETRY_UNAVAILABLE").apply();
  }else if(health==DvbVideoHealth.State.STALLED){
   prefs.edit().putString("decoder_video_health","FRAME_COUNTER_STALLED").apply();
   if(!videoHealthWarned){
    videoHealthWarned=true;
    if(selected.ca!=0)prefs.edit().putString("own_ca_status","VIDEO_UNCONFIRMED").apply();
    String code=selected.ca==0?"DVB_VIDEO_STALLED":"DVB_CA_VIDEO_UNCONFIRMED";
    main.post(()->{if(!closed&&token==generation)listener.error(code);});
    // Only a verified locked FTA service may get ONE automatic decoder
    // restart. Never retry encrypted/CA channels or retune the motor.
    if(selected.ca==0&&recoveries<1&&
       !selected.physical().equals(recoveredPhysical)){
      recoveredPhysical=selected.physical();
      recoveries++;
      prefs.edit().putString("decoder_recovery","FTA_RESTART_SCHEDULED")
        .putInt("decoder_recovery_count",recoveries).apply();
      main.postDelayed(()->{
       if(!closed&&token==generation&&current==selected&&owns()&&!loading){
        prefs.edit().putString("decoder_recovery","FTA_RESTART_REQUESTED").apply();
        play(selected);
       }
      },2500);
    }
   }
  }else if(health==DvbVideoHealth.State.ADVANCING){
   SharedPreferences.Editor flowing=prefs.edit().putString("decoder_video_health",
     size!=null?"FRAMES_ADVANCING":"FRAMES_ADVANCING_SIZE_UNKNOWN");
   if(prefs.getLong("zap_first_frame_counter_ms",-1)<0){
     flowing.putLong("zap_first_frame_counter_ms",
       Math.max(0,now-zapStartedAt));
   }
   flowing.apply();
   if(selected.ca!=0&&size!=null)prefs.edit().putString("own_ca_status","VIDEO_FLOWING").apply();
   if(videoHealthWarned&&size!=null){
    videoHealthWarned=false;
    main.post(()->{if(!closed&&token==generation)listener.accepted(selected);});
   }
  }
  // This is firmware-wide VDEC telemetry, not proof of a visible 4:2:2 picture.
  if(size!=null&&!closed&&!loading&&token==generation&&selected==current)
   prefs.edit().putString("decoder_channel",state.channelId).putInt("decoder_width",size.width).putInt("decoder_height",size.height).apply();
 }

 void audio(int index){if(closed)return;io.execute(()->{try{JSONArray tracks=request==null?null:request.optJSONArray("stAudioInfo");if(tracks==null||index<0||index>=tracks.length())throw new IOException("DVB_AUDIO_UNAVAILABLE");pendingAudio=index;JSONObject selected=new JSONObject(tracks.getJSONObject(index).toString()).put("iVolume",volume());send(4,selected);if(current!=null)context.getSharedPreferences("smarthub",0).edit().putInt("dvb_audio_index:"+current.physical(),index).apply();main.post(()->{if(!closed)listener.audio(index);});}catch(Exception failure){main.post(()->{if(!closed)listener.error("DVB_AUDIO_UNAVAILABLE");});}});}
 private int volume(){SharedPreferences p=context.getSharedPreferences("smarthub",0);return p.getBoolean("dvb_muted",false)?0:Math.max(0,Math.min(100,p.getInt("dvb_volume",20)));}
 void volumeChanged(){if(closed)return;io.execute(()->{try{sendRaw(6,Integer.toString(volume()));}catch(Exception unavailable){main.post(()->{if(!closed)listener.error("DVB_AUDIO_CONTROL_UNAVAILABLE");});}});}
 void nativeAudio(boolean enabled){if(closed)return;io.execute(()->{try{sendRaw(117,enabled?"1":"0");}catch(Exception unavailable){main.post(()->{if(!closed)listener.error("DVB_AUDIO_CONTROL_UNAVAILABLE");});}});}
 void pip(ReceiverCatalog.Channel selected){if(closed||loading)return;io.execute(()->{try{ReceiverCatalog.Channel active=current;if(active==null||selected.ca!=0||!selected.transponder().equals(active.transponder()))throw new IOException("PIP_SAME_TRANSPONDER_REQUIRED");ReceiverSignalReading tuner=DvbTuner.read();if(tuner.locked!=1||tuner.tp!=context.getSharedPreferences("smarthub",0).getInt("own_dvb_runtime_tp",-1))throw new IOException("DVB_NOT_LOCKED");JSONObject body=DvbPlayRequest.create(selected.data,(int)selected.id,selected.sid,selected.angle,selected.frequency,selected.pol,selected.symbolRate,tuner.tp,0);body.put("bPip",1).put("iMode",0);pipPending=true;pipIssued=true;send(7,body);main.postDelayed(()->{if(!closed&&pipPending){pipPending=false;listener.error("PIP_RESPONSE_UNAVAILABLE");}},3500);}catch(Exception unavailable){main.post(()->{if(!closed)listener.error(unavailable.getMessage()==null?"PIP_UNAVAILABLE":unavailable.getMessage());});}});}
 void stopPip(){if(closed)return;io.execute(()->{try{if(owns())sendRaw(8,"null");pipActive=false;pipPending=false;pipIssued=false;}catch(Exception ignored){}});}
 void positionPip(int direction){if(closed||direction<0||direction>5)return;io.execute(()->{try{if(pipActive)sendRaw(10,Integer.toString(direction));}catch(Exception ignored){}});}
 @Override public void close(){if(closed)return;closed=true;zapQueue.clear();ReceiverInfoRepository.get(context).releaseSignal(sharedSignal);generation++;main.removeCallbacksAndMessages(null);Thread active=worker;if(active!=null)active.interrupt();new Thread(()->{try{ReceiverSignalReading frame=null;try{frame=DvbTuner.read();}catch(Exception unavailable){}synchronized(CONTROL){if(activeOwner==this){if(current!=null&&socket!=null&&request!=null&&frame!=null&&frame.tp==request.optInt("tp_id",-1)){if(pipActive||pipIssued)sendRaw(8,"null");send(3,new JSONObject().put("iTunerIndex",0));}activeOwner=null;context.getSharedPreferences("smarthub",0).edit().putBoolean("dvb_tuning",false).apply();}}}catch(Exception ignored){}finally{if(socket!=null)socket.close();}},"iraqhub-decoder-stop").start();io.shutdownNow();}
}
