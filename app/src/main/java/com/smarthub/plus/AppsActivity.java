package com.smarthub.plus;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class AppsActivity extends HubActivity {
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private List<AppCatalogue.App> all=Collections.emptyList(),apps=Collections.emptyList();private EditText query;private GridView grid;private TextView status;private boolean destroyed;private int generation;
    private final Handler refresh=new Handler(Looper.getMainLooper());
    private final Runnable refreshIcons=()->{if(!destroyed&&grid!=null&&grid.getAdapter() instanceof BaseAdapter)((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();};
    @Override public void onCreate(Bundle s){super.onCreate(s);LinearLayout root=col();root.setBackgroundColor(BG);root.setPadding(dp(26),dp(18),dp(26),dp(20));root.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);LinearLayout head=row();TextView title=text(tx("تطبيقات الجهاز","Applications"),27,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button back=button(tx("رجوع","Back"));back.setOnClickListener(v->finish());head.addView(back);root.addView(head);status=text("",14,false);status.setTextColor(MUTED);status.setPadding(0,dp(8),0,dp(12));root.addView(status);query=field(root,tx("البحث عن تطبيق","Search applications"),"");query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){filterApps();}public void afterTextChanged(android.text.Editable e){}});grid=new GridView(this);grid.setNumColumns(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density<850?4:5);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setDrawSelectorOnTop(false);grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);grid.setOnItemClickListener((p,v,index,id)->{try{startActivity(AppCatalogue.launch(apps.get(index)));}catch(Exception e){toast(tx("تعذر فتح التطبيق؛ قد يمنعه النظام أو يكون أزيل","Cannot open app; it may be restricted or removed"));}});grid.setOnItemLongClickListener((p,v,index,id)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+apps.get(index).pkg)));}catch(Exception e){toast(tx("تفاصيل التطبيق غير متاحة","App details unavailable"));}return true;});}
    @Override protected void onResume(){super.onResume();int token=++generation;status.setText(tx("جارٍ قراءة التطبيقات…","Reading applications…"));io.execute(()->{List<AppCatalogue.App> found=AppCatalogue.load(this);runOnUiThread(()->{if(destroyed||token!=generation)return;all=found;filterApps();status.setText(tx("التطبيقات المتاحة: ","Available apps: ")+apps.size()+tx("  ·  OK للفتح، ضغط مطوّل للتفاصيل","  ·  OK to open, long press for details"));if(!apps.isEmpty()&&!query.hasFocus())grid.requestFocus();});});}
    private void filterApps(){if(grid==null)return;String key=ReceiverChannelRepository.searchKey(query.getText().toString());List<AppCatalogue.App> visible=new ArrayList<>();for(AppCatalogue.App app:all)if(ReceiverChannelRepository.searchKey(app.label+" "+app.pkg).contains(key))visible.add(app);apps=visible;grid.setAdapter(new Tiles());status.setText(tx("التطبيقات المتاحة: ","Available apps: ")+apps.size());}
    private final class Tiles extends BaseAdapter {
        public int getCount(){return apps.size();}public Object getItem(int p){return apps.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View recycled,ViewGroup parent){
            LinearLayout tile=col();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(10),dp(10),dp(10),dp(10));tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(128)));StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_selected},shape(0xff286a76,12));bg.addState(new int[]{android.R.attr.state_pressed},shape(0xff286a76,12));bg.addState(new int[]{},shape(PANEL,12));tile.setBackground(bg);
            AppCatalogue.App app=apps.get(position);tile.setContentDescription(app.label);FrameLayout art=new FrameLayout(AppsActivity.this);tile.addView(art,new LinearLayout.LayoutParams(dp(62),dp(62)));TextView fallback=text(app.label.substring(0,1).toUpperCase(Locale.ROOT),30,true);fallback.setGravity(Gravity.CENTER);fallback.setTextColor(ACCENT);art.addView(fallback,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(AppsActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView name=text(app.label,15,true);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(0,dp(7),0,0);tile.addView(name,new LinearLayout.LayoutParams(-1,-2));Bitmap cached=icons.get(app.pkg);if(cached!=null){image.setImageBitmap(cached);fallback.setVisibility(View.GONE);}else if(pending.add(app.pkg)){io.execute(()->{Bitmap bitmap=null;try{Drawable d=app.info.loadIcon(getPackageManager());int size=Math.min(192,dp(72));bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);d.setBounds(0,0,size,size);d.draw(canvas);}catch(Exception e){}Bitmap b=bitmap;if(b!=null)icons.put(app.pkg,b);pending.remove(app.pkg);runOnUiThread(()->{if(destroyed)return;if(b!=null){image.setImageBitmap(b);fallback.setVisibility(View.GONE);refresh.removeCallbacks(refreshIcons);refresh.postDelayed(refreshIcons,100);}});});}return tile;
        }
    }
    @Override protected void onDestroy(){destroyed=true;generation++;refresh.removeCallbacksAndMessages(null);io.shutdownNow();icons.evictAll();super.onDestroy();}
}
