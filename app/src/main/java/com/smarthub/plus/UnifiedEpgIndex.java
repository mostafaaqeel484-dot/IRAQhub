package com.smarthub.plus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.Map;

/**
 * Small, offline SQLite index of VERIFIED broadcast event identities.
 * Supplements the existing XMLTV/observed guide stores; it never fabricates
 * EPG for arbitrary service names and it never edits the OEM EPG provider.
 */
final class UnifiedEpgIndex extends SQLiteOpenHelper {
 private static volatile UnifiedEpgIndex instance;
 private static final long MAX_FUTURE_MS=7L*86400000L;
 private static final long HISTORY_MS=6L*3600000L;
 private UnifiedEpgIndex(Context context){super(context,"iraqhub-epg-index.db",null,1);}
 static UnifiedEpgIndex get(Context context){
  UnifiedEpgIndex value=instance;
  if(value==null)synchronized(UnifiedEpgIndex.class){
   value=instance;
   if(value==null)instance=value=new UnifiedEpgIndex(context.getApplicationContext());
  }
  return value;
 }
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE epg(channel_id TEXT NOT NULL,start_ms INTEGER NOT NULL,"+
   "end_ms INTEGER NOT NULL,title TEXT NOT NULL,image TEXT NOT NULL,source TEXT NOT NULL,"+
   "source_rank INTEGER NOT NULL,observed_at INTEGER NOT NULL,"+
   "PRIMARY KEY(channel_id,start_ms))");
  db.execSQL("CREATE INDEX epg_by_channel_time ON epg(channel_id,end_ms,start_ms)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int old,int newer){}
 static int sourceRank(String value){return DvbEpgAllPolicy.rank(value);}
 private static boolean valid(String channel,long start,long end,String title,long now){
  return channel!=null&&channel.startsWith("DVB:")&&channel.length()<=250&&
   title!=null&&!title.trim().isEmpty()&&title.length()<=240&&
   start>0&&end>start&&end-start<=24*3600000L&&
   end>now-HISTORY_MS&&start<now+MAX_FUTURE_MS;
 }
 static String eventChannel(SatelliteGuideRepository.Event event){
  return event==null||event.channelId==null?"":event.channelId;
 }
 synchronized void record(Map<String,SatelliteGuideRepository.Event> events,long now){
  if(events==null||events.isEmpty())return;
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   db.delete("epg","end_ms<? OR start_ms>?",new String[]{
    Long.toString(now-HISTORY_MS),Long.toString(now+MAX_FUTURE_MS)});
   for(Map.Entry<String,SatelliteGuideRepository.Event> row:events.entrySet()){
    SatelliteGuideRepository.Event e=row.getValue();
    // Map keys distinguish multiple broadcasts (channel|start), whereas
    // lookups must use the physical DVB channel ID in each Event.
    String id=eventChannel(e);
    if(e==null||!valid(id,e.startMs,e.endMs,e.title,now))continue;
    int rank=sourceRank(e.source);
    String title=e.title.trim();
    String image=e.image==null?"":e.image.substring(0,Math.min(e.image.length(),1024));
    long end=e.endMs;
    try(Cursor cursor=db.rawQuery(
      "SELECT title,end_ms,source_rank,image FROM epg WHERE channel_id=? AND start_ms=?",
      new String[]{id,Long.toString(e.startMs)})){
     if(cursor.moveToFirst()){
      int existing=cursor.getInt(2);
      if(existing>rank)continue;
      if(existing==rank&&cursor.getString(0).equals(title)){
       String previousImage=cursor.getString(3);
       end=Math.max(end,cursor.getLong(1));
       if(image.isEmpty())image=previousImage;
       if(end==cursor.getLong(1)&&image.equals(previousImage))continue;
      }
     }
    }
    ContentValues values=new ContentValues();
    values.put("channel_id",id);values.put("start_ms",e.startMs);
    values.put("end_ms",end);values.put("title",title);
    values.put("image",image);
    values.put("source",e.source==null?"":e.source.substring(0,Math.min(e.source.length(),120)));
    values.put("source_rank",rank);values.put("observed_at",now);
    db.insertWithOnConflict("epg",null,values,SQLiteDatabase.CONFLICT_REPLACE);
   }
   // Hard bound for low-RAM X5M systems. Retain most recently observed events.
   db.execSQL("DELETE FROM epg WHERE rowid IN (SELECT rowid FROM epg "+
     "ORDER BY observed_at DESC LIMIT -1 OFFSET 50000)");
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 /**
  * Index only physically verified tuner events. Keep the native EIT
  * service identity, not the display name, to prevent cross-channel EPG.
  * Called by EIT receiver/worker off the UI thread.
  */
 synchronized void recordNative(String channel,java.util.List<NativeEpgEvents.Event> nativeEvents,
     long now){
  if(channel==null||!channel.startsWith("DVB:")||
      nativeEvents==null||nativeEvents.isEmpty())return;
  java.util.Map<String,SatelliteGuideRepository.Event> mapped=new java.util.HashMap<>();
  for(NativeEpgEvents.Event event:nativeEvents){
   if(event==null||!valid(channel,event.start,event.end,event.title,now))continue;
   SatelliteGuideRepository.Event e=new SatelliteGuideRepository.Event();
   e.channelId=channel;
   e.title=event.title;
   e.startMs=event.start;e.endMs=event.end;
   e.image=event.image==null?"":event.image;
   e.source="DVB EIT · native receiver";
   e.observedAt=now;
   mapped.put(channel+"|"+event.start,e);
  }
  if(!mapped.isEmpty())record(mapped,now);
 }
 /** EPG Pro contributes only a schedule already verified against the
  * exact manufacturer's service reference and physical DVB channel.
  * Prepared factory data can remain visible in EPG ALL past the 2min pre-zap
  * window, but the event itself is never used after its timed end. */
 synchronized void recordFactory(String physical,
       java.util.List<FactoryProtocol.Event> verified,long now){
  if(!DvbEpgAllPolicy.physical(physical)||verified==null||verified.isEmpty())return;
  java.util.Map<String,SatelliteGuideRepository.Event> rows=new java.util.LinkedHashMap<>();
  for(FactoryProtocol.Event source:verified){
   if(source==null||!valid(physical,source.start,source.end,source.title,now))continue;
   SatelliteGuideRepository.Event event=new SatelliteGuideRepository.Event();
   event.channelId=physical;
   event.title=source.title;
   event.startMs=source.start;event.endMs=source.end;
   event.image=source.pic==null?"":source.pic;
   event.source="Factory EPG Pro · verified";
   event.observedAt=now;
   rows.put(physical+"|"+source.start,event);
  }
  if(!rows.isEmpty())record(rows,now);
 }
 /** EPG ALL is the local aggregation of precisely bound EPG Pro, EIT
  * and published DVB schedules, never a name-matching remote feed. */
 synchronized SatelliteGuideRepository.Event receiverCurrent(String channel,long now){
  SatelliteGuideRepository.Event candidate=current(channel,now);
  return candidate!=null&&DvbEpgAllPolicy.receiverGrade(candidate.source)?candidate:null;
 }
 synchronized SatelliteGuideRepository.Event receiverNext(String channel,long now){
  if(!DvbEpgAllPolicy.physical(channel))return null;
  try(Cursor cursor=getReadableDatabase().rawQuery(
    "SELECT title,start_ms,end_ms,image,source FROM epg "+
    "WHERE channel_id=? AND start_ms>? AND end_ms>start_ms AND source_rank>=250 "+
    "ORDER BY start_ms ASC,source_rank DESC LIMIT 1",
    new String[]{channel,Long.toString(now)})){
   if(!cursor.moveToFirst())return null;
   SatelliteGuideRepository.Event e=new SatelliteGuideRepository.Event();
   e.channelId=channel;e.title=cursor.getString(0);
   e.startMs=cursor.getLong(1);e.endMs=cursor.getLong(2);
   e.image=cursor.getString(3);e.source=cursor.getString(4);
   return e;
  }
 }
 synchronized SatelliteGuideRepository.Event next(String channel,long now){
  if(channel==null||!channel.startsWith("DVB:"))return null;
  try(Cursor cursor=getReadableDatabase().rawQuery(
    "SELECT title,start_ms,end_ms,image,source FROM epg "+
    "WHERE channel_id=? AND start_ms>? AND end_ms>start_ms "+
    "ORDER BY start_ms ASC,source_rank DESC LIMIT 1",
    new String[]{channel,Long.toString(now)})){
   if(!cursor.moveToFirst())return null;
   SatelliteGuideRepository.Event e=new SatelliteGuideRepository.Event();
   e.channelId=channel;e.title=cursor.getString(0);
   e.startMs=cursor.getLong(1);e.endMs=cursor.getLong(2);
   e.image=cursor.getString(3);e.source=cursor.getString(4);
   return e;
  }
 }
 synchronized SatelliteGuideRepository.Event current(String channel,long now){
  if(channel==null||!channel.startsWith("DVB:"))return null;
  try(Cursor cursor=getReadableDatabase().rawQuery(
    "SELECT title,start_ms,end_ms,image,source FROM epg "+
    "WHERE channel_id=? AND start_ms<=? AND end_ms>? "+
    "ORDER BY source_rank DESC,start_ms DESC LIMIT 1",
    new String[]{channel,Long.toString(now),Long.toString(now)})){
   if(!cursor.moveToFirst())return null;
   SatelliteGuideRepository.Event e=new SatelliteGuideRepository.Event();
   e.channelId=channel;e.title=cursor.getString(0);
   e.startMs=cursor.getLong(1);e.endMs=cursor.getLong(2);
   e.image=cursor.getString(3);e.source=cursor.getString(4);
   return e;
  }
 }
}
