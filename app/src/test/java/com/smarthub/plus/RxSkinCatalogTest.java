package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Appearance catalogue must stay deterministic and offline; no DVB call here. */
public class RxSkinCatalogTest {
 @Test public void requiredUserWallpapersExist(){
  assertEquals(2,RxSkin.index("iraq"));
  assertEquals(3,RxSkin.index("manchester-united"));
  assertEquals(4,RxSkin.index("linux"));
  assertEquals(0,RxSkin.index("unknown"));
 }
 @Test public void optionsAndPaletteStayAligned(){
  assertEquals(RxSkin.IDS.length,RxSkin.AR.length);
  assertEquals(RxSkin.IDS.length,RxSkin.EN.length);
  assertEquals(RxSkin.IDS.length,RxSkin.ACCENTS.length);
  assertTrue(RxSkin.IDS.length>=8);
  for(int i=0;i<RxSkin.IDS.length;i++){
   assertEquals(i,RxSkin.index(RxSkin.IDS[i]));
   assertTrue(RxSkin.ACCENTS[i]!=0);
  }
 }
}
