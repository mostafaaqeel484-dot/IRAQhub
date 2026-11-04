package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Read-only receiver transport. It never registers a replacement renderer or changes tuning. */
final class ReceiverDvbBridge implements AutoCloseable {
 private final SharedPreferences prefs;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed,busy;
 private long retry;
 ReceiverDvbBridge(Context c){prefs=c.getSharedPreferences("smarthub",0);}
 void refresh(){long now=System.currentTimeMillis();if(closed||busy||now<retry)return;busy=true;retry=now+60000;io.execute(()->{try{JSONObject reply=readSidList();int[] counts=counts(reply);if(!closed)prefs.edit().putString("native_dvb_status","DVB_SID_CONNECTED").putInt("native_dvb_transponders",counts[0]).putInt("native_dvb_services",counts[1]).putLong("native_dvb_read_at",System.currentTimeMillis()).apply();}catch(Exception failure){if(!closed)prefs.edit().putString("native_dvb_status",failure instanceof SocketTimeoutException?"DVB_READ_TIMEOUT":"DVB_READ_UNAVAILABLE").apply();}finally{busy=false;}});}
 static JSONObject readSidList()throws Exception{try(DatagramSocket socket=new DatagramSocket()){socket.setSoTimeout(700);byte[] request="EPG_TDT\n0006\nnone".getBytes(StandardCharsets.UTF_8);socket.send(new DatagramPacket(request,request.length,InetAddress.getByName("127.0.0.1"),51021));long until=android.os.SystemClock.elapsedRealtime()+4000;while(android.os.SystemClock.elapsedRealtime()<until&&!Thread.currentThread().isInterrupted()){byte[] bytes=new byte[65507];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);try{socket.receive(packet);}catch(SocketTimeoutException wait){continue;}if(!packet.getAddress().isLoopbackAddress())continue;String text=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8);String[] fields=text.split("\n",3);if(fields.length!=3||!fields[0].equals("EPG_TDT")||!fields[1].equals("0018")||!fields[2].startsWith("SUCCESS:"))continue;int json=fields[2].indexOf('{');if(json<0)continue;JSONObject data=new JSONObject(fields[2].substring(json).replace("\u0000",""));counts(data);return data;}throw new SocketTimeoutException("DVB_READ_TIMEOUT");}}
 static int[] counts(JSONObject reply)throws Exception{JSONArray list=reply.optJSONArray("stEPGCHSIDList");if(list==null&&reply.has("iAddCount")&&reply.optInt("iAddCount",-1)==0)return new int[]{0,0};if(list==null||list.length()>512)throw new Exception("DVB_INVALID_SID_LIST");int services=0;boolean unknown=false;for(int i=0;i<list.length();i++){JSONObject tp=list.getJSONObject(i);if(tp.optInt("tp_id",-1)<0||tp.optInt("tp_type",-1)<0)throw new Exception("DVB_INVALID_TRANSPONDER");String raw=tp.optString("aucSIDList").trim();if(raw.length()>32768)throw new Exception("DVB_INVALID_SID_LIST");Set<Integer> unique=new HashSet<>();for(String sid:raw.split(":")){if(sid.trim().isEmpty())continue;String valueText=sid.trim();if(!valueText.matches("[0-9]{1,5}")){unknown=true;continue;}int value=Integer.parseInt(valueText);if(value>65535){unknown=true;continue;}unique.add(value);}services+=unique.size();if(services>10000)throw new Exception("DVB_INVALID_SID_LIST");}return new int[]{list.length(),unknown?-1:services};}
 static String label(Context c){SharedPreferences p=c.getSharedPreferences("smarthub",0);boolean ar=p.getBoolean("arabic",true);String state=p.getString("native_dvb_status","");if(state.equals("DVB_SID_CONNECTED")&&System.currentTimeMillis()-p.getLong("native_dvb_read_at",0)<120000)return (ar?"قراءة DVB متصلة · خدمات EPG: ":"DVB read connected · EPG services: ")+(p.getInt("native_dvb_services",0)<0?(ar?"تنسيق SID غير متاح":"SID format unavailable"):Integer.toString(p.getInt("native_dvb_services",0)))+(ar?". المسار الخام للترجمة غير منشور؛ تُستخدم TXT الظاهرة أو OCR.":". Raw subtitle cues are not exposed; visible TXT or OCR is used.");return ar?"قراءة DVB: لم تتوفر بيانات حديثة؛ تُستخدم المعلومات التي يعرضها المشغل.":"DVB read: no recent data; using information exposed by playback.";}
 public void close(){closed=true;io.shutdownNow();}
}
