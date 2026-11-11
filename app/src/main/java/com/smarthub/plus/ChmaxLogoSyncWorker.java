package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/** Downloads the official CHMAX satellite packages used by Aurum and binds
 * every PNG to IRAQhub's exact DVB identity. No private /data path, root or
 * name guessing is used. */
public final class ChmaxLogoSyncWorker extends Worker {
 private static final long MAX_PACKAGE=48L*1024*1024;
 private static final long MAX_TOTAL=640L*1024*1024;
 private static final int MAX_ENTRIES=60_000;
 private static final Pattern FILE=Pattern.compile("(?:^|/)([0-9]{1,4})_([0-9]{3,8})_([0-9]{1,5})\\.png$",Pattern.CASE_INSENSITIVE);
 private static final OkHttpClient HTTP=new OkHttpClient.Builder()
  .connectTimeout(8,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS)
  .callTimeout(55,TimeUnit.SECONDS).followRedirects(false)
  .followSslRedirects(false).retryOnConnectionFailure(false).build();
 public ChmaxLogoSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}

 @NonNull @Override public Result doWork(){
  Context context=getApplicationContext();SharedPreferences p=context.getSharedPreferences("smarthub",0);
  if(PlaybackResourceMode.active()||p.getBoolean("software_dvb_422_active",false)||p.getBoolean("dvb_tuning",false))return Result.retry();
  p.edit().putString("chmax_package_status","REQUESTING").apply();
  try{
   ChlChannelIndex index=ChlLogoAddon.get(context).snapshot();
   if(index==null||index.getServices().isEmpty())throw new IOException("CHMAX_CHANNEL_INDEX_REQUIRED");
   TreeSet<Integer> angles=new TreeSet<>();
   Map<String,List<ChlChannelIndex.Service>> services=new HashMap<>();
   for(ChlChannelIndex.Service service:index.getServices()){
    angles.add(service.getSat());
    services.computeIfAbsent(service.getSat()+"_"+service.getFrequency()+"_"+service.getSid(),k->new ArrayList<>()).add(service);
   }
   JSONArray sats=new JSONArray();for(int angle:angles)sats.put(new JSONObject().put("sat",Integer.toString(angle)));
   JSONArray packages=new FactoryClient(context).iconPackages(sats);
   int packageCount=0,matched=0,entries=0;long downloaded=0;
   Set<Integer> returned=new HashSet<>();
   for(int i=0;i<Math.min(packages.length(),128);i++){
    if(isStopped())throw new InterruptedIOException("FACTORY_CANCELLED");
    JSONObject row=packages.optJSONObject(i);if(row==null)continue;
    int sat=parse(row.opt("sat"));String reference=row.optString("iconPackageUrl","").trim();
    if(sat<0||!angles.contains(sat)||reference.isEmpty()||!returned.add(sat))continue;
    String marker=digest(reference);String old=p.getString("chmax_package_hash:"+sat,"");
    if(marker.equals(old)&&p.getInt("chmax_package_matches:"+sat,0)>0){
     matched+=p.getInt("chmax_package_matches:"+sat,0);packageCount++;continue;
    }
    byte[] zip=download(reference);
    downloaded+=zip.length;if(downloaded>MAX_TOTAL)throw new IOException("CHMAX_TOTAL_TOO_LARGE");
    int[] result=extract(context,zip,sat,services,MAX_ENTRIES-entries);
    entries+=result[0];matched+=result[1];packageCount++;
    p.edit().putString("chmax_package_hash:"+sat,marker)
     .putInt("chmax_package_matches:"+sat,result[1]).apply();
    if(entries>=MAX_ENTRIES)throw new IOException("CHMAX_TOO_MANY_ENTRIES");
   }
   String status=packageCount==0?"CHMAX_NO_PACKAGES":matched==0?"CHMAX_PACKAGES_WITHOUT_MATCHES":"CHMAX_PACKAGES_READY";
   p.edit().putString("chmax_package_status",status).putInt("chmax_package_count",packageCount)
    .putInt("chmax_official_logo_count",matched).putLong("chmax_package_bytes",downloaded)
    .putLong("chmax_package_synced_at",System.currentTimeMillis())
    .putLong("logo_mapping_changed_at",System.currentTimeMillis()).apply();
   LogoRepository.get(context).invalidate();
   return matched>0?Result.success():Result.retry();
  }catch(Exception failure){
   String message=failure.getMessage()==null?"":failure.getMessage();
   String safe=message.matches("CHMAX_[A-Z0-9_]{1,60}")?message:FactoryClient.safe(failure);
   p.edit().putString("chmax_package_status",safe).apply();
   return getRunAttemptCount()<3?Result.retry():Result.failure();
  }
 }
 private static int parse(Object value){try{return Integer.parseInt(String.valueOf(value));}catch(Exception e){return -1;}}
 private static byte[] download(String reference)throws Exception{
  Exception last=null;
  for(String address:candidates(reference))try{
   try(Response response=HTTP.newCall(new Request.Builder().url(address).header("User-Agent","IRAQhub/4.0 CHMAX package").build()).execute()){
    if(response.code()>=300&&response.code()<400)throw new IOException("CHMAX_REDIRECT_REJECTED");
    if(!response.isSuccessful())throw new IOException("CHMAX_PACKAGE_HTTP_"+response.code());
    ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=response.body().byteStream()){
     byte[] buffer=new byte[32*1024];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_PACKAGE)throw new IOException("CHMAX_PACKAGE_TOO_LARGE");out.write(buffer,0,n);}
    }
    byte[] bytes=out.toByteArray();if(bytes.length<4||bytes[0]!='P'||bytes[1]!='K')throw new IOException("CHMAX_PACKAGE_INVALID");return bytes;
   }
  }catch(Exception failure){last=failure;}
  throw new IOException("CHMAX_PACKAGE_UNAVAILABLE",last);
 }
 private static List<String> candidates(String reference)throws Exception{
  String raw=reference==null?"":reference.trim();
  if(raw.length()<1||raw.length()>600||raw.indexOf('\\')>=0)throw new IOException("CHMAX_PACKAGE_REFERENCE_INVALID");
  // Firmware releases have returned the same official reference in four
  // shapes: a relative stem, a dotted relative stem, a host/path without a
  // scheme and a complete HTTP(S) URL.  Canonicalise all of them to the one
  // TLS origin instead of rejecting valid packages or following redirects.
  if(raw.startsWith("//"))raw="https:"+raw;
  if(raw.toLowerCase(Locale.ROOT).startsWith("gchsicon.datasver.com/"))raw="https://"+raw;
  String path;
  if(raw.regionMatches(true,0,"https://",0,8)||raw.regionMatches(true,0,"http://",0,7)){
   URI u=new URI(raw);
   int port=u.getPort();String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT);
   if(u.getUserInfo()!=null||u.getHost()==null||!"gchsicon.datasver.com".equalsIgnoreCase(u.getHost())||
    !(port==-1||("https".equals(scheme)&&port==443)||("http".equals(scheme)&&port==80))||
    u.getQuery()!=null||u.getFragment()!=null)throw new IOException("CHMAX_PACKAGE_HOST_REJECTED");
   path=u.getPath();
  }else path=raw;
  path=path.replaceFirst("^/+","");
  if(path.length()<1||path.length()>500||!path.matches("[A-Za-z0-9._/-]+"))throw new IOException("CHMAX_PACKAGE_REFERENCE_INVALID");
  for(String segment:path.split("/",-1))if(segment.isEmpty()||".".equals(segment)||"..".equals(segment))throw new IOException("CHMAX_PACKAGE_REFERENCE_INVALID");
  String packagePath=path.endsWith("_png.zip")||path.endsWith(".zip")?path:path+"_png.zip";
  ArrayList<String> out=new ArrayList<>();
  out.add("https://gchsicon.datasver.com/"+packagePath);
  if(!packagePath.startsWith("icon/"))out.add("https://gchsicon.datasver.com/icon/"+packagePath);
  return out;
 }
 private static int[] extract(Context context,byte[] zip,int expectedSat,Map<String,List<ChlChannelIndex.Service>> services,int remaining)throws Exception{
  int entries=0,matches=0;long inflated=0;
  try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(zip))){
   ZipEntry entry;byte[] buffer=new byte[16*1024];
   while((entry=input.getNextEntry())!=null){
    if(entry.isDirectory())continue;if(++entries>remaining)throw new IOException("CHMAX_TOO_MANY_ENTRIES");
    Matcher name=FILE.matcher(entry.getName());if(!name.find())continue;
    int sat=parse(name.group(1)),frequency=parse(name.group(2)),sid=parse(name.group(3));
    if(sat!=expectedSat)throw new IOException("CHMAX_PACKAGE_SAT_MISMATCH");
    List<ChlChannelIndex.Service> rows=services.get(sat+"_"+frequency+"_"+sid);if(rows==null||rows.isEmpty())continue;
    ByteArrayOutputStream png=new ByteArrayOutputStream();int n;
    while((n=input.read(buffer))!=-1){inflated+=n;if(png.size()+n>4*1024*1024||inflated>MAX_PACKAGE*5)throw new IOException("CHMAX_ZIP_BOMB_REJECTED");png.write(buffer,0,n);}
    byte[] bytes=png.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
    if(!"image/png".equals(bounds.outMimeType)||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>8192||bounds.outHeight>8192)continue;
    for(ChlChannelIndex.Service service:rows){
     write(context,"channel:physical:v2:DVB:"+service.getPhysicalKey(),bytes);matches++;
    }
   }
  }
  return new int[]{entries,matches};
 }
 private static void write(Context context,String key,byte[] bytes)throws Exception{
  File dir=new File(context.getFilesDir(),"resolved-logos");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("CHMAX_CACHE_UNAVAILABLE");
  AtomicFile file=new AtomicFile(new File(dir,digest(key)+".png"));FileOutputStream out=file.startWrite();
  try{out.write(bytes);file.finishWrite(out);}catch(Exception failure){file.failWrite(out);throw failure;}
 }
 private static String digest(String text)throws Exception{
  StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();
 }
}
