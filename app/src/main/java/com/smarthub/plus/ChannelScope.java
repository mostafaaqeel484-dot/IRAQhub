package com.smarthub.plus;
import android.content.*;import org.json.*;
/** The selected browsing list, shared by OK, SAT, TV/Radio and CH+/CH-. */
final class ChannelScope {
 final String query;final long satellite,group;final int type;final boolean favorites,alphabetical;
 ChannelScope(String query,long satellite,int type,boolean favorites,boolean alphabetical,long group){this.query=query==null?"":query.substring(0,Math.min(200,query.length()));this.satellite=Math.max(-1,satellite);this.type=type>=0&&type<=2?type:0;this.favorites=favorites;this.alphabetical=alphabetical;this.group=Math.max(-1,group);}
 String json(){try{return new JSONObject().put("query",query).put("satellite",satellite).put("type",type).put("favorites",favorites).put("alphabetical",alphabetical).put("group",group).toString();}catch(Exception impossible){return "{}";}}
 static ChannelScope parse(String raw){try{JSONObject j=new JSONObject(raw);return new ChannelScope(j.optString("query"),j.optLong("satellite",-1),j.optInt("type",0),j.optBoolean("favorites"),j.optBoolean("alphabetical"),j.optLong("group",-1));}catch(Exception invalid){return new ChannelScope("",-1,0,false,false,-1);}}
 static ChannelScope read(Context c){return parse(c.getSharedPreferences("smarthub",0).getString("receiver_scope","{}"));}
 void save(Context c){c.getSharedPreferences("smarthub",0).edit().putString("receiver_scope",json()).apply();}
 ChannelScope kind(int value){return new ChannelScope("",satellite,value,favorites,alphabetical,group);}
}
