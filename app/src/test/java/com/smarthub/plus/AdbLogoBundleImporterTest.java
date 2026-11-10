package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdbLogoBundleImporterTest {
 @Test public void manifestZipPathsAreBounded(){
  assertTrue(AdbLogoBundleImporter.safeName("manifest.json"));
  assertTrue(AdbLogoBundleImporter.safeName("logos/channel_1.png"));
  assertFalse(AdbLogoBundleImporter.safeName("../private.xml"));
  assertFalse(AdbLogoBundleImporter.safeName("/data/data/private.png"));
  assertFalse(AdbLogoBundleImporter.safeName("logos/../../secret.png"));
  assertFalse(AdbLogoBundleImporter.safeName("logos/evil.svg"));
  assertFalse(AdbLogoBundleImporter.safeName("logos/a\\b.png"));
 }
}