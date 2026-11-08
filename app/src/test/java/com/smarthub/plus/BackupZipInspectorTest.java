package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.*;
import java.util.zip.*;
import org.json.JSONObject;
public final class BackupZipInspectorTest {
 private byte[] fixture(boolean corrupt)throws Exception{
  byte[] db=new byte[8192];
  byte[] magic="SQLite format 3\u0000".getBytes("US-ASCII");
  System.arraycopy(magic,0,db,0,16);
  JSONObject meta=new JSONObject().put("format","IRAQhub-catalog-backup-v1")
   .put("sqlite_bytes",corrupt?0:db.length).put("channels",100);
  ByteArrayOutputStream output=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(output)){
   zip.putNextEntry(new ZipEntry("manifest.json"));
   zip.write(meta.toString().getBytes("UTF-8"));zip.closeEntry();
   zip.putNextEntry(new ZipEntry("receiver-catalog.db"));
   zip.write(db);zip.closeEntry();
  }
  return output.toByteArray();
 }
 @Test public void readOnlyValidatesExistingBackups()throws Exception{
  String report=BackupZipInspector.inspect(new ByteArrayInputStream(fixture(false)));
  assertTrue(report.contains("Channels: 100"));
 }
 @Test public void rejectsManifestSizeMismatch()throws Exception{
  try{BackupZipInspector.inspect(new ByteArrayInputStream(fixture(true)));fail();}
  catch(IOException ok){assertEquals("BACKUP_SIZE_MISMATCH",ok.getMessage());}
 }
}
