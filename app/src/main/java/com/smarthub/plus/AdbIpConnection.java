package com.smarthub.plus;

import android.content.Context;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Authorized LEGACY ADB over local TCP, NOT DNS-over-TLS or modern ADB
 * pairing/TLS. No commands except fixed shell:id; no public IP, no root,
 * no shell binary, no unapproved host, no USB-debugging setting mutation.
 *
 * The generated RSA key is non-exportable and persists in AndroidKeyStore.
 * The receiver must display and receive explicit ADB authorisation.
 */
final class AdbIpConnection {
 static final String ALIAS="iraqhub_x5m_legacy_adb_host_v1";
 private static final byte[] SHA1_DIGEST_INFO={
   0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,
   0x05,0x00,0x04,0x14
 };
 static final class Result {
  final String status,detail,target;
  final boolean authorised;
  Result(String status,String detail,String target,boolean authorised){
   this.status=status;this.detail=detail;this.target=target;this.authorised=authorised;
  }
 }
 private AdbIpConnection(){}
 private static KeyPair localKey()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  KeyStore.PrivateKeyEntry saved=(KeyStore.PrivateKeyEntry)
    store.getEntry(ALIAS,null);
  if(saved!=null)return new KeyPair(saved.getCertificate().getPublicKey(),
    saved.getPrivateKey());
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
  generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
      KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
     .setKeySize(2048)
     .setDigests(KeyProperties.DIGEST_NONE)
     .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
     .setUserAuthenticationRequired(false).build());
  return generator.generateKeyPair();
 }
 private static byte[] signToken(PrivateKey key,byte[] challenge)throws Exception{
  if(challenge.length!=20)throw new IOException("ADB_AUTH_TOKEN_INVALID");
  // ADB RSA_sign(NID_sha1, token, 20) signs this DigestInfo+token;
  // SHA1withRSA would hash the 20-byte *already-digested* token again.
  Signature rsa=Signature.getInstance("NONEwithRSA");
  rsa.initSign(key);
  rsa.update(SHA1_DIGEST_INFO);
  rsa.update(challenge);
  return rsa.sign();
 }
 private static byte[] publicKeyBytes(RSAPublicKey key)throws Exception{
  String encoded=Base64.encodeToString(AdbWireProtocol.adbPublicKey(key),
    Base64.NO_WRAP)+" iraqhub@receiver\u0000";
  return encoded.getBytes(StandardCharsets.US_ASCII);
 }
 static Result test(Context context,String raw){
  AdbTcpEndpoint endpoint=null;
  try{
   endpoint=AdbTcpEndpoint.parse(raw,5556);
   InetAddress address=endpoint.resolveLocal();
   String target=endpoint.display();
   KeyPair key=localKey();
   try(Socket socket=new Socket()){
    socket.setTcpNoDelay(true);
    socket.setKeepAlive(true);
    socket.setSoTimeout(2500);
    socket.connect(new InetSocketAddress(address,endpoint.port),3500);
    InputStream in=socket.getInputStream();
    OutputStream out=socket.getOutputStream();
    AdbWireProtocol.send(out,AdbWireProtocol.CNXN,0x01000000,4096,
      "host::iraqhub\u0000".getBytes(StandardCharsets.US_ASCII));
    long deadline=SystemClock.elapsedRealtime()+26000L;
    boolean signatureSent=false,publicSent=false;
    while(SystemClock.elapsedRealtime()<deadline){
     AdbWireProtocol.Packet packet;
     try{packet=AdbWireProtocol.read(in);}
     catch(SocketTimeoutException waiting){continue;}
     if(packet.command==AdbWireProtocol.STLS)
       return new Result("ADB_TLS_REQUIRED",
         "This is an ADB TLS endpoint. Pair via Android Wireless debugging; legacy TCP cannot silently downgrade.",target,false);
     if(packet.command==AdbWireProtocol.CNXN){
      String identity=verifyShellId(in,out,deadline);
      if(identity.startsWith("uid=")){
       return new Result("ADB_AUTHORIZED",identity,target,true);
      }
      return new Result("ADB_AUTHORIZED_SHELL_UNVERIFIED",
        "ADB CNXN confirmed; shell:id returned "+identity,target,true);
     }
     if(packet.command!=AdbWireProtocol.AUTH||packet.arg0!=AdbWireProtocol.AUTH_TOKEN)
       return new Result("ADB_BAD_PROTOCOL",
         "Unexpected message "+Integer.toHexString(packet.command),target,false);
     if(packet.data.length!=20)
       return new Result("ADB_BAD_AUTH_TOKEN","Unexpected RSA challenge length",target,false);
     if(!signatureSent){
      signatureSent=true;
      AdbWireProtocol.send(out,AdbWireProtocol.AUTH,
        AdbWireProtocol.AUTH_SIGNATURE,0,signToken(key.getPrivate(),packet.data));
     }else if(!publicSent){
      publicSent=true;
      AdbWireProtocol.send(out,AdbWireProtocol.AUTH,
        AdbWireProtocol.AUTH_PUBLIC_KEY,0,
        publicKeyBytes((RSAPublicKey)key.getPublic()));
     }else{
      return new Result("ADB_UNAUTHORIZED",
        "RSA key not approved; accept the debugger prompt on your own receiver.",target,false);
     }
    }
    return new Result(publicSent?"ADB_WAITING_FOR_AUTH":"ADB_TIMEOUT",
     publicSent?"Approve the ADB RSA fingerprint on the receiver screen.":"No authenticated ADB response.",target,false);
   }
  }catch(ConnectException unavailable){
   return new Result("ADB_PORT_CLOSED","No ADB daemon listening on this IP:port. Enabling USB debugging alone does not start TCP 5556.",
     endpoint==null?"":endpoint.display(),false);
  }catch(SecurityException unsafe){
   return new Result("ADB_RESTRICTED",unsafe.getMessage(),
     endpoint==null?"":endpoint.display(),false);
  }catch(Exception failure){
   String reason=failure.getMessage();
   return new Result("ADB_CONNECTION_ERROR",failure.getClass().getSimpleName()+
      (reason==null?"":": "+reason),endpoint==null?"":endpoint.display(),false);
  }
 }
 private static String verifyShellId(InputStream in,OutputStream out,long deadline)
   throws IOException{
  int local=1,remote=0;
  AdbWireProtocol.send(out,AdbWireProtocol.OPEN,local,0,
   "shell:id\u0000".getBytes(StandardCharsets.US_ASCII));
  StringBuilder result=new StringBuilder();
  long until=Math.min(SystemClock.elapsedRealtime()+4500L,deadline);
  while(SystemClock.elapsedRealtime()<until){
   AdbWireProtocol.Packet packet;
   try{packet=AdbWireProtocol.read(in);}
   catch(SocketTimeoutException wait){continue;}
   if(packet.command==AdbWireProtocol.OKAY){
    remote=packet.arg0;continue;
   }
   if(packet.command==AdbWireProtocol.WRTE){
    remote=packet.arg0;
    if(result.length()<512)result.append(new String(packet.data,
      StandardCharsets.UTF_8).replace('\n',' ').trim());
    AdbWireProtocol.send(out,AdbWireProtocol.OKAY,local,remote,new byte[0]);
    continue;
   }
   if(packet.command==AdbWireProtocol.CLSE){
    AdbWireProtocol.send(out,AdbWireProtocol.CLSE,local,packet.arg0,new byte[0]);
    break;
   }
   if(packet.command==AdbWireProtocol.STLS)throw new IOException("ADB_TLS_REQUIRED");
  }
  String response=result.toString().trim();
  if(response.length()>240)response=response.substring(0,240);
  return response.isEmpty()?"SHELL_NO_RESPONSE":response;
 }
}
