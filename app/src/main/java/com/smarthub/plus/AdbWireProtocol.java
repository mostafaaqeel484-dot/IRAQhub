package com.smarthub.plus;

import java.io.*;
import java.nio.*;
import java.security.interfaces.RSAPublicKey;
import java.math.BigInteger;

/** Minimal legacy ADB wire framing; never treat a TCP connect as authentication. */
final class AdbWireProtocol {
 static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,
  OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43,STLS=0x534c5453;
 static final int AUTH_TOKEN=1,AUTH_SIGNATURE=2,AUTH_PUBLIC_KEY=3;
 static final int MAX_PAYLOAD=1024*1024;
 static final class Packet{
  final int command,arg0,arg1;final byte[] data;
  Packet(int command,int arg0,int arg1,byte[] data){
   this.command=command;this.arg0=arg0;this.arg1=arg1;this.data=data;
  }
 }
 private AdbWireProtocol(){}
 static void send(OutputStream output,int command,int arg0,int arg1,byte[] bytes)
    throws IOException{
  if(bytes==null)bytes=new byte[0];
  if(bytes.length>MAX_PAYLOAD)throw new IOException("ADB_PACKET_TOO_LARGE");
  int sum=0;for(byte b:bytes)sum+=(b&255);
  ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
   .putInt(command).putInt(arg0).putInt(arg1).putInt(bytes.length)
   .putInt(sum).putInt(command^0xffffffff);
  output.write(header.array());output.write(bytes);output.flush();
 }
 static Packet read(InputStream input)throws IOException{
  byte[] raw=new byte[24];new DataInputStream(input).readFully(raw);
  ByteBuffer header=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
  int command=header.getInt(),arg0=header.getInt(),arg1=header.getInt(),
    size=header.getInt(),expected=header.getInt(),magic=header.getInt();
  if(magic!=(command^0xffffffff)||size<0||size>MAX_PAYLOAD)
   throw new IOException("ADB_BAD_HEADER");
  byte[] data=new byte[size];new DataInputStream(input).readFully(data);
  int sum=0;for(byte b:data)sum+=(b&255);
  if(expected!=0&&sum!=expected)throw new IOException("ADB_BAD_CHECKSUM");
  return new Packet(command,arg0,arg1,data);
 }
 /** adbd expects its historical 524-byte RSAPublicKey (LE Montgomery form). */
 static byte[] adbPublicKey(RSAPublicKey pub)throws Exception{
  BigInteger n=pub.getModulus(),e=pub.getPublicExponent();
  if(n.bitLength()!=2048||!e.equals(BigInteger.valueOf(65537)))
    throw new IOException("ADB_RSA_2048_REQUIRED");
  BigInteger word=BigInteger.ONE.shiftLeft(32),n0=n.mod(word);
  BigInteger n0inv=word.subtract(n0.modInverse(word)).mod(word);
  BigInteger rr=BigInteger.ONE.shiftLeft(4096).mod(n);
  ByteBuffer key=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
  key.putInt(64);key.putInt(n0inv.intValue());
  littleEndian(key,n,256);littleEndian(key,rr,256);
  key.putInt(e.intValue());
  return key.array();
 }
 private static void littleEndian(ByteBuffer out,BigInteger value,int length){
  byte[] big=value.toByteArray();
  for(int i=0;i<length;i++){
   int index=big.length-1-i;
   out.put(index>=0?big[index]:(byte)0);
  }
 }
}
