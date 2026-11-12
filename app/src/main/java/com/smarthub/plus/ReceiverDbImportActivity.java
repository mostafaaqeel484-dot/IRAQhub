package com.smarthub.plus;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Imports only non-sensitive satellite/service identifiers from a user-selected
 * OEM SQLite channel database. Never edits the OEM database or publishes it.
 * Passwords, favourites, settings and keys are not copied into this index. */
public final class ReceiverDbImportActivity extends HubActivity {
 private static final int PICK=9871;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private TextView status;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout page=page(tx("استيراد هوية قنوات الرسيفر","Import receiver channel identities"),
    tx("مطابقة آمنة للقمر والتردد والرمز وService ID؛ قراءة فقط، بلا تعديل EPG أو CHMAX.",
       "Read-only satellite/frequency/symbol-rate/SID index; EPG and CHMAX are not overwritten."));
  note(page,tx("الملف يبقى عندك: لا يرفع إلى GitHub ولا تُستورد كلمات المرور أو قوائم المفضلة أو إعدادات الجهاز.",
     "Your private DB is never uploaded. Passwords, favourites and receiver settings are excluded."));
  status=text(ReceiverDbCrosswalk.status(this),16,false);page.addView(status);
  action(page,tx("اختيار قاعدة القنوات SQLite","Select receiver SQLite channel DB"),()->{
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    startActivityForResult(i,PICK);
  });
  action(page,tx("حذف الفهرس المستورد فقط","Remove imported index only"),()->{
    ReceiverDbCrosswalk.clear(this);status.setText(ReceiverDbCrosswalk.status(this));
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
  final Uri selected=data.getData();
  status.setText(tx("جارٍ فحص SQLite وتكرار الهوية…","Validating SQLite and duplicate service identities…"));
  io.execute(()->{
    String message;
    try{
     ReceiverDbCrosswalk.Report report=ReceiverDbCrosswalk.importReadOnly(this,selected);
     message=tx("نجح: ","Imported: ")+report.rows+
      tx(" قناة، "," services, ")+report.unique+
      tx(" مفتاحاً فريداً. تكرارات غير آمنة: "," unique keys. Unsafe duplicates: ")+report.duplicateKeys;
    }catch(Exception error){
     message=tx("فشل الاستيراد: ","Import failed: ")+error.getClass().getSimpleName();
    }
    final String resultText=message;
    runOnUiThread(()->{if(!closed)status.setText(resultText);});
  });
 }
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}

final class ReceiverDbCrosswalk {
 private static final String FILE="receiver-identity-map.json";
 private static final int MAX_ROWS=100000,MAX_BYTES=64*1024*1024;
 static final class Report {
  final int rows,unique,duplicateKeys;
  Report(int r,int u,int d){rows=r;unique=u;duplicateKeys=d;}
 }
 private ReceiverDbCrosswalk(){}
 private static File index(Context c){return new File(c.getFilesDir(),FILE);}
 static String status(Context c){
  File f=index(c);
  if(!f.isFile())return "Receiver DB index: not imported · CHMAX/DVB unchanged";
  return "Receiver DB index: imported ("+f.length()+" bytes) · read-only";
 }
 static void clear(Context c){index(c).delete();}
 static boolean imported(Context c){return index(c).isFile();}
 private static boolean table(SQLiteDatabase db,String table){
  try(Cursor q=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
   return q.moveToFirst();
  }
 }
 static Report importReadOnly(Context c,Uri uri)throws Exception {
  File stage=File.createTempFile("rx6-receiver-db-",".sqlite",c.getCacheDir());
  try{
   try(InputStream in=c.getContentResolver().openInputStream(uri);
       FileOutputStream out=new FileOutputStream(stage)){
    if(in==null)throw new IOException("SOURCE_UNAVAILABLE");
    byte[] b=new byte[16384];int count;long size=0;
    while((count=in.read(b))!=-1){
     size+=count;if(size>MAX_BYTES)throw new IOException("DB_EXCEEDS_LIMIT");
     out.write(b,0,count);
    }
    if(size<4096)throw new IOException("DB_TOO_SMALL");
   }
   try(SQLiteDatabase db=SQLiteDatabase.openDatabase(
         stage.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
    for(String table:new String[]{"program_table","satellite_transponder_table","satellite_table"})
     if(!table(db,table))throw new IOException("NOT_RECEIVER_CHANNEL_DB");
    try(Cursor integrity=db.rawQuery("PRAGMA quick_check",null)){
     if(!integrity.moveToFirst()||!"ok".equalsIgnoreCase(integrity.getString(0)))
      throw new IOException("INVALID_SQLITE");
    }
    final String sql="SELECT p.name,p.service_id,t.freq,t.sym_rate,t.pol,s.angle,s.sat_dir "+
       "FROM program_table p JOIN satellite_transponder_table t ON p.tp_id=t.id "+
       "JOIN satellite_table s ON t.sat_id=s.id";
    HashMap<String,String> safe=new HashMap<>();HashSet<String> duplicate=new HashSet<>();
    int total=0;
    try(Cursor cursor=db.rawQuery(sql,null)){
     while(cursor.moveToNext()){
      if(++total>MAX_ROWS)throw new IOException("TOO_MANY_CHANNELS");
      String name=cursor.getString(0);
      int sid=cursor.getInt(1),freq=cursor.getInt(2),sr=cursor.getInt(3);
      int sat=cursor.getInt(5),west=cursor.getInt(6);
      if(name==null||name.isEmpty()||sid<1||freq<1000||sr<100||sat<0)continue;
      String identity=key(west==1?-sat:sat,freq,sr,sid);
      String normalized=ChannelCatalogue.normalized(name);
      if(duplicate.contains(identity))continue;
      if(safe.containsKey(identity)){safe.remove(identity);duplicate.add(identity);}
      else safe.put(identity,normalized);
     }
    }
    JSONArray items=new JSONArray();
    for(Map.Entry<String,String> entry:safe.entrySet())
     items.put(new JSONArray().put(entry.getKey()).put(entry.getValue()));
    JSONObject result=new JSONObject().put("schema",1)
      .put("importedAt",System.currentTimeMillis()).put("total",total)
      .put("duplicates",duplicate.size()).put("unique",safe.size()).put("items",items);
    byte[] raw=result.toString().getBytes(StandardCharsets.UTF_8);
    if(raw.length>4*1024*1024)throw new IOException("IDENTITY_INDEX_TOO_LARGE");
    AtomicFile target=new AtomicFile(index(c));
    FileOutputStream out=target.startWrite();
    try{out.write(raw);target.finishWrite(out);}
    catch(Exception failed){target.failWrite(out);throw failed;}
    return new Report(total,safe.size(),duplicate.size());
   }
  }finally{stage.delete();}
 }
 static String key(int angle,int freq,int sr,int sid){
  return angle+"|"+freq+"|"+sr+"|"+sid;
 }
 /** Conservative match: no invented polarization enum, no arbitrary name-only
  * linkage, no guess when multiple identical physical services were imported. */
 static boolean matches(Context c,String physical,String title){
  if(!imported(c)||physical==null||!physical.startsWith("DVB:"))return false;
  String[] parts=physical.split(":");
  if(parts.length<7)return false;
  String key;
  try{key=key(Integer.parseInt(parts[1]),Integer.parseInt(parts[3]),
     Integer.parseInt(parts[5]),Integer.parseInt(parts[6]));}
  catch(Exception invalid){return false;}
  try{
   File f=index(c);if(f.length()>4*1024*1024)return false;
   byte[] bytes=java.nio.file.Files.readAllBytes(f.toPath());
   JSONArray items=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).getJSONArray("items");
   for(int i=0;i<items.length();i++){
    JSONArray row=items.optJSONArray(i);
    if(row!=null&&key.equals(row.optString(0)))
     return ChannelCatalogue.normalized(title).equals(row.optString(1));
   }
  }catch(Exception ignored){}
  return false;
 }
}
