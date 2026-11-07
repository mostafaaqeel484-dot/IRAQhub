package com.smarthub.plus;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;
import java.util.UUID;

/**
 * Fast channel import from a standalone IRAQhub-format SQLite .db.
 *
 * Untrusted documents are copied into an app-private bounded temporary file.
 * Validate their SQLite header, integrity, exact supported table layout and
 * foreign-key relationships before any changes. An ATTACH + INSERT SELECT
 * transaction copies all indexed rows at native SQLite speed; unlike CHL it
 * never parses 10k individual JSON objects. No OEM privileged DB is opened.
 */
final class ReceiverSqliteImport {
 static final long LIMIT=64L*1024L*1024L;
 static final String[] TABLES={"sat","tp","ch","favorite","fav_group","fav_member"};
 static final String[][] COLUMNS={
  {"_id","name","angle","band"},
  {"_id","sat_id","freq","pol","sr","fec"},
  {"_id","tp_id","name","provider","sid","tv","ca","search_key","data","order_index"},
  {"channel_id"},
  {"_id","name"},
  {"group_id","channel_id","ordinal"}
 };
 private ReceiverSqliteImport(){}

 static boolean isSqliteHeader(byte[] first){
  if(first==null||first.length<16)return false;
  byte[] magic="SQLite format 3\u0000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  for(int i=0;i<16;i++)if(magic[i]!=first[i])return false;
  return true;
 }

