package com.smarthub.plus;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Synthetic, checksum-correct PSI fixtures; does not pretend to test a receiver. */
public final class DvbPsiScanTest {
 private static byte[] bytes(int... raw){
  byte[] out=new byte[raw.length];for(int i=0;i<raw.length;i++)out[i]=(byte)raw[i];return out;
 }
 private static byte[] withCrc(byte[] section){
  int crc=0xffffffff;
  for(byte b:section)for(int bit=7;bit>=0;bit--){
   int upper=(crc>>>31)&1;
   crc<<=1;
   if((upper^((b>>>bit)&1))!=0)crc^=0x04c11db7;
  }
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  out.write(section,0,section.length);
  for(int shift=24;shift>=0;shift-=8)out.write((crc>>>shift)&255);
  return out.toByteArray();
 }
 private static byte[] packet(int pid,byte[] psi){
  byte[] transport=new byte[188];Arrays.fill(transport,(byte)0xff);
  transport[0]=0x47;
  transport[1]=(byte)(0x40|((pid>>>8)&31));
  transport[2]=(byte)pid;
  transport[3]=0x10;transport[4]=0;
  System.arraycopy(psi,0,transport,5,psi.length);
  return transport;
 }
 private static byte[] sample(boolean corruptPat){
  byte[] pat=withCrc(bytes(0x00,0xb0,0x0d,0,1,0xc1,0,0,0,1,0xe0,0x64));
  byte[] pmt=withCrc(bytes(0x02,0xb0,0x17,0,1,0xc1,0,0,0xe1,0,0xf0,0,
      0x1b,0xe1,0,0xf0,0,0x0f,0xe1,1,0xf0,0));
  byte[] sdt=withCrc(bytes(0x42,0xf0,0x1d,0,1,0xc1,0,0,0,1,0xff,
      0,1,0xfc,0x80,0x0c,
      0x48,0x0a,1,3,'H','U','B',4,'I','R','A','Q'));
  if(corruptPat)pat[8]^=0x01;
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  for(byte[] ts:new byte[][]{packet(0,pat),packet(0x11,sdt),packet(0x64,pmt)})
   out.write(ts,0,ts.length);
  return out.toByteArray();
 }
 @Test public void discoversRealPsiSemantics()throws Exception{
  DvbPsiScan.Result result=DvbPsiScan.scan(new ByteArrayInputStream(sample(false)));
  assertEquals(1,result.services.length());
  assertEquals(1,result.transportId);
  JSONObject channel=result.services.getJSONObject(0);
  assertEquals("IRAQ",channel.getString("name"));
  assertEquals("HUB",channel.getString("provider"));
  assertEquals(1,channel.getInt("sid"));
  assertEquals(100,channel.getInt("pmt"));
  assertEquals("H264",channel.getString("videoType"));
  assertEquals(256,channel.getInt("videoPid"));
  assertEquals(1,channel.getJSONArray("audio").length());
  assertEquals(0,channel.getInt("ca"));
 }
 @Test public void corruptedPatCrcIsRejected(){
  try{
   DvbPsiScan.scan(new ByteArrayInputStream(sample(true)));
   fail("Bad CRC should never produce an importable channel");
  }catch(Exception expected){assertEquals("TS_PAT_NOT_FOUND",expected.getMessage());}
 }
 @Test public void arbitraryTextIsNotTransportStream(){
  try{
   DvbPsiScan.scan(new ByteArrayInputStream("not MPEG-TS".getBytes()));
   fail("Not a transport stream");
  }catch(Exception expected){assertEquals("TS_PAT_NOT_FOUND",expected.getMessage());}
 }
}
