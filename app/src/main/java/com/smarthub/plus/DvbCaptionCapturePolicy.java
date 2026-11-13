package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;

/** Consent-only OCR of visible non-protected DVB captions.
 * A full-screen projection MUST NOT process other applications or CA broadcasts. */
final class DvbCaptionCapturePolicy {
 private DvbCaptionCapturePolicy(){}

 static boolean allowed(String activeChannel,String physical,String ownPhysical,
                        boolean foreground,boolean freeToAir,boolean optedIn,
                        boolean tuning,boolean nativeBanner){
  return optedIn&&foreground&&freeToAir&&!tuning&&!nativeBanner&&
    activeChannel!=null&&activeChannel.startsWith("DVB:")&&
    physical!=null&&physical.equals(ownPhysical)&&
    activeChannel.equals("DVB:"+physical);
 }
 static boolean safe(Context app){
  SharedPreferences p=app.getSharedPreferences("smarthub",0);
  SatelliteState state=SatelliteState.read(app);
  return allowed(state.channelId,p.getString("own_dvb_physical",""),
    p.getString("own_dvb_physical",""),
    p.getBoolean("dvb_player_visible",false),
    p.getBoolean("own_dvb_fta",false),
    p.getBoolean("native_caption_ocr",false),
    p.getBoolean("dvb_tuning",true),
    p.getBoolean("satellite_native_banner_visible",false));
 }
}
