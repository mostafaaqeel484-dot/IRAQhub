package com.smarthub.plus;

/**
 * Maintains TS continuity counters across successive Pvr recording files.
 * Rewrites packet headers only; never decrypts or modifies DVB payloads.
 */
final class TsSplice {
 private TsSplice(){}
 static final class Continuity {
  private final byte[] next=new byte[8192];
  private final boolean[] seen=new boolean[8192];
  void rewrite(byte[] data,int length){
   for(int pos=0;pos+188<=length;pos+=188){
    if((data[pos]&0xff)!=0x47)continue;
    int pid=((data[pos+1]&0x1f)<<8)|(data[pos+2]&0xff);
    if(pid==0x1fff)continue;
    int flags=(data[pos+3]>>>4)&3;
    if(flags==0)continue;
    boolean payload=(flags&1)!=0;
    int current=data[pos+3]&15;
    if(!seen[pid]){seen[pid]=true;next[pid]=(byte)current;}
    else if(payload)next[pid]=(byte)((next[pid]+1)&15);
    data[pos+3]=(byte)((data[pos+3]&0xf0)|(next[pid]&15));
   }
  }
 }
}
