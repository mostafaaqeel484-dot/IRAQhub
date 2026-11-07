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
  StringBuilder b=new StringBuilder("IRAQhub 3.7.3 · receiver diagnostics (app-owned)\n");
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
  b.append("\n[Fast zap & recovery]\n");
  b.append("zap_target=").append(safe(p.getString("zap_target","NOT_TESTED"))).append('\n');
  b.append("zap_command_ack_ms=").append(p.getLong("zap_command_ack_ms",-1))
   .append(" (OEM accepted, not first picture)\n");
  b.append("zap_first_frame_counter_ms=").append(p.getLong("zap_first_frame_counter_ms",-1))
   .append(" (firmware counter, not visual confirmation)\n");
  b.append("decoder_recovery=").append(safe(p.getString("decoder_recovery","NONE")))
   .append(" count=").append(p.getInt("decoder_recovery_count",0)).append('\n');
  b.append("selected_422_engine=").append(safe(p.getString("decoder_422_engine","vlc")))
   .append(" mpv_load_latency_ms=").append(p.getLong("mpv_load_latency_ms",-1)).append('\n');
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
