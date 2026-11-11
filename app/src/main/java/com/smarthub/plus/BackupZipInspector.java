package com.smarthub.plus;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.json.JSONObject;

/** Bounded read-only verifier for IRAQhub channel database backups.
 * Does not restore, overwrite, delete or access protected OEM databases.
 */
final class BackupZipInspector {
 private BackupZipInspector(){}
 static String inspect(InputStream source)throws Exception{
  if(source==null)throw new IOException("BACKUP_INPUT_MISSING");
  boolean manifest=false,database=false;
  long dbBytes=0,total=0;JSONObject metadata=null;
  try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){
   ZipEntry entry;
   byte[] buffer=new byte[32768];
   int entries=0;
   while((entry=zip.getNextEntry())!=null){
    if(++entries>8)throw new IOException("TOO_MANY_BACKUP_MEMBERS");
    String name=entry.getName();
    if(name==null||name.contains("/")||name.contains("\\")||
      name.startsWith("."))throw new IOException("BACKUP_UNSAFE_ENTRY");
    if(entry.isDirectory())throw new IOException("BACKUP_UNEXPECTED_DIRECTORY");
    if("manifest.json".equals(name)){
     if(manifest)throw new IOException("DUPLICATE_MANIFEST");
     manifest=true;
     ByteArrayOutputStream json=new ByteArrayOutputStream();
     int read;while((read=zip.read(buffer))!=-1){
      if(json.size()+read>16384)throw new IOException("BACKUP_MANIFEST_TOO_LARGE");
      json.write(buffer,0,read);
     }
     metadata=new JSONObject(json.toString("UTF-8"));
     if(!"IRAQhub-catalog-backup-v1".equals(metadata.optString("format")))
      throw new IOException("WRONG_BACKUP_FORMAT");
    }else if("receiver-catalog.db".equals(name)){
     if(database)throw new IOException("DUPLICATE_DATABASE");
     database=true;
     int size=0;byte[] header=new byte[16];int got=0;int read;
     while((read=zip.read(buffer))!=-1){
      total+=read;dbBytes+=read;
      if(total>96L*1024*1024)throw new IOException("BACKUP_EXCEEDS_SIZE_LIMIT");
      if(got<header.length){
       int copy=Math.min(read,header.length-got);
       System.arraycopy(buffer,0,header,got,copy);got+=copy;
      }
     }
     if(got!=16||!new String(header,StandardCharsets.US_ASCII).equals("SQLite format 3\u0000"))
      throw new IOException("BACKUP_NOT_SQLITE");
     if(dbBytes<4096)throw new IOException("BACKUP_DATABASE_EMPTY");
    }else{
     throw new IOException("UNKNOWN_BACKUP_ENTRY");
    }
    zip.closeEntry(); // validates zip entry checksum when supplied
   }
  }
  if(!manifest||!database||metadata==null)
   throw new IOException("BACKUP_INCOMPLETE");
  if(metadata.optLong("sqlite_bytes",-1)!=dbBytes)
   throw new IOException("BACKUP_SIZE_MISMATCH");
  int channels=metadata.optInt("channels",-1);
  if(channels<0||channels>1000000)throw new IOException("BACKUP_CHANNEL_COUNT_INVALID");
  return "IRAQhub channel backup v1\nChannels: "+channels+
    "\nSQLite bytes: "+dbBytes+
    "\nIntegrity: ZIP + SQLite header verified (read-only)";
 }
}
