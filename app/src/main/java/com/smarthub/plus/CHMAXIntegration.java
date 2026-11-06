package com.smarthub.plus;
import android.accessibilityservice.AccessibilityService;import android.content.*;import android.content.pm.PackageManager;import android.graphics.*;import android.os.*;import android.view.accessibility.AccessibilityNodeInfo;import java.io.*;import java.util.*;
/** Receiver/CHMAX boundary. No root, private database access, guessed IPC or signature-permission bypass. */
final class CHMAXIntegration {
 /** Verified Aurum R.id entries from the owner's original receiver APK.
  * Do not use EPG grid icons: they may belong to another channel. */
 static final List<String> CHANNEL_LOGO_IDS=Arrays.asList(
   "detail_info_tv_program_logo","channel_icon","iv_channel_icon");
 static final Set<String> TEXT_IDS=new HashSet<>(Arrays.asList("detail_info_tv_channel_id","detail_info_tv_program_name","tv_provider","tv_service_provider","pt_menu_provider_name","tv_service_id","tv_network_id","tv_original_network_id","tv_transport_stream_id","tv_frequency","channel_info_frequency_tv","mtv_channel_info_tp","mtv_channel_info_satellite"));
 private final AccessibilityService service;private boolean busy,display;private long retry;private String captured="";
 CHMAXIntegration(AccessibilityService s){service=s;}
 static String diagnostics(Context c){return FactoryStatus.label(c,c.getSharedPreferences("smarthub",0).getString("factory_status","FACTORY_NOT_CONNECTED"))+"\n"+ReceiverDvbBridge.label(c);}
 static String identity(SatelliteState s){return s.channelId.startsWith("DVB:")?s.channelId:ReceiverInfoRepository.channelKey(s);}
 private static File directory(Context c){File f=new File(c.getCacheDir(),"native-channel-branding");f.mkdirs();return f;}
 static void saveObservedLogo(Context c,String channelId,String name,Bitmap image)throws Exception{SatelliteState state=new SatelliteState();state.channelId=channelId;state.channelName=name;try(FileOutputStream out=new FileOutputStream(new File(directory(c),SportsCache.key(identity(state))+"-channel.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}File[] old=directory(c).listFiles();if(old!=null&&old.length>128){Arrays.sort(old,Comparator.comparingLong(File::lastModified));for(int n=0;n<old.length-128;n++)old[n].delete();}c.getSharedPreferences("smarthub",0).edit().putLong("native_logos_changed_at",System.currentTimeMillis()).putString("native_logo_source:"+identity(state),"receiver visible EPG / CHMAX branding").apply();}
 static Bitmap logo(Context c,SatelliteState s,boolean provider){
  if(s==null||s.channelId==null||s.channelName==null)return null;
  try{
   String suffix=provider?"-provider.png":"-channel.png";
   String key=identity(s),nameKey=ReceiverInfoRepository.channelKey(s);
   File dir=directory(c),file=new File(dir,SportsCache.key(key)+suffix);
   if(!file.isFile()&&s.channelId.startsWith("DVB:")){
    File legacy=new File(dir,SportsCache.key(nameKey)+suffix);
    if(legacy.isFile())file=legacy;
   }
   if(!file.isFile()&&s.channelId.startsWith("DVB:")){
    String nativeSource=AurumDvbLogoBridge.sourceFor(c,s);
    if(!nativeSource.isEmpty())file=new File(dir,SportsCache.key(nativeSource)+suffix);
   }
   SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
   boolean manual="MANUAL_CHANNEL_IMAGE".equals(prefs.getString("native_logo_source:"+key,""))||
     "MANUAL_CHANNEL_IMAGE".equals(prefs.getString("native_logo_source:"+nameKey,""));
   return file.isFile()&&(manual||System.currentTimeMillis()-file.lastModified()<7*86400000L)
     ?BitmapFactory.decodeFile(file.getAbsolutePath()):null;
  }catch(Exception e){return null;}
 }
 static String provider(Context c,SatelliteState s){return c.getSharedPreferences("smarthub",0).getString("native_provider:"+identity(s),"");}
 /** The native detail title may be the *current programme*, not the channel.
  * Verify the visible CHANNEL label instead of letting an EPG title mask it.
  */
 static String visibleChannelName(Map<String,String> fields,SatelliteState current){
  if(fields==null||current==null||current.channelName.isEmpty())return "";
  for(String id:new String[]{"tv_channel_info_program_name","detail_info_tv_program_name"}){
   String label=fields.getOrDefault(id,"");
   if(!label.isEmpty()&&ChannelCatalogue.normalized(label)
     .equals(ChannelCatalogue.normalized(current.channelName)))return label;
  }
  return "";
 }
 void observe(AccessibilityNodeInfo root,SatelliteState current,Map<String,String> fields){
  if(current==null||!current.channelId.startsWith("Aurum:"))return;
  String detail=visibleChannelName(fields,current);
  if(detail.isEmpty())return;SharedPreferences.Editor e=service.getSharedPreferences("smarthub",0).edit();String name=fields.getOrDefault("tv_service_provider",fields.getOrDefault("tv_provider",fields.getOrDefault("pt_menu_provider_name","")));if(!name.isEmpty()&&!name.equalsIgnoreCase("null"))e.putString("native_provider:"+identity(current),name);for(String key:Arrays.asList("tv_service_id","tv_network_id","tv_original_network_id","tv_transport_stream_id","channel_info_frequency_tv")){String value=fields.getOrDefault(key,"");if(!value.isEmpty())e.putString("native_"+key+":"+identity(current),value).putLong("native_"+key+"_seen:"+identity(current),System.currentTimeMillis());}e.apply();if(Build.VERSION.SDK_INT<34||busy||SystemClock.elapsedRealtime()<retry)return;Rect channel=channelRect(root),provider=rect(root,"detail_info_tv_provider_logo");if(channel==null&&provider==null){
  updateStatus("NO_VISIBLE_CHANNEL_LOGO_VIEW");return;
 }String key=identity(current)+"|"+channel+"|"+provider;if(key.equals(captured))return;Rect screen=new Rect();root.getBoundsInScreen(screen);int window=root.getWindowId();busy=true;retry=SystemClock.elapsedRealtime()+2500;AccessibilityService.TakeScreenshotCallback cb=new AccessibilityService.TakeScreenshotCallback(){public void onFailure(int error){busy=false;updateStatus("SCREENSHOT_DENIED_"+error);if(error==1)display=true;}public void onSuccess(AccessibilityService.ScreenshotResult shot){Bitmap b=null;AccessibilityNodeInfo active=TvWindowRoot.find(service);try{if(active==null||!SatelliteUiParser.DEFAULT_PACKAGE.contentEquals(active.getPackageName())||
  !identity(SatelliteState.read(service)).equals(identity(current))||
  !(text(active,"tv_channel_info_program_name").equals(detail)||
    text(active,"detail_info_tv_program_name").equals(detail)))return;b=Bitmap.wrapHardwareBuffer(shot.getHardwareBuffer(),shot.getColorSpace());if(b==null)return;boolean channelSaved=save(b,channel,screen,current,false);boolean providerSaved=save(b,provider,screen,current,true);boolean saved=channelSaved||providerSaved;if(channelSaved)AurumDvbLogoBridge.learn(service,current,fields);if(saved){captured=key;updateStatus("VISIBLE_LOGO_SAVED");service.getSharedPreferences("smarthub",0).edit().putLong("native_logos_changed_at",System.currentTimeMillis()).apply();}else updateStatus("VISIBLE_LOGO_CROP_REJECTED");}catch(Exception ignored){}finally{if(active!=null)active.recycle();if(b!=null)b.recycle();shot.getHardwareBuffer().close();busy=false;}}};if(display)service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY,service.getMainExecutor(),cb);else service.takeScreenshotOfWindow(window,service.getMainExecutor(),cb);}
 private void updateStatus(String status){
  SharedPreferences p=service.getSharedPreferences("smarthub",0);
  if(!status.equals(p.getString("chmax_observe_status","")))
   p.edit().putString("chmax_observe_status",status).apply();
 }
 private boolean save(Bitmap full,Rect rect,Rect screen,SatelliteState state,boolean provider)throws Exception{if(rect==null||screen.width()<=0||screen.height()<=0)return false;int x=Math.max(0,(rect.left-screen.left)*full.getWidth()/screen.width()),y=Math.max(0,(rect.top-screen.top)*full.getHeight()/screen.height()),w=Math.min(full.getWidth()-x,rect.width()*full.getWidth()/screen.width()),h=Math.min(full.getHeight()-y,rect.height()*full.getHeight()/screen.height());if(w<12||h<12)return false;Bitmap crop=Bitmap.createBitmap(full,x,y,w,h),copy=crop.copy(Bitmap.Config.ARGB_8888,false);if(crop!=full)crop.recycle();try{int low=255,high=0;for(int a=0;a<copy.getWidth();a+=4)for(int b=0;b<copy.getHeight();b+=4){int pixel=copy.getPixel(a,b),v=(Color.red(pixel)+Color.green(pixel)+Color.blue(pixel))/3;low=Math.min(low,v);high=Math.max(high,v);}if(high-low<45)return false;String key=SportsCache.key(identity(state))+(provider?"-provider":"-channel")+".png";try(FileOutputStream out=new FileOutputStream(new File(directory(service),key))){copy.compress(Bitmap.CompressFormat.PNG,100,out);}File[] files=directory(service).listFiles();if(files!=null&&files.length>128){Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(int i=0;i<files.length-128;i++)files[i].delete();}return true;}finally{copy.recycle();}}
 private Rect channelRect(AccessibilityNodeInfo root){
  for(String id:CHANNEL_LOGO_IDS){
   Rect found=rect(root,id);
   if(found!=null)return found;
  }
  return null;
 }
 private Rect rect(AccessibilityNodeInfo root,String id){List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(SatelliteUiParser.DEFAULT_PACKAGE+":id/"+id);Rect out=null;for(AccessibilityNodeInfo n:nodes)try{if(n.isVisibleToUser()){Rect r=new Rect();n.getBoundsInScreen(r);if(r.width()>12&&r.height()>12)out=r;}}finally{n.recycle();}return out;}
 private String text(AccessibilityNodeInfo root,String id){List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(SatelliteUiParser.DEFAULT_PACKAGE+":id/"+id);String out="";for(AccessibilityNodeInfo n:nodes)try{if(n.isVisibleToUser()&&n.getText()!=null)out=n.getText().toString();}finally{n.recycle();}return out;}
}
