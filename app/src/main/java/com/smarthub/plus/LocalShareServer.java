package com.smarthub.plus;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
/** A short-lived token protected LAN endpoint serving only one approved memory snapshot. */
public final class LocalShareServer implements AutoCloseable {
 private final ServerSocket server;private final String token;private final byte[] payload;private volatile boolean running=true;private final Thread thread;
 public LocalShareServer(byte[] value)throws IOException{if(value.length>12*1024*1024)throw new IOException("Package too large");payload=value.clone();byte[] random=new byte[20];new SecureRandom().nextBytes(random);StringBuilder t=new StringBuilder();for(byte b:random)t.append(String.format(Locale.ROOT,"%02x",b&255));token=t.toString();server=new ServerSocket(0);server.setSoTimeout(1000);thread=new Thread(this::serve,"smarthub-lan-share");thread.setDaemon(true);thread.start();}
 public String address()throws SocketException{Enumeration<NetworkInterface> list=NetworkInterface.getNetworkInterfaces();while(list.hasMoreElements()){NetworkInterface i=list.nextElement();if(!i.isUp()||i.isLoopback())continue;Enumeration<InetAddress>a=i.getInetAddresses();while(a.hasMoreElements()){InetAddress ip=a.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())return "http://"+ip.getHostAddress()+":"+server.getLocalPort()+"/share/"+token;}}return "";}
 private void serve(){while(running){try(Socket socket=server.accept()){socket.setSoTimeout(4000);InputStream in=socket.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int b;while(header.size()<8192&&(b=in.read())!=-1){header.write(b);String h=header.toString("US-ASCII");if(h.endsWith("\r\n\r\n"))break;}String first=header.toString("US-ASCII").split("\r?\n",2)[0];boolean ok=first.equals("GET /share/"+token+" HTTP/1.1")||first.equals("GET /share/"+token+" HTTP/1.0");byte[] body=ok?payload:"Not found".getBytes(StandardCharsets.UTF_8);String response="HTTP/1.1 "+(ok?"200 OK":"404 Not Found")+"\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";OutputStream out=socket.getOutputStream();out.write(response.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}catch(SocketTimeoutException ignored){}catch(IOException e){if(!running)return;}}}
 @Override public void close(){running=false;try{server.close();}catch(IOException ignored){}}
}
