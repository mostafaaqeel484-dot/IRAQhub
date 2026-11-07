package com.smarthub.plus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Non-destructive merge of an EXPORTED Aurum/ToSmart SQLite backup.
 * Matching satellite orbital slot, TP tuple and SID are used as physical
 * identity; existing channel metadata, order and favourites are never changed.
 * There is NO access to the live OEM database.
 */
final class ReceiverOemSqliteMerge {
 private ReceiverOemSqliteMerge(){}
 private static final long MAX_DB=128L*1024L*1024L;
 static final class Summary {
  final long satellites,transponders,channels,favorites;
  Summary(long a,long b,long c,long d){satellites=a;transponders=b;channels=c;favorites=d;}
  String label(){return "Aurum / ToSmart DB · "+satellites+" satellites, "+
   transponders+" transponders, "+channels+" services, "+favorites+" favourites";}
 }
 static File stage(Context ctx,InputStream input)throws Exception {
  if(input==null)throw new IOException("DB_STREAM_MISSING");
  File file=File.createTempFile("iraqhub-oem-merge-",".db",ctx.getCacheDir());
  boolean success=false;
  try(FileOutputStream out=new FileOutputStream(file)){
   byte[] bytes=new byte[32768];long size=0;int n;
   while((n=input.read(bytes))!=-1){
    size+=n;
    if(size>MAX_DB||Thread.currentThread().isInterrupted())throw new IOException("DB_MERGE_TOO_LARGE_OR_CANCELLED");
    out.write(bytes,0,n);
   }
   out.flush();success=true;return file;
  }finally{if(!success)file.delete();}
 }
 static Summary preview(Context ctx,InputStream source)throws Exception{
  File file=stage(ctx,source);
  try{
   if(!ReceiverOemSqliteImport.supports(file))throw new IOException("DB_MERGE_REQUIRES_AURUM_EXPORT");
   try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
       SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
    ReceiverOemSqliteImport.validate(db);
    return new Summary(count(db,"satellite_table"),count(db,"satellite_transponder_table"),
     count(db,"program_table"),count(db,"fav_prog_table"));
   }
  }finally{file.delete();}
 }
 private static long count(SQLiteDatabase db,String name){
  return DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM "+name,null);
 }
 private static String key(long sat,int freq,String pol,int sr){
  return sat+"|"+freq+"|"+pol+"|"+sr;
 }
 static final class Result {
  final int added,duplicates,groups,memberships;
  Result(int a,int d,int g,int m){added=a;duplicates=d;groups=g;memberships=m;}
  String label(){return "Added "+added+" services, skipped "+duplicates+
    " duplicate SIDs, new groups "+groups+", new favourite memberships "+memberships;}
 }
 static Result merge(Context context,InputStream input)throws Exception{
  File file=stage(context,input);
  try{return mergeFile(context,file);}finally{file.delete();}
 }
 private static ContentValues values(Object... pairs){
  ContentValues c=new ContentValues();
  for(int i=0;i<pairs.length;i+=2){
   String key=(String)pairs[i];Object v=pairs[i+1];
   if(v instanceof Integer)c.put(key,(Integer)v);
   else if(v instanceof Long)c.put(key,(Long)v);
   else c.put(key,String.valueOf(v));
  }
  return c;
 }
 static Result mergeFile(Context context,File file)throws Exception{
  try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
      SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
   ReceiverOemSqliteImport.validate(source);
   Map<Integer,JSONArray> audio=ReceiverOemSqliteImport.readAudio(source);
   ReceiverCatalog catalog=ReceiverCatalog.get(context);
   synchronized(catalog){
    SQLiteDatabase dest=catalog.getWritableDatabase();
    HashMap<Integer,Long> satIds=new HashMap<>(),tpIds=new HashMap<>(),programIds=new HashMap<>();
    HashMap<String,Long> existingSat=new HashMap<>(),existingTp=new HashMap<>();
    HashMap<String,Long> existingGroups=new HashMap<>();
    long satNext=DatabaseUtils.longForQuery(dest,"SELECT COALESCE(MAX(_id),0) FROM sat",null);
    long tpNext=DatabaseUtils.longForQuery(dest,"SELECT COALESCE(MAX(_id),0) FROM tp",null);
    long chNext=DatabaseUtils.longForQuery(dest,"SELECT COALESCE(MAX(_id),0) FROM ch",null);
    long groupNext=DatabaseUtils.longForQuery(dest,"SELECT COALESCE(MAX(_id),0) FROM fav_group",null);
    long rank=DatabaseUtils.longForQuery(dest,"SELECT COALESCE(MAX(order_index),0) FROM ch",null);
    int added=0,duplicates=0,groupsAdded=0,members=0;
    dest.beginTransaction();
    try{
     try(Cursor rows=dest.rawQuery("SELECT _id,angle,band FROM sat",null)){
      while(rows.moveToNext())existingSat.put(rows.getInt(1)+"|"+rows.getString(2),rows.getLong(0));
     }
     try(Cursor rows=source.rawQuery(
         "SELECT id,name,angle,sat_dir,low_lnb_freq FROM satellite_table ORDER BY disp_order,id",null)){
      while(rows.moveToNext()){
       int a=rows.getInt(2);
       if(rows.getInt(3)==1)a=(3600-a)%3600;
       String band=rows.getInt(4)>=4000&&rows.getInt(4)<=6000?"C":"KU";
       String identity=a+"|"+band;
       Long target=existingSat.get(identity);
       if(target==null){
        if(++satNext>1000000)throw new IOException("MERGE_SAT_ID_LIMIT");
        target=satNext;
        dest.insertOrThrow("sat",null,values("_id",target,"name",rows.getString(1),
          "angle",a,"band",band));
        existingSat.put(identity,target);
       }
       satIds.put(rows.getInt(0),target);
      }
     }
     try(Cursor rows=dest.rawQuery("SELECT _id,sat_id,freq,pol,sr FROM tp",null)){
      while(rows.moveToNext())existingTp.put(key(rows.getLong(1),
       rows.getInt(2),rows.getString(3),rows.getInt(4)),rows.getLong(0));
     }
     try(Cursor rows=source.rawQuery(
         "SELECT id,sat_id,freq,pol,sym_rate,fec FROM satellite_transponder_table ORDER BY id",null)){
      while(rows.moveToNext()){
       Long sat=satIds.get(rows.getInt(1));if(sat==null)throw new IOException("MERGE_TP_NO_SAT");
       String pol=rows.getInt(3)==0?"H":"V";
       int freq=rows.getInt(2),sr=rows.getInt(4);
       String identity=key(sat,freq,pol,sr);
       Long id=existingTp.get(identity);
       if(id==null){
        if(++tpNext>1000000)throw new IOException("MERGE_TP_ID_LIMIT");
        id=tpNext;
        dest.insertOrThrow("tp",null,values("_id",id,"sat_id",sat,
          "freq",freq,"pol",pol,"sr",sr,"fec",rows.getString(5)==null?"":rows.getString(5)));
        existingTp.put(identity,id);
       }
       tpIds.put(rows.getInt(0),id);
      }
     }
     try(Cursor rows=source.rawQuery(
      "SELECT p.id,p.tp_id,p.name,p.service_id,p.tv_type,p.vid_pid,p.pcr_pid,p.pmt_pid,p.vid_type,"+
      "p.ca_systemid_list,p.disp_order,p.video_width,p.video_height,p.chromaformat,p.lcn_no,"+
      "t.ts_id,t.on_id,COALESCE((SELECT name FROM tp_network_name_table n WHERE n.tp_id=p.tp_id ORDER BY n.id LIMIT 1),''),t.freq "+
      "FROM program_table p JOIN satellite_transponder_table t ON t.id=p.tp_id ORDER BY p.disp_order,p.id",null)){
      while(rows.moveToNext()){
       if(Thread.currentThread().isInterrupted())throw new IOException("DB_MERGE_CANCELLED");
       int sourceId=rows.getInt(0);Long tp=tpIds.get(rows.getInt(1));
       if(tp==null)throw new IOException("MERGE_SERVICE_NO_TP");
       String[] args={Long.toString(tp),Integer.toString(rows.getInt(3))};
       long existing=-1;
       try(Cursor found=dest.rawQuery("SELECT _id FROM ch WHERE tp_id=? AND sid=? LIMIT 1",args)){
        if(found.moveToFirst())existing=found.getLong(0);
       }
       if(existing>=0){duplicates++;programIds.put(sourceId,existing);continue;}
       if(++chNext>1000000)throw new IOException("MERGE_CH_ID_LIMIT");
       int tv=rows.getInt(4),vid=rows.getInt(5);
       String name=rows.getString(2),provider=rows.getString(17);
       if(provider==null)provider="";
       if(provider.length()>200)provider=provider.substring(0,200);
       JSONArray cas;try{cas=new JSONArray(rows.getString(9));}catch(Exception e){cas=new JSONArray();}
       JSONObject meta=new JSONObject().put("PmtPID",rows.getInt(7))
        .put("PcrPID",rows.getInt(6)).put("VideoPID",tv==1?8191:vid)
        .put("VideoType",ReceiverOemSqliteImport.video(rows.getInt(8)))
        .put("Audio",audio.getOrDefault(sourceId,new JSONArray()))
        .put("Sub",new JSONArray()).put("Tsid",rows.getInt(15)).put("Onid",rows.getInt(16))
        .put("CaSystemIdList",cas).put("Width",rows.getInt(11)).put("Height",rows.getInt(12))
        .put("ChromaFormat",rows.getInt(13)).put("Lcn",rows.getInt(14))
        .put("RfFrequencyValid",rows.getInt(18)>=100&&rows.getInt(18)<=100000)
        .put("SourceRfFrequency",rows.getInt(18))
        .put("Discovery","AURUM_EXPORTED_SQLITE_MERGED");
       dest.insertOrThrow("ch",null,values("_id",chNext,"tp_id",tp,
        "name",name,"provider",provider,"sid",rows.getInt(3),"tv",tv,
        "ca",cas.length()>0?1:0,"search_key",ReceiverChannelRepository.searchKey(name+" "+provider),
        "data",meta.toString(),"order_index",++rank));
       added++;programIds.put(sourceId,chNext);
      }
     }
     try(Cursor rows=dest.rawQuery("SELECT _id,name FROM fav_group",null)){
      while(rows.moveToNext())existingGroups.put(rows.getString(1).trim().toLowerCase(Locale.ROOT),rows.getLong(0));
     }
     HashMap<Integer,Long> groupIds=new HashMap<>();
     try(Cursor rows=source.rawQuery("SELECT id,fav_name FROM fav_name_table ORDER BY id",null)){
      while(rows.moveToNext()){
       String name=rows.getString(1);if(name==null||name.trim().isEmpty())name="Favourite "+rows.getInt(0);
       String identity=name.trim().toLowerCase(Locale.ROOT);
       Long id=existingGroups.get(identity);
       if(id==null){
        if(++groupNext>1000000)throw new IOException("MERGE_GROUP_ID_LIMIT");
        id=groupNext;dest.insertOrThrow("fav_group",null,values("_id",id,"name",name));
        existingGroups.put(identity,id);groupsAdded++;
       }
       groupIds.put(rows.getInt(0),id);
      }
     }
     try(Cursor rows=source.rawQuery(
       "SELECT fav_group_id,prog_id,disp_order FROM fav_prog_table ORDER BY fav_group_id,disp_order,id",null)){
      while(rows.moveToNext()){
       Long group=groupIds.get(rows.getInt(0)),channel=programIds.get(rows.getInt(1));
       if(group==null||channel==null)throw new IOException("MERGE_FAV_REFERENCE_MISSING");
       ContentValues v=values("group_id",group,"channel_id",channel,
         "ordinal",Math.max(0,rows.getInt(2)));
       if(dest.insertWithOnConflict("fav_member",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)members++;
       dest.insertWithOnConflict("favorite",null,values("channel_id",channel),
         SQLiteDatabase.CONFLICT_IGNORE);
      }
     }
     dest.setTransactionSuccessful();
    }finally{dest.endTransaction();}
    context.getSharedPreferences("smarthub",0).edit()
     .putLong("receiver_catalog_imported_at",System.currentTimeMillis())
     .putLong("installation_changed_at",System.currentTimeMillis())
     .putInt("receiver_catalog_merge_added",added)
     .putInt("receiver_catalog_merge_duplicates",duplicates)
     .putString("receiver_catalog_merge_source","AURUM_EXPORTED_SQLITE").apply();
    return new Result(added,duplicates,groupsAdded,members);
   }
  }
 }
}
