package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;

public final class TsSpliceContinuityTest {
 private byte[] ts(int pid,int cc,boolean payload){
  byte[] packet=new byte[188];
  packet[0]=0x47;
  packet[1]=(byte)((pid>>8)&0x1f);
  packet[2]=(byte)pid;
  packet[3]=(byte)((payload?0x10:0x20)|cc);
  if(!payload)packet[4]=0;
  return packet;
 }
 private byte[] joined(byte[]... packets){
  byte[] value=new byte[188*packets.length];
  for(int i=0;i<packets.length;i++)
   System.arraycopy(packets[i],0,value,i*188,188);
  return value;
 }
 @Test public void preservesExactCapturedBytesEvenWhenContinuityMissing(){
  TsSplice.Continuity c=new TsSplice.Continuity();
  byte[] original=joined(ts(256,0,true),ts(256,4,true),ts(256,5,true));
  byte[] copy=original.clone();
  c.observe(original,original.length);
  assertArrayEquals("Do not hide lost original packets by fabricating CC",copy,original);
  assertEquals(1,c.continuityGaps());
  assertEquals(256,c.lastGapPid());
  assertEquals(3,c.payloadPackets());
 }
 @Test public void tracksGapsAcrossSeparateSegmentsWithoutFalseRotationReset(){
  TsSplice.Continuity c=new TsSplice.Continuity();
  byte[] first=joined(ts(400,14,true),ts(400,15,true));
  byte[] second=joined(ts(400,0,true),ts(400,2,true));
  c.observe(first,first.length);
  c.observe(second,second.length);
  assertEquals(1,c.continuityGaps());
 }
 @Test public void adaptationOnlyMustRepeatCCAndSignalledDiscontinuityResets(){
  TsSplice.Continuity c=new TsSplice.Continuity();
  byte[] adapt=ts(511,1,false);
  byte[] initial=ts(511,1,true);
  byte[] disc=ts(511,12,false);
  disc[4]=1;
  disc[5]=(byte)0x80;
  c.observe(initial,188);
  c.observe(adapt,188);
  c.observe(disc,188);
  c.observe(ts(511,9,true),188);
  assertEquals(0,c.continuityGaps());
  assertEquals(1,c.signalledDiscontinuities());
 }
 @Test public void transportErrorAndBadSyncAreNotSilentlyRewritten(){
  TsSplice.Continuity c=new TsSplice.Continuity();
  byte[] bad=ts(333,5,true);bad[1]|=(byte)0x80;
  byte[] sync=ts(333,6,true);sync[0]=0;
  c.observe(joined(bad,sync),376);
  assertEquals(1,c.transportErrors());
  assertEquals(1,c.invalidPackets());
 }
 @Test public void ignoresNullPidAndCountsEncryptedPackets(){
  TsSplice.Continuity c=new TsSplice.Continuity();
  byte[] scrambled=ts(455,5,true);scrambled[3]|=(byte)0x80;
  c.observe(joined(ts(8191,0,true),scrambled),376);
  assertEquals(0,c.continuityGaps());
  assertEquals(1,c.scrambledPackets());
 }
}
