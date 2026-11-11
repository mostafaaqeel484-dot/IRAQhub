package com.smarthub.plus;
import android.os.*;
import android.widget.*;
import android.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
public final class WorldClockActivity extends HubActivity {
 private final Handler handler=new Handler(Looper.getMainLooper());private LinearLayout clocks;private EditText search;private final Runnable tick=new Runnable(){public void run(){render();handler.postDelayed(this,30_000);}};
 @Override public void onCreate(Bundle s){super.onCreate(s);LinearLayout body=page(tx("توقيتات العالم","World clocks"),tx("توقيت الجهاز والمناطق الزمنية • يعمل دون إنترنت","Device clock and timezones • available offline"));search=field(body,tx("ابحث: Baghdad، Riyadh، London…","Search Baghdad, Riyadh, London…"),"");search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int f){}public void onTextChanged(CharSequence x,int a,int b,int c){render();}public void afterTextChanged(Editable e){}});clocks=col();body.addView(clocks);render();}
 private void render(){if(clocks==null)return;clocks.removeAllViews();String query=search.getText().toString().toLowerCase(Locale.ROOT).replace(' ','_');List<String> zones=new ArrayList<>();if(query.isEmpty())zones.addAll(Arrays.asList("Asia/Baghdad","Asia/Riyadh","Asia/Dubai","Asia/Qatar","Asia/Kuwait","Asia/Amman","Asia/Beirut","Africa/Cairo","Africa/Casablanca","Europe/London","Europe/Berlin","America/New_York","Asia/Tokyo","Australia/Sydney"));else{for(String z:new TreeSet<>(ZoneId.getAvailableZoneIds()))if(z.toLowerCase(Locale.ROOT).contains(query))zones.add(z);}if(zones.isEmpty()){note(clocks,tx("لا توجد منطقة مطابقة","No matching timezone"));return;}DateTimeFormatter f=DateTimeFormatter.ofPattern("EEE dd MMM yyyy   HH:mm:ss",ar?new Locale("ar"):Locale.ENGLISH);for(String id:zones){LinearLayout c=card(clocks);ZonedDateTime now=ZonedDateTime.now(ZoneId.of(id));c.addView(text(id.replace('_',' ')+"   UTC"+now.getOffset(),18,true));TextView value=text(f.format(now),24,false);value.setTextColor(ACCENT);c.addView(value);}}
 @Override protected void onResume(){super.onResume();handler.post(tick);}@Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
