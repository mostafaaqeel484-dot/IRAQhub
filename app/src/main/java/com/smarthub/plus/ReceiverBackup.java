package com.smarthub.plus;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** User-controlled backup; does not change firmware or restore over live DB. */
final class ReceiverBackup {
 static void export(Context context,Uri target)throws Exception{
  if(target==null)throw new IOException("BACKUP_DESTINATION_MISSING");
  File snapshot=new File(context.getCacheDir(),"receiver-catalog-snapshot-"+System.nanoTime()+".db");
  if(!snapshot.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()+File.separator))
   throw new IOException("INVALID_BACKUP_PATH");
  try{
   SQLiteDatabase db=ReceiverCatalog.get(context).getReadableDatabase();
   // VACUUM INTO produces a consistent snapshot of WAL-backed databases.
   // The file must not exist before SQLite creates it.
   db.execSQL("VACUUM INTO '"+snapshot.getAbsolutePath().replace("'","''")+"'");
   if(!snapshot.isFile()||snapshot.length()<4096)
    throw new IOException("DATABASE_SNAPSHOT_EMPTY");
   JSONObject meta=new JSONObject();
   meta.put("format","IRAQhub-catalog-backup-v1");
   meta.put("app","com.smarthub.plus");
   meta.put("created",System.currentTimeMillis());
   meta.put("channels",ReceiverCatalog.get(context).count());
   meta.put("sqlite_bytes",snapshot.length());
   try(OutputStream raw=context.getContentResolver().openOutputStream(target,"w")){
    if(raw==null)throw new IOException("BACKUP_OUTPUT_UNAVAILABLE");
    try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))){
     zip.putNextEntry(new ZipEntry("manifest.json"));
     zip.write(meta.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
     zip.closeEntry();
     zip.putNextEntry(new ZipEntry("receiver-catalog.db"));
     try(InputStream in=new BufferedInputStream(new FileInputStream(snapshot))){
      byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)zip.write(buf,0,n);
     }
     zip.closeEntry();
    }
   }
  }finally{snapshot.delete();}
 }
 private ReceiverBackup(){}
}
