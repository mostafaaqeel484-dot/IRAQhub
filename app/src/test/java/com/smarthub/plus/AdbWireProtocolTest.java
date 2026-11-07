package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;

public class AdbWireProtocolTest {
 @Test public void actualProtocolRoundTripAndRejectsBadHeader()throws Exception{
  ByteArrayOutputStream output=new ByteArrayOutputStream();
  AdbWireProtocol.send(output,AdbWireProtocol.CNXN,0x01000000,4096,
    "host::iraqhub\u0000".getBytes(StandardCharsets.US_ASCII));
  AdbWireProtocol.Packet packet=AdbWireProtocol.read(
    new ByteArrayInputStream(output.toByteArray()));
  assertEquals(AdbWireProtocol.CNXN,packet.command);
  assertEquals(4096,packet.arg1);
  assertEquals("host::iraqhub\u0000",
    new String(packet.data,StandardCharsets.US_ASCII));
  byte[] corrupted=output.toByteArray();
  corrupted[20]^=0x01;
  try{AdbWireProtocol.read(new ByteArrayInputStream(corrupted));
   fail("corrupt header accepted");
  }catch(IOException expected){}
 }
 @Test public void exportsCorrectAdbRsaPublicStruct()throws Exception{
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
  generator.initialize(2048);
  KeyPair pair=generator.generateKeyPair();
  byte[] adb=AdbWireProtocol.adbPublicKey((RSAPublicKey)pair.getPublic());
  assertEquals(524,adb.length);
  ByteBuffer values=ByteBuffer.wrap(adb).order(ByteOrder.LITTLE_ENDIAN);
  assertEquals(64,values.getInt(0));
  assertEquals(65537,values.getInt(520));
 }
}
