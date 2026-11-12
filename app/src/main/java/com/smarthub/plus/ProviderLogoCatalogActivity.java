package com.smarthub.plus;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Virtualized 234-row catalogue: display verified provider images only;
 * otherwise show the provider name without inventing a corporate logo.
 * CHMAX/OEM channel logos and EPG are unaffected. */
public final class ProviderLogoCatalogActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout page=col();
  page.setPadding(dp(22),dp(15),dp(22),dp(15));
  page.setBackground(RxSkin.wallpaper(this));
  TextView title=text(tx("شعارات 234 مزوّداً","234 provider artwork catalogue"),23,true);
  page.addView(title);
  TextView subtitle=text(tx("شعار مزوّد موثوق أو اسمه كنص فقط، وليس شعار قناة.",
    "Verified provider mark or provider name text only; never channel artwork."),13,false);
  page.addView(subtitle);
  setContentView(page);
  action(page,tx("إضافة أو تعديل شعار مزوّد","Add / edit provider logo"),
    ()->startActivity(new android.content.Intent(this,ProviderLogoEditorActivity.class)));
  List<ProviderCountryCatalog.Provider> providers=
    new ArrayList<>(ProviderCountryCatalog.get(this).all());
  Collections.sort(providers,(a,b)->a.name.compareToIgnoreCase(b.name));
  int verified=0;
  ProviderBrandArt brands=ProviderBrandArt.get(this);
  for(ProviderCountryCatalog.Provider p:providers)
   if(brands.verified(p.name,p.country))verified++;
  int approved=0;
  for(ProviderCountryCatalog.Provider p:providers)
    if(ProviderApprovedArtwork.selected(this,p.name))approved++;
  TextView count=text(providers.size()+" providers · "+verified+" bundled/manual images · "+
      approved+" approved remote sources · remaining names without image",14,false);
  page.addView(count);
  ListView list=new ListView(this);
  list.setDividerHeight(dp(2));
  BaseAdapter adapter=new BaseAdapter(){
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
    boolean approved=ProviderApprovedArtwork.selected(ProviderLogoCatalogActivity.this,p.name);
    Bitmap selected=official?brands.badge(p.name,""):
      (approved?ProviderApprovedArtwork.cached(ProviderLogoCatalogActivity.this,p.name):null);
    row.image.setImageBitmap(selected);
    row.image.setVisibility(selected!=null?View.VISIBLE:View.GONE);
    row.label.setText(p.name+" · "+p.country+"\n"+
      (official?"Bundled or manually added provider image":
       !approved?"Name only — no selected image":
       selected!=null?"User-approved image downloaded":"User-approved source · tap to test download"));
    row.root.setContentDescription(row.label.getText());
    return row.root;
   }
  };
  list.setAdapter(adapter);
  list.setOnItemClickListener((parent,view,index,itemId)->{
   ProviderCountryCatalog.Provider p=providers.get(index);
   if(!ProviderApprovedArtwork.selected(this,p.name)){
    toast(tx("لا يوجد رابط صورة معتمد لهذا المزوّد","No approved image source for this provider"));
    return;
   }
   count.setText(tx("جارٍ فحص صورة المزوّد: ","Checking provider image: ")+p.name);
   io.execute(()->{
    Bitmap loaded=ProviderApprovedArtwork.resolve(this,p.name);
    runOnUiThread(()->{if(!closed){
     count.setText(p.name+" · "+(loaded==null?
       tx("المصدر غير متاح أو الصورة غير صالحة","Source unavailable or invalid image"):
       tx("تم تنزيل وحفظ الصورة","Image downloaded and cached")));
     adapter.notifyDataSetChanged();
    }});
   });
  });
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
 }
 private static final class Row {
  final LinearLayout root;final ImageView image;final TextView label;
  Row(LinearLayout r,ImageView i,TextView l){root=r;image=i;label=l;}
 }
}
