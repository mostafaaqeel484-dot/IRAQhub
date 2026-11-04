package com.smarthub.plus;
import org.junit.Test;import org.json.JSONObject;import java.io.IOException;import static org.junit.Assert.*;
public class LnbProfileTest {
 @Test public void measuredNilesatProfileProducesTheVerifiedTunePacket()throws Exception{LnbProfile p=new LnbProfile(9750,10600,0,2,0,0);JSONObject j=p.tune(219,12419,"H",30000);assertEquals(1200000219,j.getInt("iTpId"));assertEquals(12419,j.getInt("uiFrequency"));assertEquals(30000,j.getInt("uiSymbolRate"));assertEquals(2,j.getInt("b22KOn"));assertEquals(0,j.getInt("iLnbPower"));assertEquals(0,j.getInt("iMotor_Mode"));assertEquals(0,j.getInt("bBlindScan"));assertEquals(0,j.getInt("bUnicable_Enable"));}
 @Test public void profileRoundtripPreservesPortsAndPowerEnums()throws Exception{LnbProfile p=LnbProfile.parse(new LnbProfile(9750,10750,6,0,4,16).json());assertEquals(6,p.power);assertEquals(0,p.tone);assertEquals(4,p.port10);assertEquals(16,p.port11);}
 @Test public void verticalPolarizationIsNotInferredFromTheChannelName()throws Exception{assertEquals(1,new LnbProfile(9750,10600,0,2,0,0).tune(2,11013,"V",27500).getInt("iPol"));}
 @Test(expected=IOException.class)public void invalidSwitchPortCannotBeSaved()throws Exception{new LnbProfile(9750,10600,0,2,7,0);}
 @Test(expected=IOException.class)public void unknownPolarizationCannotTriggerTuning()throws Exception{new LnbProfile(9750,10600,0,2,0,0).tune(1,12419,"unknown",30000);}
}
