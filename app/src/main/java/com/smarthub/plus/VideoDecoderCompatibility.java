package com.smarthub.plus;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import java.lang.reflect.Field;
import java.util.*;

/** Reports only 4:2:2 profiles explicitly exposed by installed hardware decoders. */
final class VideoDecoderCompatibility {
 static final class Result {
  final List<String> hardware422=new ArrayList<>();
  final List<String> software422=new ArrayList<>();
  final List<String> videoDecoders=new ArrayList<>();
  boolean hardwareScanAvailable;
  String summary(){
   if(!hardwareScanAvailable)return "Hardware codec capability scan unavailable";
   if(!hardware422.isEmpty())return "Hardware 4:2:2 profile(s): "+String.join(", ",hardware422);
   return "No hardware 4:2:2 profile explicitly advertised; 4:2:2 playback is not guaranteed";
  }
 }
 private VideoDecoderCompatibility(){}
 static Result inspect(){
  Result result=new Result();
  try{
   MediaCodecInfo[] codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
   result.hardwareScanAvailable=true;
   for(MediaCodecInfo codec:codecs){
    if(codec.isEncoder())continue;
    boolean hardware=isHardware(codec);
    for(String mime:codec.getSupportedTypes()){
     if(!mime.startsWith("video/"))continue;
     if(result.videoDecoders.size()<32)result.videoDecoders.add(codec.getName()+" · "+mime+(hardware?" · HW":" · SW/unknown"));
     if(!mime.equalsIgnoreCase("video/hevc")&&!mime.equalsIgnoreCase("video/avc"))continue;
     MediaCodecInfo.CodecCapabilities caps;
     try{caps=codec.getCapabilitiesForType(mime);}catch(Exception ignored){continue;}
     for(MediaCodecInfo.CodecProfileLevel pl:caps.profileLevels){
      String profile=profileName(pl.profile);
      if(profile.isEmpty())continue;
      String entry=codec.getName()+" · "+mime+" · "+profile;
      List<String> target=hardware?result.hardware422:result.software422;
      if(!target.contains(entry))target.add(entry);
     }
    }
   }
  }catch(Throwable unavailable){result.hardwareScanAvailable=false;}
  return result;
 }
 static String decoderKind(String name){
  if(name==null||name.trim().isEmpty())return "UNKNOWN";
  String value=name.toLowerCase(Locale.ROOT);
  if(value.contains("google")||value.contains("android")||value.contains("software")||value.contains("ffmpeg")||value.contains("dav1d")||value.contains("sw.")||value.endsWith(".sw"))return "SOFTWARE";
  return "HARDWARE_OR_VENDOR";
 }
 private static boolean isHardware(MediaCodecInfo codec){
  if(Build.VERSION.SDK_INT>=29)return codec.isHardwareAccelerated()&&!codec.isSoftwareOnly();
  String name=codec.getName().toLowerCase(Locale.ROOT);
  return !(name.contains("google")||name.contains("android")||name.contains("software")||name.contains("ffmpeg")||name.contains("dav1d"));
 }
 private static String profileName(int value){
  String[] names={"HEVCMain422_10","HEVCMain422_12","HEVCProfileMain422_10","HEVCProfileMain422_12","AVCProfileHigh422"};
  for(String name:names)try{
   Field field=MediaCodecInfo.CodecProfileLevel.class.getField(name);
   if(field.getInt(null)==value)return name;
  }catch(Exception ignored){}
  return "";
 }
 /**
  * An AVC profile_idc of 122 identifies High 4:2:2. A HEVC general_profile_idc
  * of 4/5 means Range Extensions / High Throughput; it DOES NOT identify the
  * chroma format. Never report a 4:2:2 stream from HEVC codec strings alone.
  */
 static boolean isKnown422CodecString(String codec){
  if(codec==null)return false;
  String value=codec.trim().toLowerCase(Locale.ROOT);
  for(String item:value.split(",")){
   String part=item.trim();
   if(!part.startsWith("avc1.")&&!part.startsWith("avc3."))continue;
   String suffix=part.substring(part.indexOf('.')+1);
   if(suffix.length()>=6&&suffix.substring(0,2).matches("[0-9a-f]{2}"))
    try {if(Integer.parseInt(suffix.substring(0,2),16)==122)return true;}catch(NumberFormatException ignored){}
  }
  return false;
 }
 static boolean isPossible422CodecString(String codec){
  if(isKnown422CodecString(codec))return true;
  if(codec==null)return false;
  for(String item:codec.toLowerCase(Locale.ROOT).split(",")){
   String part=item.trim();
   if(!part.startsWith("hvc1.")&&!part.startsWith("hev1."))continue;
   String[] fields=part.split("\\.");
   if(fields.length<2)continue;
   // Both idc 4 (RExt) and 5 (High Throughput) can contain non-4:2:0 variants.
   String token=fields[1].replaceFirst("^[a-c]", "");
   if(token.equals("4")||token.equals("5"))return true;
  }
  return false;
 }

}
