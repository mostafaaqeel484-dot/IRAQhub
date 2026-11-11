package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ProviderCountryCatalogTest {
 @Test public void isoCountryCodesAreValidated(){
  assertTrue(ProviderCountryCatalog.iso("IQ"));
  assertTrue(ProviderCountryCatalog.iso("RS"));
  assertTrue(ProviderCountryCatalog.iso("XK"));
  assertFalse(ProviderCountryCatalog.iso("Serbia"));
  assertFalse(ProviderCountryCatalog.iso(""));
 }
 @Test public void countryFlagsAreUnicodePairsNotMixedBranding(){
  assertEquals(4,ProviderCountryCatalog.flag("IT").length());
  assertEquals(4,ProviderCountryCatalog.flag("TR").length());
  assertEquals("",ProviderCountryCatalog.flag(""));
 }
 @Test public void normalizationNeverUsesWorkbookEnumeration(){
  assertEquals(ProviderCountryCatalog.key("OSNtv Movies Premiere HD"),
               ProviderCountryCatalog.key("OSNtv Movies Premiere"));
 }
}
