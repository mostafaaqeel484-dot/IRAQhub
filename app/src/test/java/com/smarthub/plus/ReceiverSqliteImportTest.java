package com.smarthub.plus;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Fast DB import must reject misleading extensions and unrecognised formats. */
public class ReceiverSqliteImportTest {
 @Test public void validSQLiteMagicIsRecognised(){
  byte[] header="SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);
  assertEquals(16,header.length);
  assertTrue(ReceiverSqliteImport.isSqliteHeader(header));
  assertFalse(ReceiverSqliteImport.isSqliteHeader(null));
  assertFalse(ReceiverSqliteImport.isSqliteHeader(new byte[15]));
  assertFalse(ReceiverSqliteImport.isSqliteHeader("SQLite format 4\u0000".getBytes(StandardCharsets.US_ASCII)));
  assertFalse(ReceiverSqliteImport.isSqliteHeader("{\"Type\":\"sat\"}".getBytes(StandardCharsets.US_ASCII)));
 }
 @Test public void importTablesAndFixedColumnsAreExplicit(){
  assertArrayEquals(new String[]{"sat","tp","ch","favorite","fav_group","fav_member"},ReceiverSqliteImport.TABLES);
  assertTrue(Arrays.asList(ReceiverSqliteImport.COLUMNS[0]).contains("angle"));
  assertTrue(Arrays.asList(ReceiverSqliteImport.COLUMNS[1]).contains("sr"));
  assertTrue(Arrays.asList(ReceiverSqliteImport.COLUMNS[2]).contains("data"));
  assertTrue(Arrays.asList(ReceiverSqliteImport.COLUMNS[2]).contains("sid"));
  assertEquals(64L*1024L*1024L,ReceiverSqliteImport.LIMIT);
 }
}
