package com.smarthub.plus;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Virtualized 234-row catalogue: real provider bitmap where audited;
 * a distinctly labelled colored placeholder otherwise, never fake artwork.
 * CHMAX/OEM channel logos and EPG are unaffected. */
public final class ProviderLogoCatalogActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=col();
  page.setPadding(dp(22),dp(15),dp(22),dp(15));
  page.setBackground(RxSkin.wallpaper(this));
  TextView title=text(tx("شعارات 234 مزوّداً","234 provider artwork catalogue"),23,true);
  page.addView(title);
  TextView subtitle=text(tx("شعار حقيقي أو بديل ملون باسم المزوّد، وليس شعار قناة.",
    "Verified provider mark or clearly labelled colored placeholder; not channel art."),13,false);
  page.addView(subtitle);
  setContentView(page);
  List<ProviderCountryCatalog.Provider> providers=
    new ArrayList<>(ProviderCountryCatalog.get(this).all());
  Collections.sort(providers,(a,b)->a.name.compareToIgnoreCase(b.name));
  int verified=0;
  ProviderBrandArt brands=ProviderBrandArt.get(this);
  for(ProviderCountryCatalog.Provider p:providers)
   if(brands.verified(p.name,p.country))verified++;
  TextView count=text(providers.size()+" providers · "+verified+" verified marks · "+
      (providers.size()-verified)+" colored non-official placeholders",14,false);
  page.addView(count);
  ListView list=new ListView(this);
  list.setDividerHeight(dp(2));
  list.setAdapter(new BaseAdapter(){
   @Override public int getCount(){return providers.size();}
   @Override public Object getItem(int position){return providers.get(position);}
   @Override public long getItemId(int position){return position;}
   @Override public View getView(int position,View reused,android.view.ViewGroup parent){
    Row row;
    if(reused instanceof LinearLayout&&reused.getTag() instanceof Row)row=(Row)reused.getTag();
    else{
     LinearLayout container=new LinearLayout(ProviderLogoCatalogActivity.this);
     container.setOrientation(LinearLayout.HORIZONTAL);
     container.setGravity(Gravity.CENTER_VERTICAL);
     container.setPadding(dp(8),dp(4),dp(8),dp(4));
     ImageView image=new ImageView(ProviderLogoCatalogActivity.this);
     image.setScaleType(ImageView.ScaleType.FIT_CENTER);
     container.addView(image,new LinearLayout.LayoutParams(dp(124),dp(58)));
     TextView label=text("",14,false);
     label.setPadding(dp(12),0,0,0);
     container.addView(label,new LinearLayout.LayoutParams(0,-2,1));
     row=new Row(container,image,label);
     container.setTag(row);
    }
    ProviderCountryCatalog.Provider p=providers.get(position);
    boolean official=brands.verified(p.name,p.country);
    row.image.setImageBitmap(brands.badge(p.name,""));
    row.label.setText(p.name+" · "+p.country+"\n"+
      (official?"Verified provider artwork":"Color placeholder (not an official logo)"));
    row.root.setContentDescription(row.label.getText());
    return row.root;
   }
  });
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
 }
 private static final class Row {
  final LinearLayout root;final ImageView image;final TextView label;
  Row(LinearLayout r,ImageView i,TextView l){root=r;image=i;label=l;}
 }
}
