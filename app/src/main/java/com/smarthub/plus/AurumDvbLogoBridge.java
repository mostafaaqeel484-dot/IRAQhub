package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Aurum channel number is not a DVB service ID. Link screenshots only after
 * matching an observed native transponder plus the exact service name to one
 * unique physical service in the app-owned, imported CHL database.
 */
final class AurumDvbLogoBridge {
    private static final String PREFIX="native_aurum_dvb_logo:";
    private static final Pattern TP=Pattern.compile(
        "(?i)(?<![0-9])([0-9]{4,5})(?:\\s*MHz)?\\s*[/|: -]*\\s*([HVLR])\\s*[/|: -]*\\s*([0-9]{4,5})(?![0-9])");

    private AurumDvbLogoBridge(){}

    static final class Transponder {
        final int frequency,symbolRate;
        final String polarity;
        Transponder(int f,String p,int s){frequency=f;polarity=p;symbolRate=s;}
    }

    static Transponder parseTp(String text){
        if(text==null||text.length()>256)return null;
        Matcher m=TP.matcher(text);
        if(!m.find())return null;
        try{
            int f=Integer.parseInt(m.group(1)),sr=Integer.parseInt(m.group(3));
            if(f<1000||f>50000||sr<1000||sr>70000)return null;
            return new Transponder(f,m.group(2).toUpperCase(Locale.ROOT),sr);
        }catch(NumberFormatException invalid){return null;}
    }

    static boolean validSourceFor(String sourceIdentity,String name){
        if(sourceIdentity==null||!sourceIdentity.matches("Aurum:[0-9]{1,8}\\|[\\p{L}\\p{N}]+"))return false;
        int pipe=sourceIdentity.indexOf('|');
        return !name.isEmpty()&&sourceIdentity.substring(pipe+1).equals(ChannelCatalogue.normalized(name));
    }

    /** One owner-verified legacy capture: Sky Sport 4k, Hotbird 13E, TP 11994 V 29900, SID 11628.
     * Supports the screenshot already stored on the receiver, with no re-capture or name-only mapping.
     */
    static String verifiedLegacySource(String physical,String name){
        return "DVB:130:KU:11994:V:29900:11628".equals(physical)
            && "skysport4k".equals(ChannelCatalogue.normalized(name))
            ? "Aurum:1|skysport4k" : "";
    }

    static String sourceFor(Context context,SatelliteState dvb){
        if(dvb==null||dvb.channelId==null||!dvb.channelId.startsWith("DVB:")||dvb.channelName.isEmpty())
            return "";
        String source=context.getSharedPreferences("smarthub",0).getString(PREFIX+dvb.channelId,"");
        if(validSourceFor(source,dvb.channelName))return source;
        return verifiedLegacySource(dvb.channelId,dvb.channelName);
    }

    /** Called only after a verified native channel-logo screenshot is saved. */
    static void learn(Context context,SatelliteState aurum,Map<String,String> visible){
        if(aurum==null||aurum.channelId==null||!aurum.channelId.matches("Aurum:[0-9]{1,8}")
            ||aurum.channelName.isEmpty()||visible==null)return;
        String nativeTp=visible.getOrDefault("mtv_channel_info_tp","");
        if(nativeTp.isEmpty())nativeTp=visible.getOrDefault("channel_info_frequency_tv","");
        Transponder tp=parseTp(nativeTp);
        if(tp==null)return; // Never infer another service from the name alone.
        try{
            String physical=ReceiverCatalog.get(context).uniquePhysicalForObservedLogo(
                aurum.channelName,tp.frequency,tp.polarity,tp.symbolRate);
            if(physical.isEmpty())return; // Ambiguous / absent imported transponder.
            String key=ReceiverInfoRepository.channelKey(aurum);
            if(!validSourceFor(key,aurum.channelName))return;
            SharedPreferences p=context.getSharedPreferences("smarthub",0);
            p.edit().putString(PREFIX+physical,key)
                .putString("native_logo_bridge_status","TP_AND_NAME_VERIFIED")
                .putLong("native_logos_changed_at",System.currentTimeMillis()).apply();
        }catch(RuntimeException unavailable){
            // A missing CHL import must not interfere with native screenshot capture.
        }
    }
}
