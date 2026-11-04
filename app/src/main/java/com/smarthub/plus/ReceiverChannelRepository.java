package com.smarthub.plus;

import android.content.*;
import android.database.Cursor;
import android.media.tv.TvContract;
import java.util.*;

/** Public Android TV channels and labelled imported metadata. CHL indexes are never tuning IDs. */
final class ReceiverChannelRepository {
    static final class Entry {
        final String name,number,provider,source,details,searchKey;final long systemId;
        Entry(String name,String number,String provider,String source,String details,long systemId){this.name=name;this.number=number;this.provider=provider;this.source=source;this.details=details;this.systemId=systemId;searchKey=searchKey(name+" "+provider+" "+details);}
        boolean playable(){return systemId>=0;}
    }
    static final class Result {final List<Entry> channels;final String publicStatus;Result(List<Entry> c,String status){channels=Collections.unmodifiableList(c);publicStatus=status;}}
    static Result load(Context c){
        List<Entry> result=new ArrayList<>();String status="EMPTY";
        try(Cursor cursor=c.getContentResolver().query(TvContract.Channels.CONTENT_URI,new String[]{"_id","display_name","display_number"},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext()&&result.size()<10000){String name=cursor.getString(1);if(name==null||name.trim().isEmpty())continue;result.add(new Entry(name,cursor.getString(2),"","Android TV","",cursor.getLong(0)));}if(!result.isEmpty())status="CONNECTED";
        }catch(SecurityException denied){status="PERMISSION_DENIED";}catch(Exception unavailable){status="UNAVAILABLE";}
        ChlChannelIndex index=ChlLogoAddon.get(c).snapshot();if(index!=null)for(ChlChannelIndex.Service s:index.getServices())result.add(new Entry(s.getName(),"",s.getProvider(),"CHL",s.getFrequency()+" "+s.getPolarity()+" · SR "+s.getSymbolRate()+" · SID "+s.getSid()+" · "+orbital(s.getSat()),-1));
        if(result.isEmpty()){SatelliteState current=SatelliteState.read(c);if(!current.channelName.isEmpty())result.add(new Entry(current.channelName,"",CHMAXIntegration.provider(c,current),"Observed",current.channelId,-1));}
        result.sort(Comparator.comparing((Entry e)->e.name,String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.details));return new Result(result,status);
    }
    static String orbital(int angle){int degrees=angle>1800?3600-angle:angle;return degrees/10+"."+degrees%10+"°"+(angle>1800?"W":"E");}
    /** Build once per entry. No regex replacement/compatibility logging per row and per keystroke. */
    static String searchKey(String value){String normalized=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);StringBuilder key=new StringBuilder(normalized.length());for(int i=0;i<normalized.length();){int cp=normalized.codePointAt(i);i+=Character.charCount(cp);if(cp==0x640||cp>=0x64b&&cp<=0x65f)continue;if(cp==0x622||cp==0x623||cp==0x625)cp=0x627;int digit=Character.digit(cp,10);if(digit>=0)key.append((char)('0'+digit));else if(Character.isLetterOrDigit(cp))key.appendCodePoint(cp);}return key.toString();}
    static List<Entry> search(List<Entry> all,String query,int maximum){String q=searchKey(query);List<Entry> out=new ArrayList<>();for(Entry e:all){if(q.isEmpty()||e.searchKey.contains(q)){out.add(e);if(out.size()>=maximum)break;}}return out;}
}