 static int importStream(Context context,InputStream input)throws Exception{
  if(context==null||input==null)throw new IOException("DB_INPUT_REQUIRED");
  File dir=context.getCacheDir(),temporary=new File(dir,"iraqhub-db-import-"+UUID.randomUUID()+".db");
  try{
   long length=0;
   byte[] prefix=new byte[16],block=new byte[32768];int have=0;
   try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temporary))){
    int count;
    while((count=input.read(block))!=-1){
     if(Thread.currentThread().isInterrupted())throw new IOException("DB_IMPORT_CANCELLED");
     if(count==0)continue;
     length+=count;
     if(length>LIMIT)throw new IOException("DB_FILE_TOO_LARGE");
     if(have<16){int n=Math.min(count,16-have);System.arraycopy(block,0,prefix,have,n);have+=n;}
     out.write(block,0,count);
    }
   }
   if(have<16||!isSqliteHeader(prefix))throw new IOException("NOT_SQLITE_DATABASE");
   if(length<4096)throw new IOException("DB_FILE_TRUNCATED");
   // Aurum's exported satellite DB has a distinct, verified schema.
   // Convert from a user-selected snapshot to our own catalogue, never opening
   // or changing the live OEM database.
   if(ReceiverOemSqliteImport.supports(temporary))
    return ReceiverOemSqliteImport.importFile(context,temporary);
   // Cannot import a live WAL database without its uncheckpointed -wal file.
   // Ask for a standalone, exported/closed SQLite snapshot.
   int channels=validate(temporary);
   // Never replace an existing DB file on disk; transaction preserves data on error.
   ReceiverCatalog catalog=ReceiverCatalog.get(context);
   synchronized(catalog){
    SQLiteDatabase dest=catalog.getWritableDatabase();
    boolean attached=false;
    try{
     dest.execSQL("ATTACH DATABASE ? AS channel_import",
       new Object[]{temporary.getAbsolutePath()});
     attached=true;
     dest.beginTransaction();
     try{
      // Order matters: clear dependent references first.
      for(String table:new String[]{"fav_member","favorite","fav_group","ch","tp","sat"})
       dest.execSQL("DELETE FROM "+table);
      for(int i=0;i<TABLES.length;i++){
       String table=TABLES[i],columns=String.join(",",COLUMNS[i]);
       dest.execSQL("INSERT INTO "+table+"("+columns+") SELECT "+columns+
         " FROM channel_import."+table);
      }
      if(DatabaseUtils.longForQuery(dest,"SELECT COUNT(*) FROM ch",null)!=channels)
       throw new IOException("DB_IMPORT_COUNT_MISMATCH");
      dest.setTransactionSuccessful();
     }finally{dest.endTransaction();}
    }finally{
     if(attached)dest.execSQL("DETACH DATABASE channel_import");
    }
   }
   context.getSharedPreferences("smarthub",0).edit()
     .putLong("receiver_catalog_imported_at",System.currentTimeMillis())
     .putInt("receiver_catalog_count",channels)
     .putString("receiver_catalog_format","SQLITE_DB")
     .putLong("installation_changed_at",System.currentTimeMillis()).apply();
   return channels;
  }finally{
   if(temporary.exists())temporary.delete();
  }
 }

 /** Validate a fixed, known schema, not random manufacturer's SQLite layouts. */
 /** Snapshot export also captures WAL-committed channel changes without copying live -wal files.
  * VACUUM INTO requires a modern SQLite runtime (available on the owner's Android 14).
  */
 static long exportStream(Context context,OutputStream output)throws Exception{
  if(context==null||output==null)throw new IOException("DB_OUTPUT_REQUIRED");
  File snapshot=new File(context.getCacheDir(),
    "iraqhub-db-export-"+UUID.randomUUID()+".db");
  try{
   ReceiverCatalog catalog=ReceiverCatalog.get(context);
   synchronized(catalog){
    SQLiteDatabase db=catalog.getWritableDatabase();
    if(snapshot.exists()&&!snapshot.delete())throw new IOException("DB_EXPORT_EXISTS");
    try{
     db.execSQL("VACUUM INTO ?",new Object[]{snapshot.getAbsolutePath()});
    }catch(android.database.sqlite.SQLiteException unsupported){
     throw new IOException("DB_EXPORT_VACUUM_INTO_UNAVAILABLE",unsupported);
    }
   }
   if(!snapshot.isFile()||snapshot.length()<4096||snapshot.length()>LIMIT)
    throw new IOException("DB_EXPORT_SIZE_INVALID");
   validate(snapshot);
   try(InputStream source=new BufferedInputStream(new FileInputStream(snapshot))){
    byte[] block=new byte[32768];int count;
    while((count=source.read(block))!=-1){
     if(Thread.currentThread().isInterrupted())throw new IOException("DB_EXPORT_CANCELLED");
     output.write(block,0,count);
    }
   }
   output.flush();
   return snapshot.length();
  }finally{
   if(snapshot.exists())snapshot.delete();
  }
 }

 private static int validate(File file)throws Exception{
  SQLiteDatabase source=null;
  try{
   source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
     SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
   try(Cursor check=source.rawQuery("PRAGMA quick_check(1)",null)){
    if(!check.moveToFirst()||!"ok".equalsIgnoreCase(check.getString(0)))
     throw new IOException("DB_INTEGRITY_CHECK_FAILED");
   }
   for(int i=0;i<TABLES.length;i++){
    String table=TABLES[i];
    if(DatabaseUtils.longForQuery(source,
      "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
      new String[]{table})!=1)throw new IOException("UNSUPPORTED_DB_SCHEMA_"+table);
    Set<String> columns=new HashSet<>();
    try(Cursor c=source.rawQuery("PRAGMA table_info("+table+")",null)){
     while(c.moveToNext())columns.add(c.getString(1));
    }
    if(!columns.containsAll(Arrays.asList(COLUMNS[i])))
     throw new IOException("UNSUPPORTED_DB_COLUMNS_"+table);
   }
   long satellites=count(source,"sat"),tps=count(source,"tp"),channels=count(source,"ch");
   if(satellites<1||satellites>512||tps<1||tps>100000||
      channels<1||channels>50000||count(source,"fav_group")>1024||
      count(source,"favorite")>50000||count(source,"fav_member")>100000)
    throw new IOException("DB_ROW_LIMIT_EXCEEDED");
   requireNone(source,"SELECT COUNT(*) FROM sat WHERE _id<0 OR _id>1000000 "+
    "OR name IS NULL OR length(name) NOT BETWEEN 1 AND 200 "+
    "OR angle NOT BETWEEN 0 AND 3599 OR band NOT IN ('KU','C')","DB_INVALID_SAT");
   requireNone(source,"SELECT COUNT(*) FROM tp WHERE _id<0 OR _id>1000000 "+
    // Preserve rare signed OEM RF placeholders as un-tunable list entries.
    // They are visible/importable, but DvbPlayRequest refuses to tune them.
    "OR freq IS NULL OR freq < -32768 OR freq > 100000 OR sr NOT BETWEEN 1 AND 100000 "+
    "OR pol NOT IN ('H','V','L','R') OR fec IS NULL","DB_INVALID_TP");
   requireNone(source,"SELECT COUNT(*) FROM ch WHERE _id<0 OR _id>1000000 "+
    "OR sid NOT BETWEEN 0 AND 65535 OR tv NOT BETWEEN 0 AND 2 "+
    "OR typeof(ca)!=\'integer\' OR name IS NULL "+
    "OR length(name) NOT BETWEEN 1 AND 200 OR provider IS NULL "+
    "OR length(provider)>200 OR data IS NULL OR length(data)>65536 "+
    "OR search_key IS NULL OR length(search_key)>512 OR order_index<0",
     "DB_INVALID_CHANNEL");
   requireNone(source,"SELECT COUNT(*) FROM tp t LEFT JOIN sat s "+
    "ON t.sat_id=s._id WHERE s._id IS NULL","DB_MISSING_SATELLITE");
   if(countInvalidRf(source)>16)
    throw new IOException("DB_TOO_MANY_UNTUNABLE_TPS");
   requireNone(source,"SELECT COUNT(*) FROM ch c LEFT JOIN tp t "+
    "ON c.tp_id=t._id WHERE t._id IS NULL","DB_MISSING_TRANSPONDER");
   requireNone(source,"SELECT COUNT(*) FROM favorite f LEFT JOIN ch c "+
    "ON c._id=f.channel_id WHERE c._id IS NULL","DB_MISSING_FAVORITE_CHANNEL");
   requireNone(source,"SELECT COUNT(*) FROM fav_member m LEFT JOIN ch c "+
    "ON c._id=m.channel_id LEFT JOIN fav_group g ON g._id=m.group_id "+
    "WHERE c._id IS NULL OR g._id IS NULL","DB_INVALID_FAVORITE_MEMBER");
   return (int)channels;
  }catch(android.database.sqlite.SQLiteException damaged){
   throw new IOException("UNSUPPORTED_OR_CORRUPT_SQLITE_DB",damaged);
  }finally{
   if(source!=null)source.close();
  }
 }
 private static long countInvalidRf(SQLiteDatabase db){
  return DatabaseUtils.longForQuery(db,
    "SELECT COUNT(*) FROM tp WHERE freq < 100 OR freq > 100000",null);
 }
 private static long count(SQLiteDatabase db,String table){
  return DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM "+table,null);
 }
 private static void requireNone(SQLiteDatabase db,String query,String error)throws IOException{
  if(DatabaseUtils.longForQuery(db,query,null)!=0)throw new IOException(error);
 }
}
