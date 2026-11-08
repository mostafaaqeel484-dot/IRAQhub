package com.smarthub.plus;

/**
 * Audit MPEG-TS transport integrity while preserving the OEM PVR bytes.
 *
 * Previous relay code rewrote EVERY PID continuity counter. This concealed
 * lost/duplicated source packets from FFmpeg, preventing its HEVC demuxer
 * from detecting gaps around reference pictures (RPS/POC). Synthetic CC
 * values cannot restore a missing NAL unit; transmit original TS bytes.
 *
 * A discontinuity_indicator is an explicit reset, not packet loss.
 * Counters are per HTTP client and persist across PVR file boundaries.
 */
final class TsSplice {
 private TsSplice(){}
 static final class Continuity {
  private final int[] previous=new int[8192];
  private final boolean[] seen=new boolean[8192];
  private long packets,payloadPackets,continuityGaps,transportErrors,
       invalidPackets,scrambledPackets,signalledDiscontinuities;
  private int lastGapPid=-1;

  void observe(byte[] data,int length) {
   if(data==null||length<188)return;
   for(int pos=0;pos+188<=Math.min(length,data.length);pos+=188){
    if((data[pos]&0xff)!=0x47){invalidPackets++;continue;}
    packets++;
    int pid=((data[pos+1]&0x1f)<<8)|(data[pos+2]&0xff);
    if(pid==0x1fff)continue;
    int afc=(data[pos+3]>>>4)&3;
    if(afc==0){invalidPackets++;seen[pid]=false;continue;}
    if((data[pos+1]&0x80)!=0){
     transportErrors++;seen[pid]=false;continue;
    }
    if((data[pos+3]&0xc0)!=0) {
     scrambledPackets++;
     // Scrambled packets are left unmodified; the OEM CA path is separate.
    }
    boolean discontinuity=false;
    if((afc&2)!=0){
     int afLength=data[pos+4]&0xff;
     if(afLength>183||((afc==3)&&afLength>182)){
      invalidPackets++;seen[pid]=false;continue;
     }
     discontinuity=afLength>0&&(data[pos+5]&0x80)!=0;
    }
    if(discontinuity){
     signalledDiscontinuities++;
     seen[pid]=false;
    }
    int cc=data[pos+3]&15;
    if((afc&1)!=0){
     payloadPackets++;
     if(seen[pid]&&cc!=((previous[pid]+1)&15)){
      continuityGaps++;lastGapPid=pid;
     }
     previous[pid]=cc;seen[pid]=true;
    }else{
     // For adaptation-only packets the CC MUST NOT increment.
     if(seen[pid]&&cc!=previous[pid]){
      continuityGaps++;lastGapPid=pid;
      previous[pid]=cc;
     }
    }
   }
  }
  long packets(){return packets;}
  long payloadPackets(){return payloadPackets;}
  long continuityGaps(){return continuityGaps;}
  long transportErrors(){return transportErrors;}
  long invalidPackets(){return invalidPackets;}
  long scrambledPackets(){return scrambledPackets;}
  long signalledDiscontinuities(){return signalledDiscontinuities;}
  int lastGapPid(){return lastGapPid;}
 }
}
