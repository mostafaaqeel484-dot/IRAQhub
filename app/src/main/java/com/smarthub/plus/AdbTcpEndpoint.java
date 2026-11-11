package com.smarthub.plus;

import java.net.*;
import java.util.Locale;

/**
 * ADB legacy TCP endpoint. Accept explicit local IP or a DNS name resolving
 * only to a private/loopback/link-local address. Does NOT enable adbd.
 */
final class AdbTcpEndpoint {
 final String host;
 final int port;
 AdbTcpEndpoint(String host,int port){this.host=host;this.port=port;}
 static AdbTcpEndpoint parse(String input,int defaultPort) {
  if(input==null)throw new IllegalArgumentException("ADB_HOST_REQUIRED");
  String value=input.trim();
  if(value.isEmpty()||value.length()>255||value.indexOf('/')>=0||
     value.indexOf('@')>=0||value.indexOf('#')>=0||value.indexOf('?')>=0||
     value.indexOf(' ')>=0||value.indexOf('\\')>=0)
   throw new IllegalArgumentException("ADB_HOST_INVALID");
  int port=defaultPort;
  String host=value;
  if(value.startsWith("[")){
   int close=value.indexOf(']');
   if(close<2)throw new IllegalArgumentException("ADB_IPV6_BRACKET_INVALID");
   host=value.substring(1,close);
   String suffix=value.substring(close+1);
   if(!suffix.isEmpty())port=parsePort(suffix);
  }else {
   int first=value.indexOf(':'),last=value.lastIndexOf(':');
   if(first>=0&&first==last){
    host=value.substring(0,first);
    port=parsePort(value.substring(first));
   }else if(first>=0)
    throw new IllegalArgumentException("ADB_IPV6_NEEDS_BRACKETS");
  }
  if(host.isEmpty()||port<1||port>65535)
    throw new IllegalArgumentException("ADB_PORT_INVALID");
  if(!host.matches("[a-zA-Z0-9._:%-]+"))
    throw new IllegalArgumentException("ADB_HOST_INVALID");
  return new AdbTcpEndpoint(host,port);
 }
 private static int parsePort(String raw){
  if(!raw.matches(":[0-9]{1,5}"))throw new IllegalArgumentException("ADB_PORT_INVALID");
  try{
   int port=Integer.parseInt(raw.substring(1));
   if(port>=1&&port<=65535)return port;
  }catch(NumberFormatException ignored){}
  throw new IllegalArgumentException("ADB_PORT_INVALID");
 }
 static boolean localAddress(InetAddress address){
  if(address.isAnyLocalAddress()||address.isMulticastAddress())return false;
  if(address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress())return true;
  if(address instanceof Inet6Address){
   byte[] b=address.getAddress();
   return (b[0]&0xfe)==0xfc; // IPv6 Unique Local Address fc00::/7
  }
  return false;
 }
 InetAddress resolveLocal()throws Exception{
  InetAddress[] addresses=InetAddress.getAllByName(host);
  if(addresses==null||addresses.length==0)
    throw new UnknownHostException("ADB_DNS_NOT_RESOLVED");
  for(InetAddress address:addresses)
   if(localAddress(address))return address;
  throw new SecurityException("ADB_REMOTE_PUBLIC_IP_REFUSED");
 }
 String display(){
  return (host.indexOf(':')>=0?"["+host+"]":host)+":"+port;
 }
}
