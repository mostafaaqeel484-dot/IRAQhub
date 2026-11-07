package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.InetAddress;

public class AdbTcpEndpointTest {
 @Test public void parsesReceiverPortAndHostname(){
  AdbTcpEndpoint target=AdbTcpEndpoint.parse("192.168.0.214:5556",5556);
  assertEquals("192.168.0.214",target.host);
  assertEquals(5556,target.port);
  assertEquals("192.168.0.214:5556",target.display());
  assertEquals("receiver.local:5556",
    AdbTcpEndpoint.parse("receiver.local",5556).display());
  assertEquals("[fd00::1]:5556",
    AdbTcpEndpoint.parse("[fd00::1]:5556",5556).display());
 }
 @Test public void neverAcceptsMalformedTargets(){
  for(String bad:new String[]{"","192.168.1.2:0","192.168.1.2:65536",
     "https://192.168.1.2:5556","user@host:5556","host:abc",
     "2001:db8::1","192.168.0.1/secret"}){
   try{AdbTcpEndpoint.parse(bad,5556);fail("accepted "+bad);}
   catch(IllegalArgumentException expected){}
  }
 }
 @Test public void onlyPrivateOrLoopbackAddresses()throws Exception{
  for(String ip:new String[]{"192.168.0.214","10.5.1.4","172.16.1.3",
     "127.0.0.1","169.254.1.3","fd00::1"})
   assertTrue(ip,AdbTcpEndpoint.localAddress(InetAddress.getByName(ip)));
  for(String ip:new String[]{"8.8.8.8","1.1.1.1","224.0.0.1"})
   assertFalse(ip,AdbTcpEndpoint.localAddress(InetAddress.getByName(ip)));
 }
}
