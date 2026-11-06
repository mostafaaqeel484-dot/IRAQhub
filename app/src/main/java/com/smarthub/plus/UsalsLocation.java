package com.smarthub.plus;

import java.io.IOException;
import org.json.JSONObject;

/** USALS location packet ported from the IRAQhub 3.7.0 APK. */
final class UsalsLocation {
 final double latitude, longitude;
 UsalsLocation(double latitude,double longitude)throws IOException{
  if(!Double.isFinite(latitude)||!Double.isFinite(longitude)
     ||Math.abs(latitude)>=90.0||Math.abs(longitude)>180.0)
     throw new IOException("INVALID_USALS_LOCATION");
  this.latitude=latitude;this.longitude=longitude;
 }
 static UsalsLocation parse(String raw)throws Exception{
  JSONObject json=new JSONObject(raw);
  return new UsalsLocation(json.getDouble("latitude"),json.getDouble("longitude"));
 }
 String json()throws Exception{
  return new JSONObject().put("latitude",latitude).put("longitude",longitude).toString();
 }
 void apply(JSONObject packet,int orbitalTenths,boolean westFromLocation)throws Exception{
  if(orbitalTenths<0||orbitalTenths>=3600)throw new IOException("INVALID_SATELLITE_ANGLE");
  int signed=orbitalTenths>1800?orbitalTenths-3600:orbitalTenths;
  double delta=signed/10.0-longitude;
  if(delta>180.0)delta-=360.0;
  if(delta< -180.0)delta+=360.0;
  if(Math.abs(delta)>80.0)throw new IOException("USALS_SATELLITE_OUT_OF_RANGE");
  packet.put("iMotor_Mode",2)
   .put("iMotor_SatNo",0)
   .put("iMotor_LatitudeAngle",Math.round(latitude*100.0))
   .put("iMotor_LongitudeAngle",Math.round(longitude*100.0))
   .put("iMotor_SatAngle",Math.abs(signed))
   .put("bMotor_West",(westFromLocation?longitude<0:signed<0)?1:0);
 }
}
