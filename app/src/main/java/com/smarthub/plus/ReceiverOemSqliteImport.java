package com.smarthub.plus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only import of an EXPORTED Aurum/ToSmart channel backup database.
 * Works on the user-selected file, never on /data/data or the OEM live DB.
 * Maps OEM IDs, transport PID metadata, original display order and favourites
 * into IRAQhub's own SQLite schema. Never calls privileged firmware services.
 */
final class ReceiverOemSqliteImport {
 private ReceiverOemSqliteImport(){}
 private static final String[] TABLES={
  "satellite_table","satellite_transponder_table","program_table",
  "audio_table","fav_name_table","fav_prog_table","tp_network_name_table"
 };
 private static long count(SQLiteDatabase db,String table){
  return DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM "+table,null);
 }
 private static void none(SQLiteDatabase db,String query,String code)throws IOException{
  if(DatabaseUtils.longForQuery(db,query,null)!=0)throw new IOException(code);
 }
 static boolean supports(File file){
  SQLiteDatabase db=null;
  try{
   db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
     SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
   for(String table:TABLES)if(DatabaseUtils.longForQuery(db,
     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
     new String[]{table})!=1)return false;
   return true;
  }catch(Exception ignored){return false;}
  finally{if(db!=null)db.close();}
 }
 static void validate(SQLiteDatabase db)throws Exception{
  try(Cursor check=db.rawQuery("PRAGMA quick_check(1)",null)){
   if(!check.moveToFirst()||!"ok".equalsIgnoreCase(check.getString(0)))
    throw new IOException("OEM_DB_INTEGRITY_FAILED");
  }
  long sats=count(db,"satellite_table"),tp=count(db,"satellite_transponder_table"),
    channels=count(db,"program_table");
  if(sats<1||sats>512||tp<1||tp>100000||channels<1||channels>50000||
    count(db,"fav_name_table")>1024||count(db,"fav_prog_table")>100000)
   throw new IOException("OEM_DB_COUNT_LIMIT");
  none(db,"SELECT COUNT(*) FROM program_table p LEFT JOIN satellite_transponder_table t ON p.tp_id=t.id WHERE t.id IS NULL","OEM_DB_ORPHAN_CHANNEL");
  none(db,"SELECT COUNT(*) FROM satellite_transponder_table t LEFT JOIN satellite_table s ON t.sat_id=s.id WHERE s.id IS NULL","OEM_DB_ORPHAN_TRANSPONDER");
  none(db,"SELECT COUNT(*) FROM fav_prog_table f LEFT JOIN program_table p ON f.prog_id=p.id LEFT JOIN fav_name_table g ON f.fav_group_id=g.id WHERE p.id IS NULL OR g.id IS NULL","OEM_DB_ORPHAN_FAVOURITE");
  none(db,"SELECT COUNT(*) FROM satellite_table WHERE id<1 OR id>1000000 OR angle<0 OR angle>1800 OR sat_dir NOT IN ('0','1') OR name IS NULL OR length(name) NOT BETWEEN 1 AND 200","OEM_DB_SAT_INVALID");
  // Aurum exports can contain a few signed/placeholder RF frequencies.
  // Retain those services for list parity, but never tune them.
  // The playback request explicitly rejects frequencies outside 100..100000.
  none(db,"SELECT COUNT(*) FROM satellite_transponder_table WHERE id<1 OR id>1000000 OR freq IS NULL OR freq < -32768 OR freq > 100000 OR sym_rate NOT BETWEEN 1 AND 100000 OR pol NOT IN ('0','1')","OEM_DB_TP_INVALID");
  if(DatabaseUtils.longForQuery(db,
      "SELECT COUNT(*) FROM satellite_transponder_table WHERE freq < 100",null)>16)
   throw new IOException("OEM_DB_TOO_MANY_UNTUNABLE_TPS");
  none(db,"SELECT COUNT(*) FROM program_table WHERE id<1 OR id>1000000 OR service_id NOT BETWEEN 0 AND 65535 OR tv_type NOT IN ('0','1','2') OR name IS NULL OR length(name) NOT BETWEEN 1 AND 200","OEM_DB_CHANNEL_INVALID");
 }
 private static ContentValues values(Object... pairs){
  ContentValues v=new ContentValues();
  for(int n=0;n<pairs.length;n+=2){
   String k=(String)pairs[n];Object x=pairs[n+1];
   if(x instanceof Integer)v.put(k,(Integer)x);
   else if(x instanceof Long)v.put(k,(Long)x);
   else v.put(k,String.valueOf(x));
  }
  return v;
 }
 static String video(int codec){
  switch(codec){case 1:return "MPEG1";case 2:return "MPEG2";case 27:return "H264";case 36:return "H265";default:return "";}
 }
 static String audio(int codec){
  switch(codec){case 3:return "MPEG1";case 4:return "MPEG2";case 15:return "AAC";case 17:return "LATM";case 129:return "AC3";case 135:return "EAC3";default:return "";}
 }
 static Map<Integer,JSONArray> readAudio(SQLiteDatabase db)throws Exception{
  Map<Integer,JSONArray> tracks=new HashMap<>();
  try(Cursor rows=db.rawQuery("SELECT prog_id,audio_pid,type FROM audio_table ORDER BY id",null)){
   while(rows.moveToNext()){
    int id=rows.getInt(0),pid=rows.getInt(1);
    String codec=audio(rows.getInt(2));
    if(pid<=0||pid>=8191||codec.isEmpty())continue;
    JSONArray a=tracks.get(id);
    if(a==null){a=new JSONArray();tracks.put(id,a);}
    if(a.length()<16)a.put(new JSONObject().put("PID",pid).put("Type",codec));
   }
  }
  return tracks;
 }
 static int importFile(Context context,File file)throws Exception{
  SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
    SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
  try{
   validate(source);
   Map<Integer,JSONArray> audio=readAudio(source);
   ReceiverCatalog catalog=ReceiverCatalog.get(context);
   synchronized(catalog){
    SQLiteDatabase target=catalog.getWritableDatabase();
    target.beginTransaction();
    try{
     for(String table:new String[]{"fav_member","favorite","fav_group","ch","tp","sat"})
      target.delete(table,null,null);
     try(Cursor rows=source.rawQuery("SELECT id,name,angle,sat_dir,low_lnb_freq FROM satellite_table ORDER BY disp_order,id",null)){
      while(rows.moveToNext()){
       int id=rows.getInt(0),angle=rows.getInt(2);
       if(rows.getInt(3)==1)angle=(3600-angle)%3600;
       String band=rows.getInt(4)>=4000&&rows.getInt(4)<=6000?"C":"KU";
       target.insertOrThrow("sat",null,values("_id",id,"name",rows.getString(1),"angle",angle,"band",band));
      }
     }
     try(Cursor rows=source.rawQuery("SELECT id,sat_id,freq,pol,sym_rate,fec FROM satellite_transponder_table ORDER BY id",null)){
      while(rows.moveToNext()){
       target.insertOrThrow("tp",null,values("_id",rows.getInt(0),
         "sat_id",rows.getInt(1),"freq",rows.getInt(2),
         "pol",rows.getInt(3)==0?"H":"V",
         "sr",rows.getInt(4),"fec",rows.getString(5)));
      }
     }
     try(Cursor rows=source.rawQuery(
      "SELECT p.id,p.tp_id,p.name,p.service_id,p.tv_type,p.vid_pid,p.pcr_pid,p.pmt_pid,p.vid_type,"+
      "p.ca_systemid_list,p.disp_order,p.video_width,p.video_height,p.chromaformat,p.lcn_no,"+
      "t.ts_id,t.on_id,COALESCE((SELECT name FROM tp_network_name_table n WHERE n.tp_id=p.tp_id ORDER BY n.id LIMIT 1),''),t.freq "+
      "FROM program_table p JOIN satellite_transponder_table t ON t.id=p.tp_id ORDER BY p.disp_order,p.id",null)){
      while(rows.moveToNext()){
       if(Thread.currentThread().isInterrupted())throw new IOException("OEM_DB_IMPORT_CANCELLED");
       int id=rows.getInt(0),tv=rows.getInt(4),pid=rows.getInt(5),codec=rows.getInt(8);
       String name=rows.getString(2),provider=rows.getString(17);
       if(provider==null)provider="";
       if(provider.length()>200)provider=provider.substring(0,200);
       String caList=rows.getString(9);JSONArray systems;
       try{systems=new JSONArray(caList);}catch(Exception invalid){systems=new JSONArray();}
       JSONObject metadata=new JSONObject()
        .put("PmtPID",rows.getInt(7)).put("PcrPID",rows.getInt(6))
        .put("VideoPID",tv==1?8191:pid)
        .put("VideoType",video(codec))
        .put("Audio",audio.getOrDefault(id,new JSONArray()))
        .put("Sub",new JSONArray())
        .put("Tsid",rows.getInt(15)).put("Onid",rows.getInt(16))
        .put("CaSystemIdList",systems)
        .put("Width",rows.getInt(11)).put("Height",rows.getInt(12))
        .put("ChromaFormat",rows.getInt(13)).put("Lcn",rows.getInt(14))
        .put("RfFrequencyValid",rows.getInt(18)>=100&&rows.getInt(18)<=100000)
        .put("SourceRfFrequency",rows.getInt(18))
        .put("Discovery","AURUM_EXPORTED_SQLITE");
       target.insertOrThrow("ch",null,values("_id",id,"tp_id",rows.getInt(1),
        "name",name,"provider",provider,"sid",rows.getInt(3),"tv",tv,
        "ca",systems.length()>0?1:0,
        "search_key",ReceiverChannelRepository.searchKey(name+" "+provider),
        "data",metadata.toString(),
        "order_index",Math.max(0,rows.getInt(10))));
      }
     }
     try(Cursor rows=source.rawQuery("SELECT id,fav_name FROM fav_name_table ORDER BY id",null)){
      while(rows.moveToNext()){
       String name=rows.getString(1);
       if(name==null||name.trim().isEmpty())name="Favourite "+rows.getInt(0);
       target.insertOrThrow("fav_group",null,values("_id",rows.getInt(0),"name",name));
      }
     }
     try(Cursor rows=source.rawQuery("SELECT fav_group_id,prog_id,disp_order FROM fav_prog_table ORDER BY fav_group_id,disp_order,id",null)){
      while(rows.moveToNext()){
       target.insertWithOnConflict("fav_member",null,values(
        "group_id",rows.getInt(0),"channel_id",rows.getInt(1),
        "ordinal",Math.max(0,rows.getInt(2))),SQLiteDatabase.CONFLICT_IGNORE);
       target.insertWithOnConflict("favorite",null,values("channel_id",rows.getInt(1)),SQLiteDatabase.CONFLICT_IGNORE);
      }
     }
     int imported=(int)count(target,"ch");
     if(imported!=count(source,"program_table"))
      throw new IOException("OEM_DB_IMPORT_COUNT_MISMATCH");
     target.setTransactionSuccessful();
     context.getSharedPreferences("smarthub",0).edit()
      .putString("receiver_catalog_format","SQLITE_OEM_AURUM")
      .putInt("receiver_catalog_count",imported)
      .putLong("receiver_catalog_imported_at",System.currentTimeMillis())
      .putLong("installation_changed_at",System.currentTimeMillis()).apply();
     return imported;
    }finally{target.endTransaction();}
   }
  }finally{source.close();}
 }
}
