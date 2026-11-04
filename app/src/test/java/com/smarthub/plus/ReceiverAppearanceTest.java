package com.smarthub.plus;import org.junit.Test;import static org.junit.Assert.*;public class ReceiverAppearanceTest {
@Test public void referenceDefaultIsSmallerAndManualMinimumCanShrink(){assertEquals(216,OsdGeometry.bar(1080,1.5f,0,20,true));assertEquals(173,OsdGeometry.bar(1080,1.5f,0,16,false));}
@Test public void observedReceiverSizeWinsInAutomaticMode(){assertEquals(346,OsdGeometry.bar(1080,1.5f,320,20,true));assertEquals(216,OsdGeometry.bar(1080,1.5f,320,20,false));}
@Test public void unsafeSizesAreClamped(){assertEquals(432,OsdGeometry.bar(1080,1.5f,900,90,true));}
@Test public void subtitleLanguagesUseWholeLabels(){assertEquals("en",NativeTrackInfo.language("English DVB"));assertEquals("nl",NativeTrackInfo.language("Nederlands TXT"));assertEquals("ar",NativeTrackInfo.language("العربية"));assertEquals("",NativeTrackInfo.language("None"));}
}