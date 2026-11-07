package com.smarthub.plus;
import android.os.Bundle;import android.widget.*;import java.util.concurrent.*;import org.json.*;
public final class MatchDetailsView extends HubActivity {
 private final ExecutorService network=Executors.newSingleThreadExecutor();private boolean closed;private LinearLayout body;private SportsMatch match;
 @Override public void onCreate(Bundle state){super.onCreate(state);body=page("IRAQhub · Match details");match=MatchRepository.get(this).find(getIntent().getStringExtra("match_id"));if(match==null){note(body,tx("المباراة غير موجودة في البيانات المحفوظة","Event is not present in saved data"));return;}body.addView(text(match.home+"   "+match.score()+"   "+match.away,25,true));note(body,match.league+"\n"+match.time()+" Asia/Baghdad · "+match.state()+"\n"+match.source);if(!match.venue.isEmpty())note(body,tx("الملعب: ","Venue: ")+match.venue);// An event poster is accepted only when the verified event record supplies it.
 // Otherwise use the actual event's provider-identified team and league logos;
 // never substitute an unrelated movie poster or a title-only image match.
 if(!match.image.isEmpty()&&TeamLogoManager.trusted(match.image)){
  ImageView poster=new ImageView(this);
  poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
  poster.setContentDescription(match.home+" vs "+match.away+" event artwork");
  body.addView(poster,new LinearLayout.LayoutParams(-1,dp(200)));
  TeamLogoManager.bind(this,poster,match.id,match.image);
 }else{
  LinearLayout logos=row();
  addVerifiedLogo(logos,match.homeId,match.homeLogo,match.home);
  addVerifiedLogo(logos,match.leagueId,match.leagueLogo,match.league);
  addVerifiedLogo(logos,match.awayId,match.awayLogo,match.away);
  body.addView(logos,new LinearLayout.LayoutParams(-1,dp(130)));
 }
 if(!match.description.isEmpty())note(body,match.description);if(!match.timeline.isEmpty()){body.addView(text("Goals · source timeline",18,true));note(body,match.timeline);}action(body,tx("تحديث تفاصيل المصدر","Refresh source details"),this::load);load();}
 private void addVerifiedLogo(LinearLayout parent,String id,String url,String label){
  LinearLayout column=new LinearLayout(this);
  column.setOrientation(LinearLayout.VERTICAL);
  ImageView logo=new ImageView(this);
  logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  logo.setContentDescription(label);
  column.addView(logo,new LinearLayout.LayoutParams(-1,dp(92)));
  if(id!=null&&!id.isEmpty()&&TeamLogoManager.trusted(url))
   TeamLogoManager.bind(this,logo,id,url);
  else logo.setImageResource(android.R.drawable.ic_menu_gallery);
  TextView caption=text(label,12,false);
  caption.setSingleLine(true);
  caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
  column.addView(caption);
  parent.addView(column,new LinearLayout.LayoutParams(0,-2,1));
 }
 private void load(){if(match==null)return;network.execute(()->{TheSportsDBProvider provider=new TheSportsDBProvider(this);for(String endpoint:new String[]{"lookuptimeline","lookuplineup","lookupeventstats"}){String title=endpoint.equals("lookuptimeline")?"Goals / cards / substitutions":endpoint.equals("lookuplineup")?"Lineups":"Statistics";String message;try{JSONObject wrapper=provider.details(match.id,endpoint),data=wrapper.getJSONObject("data");JSONArray rows=null;java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){Object value=data.opt(keys.next());if(value instanceof JSONArray){rows=(JSONArray)value;break;}}StringBuilder b=new StringBuilder();if(rows!=null)for(int i=0;i<Math.min(rows.length(),150);i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;for(String key:new String[]{"intTime","strTimeline","strTimelineDetail","strPlayer","strTeam","strPosition","strStat","intHome","intAway"})if(!r.isNull(key)&&!r.optString(key).isEmpty())b.append(r.optString(key)).append(" · ");if(b.length()>0)b.append('\n');}message=b.length()>0?b.toString():"Unavailable from this source / free plan";}catch(Exception e){message="Unavailable from this source / connection / free plan";}String output=message;runOnUiThread(()->{if(!closed){body.addView(text(title,18,true));note(body,output);}});}});}
 @Override protected void onDestroy(){closed=true;network.shutdownNow();super.onDestroy();}
}
