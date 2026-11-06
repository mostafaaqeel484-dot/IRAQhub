package com.smarthub.plus;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded MPEG-TS PSI discovery for genuine DVB transport-stream recordings.
 * Reads PAT (0x00), PMT (0x02), and actual-network SDT (0x42), with MPEG CRC32.
 * Does not query, modify or depend on the firmware's protected service database.
 * This discovers services only present in the supplied TS file; it is NOT blind scan.
 */
final class DvbPsiScan {
 private static final int PACKET=188, LIMIT=64*1024*1024, MAX_SERVICES=512;
 private final Map<Integer,Integer> programmes=new LinkedHashMap<>();
 private final Map<Integer,Service> named=new HashMap<>();
 private final Map<Integer,Service> tracks=new HashMap<>();
 private final Map<Integer,SectionBuffer> buffers=new HashMap<>();
 private int packets,validPackets,validSections,tsId=-1,networkId=-1;
 private boolean sawPat,sawSdt;

 static final class Service {
  final int sid;
  String name="",provider="",videoType="";
  int videoPid=-1,pcrPid=-1,pmtPid=-1,ca=0,serviceType=-1;
  final JSONArray audio=new JSONArray(),caIds=new JSONArray();
  Service(int sid){this.sid=sid;}
 }
 static final class Result {
  final int packets,sections,transportId,networkId,withoutNames;
  final JSONArray services;
  Result(int packets,int sections,int tsId,int networkId,int withoutNames,JSONArray services){
   this.packets=packets;this.sections=sections;this.transportId=tsId;this.networkId=networkId;
   this.withoutNames=withoutNames;this.services=services;
  }
  JSONObject json()throws Exception{
   return new JSONObject().put("packets",packets).put("sections",sections)
    .put("tsid",transportId).put("onid",networkId).put("unnamed",withoutNames)
    .put("services",services);
  }
 }
 private interface Ready{void onSection(byte[] data);}
 private static final class SectionBuffer {
  byte[] stored=new byte[4096];
  int used,needed=-1,lastContinuity=-1;
  void reset(){used=0;needed=-1;}
  void consume(byte[] packet,int offset,int length,boolean start,int continuity,Ready ready){
   if(lastContinuity>=0&&((lastContinuity+1)&15)!=continuity)reset();
   lastContinuity=continuity;
   if(start){
    if(length==0){reset();return;}
    int pointer=packet[offset]&255;
    if(pointer>=length){reset();return;}
    if(used>0&&pointer>0)append(packet,offset+1,pointer,ready);
    // The first payload unit following the pointer starts a new section.
    reset();
    int startAt=offset+1+pointer;
    append(packet,startAt,length-1-pointer,ready);
   }else if(used>0)append(packet,offset,length,ready);
  }
  private void append(byte[] packet,int offset,int length,Ready ready){
   while(length>0){
    if(used==0&&(packet[offset]&255)==0xff)return;
    int target=needed<0?3:needed;
    int count=Math.min(length,target-used);
    if(count<=0){reset();return;}
    System.arraycopy(packet,offset,stored,used,count);
    offset+=count;length-=count;used+=count;
    if(needed<0&&used==3){
     int sectionLength=((stored[1]&15)<<8)|(stored[2]&255);
     if(sectionLength<4||sectionLength>4093){reset();return;}
     needed=sectionLength+3;
    }
    if(needed>0&&used==needed){
     ready.onSection(Arrays.copyOf(stored,used));
     reset();
    }
   }
  }
 }
 private static int u16(byte[] data,int at){return ((data[at]&255)<<8)|(data[at+1]&255);}
 private static int pid(byte[] data,int at){return ((data[at]&31)<<8)|(data[at+1]&255);}
 private static boolean crc(byte[] data){
  int crc=0xffffffff;
  for(byte b:data)for(int bit=7;bit>=0;bit--){
   int top=(crc>>>31)&1;
   crc<<=1;
   if((top^((b>>>bit)&1))!=0)crc^=0x04c11db7;
  }
  return crc==0;
 }
 static Result scan(InputStream input)throws Exception{
  if(input==null)throw new IOException("TS_INPUT_MISSING");
  DvbPsiScan parser=new DvbPsiScan();
  BufferedInputStream in=new BufferedInputStream(input,64*1024);
  byte[] packet=new byte[PACKET];
  int readBytes=0;
  while(readBytes+PACKET<=LIMIT&&!Thread.currentThread().isInterrupted()){
   int used=0;
   while(used<PACKET){
    int n=in.read(packet,used,PACKET-used);
    if(n<0)break;
    if(n==0)continue;
    used+=n;
   }
   if(used==0)break;
   if(used!=PACKET)break;
   readBytes+=PACKET;parser.packets++;
   if((packet[0]&255)!=0x47){
    if(parser.packets<=4)throw new IOException("TS_SYNC_MISSING");
    if(parser.packets-parser.validPackets>4)throw new IOException("TS_SYNC_LOST");
    continue;
   }
   parser.validPackets++;
   if((packet[1]&0x80)!=0)continue;
   int id=((packet[1]&31)<<8)|(packet[2]&255);
   if(id!=0&&id!=0x11&&!parser.programmes.containsValue(id))continue;
   if((packet[3]&0xc0)!=0)continue;
   int afc=(packet[3]>>>4)&3;
   if((afc&1)==0)continue;
   int off=4;
   if((afc&2)!=0){off+=1+(packet[4]&255);if(off>=PACKET)continue;}
   final int finalPid=id;
   parser.buffers.computeIfAbsent(id,k->new SectionBuffer()).consume(
    packet,off,PACKET-off,(packet[1]&0x40)!=0,packet[3]&15,
    section->parser.section(finalPid,section));
  }
  if(Thread.currentThread().isInterrupted())throw new IOException("TS_SCAN_CANCELLED");
  if(!parser.sawPat)throw new IOException("TS_PAT_NOT_FOUND");
  if(!parser.sawSdt)throw new IOException("TS_SDT_NOT_FOUND");
  JSONArray rows=new JSONArray();
  int unnamed=0;
  for(Map.Entry<Integer,Integer> p:parser.programmes.entrySet()){
   Service name=parser.named.get(p.getKey()),data=parser.tracks.get(p.getKey());
   if(name==null||name.name.isEmpty()||data==null){unnamed++;continue;}
   if(data.videoPid<1&&data.audio.length()==0)continue;
   JSONObject result=new JSONObject()
     .put("sid",p.getKey()).put("name",name.name).put("provider",name.provider)
     .put("pmt",p.getValue()).put("pcr",data.pcrPid)
     .put("videoPid",data.videoPid).put("videoType",data.videoType)
     .put("audio",data.audio).put("ca",name.ca!=0||data.ca!=0?1:0)
     .put("caIds",data.caIds).put("tv",data.videoPid>0?0:1)
     .put("tsid",parser.tsId).put("onid",parser.networkId);
   if(rows.length()>=MAX_SERVICES)break;
   rows.put(result);
  }
  if(rows.length()==0)throw new IOException("TS_NO_COMPLETE_NAMED_SERVICES");
  return new Result(parser.validPackets,parser.validSections,
    parser.tsId,parser.networkId,unnamed,rows);
 }
 private void section(int id,byte[] data){
  if(data.length<12||!crc(data))return;
  validSections++;
  int type=data[0]&255;
  if(id==0&&type==0x00)pat(data);
  else if(id==0x11&&type==0x42)sdt(data);
  else if(type==0x02)pmt(data);
 }
 private void pat(byte[] data){
  sawPat=true;tsId=u16(data,3);
  for(int i=8;i+4<=data.length-4&&programmes.size()<MAX_SERVICES;i+=4){
   int sid=u16(data,i),pmt=pid(data,i+2);
   if(sid>0&&pmt>0&&pmt<0x1fff)programmes.put(sid,pmt);
  }
 }
 private void sdt(byte[] data){
  sawSdt=true;
  if(data.length<15)return;
  if(tsId>=0&&u16(data,3)!=tsId)return;
  networkId=u16(data,8);
  int index=11;
  while(index+5<=data.length-4&&named.size()<MAX_SERVICES){
   int sid=u16(data,index);
   int length=((data[index+3]&15)<<8)|(data[index+4]&255);
   int end=index+5+length;
   if(end>data.length-4)break;
   Service service=named.computeIfAbsent(sid,Service::new);
   service.ca=(data[index+3]&0x10)!=0?1:0;
   for(int p=index+5;p+2<=end;){
    int tag=data[p]&255,size=data[p+1]&255,next=p+2+size;
    if(next>end)break;
    if(tag==0x48&&size>=3){
     int at=p+2;
     int type=data[at++]&255,providerLength=data[at++]&255;
     if(at+providerLength<next){
      String provider=decode(data,at,providerLength);at+=providerLength;
      int nameLength=data[at++]&255;
      if(at+nameLength<=next){
       String name=decode(data,at,nameLength);
       if(!name.isEmpty()){
        service.name=name;service.provider=provider;service.serviceType=type;
       }
      }
     }
    }
    p=next;
   }
   index=end;
  }
 }
 private void pmt(byte[] data){
  int sid=u16(data,3),expected=programmes.getOrDefault(sid,-1);
  if(expected<0)return;
  if(data.length<16)return;
  Service service=new Service(sid);
  service.pmtPid=expected;service.pcrPid=pid(data,8);
  int info=((data[10]&15)<<8)|(data[11]&255),offset=12+info;
  if(offset>data.length-4)return;
  caDescriptors(data,12,offset,service);
  while(offset+5<=data.length-4){
   int streamType=data[offset]&255,elementaryPid=pid(data,offset+1);
   int size=((data[offset+3]&15)<<8)|(data[offset+4]&255);
   int end=offset+5+size;
   if(end>data.length-4)break;
   caDescriptors(data,offset+5,end,service);
   String video=videoType(streamType);
   String audio=audioType(streamType,data,offset+5,end);
   if(!video.isEmpty()&&service.videoPid<0){service.videoPid=elementaryPid;service.videoType=video;}
   else if(!audio.isEmpty()&&service.audio.length()<12){
    try{service.audio.put(new JSONObject().put("PID",elementaryPid).put("Type",audio)
     .put("Lang",language(data,offset+5,end)));}catch(Exception invalid){/* malformed descriptor */}
   }
   offset=end;
  }
  if(service.pcrPid>0&&service.pcrPid<8191)tracks.put(sid,service);
 }
 private static String videoType(int id){
  if(id==1)return "MPEG1";
  if(id==2)return "MPEG2";
  if(id==27)return "H264";
  if(id==36)return "H265";
  return "";
 }
 private static String audioType(int id,byte[] bytes,int start,int end){
  switch(id){
   case 3:return "MPEG1";case 4:return "MPEG2";
   case 15:return "AAC";case 17:return "LATM";
   case 6:
    for(int i=start;i+2<=end;){
     int tag=bytes[i]&255,length=bytes[i+1]&255,next=i+2+length;
     if(next>end)break;
     if(tag==0x6a)return "AC3";
     if(tag==0x7a)return "EAC3";
     i=next;
    }
  }
  return "";
 }
 private static void caDescriptors(byte[] d,int begin,int end,Service s){
  for(int i=begin;i+2<=end;){
   int tag=d[i]&255,size=d[i+1]&255,next=i+2+size;
   if(next>end)break;
   if(tag==9&&size>=4){
    s.ca=1;int system=u16(d,i+2);boolean seen=false;
    for(int j=0;j<s.caIds.length();j++)if(s.caIds.optInt(j)==system)seen=true;
    if(!seen&&s.caIds.length()<32)s.caIds.put(system);
   }
   i=next;
  }
 }
 private static String language(byte[] d,int begin,int end){
  for(int i=begin;i+2<=end;){
   int tag=d[i]&255,size=d[i+1]&255,next=i+2+size;
   if(next>end)break;
   if(tag==0x0a&&size>=3)return new String(d,i+2,3,StandardCharsets.US_ASCII);
   i=next;
  }
  return "";
 }
 private static String decode(byte[] d,int off,int size){
  if(size<=0||size>200)return "";
  String name;
  int marker=d[off]&255;
  try{
   if(marker==0x15&&size>1)name=new String(d,off+1,size-1,StandardCharsets.UTF_8);
   else if(marker==0x11&&size>2&&size%2==1)
    name=new String(d,off+1,size-1,StandardCharsets.UTF_16BE);
   else if(marker==0x10&&size>3&&(d[off+1]&255)==0){
    int n=d[off+2]&255;
    if(n<1||n>15||n==12)return "";
    name=new String(d,off+3,size-3,java.nio.charset.Charset.forName("ISO-8859-"+n));
   }else if(marker>=1&&marker<=11){
    int n=marker+4;if(n==12)return "";
    name=new String(d,off+1,size-1,java.nio.charset.Charset.forName("ISO-8859-"+n));
   }else if(marker<0x20)return "";
   else name=new String(d,off,size,StandardCharsets.ISO_8859_1);
  }catch(Exception unsupported){return "";}
  name=name.trim();
  if(name.length()>120)return "";
  for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))return "";
  return name;
 }
}
