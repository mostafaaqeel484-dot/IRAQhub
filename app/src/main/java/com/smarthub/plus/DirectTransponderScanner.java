package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * On-device manual DVB-S/S2 service search. Tunes an actual transponder, tries
 * the documented app-owned Pvr IPC in whole-transponder mode, then accepts only
 * MPEG-TS services proven by PAT + PMT + SDT (including CRC verification).
 *
 * IMPORTANT: Some OEM Pvr implementations do not permit tp_record=1, or filter
 * out non-selected programmes. In that case this fails visibly; it never
 * pretends that an SID list alone is a discovered playable service.
 */
final class DirectTransponderScanner implements AutoCloseable {
 interface Progress { void report(String status); }
 private static final long MAX_CAPTURE=56L*1024*1024;
 private static final long RESERVED=192L*1024*1024;
 private static final long DEADLINE_MS=32000;
 private static final long[] SNAPSHOTS={8L*1024*1024,24L*1024*1024,48L*1024*1024};
 private final Context context;
 private final String token=UUID.randomUUID().toString();
 private final int captureId=1_101_000_000+ThreadLocalRandom.current().nextInt(900_000);
 private final SharedPreferences prefs;
 private File folder,capture;
 private volatile boolean closed,attempted,owner;

 DirectTransponderScanner(Context context){
  this.context=context.getApplicationContext();
  prefs=this.context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);
 }

 DvbPsiScan.Result scan(ReceiverCatalog.Sat sat,int freq,String pol,int sr,Progress status)throws Exception {
  if(sat==null||freq<100||freq>100000||sr<1||sr>100000
     ||(!"H".equals(pol)&&!"V".equals(pol)))throw new IOException("INVALID_TRANSPONDER");
  if(closed||Thread.currentThread().isInterrupted())throw new IOException("MANUAL_SCAN_CANCELLED");
  status.report("TUNING_TRANSPONDER");
  ReceiverSignalReading lock=DvbTuner.manualTune(context,sat,freq,pol,sr);
  if(closed||Thread.currentThread().isInterrupted())throw new IOException("MANUAL_SCAN_CANCELLED");
  if(lock.locked!=1||lock.tp<0)throw new IOException("MANUAL_TUNE_NO_LOCK");

  File base=context.getExternalFilesDir(null);
  if(base==null||base.getUsableSpace()<RESERVED+MAX_CAPTURE)
   throw new IOException("MANUAL_CAPTURE_STORAGE_REQUIRED");
  folder=new File(base,"manual-tp-"+token);
  if(!folder.mkdirs())throw new IOException("MANUAL_CAPTURE_DIR_FAILED");
  capture=new File(folder,"transponder.ts");
  synchronized(NativeCapture.CONTROL){
   if(closed||Thread.currentThread().isInterrupted())throw new IOException("MANUAL_SCAN_CANCELLED");
   if(!prefs.getString("capture_session","").isEmpty()||
      !prefs.getString("iraqhub_pvr_session","").isEmpty()||
      prefs.getBoolean("dvb_player_visible",false)||
      prefs.getBoolean("software_dvb_422_active",false))
    throw new IOException("CAPTURE_IN_USE");
   if(!NativeCapture.validCaptureId(captureId))throw new IOException("CAPTURE_ID_INVALID");
   if(!prefs.edit().putString("capture_session",token).putInt("capture_manual_id",captureId).commit())
    throw new IOException("CAPTURE_SESSION_NOT_SAVED");
   owner=true;
  }
  status.report("REQUESTING_NATIVE_FULL_TRANSPONDER_TS");
  // No invented PIDs or programme names. 0x1FFF means "not a selected stream".
  // The OEM must explicitly support whole-TP recording for this to work.
  JSONObject request=wholeTransponderRequest(sat,freq,pol,sr,lock.tp,captureId,folder,capture);
  synchronized(NativeCapture.CONTROL){
   if(closed||!owner||!token.equals(prefs.getString("capture_session",""))||
       Thread.currentThread().isInterrupted())throw new IOException("MANUAL_SCAN_CANCELLED");
   attempted=true;
   if(!NativeCapture.record(captureId,request))
    throw new IOException("FIRMWARE_FULL_TS_CAPTURE_REJECTED");
  }

  long started=SystemClock.elapsedRealtime(),nextCheck=started+1200;
  int snapshot=0,stable=0,previousServices=-1;
  DvbPsiScan.Result found=null;
  String lastError="CAPTURE_NO_MPEG_TS";
  while(!closed&&!Thread.currentThread().isInterrupted()&&
      SystemClock.elapsedRealtime()-started<DEADLINE_MS){
   if(SystemClock.elapsedRealtime()>=nextCheck){
    ReceiverSignalReading now=DvbTuner.read();
    if(now.locked!=1||now.tp!=lock.tp)
     throw new IOException("MANUAL_CAPTURE_TUNER_CHANGED");
    nextCheck=SystemClock.elapsedRealtime()+1400;
   }
   long bytes=capture.isFile()?capture.length():0;
   if(bytes>MAX_CAPTURE)break;
   if(folder.getUsableSpace()<RESERVED)
    throw new IOException("MANUAL_CAPTURE_STORAGE_LOW");
   if(snapshot<SNAPSHOTS.length&&bytes>=SNAPSHOTS[snapshot]){
    status.report("PARSING_LIVE_TS_"+(snapshot+1));
    try(InputStream input=new FileInputStream(capture)){
     DvbPsiScan.Result parsed=DvbPsiScan.scan(input);
     if(parsed.services.length()>0){
      found=parsed;
      if(parsed.services.length()==previousServices)stable++;else stable=0;
      previousServices=parsed.services.length();
      status.report("VERIFIED_DVB_SERVICES_"+parsed.services.length());
      if(stable>=1)return found;
     }
    }catch(IOException | IllegalStateException unavailable){
     lastError=unavailable.getMessage()==null?"MANUAL_TS_PSI_UNAVAILABLE":unavailable.getMessage();
    }catch(Exception unavailable){
     lastError=unavailable.getMessage()==null?"MANUAL_TS_PSI_UNAVAILABLE":unavailable.getMessage();
    }
    snapshot++;
   }
   Thread.sleep(170);
  }
  if(closed||Thread.currentThread().isInterrupted())throw new IOException("MANUAL_SCAN_CANCELLED");
  if(found!=null)return found;
  if(!capture.isFile()||capture.length()<188*400)
   throw new IOException("FIRMWARE_CAPTURE_NO_TS_DATA");
  throw new IOException(lastError);
 }

 static JSONObject wholeTransponderRequest(ReceiverCatalog.Sat sat,int freq,String pol,int sr,
     int tp,int id,File root,File output)throws Exception {
  if(sat==null||tp<0||!NativeCapture.validCaptureId(id)||root==null||output==null||
     (!"H".equals(pol)&&!"V".equals(pol)))throw new IOException("INVALID_MANUAL_CAPTURE");
  // No fabricated PIDs or programme names: firmware must accept tp_record=1.
  return new JSONObject()
    .put("iProgId",id).put("iServiceId",0).put("iTunerIndex",0)
    .put("tp_id",tp).put("sat_degree",sat.angle)
    .put("tp_freq",freq).put("tp_pol","H".equals(pol)?0:1)
    .put("tp_symbolrate",sr).put("tp_type",0)
    .put("iVideoPid",8191).put("iPcrPid",8191).put("pmt_pid",8191)
    .put("iAudioNum",0).put("iAudioSelected",0)
    .put("stAudioInfo",new JSONArray()).put("stSubInfo",new JSONArray())
    .put("iSubtitleSelected",-1).put("iTVType",0)
    .put("iTtxPid",8191).put("iCAType",0).put("iVolume",0)
    .put("plpid",0).put("stream_id",0).put("tp_onid",0)
    .put("uiSIDCount",0).put("pucSids","").put("pucPmtPids","")
    .put("path_dir",root.getAbsolutePath())
    .put("path",output.getAbsolutePath())
    .put("bTimeShift",false).put("FsType",0).put("tp_record",1);
 }

 @Override public void close(){
  closed=true;
  synchronized(NativeCapture.CONTROL){
   if(owner&&token.equals(prefs.getString("capture_session",""))){
    if(attempted)NativeCapture.stop(captureId);
    prefs.edit().remove("capture_session").remove("capture_manual_id").commit();
   }
   owner=false;
  }
  File file=capture;
  if(file!=null&&file.isFile())file.delete();
  File dir=folder;
  if(dir!=null&&dir.getName().equals("manual-tp-"+token))dir.delete();
 }
}
