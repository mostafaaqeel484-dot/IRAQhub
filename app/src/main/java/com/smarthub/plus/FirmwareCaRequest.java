package com.smarthub.plus;
import org.json.*;import java.io.IOException;import java.util.*;
/** Standard CA metadata; the receiver retains its keys and makes every authorization decision. */
final class FirmwareCaRequest {
 static void apply(JSONObject body,int ca,JSONArray systems,JSONArray services)throws Exception{
  if(ca==-1)throw new IOException("DVB_METADATA_REQUIRED");if(ca==0)return;int pmt=body.optInt("pmt_pid",8191);if(pmt<1||pmt>=8191||systems==null||systems.length()==0)throw new IOException("DVB_CA_METADATA_REQUIRED");JSONArray ids=new JSONArray();for(int n=0;n<Math.min(64,systems.length());n++){int id=systems.optInt(n,-1);if(id<1||id>65535)throw new IOException("DVB_CA_METADATA_REQUIRED");ids.put(id);}StringBuilder sidText=new StringBuilder(),pmtText=new StringBuilder();Set<Integer> seen=new HashSet<>();for(int n=0;n<Math.min(512,services.length());n++){JSONObject s=services.getJSONObject(n);int sid=s.getInt("sid"),pid=s.getInt("pmt");if(sid<0||sid>65535)continue;if(seen.add(sid)){if(sidText.length()>0)sidText.append(':');sidText.append(String.format(Locale.ROOT,"%04x",sid));}if(s.optInt("ca",0)!=0&&pid>0&&pid<8191){if(pmtText.length()>0)pmtText.append(':');pmtText.append(String.format(Locale.ROOT,"%08x",(sid<<16)|pid));}}
  body.put("iCAType",ca).put("ca_systemid_list",ids).put("force_CA_B",0).put("pt_enable",1).put("xtms_time",0);if(seen.size()>0)body.put("pucSids",sidText.toString()).put("pucPmtPids",pmtText.toString()).put("uiSIDCount",seen.size());
 }
}
