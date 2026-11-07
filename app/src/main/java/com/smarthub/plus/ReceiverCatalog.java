package com.smarthub.plus;

import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.io.*;import java.util.*;

/** App-owned receiver database. Imported IDs never grant access to the manufacturer's database. */
final class ReceiverCatalog extends SQLiteOpenHelper {
 private static volatile ReceiverCatalog instance;private final Context context;
 static ReceiverCatalog get(Context c){ReceiverCatalog r=instance;if(r==null)synchronized(ReceiverCatalog.class){r=instance;if(r==null)instance=r=new ReceiverCatalog(c.getApplicationContext());}return r;}
 private ReceiverCatalog(Context c){super(c,"receiver-catalog.db",null,3);context=c;setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE sat(_id INTEGER PRIMARY KEY,name TEXT NOT NULL,angle INTEGER NOT NULL,band TEXT NOT NULL)");db.execSQL("CREATE TABLE tp(_id INTEGER PRIMARY KEY,sat_id INTEGER NOT NULL,freq INTEGER NOT NULL,pol TEXT NOT NULL,sr INTEGER NOT NULL,fec TEXT NOT NULL)");db.execSQL("CREATE TABLE ch(_id INTEGER PRIMARY KEY,tp_id INTEGER NOT NULL,name TEXT NOT NULL,provider TEXT NOT NULL,sid INTEGER NOT NULL,tv INTEGER NOT NULL,ca INTEGER NOT NULL,search_key TEXT NOT NULL,data TEXT NOT NULL,order_index INTEGER NOT NULL)");db.execSQL("CREATE INDEX ch_tp ON ch(tp_id)");db.execSQL("CREATE INDEX ch_order ON ch(order_index)");db.execSQL("CREATE INDEX tp_sat ON tp(sat_id)");db.execSQL("CREATE TABLE favorite(channel_id INTEGER PRIMARY KEY)");createGroups(db);}
 private static void createGroups(SQLiteDatabase db){db.execSQL("CREATE TABLE fav_group(_id INTEGER PRIMARY KEY,name TEXT NOT NULL)");db.execSQL("CREATE TABLE fav_member(group_id INTEGER NOT NULL,channel_id INTEGER NOT NULL,ordinal INTEGER NOT NULL,PRIMARY KEY(group_id,channel_id))");}
 public void onUpgrade(SQLiteDatabase db,int old,int version){if(old<2)createGroups(db);if(old<3){db.execSQL("ALTER TABLE ch ADD COLUMN order_index INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE ch SET order_index=_id");db.execSQL("CREATE INDEX IF NOT EXISTS ch_order ON ch(order_index)");}}
 static final class Group {final long id;final String name;final int count;Group(long i,String n,int c){id=i;name=n;count=c;}}
 List<Group> groups(){List<Group> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT g._id,g.name,COUNT(m.channel_id) FROM fav_group g LEFT JOIN fav_member m ON g._id=m.group_id GROUP BY g._id ORDER BY g._id",null)){while(c.moveToNext())out.add(new Group(c.getLong(0),c.getString(1),c.getInt(2)));}return out;}

 synchronized long createGroup(String name){SQLiteDatabase db=getWritableDatabase();ContentValues values=new ContentValues();values.put("_id",Math.max(1000000L,DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(_id),0)+1 FROM fav_group",null)));values.put("name",groupName(name));return db.insertOrThrow("fav_group",null,values);}
 void renameGroup(long id,String name){ContentValues v=new ContentValues();v.put("name",groupName(name));getWritableDatabase().update("fav_group",v,"_id=?",new String[]{Long.toString(id)});}
 void deleteGroup(long id){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("fav_member","group_id=?",new String[]{Long.toString(id)});db.delete("fav_group","_id=?",new String[]{Long.toString(id)});db.setTransactionSuccessful();}finally{db.endTransaction();}}
 boolean member(long group,long channel){return DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM fav_member WHERE group_id=? AND channel_id=?",new String[]{Long.toString(group),Long.toString(channel)})>0;}
 void member(long group,long channel,boolean enabled){SQLiteDatabase db=getWritableDatabase();if(!enabled){db.delete("fav_member","group_id=? AND channel_id=?",new String[]{Long.toString(group),Long.toString(channel)});return;}db.execSQL("INSERT OR IGNORE INTO fav_member(group_id,channel_id,ordinal) SELECT ?,?,COALESCE((SELECT MAX(ordinal)+1 FROM fav_member WHERE group_id=?),0) WHERE EXISTS(SELECT 1 FROM ch WHERE _id=?) AND EXISTS(SELECT 1 FROM fav_group WHERE _id=?)",new Object[]{group,channel,group,channel,group});}
 private static String groupName(String value){String name=value==null?"":value.trim();if(name.isEmpty()||name.length()>80||name.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid favourite group name");return name;}
 static final class Sat {final long id;final String name,band;final int angle;Sat(long id,String name,int angle,String band){this.id=id;this.name=name;this.angle=angle;this.band=band;}String label(){return name+" ("+ReceiverChannelRepository.orbital(angle)+")";}}
 static final class Channel {
  final long id;final String name,provider,satName,band,pol,fec;final int satId,angle,tpId,frequency,symbolRate,sid,ca,tv;final JSONObject data;
  Channel(Cursor c)throws Exception{id=c.getLong(0);name=c.getString(1);provider=c.getString(2);sid=c.getInt(3);tv=c.getInt(4);ca=c.getInt(5);data=new JSONObject(c.getString(6));tpId=c.getInt(7);frequency=c.getInt(8);pol=c.getString(9);symbolRate=c.getInt(10);fec=c.getString(11);satId=c.getInt(12);satName=c.getString(13);angle=c.getInt(14);band=c.getString(15);}
  String physical(){return angle+":"+band+":"+frequency+":"+pol+":"+symbolRate+":"+sid;}
  String transponder(){return angle+":"+band+":"+frequency+":"+pol+":"+symbolRate;}
  int programToken(){return 1_100_000_000+(int)id;}
  String rf(){return frequency+" "+pol+"  |  "+symbolRate+"  |  "+satName;}
  JSONArray audio(){JSONArray a=data.optJSONArray("Audio");return a==null?new JSONArray():a;}
 }
 private static final String JOIN="SELECT c._id,c.name,c.provider,c.sid,c.tv,c.ca,c.data,t._id,t.freq,t.pol,t.sr,t.fec,s._id,s.name,s.angle,s.band FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id";
 List<Sat> satellites(){List<Sat> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,name,angle,band FROM sat ORDER BY _id",null)){while(c.moveToNext())out.add(new Sat(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3)));}return out;}
 int count(){return (int)DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM ch",null);}
 /** Number visible TV and radio lists independently from 1 per satellite.
  * Imported CHL IDs are not user-visible channel numbers. Called off the UI thread. */
 Map<Long,Integer> satelliteNumbers(){
  Map<Long,Integer> out=new HashMap<>();long previous=Long.MIN_VALUE;int previousType=-1,ordinal=0;
  try(Cursor c=getReadableDatabase().rawQuery(
    "SELECT c._id,t.sat_id,c.tv FROM ch c JOIN tp t ON c.tp_id=t._id "+
    "ORDER BY t.sat_id,c.tv,c.order_index,c._id",null)){
   while(c.moveToNext()){
    long sat=c.getLong(1);int type=c.getInt(2);
    if(sat!=previous||type!=previousType){ordinal=0;previous=sat;previousType=type;}
    out.put(c.getLong(0),++ordinal);
   }
  }return out;
 }
 /** Ambiguous source exports may contain two different services with the same
  * satellite/RF/SID identity. Never attach a physical-key logo to those rows:
  * a correct logo for one service would be wrong for the other.
  */
 Set<String> ambiguousPhysicalKeys(){
  Set<String> ambiguous=new HashSet<>();
  String sql="SELECT s.angle,s.band,t.freq,t.pol,t.sr,c.sid "+
    "FROM ch c JOIN tp t ON c.tp_id=t._id JOIN sat s ON t.sat_id=s._id "+
    "GROUP BY s.angle,s.band,t.freq,t.pol,t.sr,c.sid HAVING COUNT(*)>1";
  try(Cursor cursor=getReadableDatabase().rawQuery(sql,null)){
   while(cursor.moveToNext())ambiguous.add(cursor.getInt(0)+":"+cursor.getString(1)+":"+
     cursor.getInt(2)+":"+cursor.getString(3)+":"+cursor.getInt(4)+":"+cursor.getInt(5));
  }
  return ambiguous;
 }
 int satelliteNumber(Channel active){
  if(active==null)return 0;int order=0;
  try(Cursor c=getReadableDatabase().rawQuery("SELECT order_index FROM ch WHERE _id=?",
    new String[]{Long.toString(active.id)})){if(!c.moveToFirst())return 0;order=c.getInt(0);}
  try(Cursor c=getReadableDatabase().rawQuery(
    "SELECT COUNT(*) FROM ch c JOIN tp t ON c.tp_id=t._id "+
    "WHERE t.sat_id=? AND c.tv=? AND (c.order_index<? OR (c.order_index=? AND c._id<=?))",
    new String[]{Integer.toString(active.satId),Integer.toString(active.tv),Integer.toString(order),
      Integer.toString(order),Long.toString(active.id)})){
   return c.moveToFirst()?c.getInt(0):0;
  }catch(Exception unavailable){return 0;}
 }

 List<String> transponders(long satellite){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT freq,pol,sr,fec FROM tp WHERE sat_id=? ORDER BY freq,pol",new String[]{Long.toString(satellite)})){while(c.moveToNext())out.add(c.getInt(0)+" "+c.getString(1)+" · "+c.getInt(2)+(c.getString(3).isEmpty()?"":" · "+c.getString(3)));}return out;}
 Set<Long> favorites(){Set<Long> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT channel_id FROM favorite",null)){while(c.moveToNext())out.add(c.getLong(0));}return out;}
 /**
  * Resolve an Aurum logo to exactly one imported DVB physical service. Frequency,
  * polarization, symbol rate and service name must match. Never use the list
  * position or an Aurum channel number as SID.
  */
 String uniquePhysicalForObservedLogo(String name,int frequency,String polarity,int symbolRate){
  if(name==null||name.isEmpty()||frequency<1000||frequency>50000||
     symbolRate<1000||symbolRate>70000||!Arrays.asList("H","V","L","R").contains(polarity))return "";
  String wanted=ChannelCatalogue.normalized(name),found="";
  String sql="SELECT c.name,s.angle,s.band,t.freq,t.pol,t.sr,c.sid "+
    "FROM ch c JOIN tp t ON c.tp_id=t._id JOIN sat s ON t.sat_id=s._id "+
    "WHERE t.freq=? AND t.pol=? AND t.sr=? LIMIT 2049";
  int visited=0;
  try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{
      Integer.toString(frequency),polarity,Integer.toString(symbolRate)})){
   while(c.moveToNext()){
    if(++visited>2048)return "";
    if(!wanted.equals(ChannelCatalogue.normalized(c.getString(0))))continue;
    String physical="DVB:"+c.getInt(1)+":"+c.getString(2)+":"+
      c.getInt(3)+":"+c.getString(4)+":"+c.getInt(5)+":"+c.getInt(6);
    if(!found.isEmpty()&&!found.equals(physical))return "";
    found=physical;
   }
  }catch(RuntimeException unavailable){return "";}
  return found;
 }
 long uniqueChannel(String name){if(name==null||name.isEmpty())return -1;try(Cursor c=getReadableDatabase().rawQuery("SELECT _id FROM ch WHERE name=? COLLATE NOCASE LIMIT 2",new String[]{name})){if(!c.moveToFirst())return -1;long id=c.getLong(0);return c.moveToNext()?-1:id;}}
 String currentTransponder(String name){if(name==null||name.isEmpty())return "";try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT s.angle,s.band,t.freq,t.pol,t.sr FROM ch c JOIN tp t ON c.tp_id=t._id JOIN sat s ON t.sat_id=s._id WHERE c.name=? COLLATE NOCASE LIMIT 2",new String[]{name})){if(!c.moveToFirst())return "";String key=c.getInt(0)+":"+c.getString(1)+":"+c.getInt(2)+":"+c.getString(3)+":"+c.getInt(4);return c.moveToNext()?"":key;}}
 List<Channel> sameTransponder(Channel active){List<Channel> out=new ArrayList<>();Set<String> seen=new HashSet<>();String sql=JOIN+" WHERE s.angle=? AND s.band=? AND t.freq=? AND t.pol=? AND t.sr=? AND c.tv=0 AND c.ca=0 ORDER BY c._id";try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{Integer.toString(active.angle),active.band,Integer.toString(active.frequency),active.pol,Integer.toString(active.symbolRate)})){while(c.moveToNext())try{Channel row=new Channel(c);if(seen.add(row.physical()))out.add(row);}catch(Exception invalid){}}return out;}
 List<Channel> channels(String query,long satellite,int type,boolean favorite,boolean alphabetical){return channels(query,satellite,type,favorite,alphabetical,0);}
 List<Channel> channels(String query,long satellite,int type,boolean favorite,boolean alphabetical,int offset){return channels(query,satellite,type,favorite,alphabetical,offset,-1);}
 List<Channel> channels(String query,long satellite,int type,boolean favorite,boolean alphabetical,int offset,long group){
  return channels(query,satellite,type,favorite,alphabetical,offset,group,false);
 }
 List<Channel> channels(String query,long satellite,int type,boolean favorite,boolean alphabetical,int offset,long group,boolean encryptedOnly){StringBuilder sql=new StringBuilder(JOIN+" WHERE instr(c.search_key || t.freq || lower(t.pol) || t.sr,?)>0");List<String> args=new ArrayList<>();args.add(ReceiverChannelRepository.searchKey(query));if(satellite>=0){sql.append(" AND s._id=?");args.add(Long.toString(satellite));}if(type>=0){sql.append(" AND c.tv=?");args.add(Integer.toString(type));}if(encryptedOnly)sql.append(" AND c.ca>0");if(favorite)sql.append(" AND EXISTS(SELECT 1 FROM favorite f WHERE f.channel_id=c._id)");if(group>=0){sql.append(" AND EXISTS(SELECT 1 FROM fav_member m WHERE m.channel_id=c._id AND m.group_id=?)");args.add(Long.toString(group));}sql.append(alphabetical?" ORDER BY c.name COLLATE NOCASE,c._id":group>=0?" ORDER BY (SELECT m.ordinal FROM fav_member m WHERE m.channel_id=c._id AND m.group_id="+group+"),c._id":" ORDER BY c.order_index,c._id");sql.append(" LIMIT 120 OFFSET ").append(Math.max(0,offset));List<Channel> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]))){while(c.moveToNext())try{out.add(new Channel(c));}catch(Exception malformed){}}return out;}
 int pageFor(Channel channel){return pageFor(channel,new ChannelScope("",channel.satId,channel.tv,false,false,-1));}
 Channel adjacent(Channel active,int direction){return adjacent(active,direction,new ChannelScope("",active.satId,active.tv,false,false,-1));}
 private String scopeWhere(ChannelScope scope,List<String> args){StringBuilder sql=new StringBuilder(" WHERE instr(c.search_key || t.freq || lower(t.pol) || t.sr,?)>0");args.add(ReceiverChannelRepository.searchKey(scope.query));if(scope.satellite>=0){sql.append(" AND s._id=?");args.add(Long.toString(scope.satellite));}sql.append(" AND c.tv=?");args.add(Integer.toString(scope.type));if(scope.favorites)sql.append(" AND EXISTS(SELECT 1 FROM favorite f WHERE f.channel_id=c._id)");if(scope.group>=0){sql.append(" AND EXISTS(SELECT 1 FROM fav_member m WHERE m.channel_id=c._id AND m.group_id=?)");args.add(Long.toString(scope.group));}return sql.toString();}
 private String scopeOrder(ChannelScope scope){return scope.alphabetical?" ORDER BY c.name COLLATE NOCASE,c._id":scope.group>=0?" ORDER BY (SELECT m.ordinal FROM fav_member m WHERE m.channel_id=c._id AND m.group_id="+scope.group+"),c._id":" ORDER BY c.order_index,c._id";}
 ChannelScope validScope(ChannelScope scope){SQLiteDatabase db=getReadableDatabase();long sat=scope.satellite,group=scope.group;if(sat>=0&&DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM sat WHERE _id=?",new String[]{Long.toString(sat)})==0)sat=-1;if(group>=0&&DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM fav_group WHERE _id=?",new String[]{Long.toString(group)})==0)group=-1;return new ChannelScope(scope.query,sat,scope.type,scope.favorites,scope.alphabetical,group);}
 List<Long> navigationIds(ChannelScope scope){List<String> args=new ArrayList<>();String where=scopeWhere(scope,args);List<Long> ids=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT c._id FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id"+where+scopeOrder(scope),args.toArray(new String[0]))){while(c.moveToNext())ids.add(c.getLong(0));}return ids;}
 int pageFor(Channel channel,ChannelScope scope){int index=navigationIds(scope).indexOf(channel.id);return index<0?0:index/120*120;}
 Channel adjacent(Channel active,int direction,ChannelScope scope){
  return adjacent(active,direction,scope,1);
 }
 Channel adjacent(Channel active,int direction,ChannelScope scope,int steps){
  if(active==null)return null;
  ChannelScope tuned=scope==null?new ChannelScope("",-1,active.tv,false,false,-1):scope;
  if(tuned.type!=active.tv)tuned=tuned.kind(active.tv);
  List<Long> ids=navigationIds(tuned);
  int position=ids.indexOf(active.id);
  if(position<0){
   // A previous search/favourites filter must not trap CH+/CH- in the wrong list.
   tuned=new ChannelScope("",-1,active.tv,false,tuned.alphabetical,-1);
   ids=navigationIds(tuned);
   position=ids.indexOf(active.id);
  }
  if(ids.isEmpty())return null;
  int index=position<0?(direction>0?0:ids.size()-1):
    Math.floorMod(position+(direction>0?1:-1)*Math.min(Math.max(1,steps),50),ids.size());
  return channel(ids.get(index));
 }
 JSONArray transportServices(Channel ch)throws Exception{JSONArray out=new JSONArray();try(Cursor cursor=getReadableDatabase().rawQuery(JOIN+" WHERE s.angle=? AND s.band=? AND t.freq=? AND t.pol=? AND t.sr=? ORDER BY c._id LIMIT 512",new String[]{Integer.toString(ch.angle),ch.band,Integer.toString(ch.frequency),ch.pol,Integer.toString(ch.symbolRate)})){while(cursor.moveToNext()){Channel s=new Channel(cursor);out.put(new JSONObject().put("sid",s.sid).put("pmt",s.data.optInt("PmtPID",8191)).put("ca",s.ca));}}return out;}
 Channel channel(long id){try(Cursor c=getReadableDatabase().rawQuery(JOIN+" WHERE c._id=?",new String[]{Long.toString(id)})){return c.moveToFirst()?new Channel(c):null;}catch(Exception unavailable){return null;}}
 /**
  * Reorder only IRAQhub's imported catalogue. Database identity/SID, EPG and
  * CHMAX physical channel IDs stay stable; OEM firmware DB is untouched.
  */
 synchronized boolean moveLocal(long id,int direction){
  if(direction!=1&&direction!=-1)return false;
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   long current;
   try(Cursor row=db.rawQuery("SELECT order_index FROM ch WHERE _id=?",
        new String[]{Long.toString(id)})){
    if(!row.moveToFirst())return false;
    current=row.getLong(0);
   }
   long other=-1,rank=0;
   String sign=direction<0?"<":">",sort=direction<0?"DESC":"ASC";
   try(Cursor row=db.rawQuery("SELECT _id,order_index FROM ch WHERE order_index "+sign+
     "? ORDER BY order_index "+sort+",_id "+sort+" LIMIT 1",
     new String[]{Long.toString(current)})){
    if(!row.moveToFirst())return false;
    other=row.getLong(0);rank=row.getLong(1);
   }
   db.execSQL("UPDATE ch SET order_index=? WHERE _id=?",new Object[]{rank,id});
   db.execSQL("UPDATE ch SET order_index=? WHERE _id=?",new Object[]{current,other});
   db.setTransactionSuccessful();
   return true;
  }finally{db.endTransaction();}
 }
 synchronized boolean deleteLocal(long id){
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM ch",null)<=1)return false;
   String key=Long.toString(id);
   db.delete("fav_member","channel_id=?",new String[]{key});
   db.delete("favorite","channel_id=?",new String[]{key});
   boolean deleted=db.delete("ch","_id=?",new String[]{key})>0;
   if(deleted)db.setTransactionSuccessful();
   return deleted;
  }finally{db.endTransaction();}
 }

 /** Editable data in IRAQhub's own database; never writes to the OEM programme DB. */
 static final class Tp {
  final long id,satellite;final int frequency,symbolRate;final String polarity,fec;
  Tp(long id,long satellite,int frequency,String polarity,int symbolRate,String fec){
   this.id=id;this.satellite=satellite;this.frequency=frequency;this.polarity=polarity;
   this.symbolRate=symbolRate;this.fec=fec;
  }
  String label(){return frequency+" "+polarity+"  ·  "+symbolRate+(fec.isEmpty()?"":"  ·  "+fec);}
 }
 List<Tp> tpRows(long satellite){
  List<Tp> out=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery(
    "SELECT _id,sat_id,freq,pol,sr,fec FROM tp WHERE sat_id=? ORDER BY freq,pol,sr",
    new String[]{Long.toString(satellite)})){
   while(c.moveToNext())out.add(new Tp(c.getLong(0),c.getLong(1),c.getInt(2),
     c.getString(3),c.getInt(4),c.getString(5)));
  }
  return out;
 }
 synchronized long addSatelliteLocal(String name,int angle,String band)throws IOException{
  String n=name==null?"":name.trim(),b=band==null?"":band.toUpperCase(Locale.ROOT);
  if(n.isEmpty()||n.length()>80||n.chars().anyMatch(c->c<32)
    ||angle<0||angle>=3600||(!b.equals("KU")&&!b.equals("C")))
   throw new IOException("INVALID_SATELLITE");
  SQLiteDatabase db=getWritableDatabase();
  if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM sat WHERE angle=? AND band=?",
    new String[]{Integer.toString(angle),b})>0)throw new IOException("SATELLITE_ALREADY_EXISTS");
  long id=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(_id),-1)+1 FROM sat",null);
  if(id>1000000)throw new IOException("SATELLITE_LIMIT");
  ContentValues values=new ContentValues();
  values.put("_id",id);values.put("name",n);values.put("angle",angle);values.put("band",b);
  if(db.insert("sat",null,values)<0)throw new IOException("SATELLITE_INSERT_FAILED");
  context.getSharedPreferences("smarthub",0).edit().putLong("installation_changed_at",System.currentTimeMillis()).apply();
  return id;
 }
 synchronized boolean renameSatelliteLocal(long id,String name){
  String n=name==null?"":name.trim();
  if(n.isEmpty()||n.length()>80||n.chars().anyMatch(c->c<32))return false;
  ContentValues values=new ContentValues();values.put("name",n);
  boolean changed=getWritableDatabase().update("sat",values,"_id=?",new String[]{Long.toString(id)})>0;
  if(changed)context.getSharedPreferences("smarthub",0).edit().putLong("installation_changed_at",System.currentTimeMillis()).apply();
  return changed;
 }
 synchronized long addTransponderLocal(long satellite,int freq,String pol,int sr)throws IOException{
  String p=pol==null?"":pol.toUpperCase(Locale.ROOT);
  if(freq<100||freq>100000||sr<1||sr>100000||(!p.equals("H")&&!p.equals("V")))
   throw new IOException("INVALID_TRANSPONDER");
  SQLiteDatabase db=getWritableDatabase();
  if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM sat WHERE _id=?",
      new String[]{Long.toString(satellite)})!=1)throw new IOException("SATELLITE_NOT_FOUND");
  if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM tp WHERE sat_id=? AND freq=? AND pol=? AND sr=?",
    new String[]{Long.toString(satellite),Integer.toString(freq),p,Integer.toString(sr)})>0)
   throw new IOException("TRANSPONDER_ALREADY_EXISTS");
  long id=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(_id),-1)+1 FROM tp",null);
  if(id>1000000)throw new IOException("TRANSPONDER_LIMIT");
  ContentValues v=new ContentValues();
  v.put("_id",id);v.put("sat_id",satellite);v.put("freq",freq);v.put("pol",p);v.put("sr",sr);v.put("fec","");
  if(db.insert("tp",null,v)<0)throw new IOException("TRANSPONDER_INSERT_FAILED");
  context.getSharedPreferences("smarthub",0).edit().putLong("installation_changed_at",System.currentTimeMillis()).apply();
  return id;
 }
 synchronized boolean removeEmptyTpLocal(long id){
  SQLiteDatabase db=getWritableDatabase();
  if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM ch WHERE tp_id=?",
      new String[]{Long.toString(id)})>0)return false;
  boolean deleted=db.delete("tp","_id=?",new String[]{Long.toString(id)})>0;
  if(deleted)context.getSharedPreferences("smarthub",0).edit().putLong("installation_changed_at",System.currentTimeMillis()).apply();
  return deleted;
 }
 /** Saves only complete PSI-discovered services, with their verified RF context
  * supplied by the user. Never touches the OEM programme content provider.
  * Existing CHL data, channel order, groups, and favourites are preserved.
  */
 static final class PsiImportResult {
  final int added,duplicates,invalid;
  PsiImportResult(int added,int duplicates,int invalid){
   this.added=added;this.duplicates=duplicates;this.invalid=invalid;
  }
 }
 synchronized PsiImportResult savePsiServices(long satelliteId,int frequency,String polarity,
     int symbolRate,DvbPsiScan.Result report)throws Exception{
  if(report==null||report.services.length()==0||report.services.length()>512
     ||frequency<100||frequency>100000||symbolRate<1||symbolRate>100000
     ||(!"H".equals(polarity)&&!"V".equals(polarity)))
   throw new IOException("TS_IMPORT_INVALID");
  SQLiteDatabase db=getWritableDatabase();
  String satKey=Long.toString(satelliteId);
  if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM sat WHERE _id=?",
      new String[]{satKey})!=1)throw new IOException("TS_IMPORT_SATELLITE_NOT_FOUND");
  int added=0,duplicates=0,invalid=0;
  db.beginTransaction();
  try{
   long tpId=-1;
   try(Cursor existing=db.rawQuery(
       "SELECT _id FROM tp WHERE sat_id=? AND freq=? AND pol=? AND sr=? LIMIT 1",
       new String[]{satKey,Integer.toString(frequency),polarity,Integer.toString(symbolRate)})){
    if(existing.moveToFirst())tpId=existing.getLong(0);
   }
   if(tpId<0){
    tpId=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(_id),-1)+1 FROM tp",null);
    if(tpId>1000000)throw new IOException("TS_IMPORT_TP_LIMIT");
    ContentValues tp=new ContentValues();tp.put("_id",tpId);tp.put("sat_id",satelliteId);
    tp.put("freq",frequency);tp.put("pol",polarity);tp.put("sr",symbolRate);tp.put("fec","");
    db.insertOrThrow("tp",null,tp);
   }
   long channelId=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(_id),0) FROM ch",null);
   long rank=DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(order_index),0) FROM ch",null);
   for(int i=0;i<report.services.length();i++){
    JSONObject service=report.services.optJSONObject(i);
    if(service==null){invalid++;continue;}
    int sid=service.optInt("sid",-1),pmt=service.optInt("pmt",-1),
      pcr=service.optInt("pcr",-1),video=service.optInt("videoPid",-1),
      tv=service.optInt("tv",-1),ca=service.optInt("ca",-1);
    String name=service.optString("name","").trim(),provider=service.optString("provider","").trim();
    String codec=service.optString("videoType","");
    JSONArray audio=service.optJSONArray("audio");
    if(sid<1||sid>65535||pmt<1||pmt>=8191||pcr<1||pcr>=8191
       ||name.isEmpty()||name.length()>120||provider.length()>120
       ||name.chars().anyMatch(c->c<32)||provider.chars().anyMatch(c->c<32)
       ||(tv!=0&&tv!=1)||(ca!=0&&ca!=1)
       ||(tv==0&&(video<1||video>=8191||DvbPlayRequest.video(codec)<0))
       ||(tv==1&&(audio==null||audio.length()==0))){
     invalid++;continue;
    }
    if(DatabaseUtils.longForQuery(db,
       "SELECT COUNT(*) FROM ch WHERE tp_id=? AND sid=?",
       new String[]{Long.toString(tpId),Integer.toString(sid)})>0){duplicates++;continue;}
    if(channelId>=1000000)throw new IOException("TS_IMPORT_CHANNEL_ID_LIMIT");
    JSONObject metadata=new JSONObject();
    metadata.put("PmtPID",pmt).put("PcrPID",pcr)
      .put("VideoPID",tv==1?8191:video).put("VideoType",tv==1?"":codec)
      .put("Audio",audio==null?new JSONArray():audio).put("Sub",new JSONArray())
      .put("CaSystemIdList",service.optJSONArray("caIds")==null
          ?new JSONArray():service.optJSONArray("caIds"))
      .put("Tsid",report.transportId).put("Onid",report.networkId)
      .put("Discovery","MPEG-TS PAT/PMT/SDT");
    ContentValues channel=new ContentValues();
    channel.put("_id",++channelId);channel.put("order_index",++rank);
    channel.put("tp_id",tpId);channel.put("name",name);channel.put("provider",provider);
    channel.put("sid",sid);channel.put("tv",tv);channel.put("ca",ca);
    channel.put("data",metadata.toString());
    channel.put("search_key",ReceiverChannelRepository.searchKey(name+" "+provider));
    db.insertOrThrow("ch",null,channel);added++;
   }
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
  if(added>0)context.getSharedPreferences("smarthub",0).edit()
     .putLong("receiver_catalog_imported_at",System.currentTimeMillis())
     .putLong("installation_changed_at",System.currentTimeMillis())
     .putInt("psi_import_added",added).apply();
  return new PsiImportResult(added,duplicates,invalid);
 }
 /**
  * Manual PID editor for the app-owned channel profile. The firmware DB and CA
  * authorisation remain unchanged. Playback reads this same JSON via DvbPlayRequest.
  */
 synchronized boolean editPidsLocal(long channelId,int videoPid,int pcrPid,int pmtPid,
    String videoCodec,int audioPid,String audioCodec)throws Exception{
  if(videoPid<1||videoPid>=8191||pcrPid<1||pcrPid>=8191
     ||pmtPid<1||pmtPid>=8191||DvbPlayRequest.video(videoCodec)<0
     ||(audioPid!=-1&&(audioPid<1||audioPid>=8191||DvbPlayRequest.audio(audioCodec)<0)))
   throw new IOException("INVALID_PID_OR_CODEC");
  SQLiteDatabase db=getWritableDatabase();
  String stored;
  try(Cursor cursor=db.rawQuery("SELECT data FROM ch WHERE _id=?",
        new String[]{Long.toString(channelId)})){
   if(!cursor.moveToFirst())return false;
   stored=cursor.getString(0);
  }
  JSONObject metadata=new JSONObject(stored);
  metadata.put("VideoPID",videoPid).put("PcrPID",pcrPid).put("PmtPID",pmtPid)
    .put("VideoType",videoCodec);
  if(audioPid>0){
   JSONArray tracks=metadata.optJSONArray("Audio");if(tracks==null)tracks=new JSONArray();
   JSONObject track=tracks.optJSONObject(0);
   if(track==null)track=new JSONObject();
   track.put("PID",audioPid).put("Type",audioCodec);
   if(tracks.length()==0)tracks.put(track);else tracks.put(0,track);
   metadata.put("Audio",tracks);
  }
  ContentValues values=new ContentValues();values.put("data",metadata.toString());
  boolean updated=db.update("ch",values,"_id=?",new String[]{Long.toString(channelId)})>0;
  if(updated)context.getSharedPreferences("smarthub",0).edit()
    .putLong("receiver_local_edits_at",System.currentTimeMillis()).apply();
  return updated;
 }
 synchronized boolean renameChannelLocal(long id,String name){
  String n=name==null?"":name.trim();
  if(n.isEmpty()||n.length()>120||n.chars().anyMatch(c->c<32))return false;
  SQLiteDatabase db=getWritableDatabase();
  String provider="";
  try(Cursor c=db.rawQuery("SELECT provider FROM ch WHERE _id=?",new String[]{Long.toString(id)})){
   if(!c.moveToFirst())return false;provider=c.getString(0);
  }
  ContentValues v=new ContentValues();v.put("name",n);
  v.put("search_key",ReceiverChannelRepository.searchKey(n+" "+provider));
  boolean changed=db.update("ch",v,"_id=?",new String[]{Long.toString(id)})>0;
  if(changed)context.getSharedPreferences("smarthub",0).edit().putLong("receiver_local_edits_at",System.currentTimeMillis()).apply();
  return changed;
 }
 void favorite(long id,boolean enabled){if(enabled){ContentValues v=new ContentValues();v.put("channel_id",id);getWritableDatabase().insertWithOnConflict("favorite",null,v,SQLiteDatabase.CONFLICT_IGNORE);}else getWritableDatabase().delete("favorite","channel_id=?",new String[]{Long.toString(id)});}
 boolean favorite(long id){return DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM favorite WHERE channel_id=?",new String[]{Long.toString(id)})>0;}
 synchronized void ensure(){if(count()>0||DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM sat",null)>0)return;ChlChannelIndex old=ChlLogoAddon.get(context).snapshot();if(old!=null)replaceCompact(old);}
 void clear(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("fav_member",null,null);db.delete("fav_group",null,null);db.delete("favorite",null,null);db.delete("ch",null,null);db.delete("tp",null,null);db.delete("sat",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
 void replaceCompact(ChlChannelIndex old){SQLiteDatabase db=getWritableDatabase();Map<String,Integer> sats=new LinkedHashMap<>(),tps=new LinkedHashMap<>();db.beginTransaction();try{db.delete("fav_member",null,null);db.delete("fav_group",null,null);db.delete("favorite",null,null);db.delete("ch",null,null);db.delete("tp",null,null);db.delete("sat",null,null);int row=0;for(ChlChannelIndex.Service s:old.getServices()){
  String satKey=s.getSat()+":"+s.getBand();Integer sat=sats.get(satKey);if(sat==null){sat=sats.size();sats.put(satKey,sat);ContentValues v=new ContentValues();v.put("_id",sat);v.put("name",ReceiverChannelRepository.orbital(s.getSat()));v.put("angle",s.getSat());v.put("band",s.getBand());db.insertOrThrow("sat",null,v);}
  String tpKey=sat+":"+s.getFrequency()+":"+s.getPolarity()+":"+s.getSymbolRate();Integer tp=tps.get(tpKey);if(tp==null){tp=tps.size();tps.put(tpKey,tp);ContentValues v=new ContentValues();v.put("_id",tp);v.put("sat_id",sat);v.put("freq",s.getFrequency());v.put("pol",s.getPolarity());v.put("sr",s.getSymbolRate());v.put("fec","");db.insertOrThrow("tp",null,v);}
  ContentValues v=new ContentValues();v.put("_id",++row);v.put("order_index",row);v.put("tp_id",tp);v.put("name",s.getName());v.put("provider",s.getProvider());v.put("sid",s.getSid());v.put("tv",2);v.put("ca",-1);v.put("search_key",ReceiverChannelRepository.searchKey(s.getName()+" "+s.getProvider()));v.put("data","{}");db.insertOrThrow("ch",null,v);
 }db.setTransactionSuccessful();}finally{db.endTransaction();}}
 int importText(String text)throws Exception{
  if(text.length()>16*1024*1024)throw new IOException("CHL too large");SQLiteDatabase db=getWritableDatabase();List<String[]> savedFavorites=new ArrayList<>();try(Cursor favorites=getReadableDatabase().rawQuery("SELECT s.angle,s.band,t.freq,t.pol,t.sr,c.sid FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id JOIN favorite f ON f.channel_id=c._id",null)){while(favorites.moveToNext()){String[] key=new String[6];for(int i=0;i<6;i++)key[i]=favorites.getString(i);savedFavorites.add(key);}}List<Group> customGroups=new ArrayList<>();Map<Long,List<String[]>> customMembers=new LinkedHashMap<>();for(Group g:groups())if(g.id>=1000000){customGroups.add(g);List<String[]> keys=new ArrayList<>();try(Cursor members=db.rawQuery("SELECT s.angle,s.band,t.freq,t.pol,t.sr,c.sid FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id JOIN fav_member m ON m.channel_id=c._id WHERE m.group_id=? ORDER BY m.ordinal",new String[]{Long.toString(g.id)})){while(members.moveToNext()){String[] key=new String[6];for(int i=0;i<6;i++)key[i]=members.getString(i);keys.add(key);}}customMembers.put(g.id,keys);}int[] counts={0,0,0,0};List<JSONObject> groups=new ArrayList<>();db.beginTransaction();try{db.delete("fav_member",null,null);db.delete("fav_group",null,null);db.delete("favorite",null,null);db.delete("ch",null,null);db.delete("tp",null,null);db.delete("sat",null,null);
   ChlChannelIndex.visit(new StringReader(text),j->{if(++counts[3]>65000)throw new IllegalArgumentException("CHL row limit");String kind=j.optString("Type");if(kind.equals("fav")){if(groups.size()>=128)throw new IllegalArgumentException("Favourites limit");groups.add(j);return;}ContentValues v=new ContentValues();if(kind.equals("sat")){if(++counts[0]>512)throw new IllegalArgumentException("Satellite limit");v.put("_id",number(j,"Index",0,1000000));v.put("name",label(j,"Name"));v.put("angle",number(j,"Angle",0,3599));String band=label(j,"Band").toUpperCase(Locale.ROOT);if(!band.equals("KU")&&!band.equals("C"))throw new IllegalArgumentException("CHL band");v.put("band",band);db.insertOrThrow("sat",null,v);}else if(kind.equals("tp")){if(++counts[1]>100000)throw new IllegalArgumentException("TP limit");v.put("_id",number(j,"Index",0,1000000));v.put("sat_id",number(j,"SatIndex",0,1000000));v.put("freq",number(j,"Freq",100,100000));String pol=label(j,"Pol").toUpperCase(Locale.ROOT);if(!Arrays.asList("H","V","L","R").contains(pol))throw new IllegalArgumentException("CHL polarity");v.put("pol",pol);v.put("sr",number(j,"SR",1,100000));v.put("fec",j.optString("FEC","").trim());db.insertOrThrow("tp",null,v);}else if(kind.equals("ch")){if(++counts[2]>50000)throw new IllegalArgumentException("Channel limit");String name=label(j,"Name"),provider=j.optString("Provider","").trim();if(provider.length()>200)throw new IllegalArgumentException("Provider too long");v.put("_id",number(j,"Index",0,1000000));v.put("tp_id",number(j,"TPIndex",0,1000000));v.put("order_index",number(j,"Index",0,1000000));v.put("name",name);v.put("provider",provider);v.put("sid",number(j,"SID",0,65535));v.put("tv",j.optString("TVType").equalsIgnoreCase("TV")?0:j.optString("TVType").equalsIgnoreCase("Radio")?1:2);v.put("ca",j.optInt("CA",-1));v.put("search_key",ReceiverChannelRepository.searchKey(name+" "+provider));v.put("data",cleanChannel(j).toString());db.insertOrThrow("ch",null,v);}});
   for(String[] key:savedFavorites)db.execSQL("INSERT OR IGNORE INTO favorite(channel_id) SELECT MIN(c._id) FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id WHERE s.angle=? AND s.band=? AND t.freq=? AND t.pol=? AND t.sr=? AND c.sid=? HAVING COUNT(*)>0",key);
   for(JSONObject group:groups){int groupId=number(group,"Index",0,127);ContentValues values=new ContentValues();values.put("_id",groupId);values.put("name",label(group,"Name"));db.insertOrThrow("fav_group",null,values);int ordinal=0;for(String field:new String[]{"TVChs","RadioChs"}){JSONArray entries=group.optJSONArray(field);if(entries==null)continue;if(entries.length()>50000)throw new IOException("Favourite group too large");for(int n=0;n<entries.length();n++){String entry=entries.optString(n);if(!entry.matches("[0-9]{1,7}-[0-9]{1,5}"))continue;String[] parts=entry.split("-");db.execSQL("INSERT OR IGNORE INTO fav_member(group_id,channel_id,ordinal) SELECT ?,_id,? FROM ch WHERE tp_id=? AND sid=?",new Object[]{groupId,ordinal++,Integer.parseInt(parts[0]),Integer.parseInt(parts[1])});}}}
   for(Group g:customGroups){ContentValues v=new ContentValues();v.put("_id",g.id);v.put("name",g.name);db.insertOrThrow("fav_group",null,v);int ordinal=0;for(String[] key:customMembers.get(g.id)){Object[] args=new Object[]{g.id,ordinal++,key[0],key[1],key[2],key[3],key[4],key[5]};db.execSQL("INSERT OR IGNORE INTO fav_member(group_id,channel_id,ordinal) SELECT ?,MIN(c._id),? FROM ch c JOIN tp t ON t._id=c.tp_id JOIN sat s ON s._id=t.sat_id WHERE s.angle=? AND s.band=? AND t.freq=? AND t.pol=? AND t.sr=? AND c.sid=? HAVING COUNT(*)>0",args);}}
   if(counts[0]==0||counts[1]==0||counts[2]==0||DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM ch c LEFT JOIN tp t ON c.tp_id=t._id LEFT JOIN sat s ON t.sat_id=s._id WHERE t._id IS NULL OR s._id IS NULL",null)>0)throw new IOException("Missing CHL references");db.setTransactionSuccessful();
  }finally{db.endTransaction();}context.getSharedPreferences("smarthub",0).edit().putLong("receiver_catalog_imported_at",System.currentTimeMillis()).putInt("receiver_catalog_count",counts[2]).apply();return counts[2];
 }
 private static JSONObject cleanChannel(JSONObject j){try{JSONObject out=new JSONObject();for(String key:new String[]{"VideoPID","PcrPID","PmtPID","PvtPID","TTXPID"})out.put(key,j.optInt(key,8191));out.put("VideoType",j.optString("VideoType"));JSONArray audio=new JSONArray(),source=j.optJSONArray("Audio");if(source!=null)for(int n=0;n<Math.min(16,source.length());n++){JSONObject a=source.optJSONObject(n);if(a==null)continue;int pid=a.optInt("PID",8191);if(pid<0||pid>=8191)continue;audio.put(new JSONObject().put("PID",pid).put("Type",a.optString("Type")).put("Lang",a.optString("Lang").substring(0,Math.min(12,a.optString("Lang").length()))).put("DolbyAC3",a.optInt("DolbyAC3",0)));}out.put("Audio",audio);JSONArray subtitles=new JSONArray(),subSource=j.optJSONArray("Sub");if(subSource!=null)for(int i=0;i<Math.min(32,subSource.length());i++){JSONObject s=subSource.optJSONObject(i);if(s==null)continue;int pid=s.optInt("PID",8191),type=s.optInt("Type",-1),page=s.optInt("CompPageID",-1),anci=s.optInt("AnciPageID",-1);if(pid>0&&pid<8191&&type>=0&&type<=255&&page>=0&&page<=65535&&anci>=0&&anci<=65535)subtitles.put(new JSONObject().put("PID",pid).put("Type",type).put("CompPageID",page).put("AnciPageID",anci).put("Lang",s.optString("Lang","").substring(0,Math.min(12,s.optString("Lang","").length()))));}out.put("Sub",subtitles);JSONArray ca=new JSONArray(),ids=j.optJSONArray("CaSystemIdList");if(ids!=null)for(int i=0;i<Math.min(64,ids.length());i++){int id=ids.optInt(i,-1);if(id>=0&&id<=65535)ca.put(id);}out.put("CaSystemIdList",ca);return out;}catch(JSONException e){throw new IllegalArgumentException("Invalid channel metadata",e);}}
 private static int number(JSONObject j,String key,int min,int max){int v=j.optInt(key,Integer.MIN_VALUE);if(v<min||v>max)throw new IllegalArgumentException("CHL field "+key);return v;}
 private static String label(JSONObject j,String key){String s=j.optString(key,"").trim();if(s.isEmpty()||s.length()>200||s.chars().anyMatch(c->c<32))throw new IllegalArgumentException("CHL label "+key);return s;}
 static String readText(Reader input)throws IOException{StringBuilder text=new StringBuilder();char[] buffer=new char[8192];int n;while((n=input.read(buffer))>=0){if(text.length()+n>16*1024*1024)throw new IOException("CHL exceeds 16 MB");text.append(buffer,0,n);}return text.toString();}
}
