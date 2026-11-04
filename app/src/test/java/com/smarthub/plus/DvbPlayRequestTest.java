package com.smarthub.plus;
import org.junit.Test;import org.json.*;import java.io.IOException;import static org.junit.Assert.*;

public class DvbPlayRequestTest {
 private JSONObject service()throws Exception{return new JSONObject().put("VideoPID",205).put("VideoType","H264").put("PcrPID",205).put("PmtPID",505).put("Audio",new JSONArray().put(new JSONObject().put("PID",305).put("Type","MPEG2")).put(new JSONObject().put("PID",405).put("Type","AC3")));}
 @Test public void runtimeTransponderNeverUsesExportIndex()throws Exception{JSONObject j=DvbPlayRequest.create(service(),2986,5,3530,12419,"H",30000,4073,20);assertEquals(4073,j.getInt("tp_id"));assertEquals(1100002986,j.getInt("iProgId"));assertEquals(205,j.getInt("iVideoPid"));assertEquals(27,j.getInt("iVideoType"));assertEquals(0,j.getInt("force_CA_B"));assertEquals(0,j.getInt("iCAType"));assertEquals(0,j.getJSONArray("ca_systemid_list").length());}
 @Test public void filtersInvalidAudioAndKeepsSelectionIndicesAligned()throws Exception{JSONObject s=service();s.getJSONArray("Audio").put(new JSONObject().put("PID",8191).put("Type","AAC"));JSONObject j=DvbPlayRequest.create(s,1,5,3530,12419,"H",30000,4073,20);assertEquals(2,j.getInt("iAudioNum"));assertEquals(4,j.getJSONArray("stAudioInfo").getJSONObject(0).getInt("iAudioType"));assertEquals(129,j.getJSONArray("stAudioInfo").getJSONObject(1).getInt("iAudioType"));assertEquals(1,j.getJSONArray("stAudioInfo").getJSONObject(1).getInt("iAudioIndex"));}
 @Test(expected=IOException.class)public void missingPidsCannotBecomeFakePlayback()throws Exception{DvbPlayRequest.create(new JSONObject(),1,5,3530,12419,"H",30000,4073,20);}
 @Test(expected=IOException.class)public void unknownCodecIsRejected()throws Exception{JSONObject s=service().put("VideoType","unknown");DvbPlayRequest.create(s,1,5,3530,12419,"H",30000,4073,20);}
 @Test public void audioOnlyRadioUsesNoFakeVideoPid()throws Exception{JSONObject j=DvbPlayRequest.create(service().put("VideoPID",8191).put("VideoType",""),10,5,3530,12419,"H",30000,1200000219,20,true);assertEquals(1,j.getInt("iTVType"));assertEquals(8191,j.getInt("iVideoPid"));assertEquals(0,j.getInt("iVideoType"));assertEquals(2,j.getInt("iAudioNum"));assertEquals(0,j.getInt("iCAType"));}
 @Test(expected=IOException.class)public void radioWithoutAnAudioTrackIsRejected()throws Exception{DvbPlayRequest.create(new JSONObject(),1,5,3530,12419,"H",30000,1200000219,20,true);}
}

