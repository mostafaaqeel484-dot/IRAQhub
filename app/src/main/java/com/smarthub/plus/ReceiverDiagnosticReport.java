package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Privacy-conscious X5M receiver diagnostic snapshot.
 * No BISS keys, subscriber identifiers, serials, cookies, network addresses
 * or exported vendor database content. Device playback must be verified separately.
 */
final class ReceiverDiagnosticReport {
 private ReceiverDiagnosticReport(){}
 private static void line(StringBuilder b,String label,String value){
  b.append(label).append(": ").append(safe(value)).append('\n');
 }
 private static String safe(String value){
  if(value==null)return "—";
  String clean=value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]"," ").trim();
  if(clean.length()>180)clean=clean.substring(0,180)+"…";
  return clean.isEmpty()?"—":clean;
 }
 static String capture(Context context){
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  StringBuilder b=new StringBuilder("IRAQhub 3.7.6 · receiver diagnostics (app-owned)\n");
  b.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT)
    .format(new Date())).append("\n");
  line(b,"Android",Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT);
  line(b,"Hardware",Build.HARDWARE+" · "+Build.MODEL);
  b.append("\n[Manual TP search]\n");
  line(b,"status",p.getString("manual_direct_scan_status","NOT_TESTED"));
  line(b,"last_error",p.getString("manual_direct_scan_last_error","NONE"));
  line(b,"stage",p.getString("manual_capture_stage","NOT_STARTED"));
  b.append("services=").append(p.getInt("manual_direct_scan_count",0))
   .append(" incomplete=").append(p.getInt("manual_direct_scan_missing",0)).append('\n');
  b.append("capture_bytes=").append(p.getLong("manual_capture_bytes",0))
   .append(" psi_snapshots=").append(p.getInt("manual_capture_snapshots",0)).append('\n');
  line(b,"native_search_route",p.getString("native_search_launch_status","NOT_TESTED"));
  b.append("\n[4:2:2 playback]\n");
  line(b,"profile",Software422Profile.current(context));
  line(b,"preferred_engine",p.getString("software_422_preferred_engine","vlc"));
  line(b,"mpv_last_fallback",p.getString("mpv_last_fallback","NONE"));
  line(b,"runtime",p.getString("software_422_runtime","NOT_TESTED"));
  line(b,"video_health",p.getString("decoder_video_health","NOT_MEASURED"));
  line(b,"decoder",p.getString("decoder_name","UNAVAILABLE"));
  line(b,"decoder_kind",p.getString("decoder_kind","UNKNOWN"));
  line(b,"codec_mime",p.getString("decoder_input_mime","UNKNOWN"));
  line(b,"422_detection",p.getString("decoder_422_verification","NOT_TESTED"));
  b.append("first_frame=")
   .append(p.getLong("decoder_first_frame_at",0)>0?"REPORTED":"NOT_REPORTED")
   .append(" dropped=").append(p.getLong("decoder_dropped_frames",0))
   .append(" processed=").append(p.getLong("decoder_processed_frames",0)).append('\n');
  b.append("TS bitrate=").append(p.getLong("software_ts_bitrate",0)).append(" bits/s\n");
  b.append("TS capture bytes=").append(p.getLong("software_ts_capture_bytes",0))
   .append(" error=").append(safe(p.getString("software_ts_capture_error","NONE"))).append('\n');
  b.append("LibVLC video outputs=").append(p.getInt("libvlc_vout_count",0))
   .append(" first_vout_latency_ms=").append(p.getLong("libvlc_vout_latency_ms",0))
   .append('\n');
  b.append("Note: OEM advancing frames are NOT LibVLC video outputs.\n");
  line(b,"last_decoder_error",p.getString("decoder_last_error","NONE"));
  b.append("\n[Channel zapping and recovery]\n");
  line(b,"stage",p.getString("zap_stage","NOT_TESTED"));
  b.append("same_transponder=").append(p.getBoolean("zap_same_tp",false))
   .append(" lock_ms=").append(p.getLong("zap_tune_lock_ms",-1))
   .append(" decoder_ack_ms=").append(p.getLong("zap_decoder_ack_ms",-1))
   .append(" firmware_frames_ms=").append(p.getLong("zap_firmware_frame_advance_ms",-1))
   .append(" attempts=").append(p.getInt("zap_recovery_attempts",0)).append('\n');
  line(b,"recovery_reason",p.getString("zap_recovery_reason","NONE"));
  line(b,"manual_capture_failure_analysis",
    "zero capture_bytes means no MPEG-TS delivered, PSI parsing cannot discover services");
  b.append("\n[EPG, posters and logos]\n");
  line(b,"XMLTV",p.getString("iraqhub_unified_sync_state","NOT_TESTED"));
  line(b,"Poster",p.getString("poster_sync_status","NOT_TESTED"));
  line(b,"Native EIT",p.getString("native_epg_sync_status","NOT_TESTED"));
  line(b,"CHMAX",p.getString("chmax_observe_status","NOT_TESTED"));
  line(b,"Logo bridge",p.getString("native_logo_bridge_status","NOT_TESTED"));
  b.append("Sports poster cached=").append(p.getInt("sports_poster_prefetch_verified",0))
   .append(" attempts=").append(p.getInt("sports_poster_prefetch_attempted",0)).append('\n');
  b.append("\n[Channel database]\n");
  line(b,"format",p.getString("receiver_catalog_format","UNKNOWN"));
  b.append("previous_import_count=").append(p.getInt("receiver_catalog_count",0))
   .append(" merge_new=").append(p.getInt("receiver_catalog_merge_added",0))
   .append(" merge_duplicates=").append(p.getInt("receiver_catalog_merge_duplicates",0)).append('\n');
  b.append("\n[Unverified hardware interfaces]\n");
  b.append("Firmware whole-TP capture: may be unavailable to third-party apps.\n");
  b.append("Front panel display: OEM interface not proven; no sysfs writes attempted.\n");
  b.append("First-frame and codec initialization do not establish visual 4:2:2 fidelity.\n");
  return b.toString();
 }
}
