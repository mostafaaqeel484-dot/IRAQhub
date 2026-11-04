package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReceptionDataTest {
 @Test public void guideTuningIsBoundToBothNativeIndexAndCanonicalName(){SatelliteState state=new SatelliteState();state.channelId="Aurum:1";state.channelName="الشارقة رياضية HD";java.util.Map<String,String> f=new java.util.HashMap<>();f.put("selected_channel_index","0001");f.put("selected_channel","الــــشارقة ريــــاضية HD");assertTrue(ReceptionData.belongsTo(state,f));f.put("selected_channel_index","2");assertFalse(ReceptionData.belongsTo(state,f));}
 @Test public void matchingRowNumberCannotBorrowAnotherChannelsSatellite(){SatelliteState state=new SatelliteState();state.channelId="Aurum:1";state.channelName="HBO";java.util.Map<String,String> f=new java.util.HashMap<>();f.put("selected_channel_index","1");f.put("selected_channel","Sky Cinema");assertFalse(ReceptionData.belongsTo(state,f));}
 @Test public void distinguishesQualityPercentFromDb(){assertEquals("77%",ReceptionData.number("٧٧٪",true));assertEquals("12.5 dB".replace(" ",""),ReceptionData.number("12.5 dB",false));assertEquals("",ReceptionData.number("105%",true));}
 @Test public void unavailableMeasurementIsNotZero(){assertEquals("",ReceptionData.number("N/A",false));assertEquals("",ReceptionData.number(null,true));assertEquals("0%",ReceptionData.number("0%",true));}
 @Test public void artworkCacheIsBoundToChannelAndTitle(){assertNotEquals(EpgArtwork.key("HBO2","Film A"),EpgArtwork.key("HBO2","Film B"));assertNotEquals(EpgArtwork.key("HBO2","Film A"),EpgArtwork.key("HBO","Film A"));}
}
