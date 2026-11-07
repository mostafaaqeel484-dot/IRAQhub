package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Media3 codec selection: the platform remains authoritative by default.
 * A hardware 4:2:2 decoder may be promoted only after an explicit real-world
 * successful device test. HEVC Main10 alone is NOT proof of 4:2:2 support.
 * Media3 decoder fallback is for init failures, not incorrect decoded pixels.
 */
@androidx.media3.common.util.UnstableApi
final class X5MDecoderSelector {
 private X5MDecoderSelector(){}

 static MediaCodecSelector forDevice(Context context){
  Context app=context.getApplicationContext();
  return (mime,requiresSecure,requiresTunneling)->{
   List<MediaCodecInfo> normal=
    MediaCodecSelector.DEFAULT.getDecoderInfos(mime,requiresSecure,requiresTunneling);
   if(!"video/hevc".equalsIgnoreCase(mime)&&
      !"video/avc".equalsIgnoreCase(mime))return normal;
   SharedPreferences prefs=app.getSharedPreferences("smarthub",0);
   String approved=prefs.getString("verified_422_decoder_name","");
   String approvedMime=prefs.getString("verified_422_decoder_mime","");
   if(!mime.equalsIgnoreCase(approvedMime)||approved.isEmpty())return normal;
   List<MediaCodecInfo> ordered=new ArrayList<>(normal);
   Collections.sort(ordered,(a,b)->Boolean.compare(
     !approved.equals(a.name),!approved.equals(b.name)));
   return ordered;
  };
 }
 /** Pure guard for tests; no codec is ever chosen from a guessed vendor name. */
 static boolean permitted(String candidate,String mime,String provenName,String provenMime){
  return candidate!=null&&!candidate.isEmpty()
   &&("video/avc".equalsIgnoreCase(mime)||"video/hevc".equalsIgnoreCase(mime))
   &&candidate.equals(provenName)&&mime.equalsIgnoreCase(provenMime);
 }
}
