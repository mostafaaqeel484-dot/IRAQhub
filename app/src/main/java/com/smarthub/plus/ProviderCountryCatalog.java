package com.smarthub.plus;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.*;

/**
 * Country/owner register derived from the user-supplied 2026 workbook.
 *
 * IMPORTANT: TVP0001, TVP0002 etc. are workbook row numbers, NOT broadcast
 * service IDs or official provider identifiers. This index never uses them.
 * For duplicated regional channel names, no flag is guessed.
 */
final class ProviderCountryCatalog {
 static final class Provider {
  final String name,country;final boolean published;
  Provider(String n,String c,boolean p){name=n;country=c;published=p;}
 }
 private static volatile ProviderCountryCatalog INSTANCE;
 private final Map<String,List<Provider>> byProvider=new HashMap<>();
 private final Map<String,List<ChannelRow>> byChannel=new HashMap<>();
 private final List<ArenaRow> arena=new ArrayList<>();
 private final int providerCount,channelRows;
 static final class ChannelRow {
  final String country,provider;final boolean published;
  ChannelRow(String c,String p,boolean b){country=c;provider=p;published=b;}
 }
 private static final class ArenaRow {
  final String physical,country;
  ArenaRow(String p,String c){physical=p;country=c;}
 }
 private ProviderCountryCatalog(Context context){
  int pc=0,cc=0;
  // aapt2 transparently expands *.gz assets, stripping ".gz" in the APK.
  // Read the actual unpacked assets/provider-branding/catalog-v1.json.
  try(InputStream gzip=context.getAssets().open("provider-branding/catalog-v1.json")){
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
   byte[] buffer=new byte[8192];int n;
   while((n=gzip.read(buffer))!=-1){
    if(bytes.size()+n>256000)throw new IllegalArgumentException("PROVIDER_REGISTER_TOO_LARGE");
    bytes.write(buffer,0,n);
   }
   JSONObject root=new JSONObject(bytes.toString("UTF-8"));
   JSONArray providers=root.getJSONArray("p");
   for(int i=0;i<providers.length();i++){
    JSONArray row=providers.getJSONArray(i);String name=row.getString(0);
    String country=row.optString(1,"");
    if(name.trim().isEmpty()||!iso(country))continue;
    Provider p=new Provider(name,country,row.optInt(2,0)!=0);
    byProvider.computeIfAbsent(key(name),k->new ArrayList<>()).add(p);
    for(String alias:name.split("\\s+/\\s+")){
     if(alias.trim().length()>=4)byProvider.computeIfAbsent(key(alias),k->new ArrayList<>()).add(p);
    }
    pc++;
   }
   JSONArray channels=root.getJSONArray("c");
   for(int i=0;i<channels.length();i++){
    JSONArray row=channels.getJSONArray(i);
    String name=row.optString(0,""),country=row.optString(1,"");
    if(name.isEmpty()||!iso(country))continue;
    byChannel.computeIfAbsent(key(name),k->new ArrayList<>())
      .add(new ChannelRow(country,row.optString(2,""),row.optInt(3,0)!=0));
    cc++;
   }
   JSONArray verified=root.optJSONArray("a");
   if(verified!=null)for(int i=0;i<verified.length();i++){
    JSONArray row=verified.optJSONArray(i);
    if(row!=null&&iso(row.optString(1,"")))
     arena.add(new ArenaRow(row.optString(0,""),row.optString(1,"")));
   }
  }catch(Exception malformed){
   android.util.Log.w("RX6Branding","Country register unavailable",malformed);
  }
  providerCount=pc;channelRows=cc;
 }
 static ProviderCountryCatalog get(Context c){
  ProviderCountryCatalog index=INSTANCE;
  if(index==null)synchronized(ProviderCountryCatalog.class){
   index=INSTANCE;if(index==null)INSTANCE=index=new ProviderCountryCatalog(c.getApplicationContext());
  }
  return index;
 }
 static String key(String text){
  return text==null?"":ChannelAliasDatabase.normalize(text.replace(" (CHL)",""));
 }
 static boolean iso(String c){return c!=null&&c.matches("[A-Z]{2}");}
 static String flag(String code){
  if(!iso(code))return "";
  StringBuilder flag=new StringBuilder();
  for(int i=0;i<2;i++)flag.appendCodePoint(0x1f1e6+code.charAt(i)-'A');
  return flag.toString();
 }
 Provider owner(String label,String channel){
  String normalized=key(label);
  if(normalized.contains("AIRTEL")&&normalized.length()<45)
   return new Provider("Airtel Digital TV","IN",true);
  List<Provider> exact=byProvider.get(key(label));
  Provider found=unique(exact);
  if(found!=null)return found;
  List<ChannelRow> rows=byChannel.get(key(channel));
  if(rows==null)return null;
  String published="";
  for(ChannelRow row:rows)if(row.published){
   if(published.isEmpty())published=row.provider;
   else if(!key(published).equals(key(row.provider)))return null;
  }
  if(published.isEmpty())return null;
  return unique(byProvider.get(key(published)));
 }
 private static Provider unique(List<Provider> rows){
  if(rows==null||rows.isEmpty())return null;
  Provider found=rows.get(0);
  for(Provider row:rows)if(!key(found.name).equals(key(row.name)))return null;
  return found;
 }
 String country(String channel,String identity,String providerLabel){
  if(identity!=null&&(identity.startsWith("DVB:1082:")||
       identity.startsWith("DVB:10820:"))&&key(providerLabel).contains("AIRTEL"))return "IN";
  List<ChannelRow> choices=byChannel.get(key(channel));
  if(choices==null||choices.isEmpty())return "";
  // Exact recorded service key overrides a shared channel name when possible.
  String fromService=arenaCountry(identity);
  if(!fromService.isEmpty()){
   for(ChannelRow row:choices)if(fromService.equals(row.country))return fromService;
  }
  LinkedHashSet<String> countries=new LinkedHashSet<>();
  for(ChannelRow row:choices)countries.add(row.country);
  if(countries.size()==1)return countries.iterator().next();
  // "Arena Sport 1" can refer to Serbia, Croatia, Bosnia or Montenegro:
  // use receiver provider identity only when it resolves uniquely.
  Provider provider=owner(providerLabel,"");
  if(provider!=null&&countries.contains(provider.country)){
   for(ChannelRow row:choices)if(row.country.equals(provider.country)&&
      key(row.provider).equals(key(provider.name)))return provider.country;
  }
  return ""; // Better no flag than a false national attribution.
 }
 private String arenaCountry(String identity){
  if(identity==null||!identity.startsWith("DVB:"))return "";
  String[] parts=identity.split(":");
  if(parts.length<7)return "";
  try{
   int orbital=Integer.parseInt(parts[1]),freq=Integer.parseInt(parts[3]);
   int sr=Integer.parseInt(parts[5]),sid=Integer.parseInt(parts[6]);
   String pol=parts[4];String country="";
   for(ArenaRow row:arena){
    String[] p=row.physical.split("\\|");
    if(p.length<7)continue;
    double rawOrbit=Double.parseDouble(p[0].replace("E","").replace("W",""));
    int angle=(int)Math.round(rawOrbit*10);
    if(p[0].endsWith("W"))angle=-angle;
    if(orbital!=angle&&orbital!=angle/10)continue;
    if(Math.abs(Double.parseDouble(p[1])-freq)>2||
       !p[2].equalsIgnoreCase(pol)||Integer.parseInt(p[3])!=sr||
       Integer.parseInt(p[6])!=sid)continue;
    if(country.isEmpty())country=row.country;
    else if(!country.equals(row.country))return "";
   }
   return country;
  }catch(Exception invalid){return "";}
 }
 int providers(){return providerCount;}
 int channels(){return channelRows;}
}
