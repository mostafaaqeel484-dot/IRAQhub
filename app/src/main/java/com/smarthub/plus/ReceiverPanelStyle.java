package com.smarthub.plus;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
/** Shared broadcast menu skin; every item maps to an actual local or receiver action. */
final class ReceiverPanelStyle {
 static final int GOLD=0xffffdf78,CYAN=0xff39dcff;
 static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable panel(Context c){return RxSkin.glass(c,true);}
 static StateListDrawable row(Context c){return RxSkin.focus(c);}
  interface Choice {void selected(int index);}
 static void choose(Activity a,String title,String[] labels,int selected,Choice click){boolean ar=a.getSharedPreferences("smarthub",0).getBoolean("arabic",true);LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(dp(a,18),dp(a,14),dp(a,18),dp(a,14));box.setBackground(panel(a));TextView heading=new TextView(a);heading.setText("‹  "+title+"  ›");heading.setTextColor(RxSkin.accent(a));heading.setTextSize(23);heading.setGravity(Gravity.CENTER);box.addView(heading,new LinearLayout.LayoutParams(-1,dp(a,46)));ListView list=new ListView(a);list.setSelector(android.R.color.transparent);list.setDividerHeight(0);list.setAdapter(new BaseAdapter(){public int getCount(){return labels.length;}public Object getItem(int p){return labels[p];}public long getItemId(int p){return p;}public View getView(int p,View old,ViewGroup parent){TextView v=new TextView(a);v.setText((p==selected?"✓   ":"     ")+labels[p]);v.setTextSize(21);android.graphics.Typeface f=UserTypeface.get(a);if(f!=null)v.setTypeface(f);v.setTextColor(p==selected?RxSkin.accent(a):0xfff1f1f5);v.setGravity(Gravity.CENTER_VERTICAL);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setSingleLine(true);v.setPadding(dp(a,18),0,dp(a,18),0);v.setBackground(row(a));v.setLayoutParams(new AbsListView.LayoutParams(-1,dp(a,45)));return v;}});int screen=(int)(a.getResources().getDisplayMetrics().heightPixels/a.getResources().getDisplayMetrics().density);box.addView(list,new LinearLayout.LayoutParams(-1,Math.min(dp(a,Math.max(100,screen-170)),dp(a,45*labels.length))));TextView hint=new TextView(a);hint.setText(ar?"OK اختيار     BACK رجوع":"OK Select     BACK Back");hint.setTextSize(12);hint.setTextColor(RxSkin.accent(a));hint.setPadding(0,dp(a,12),0,0);box.addView(hint);AlertDialog d=new AlertDialog.Builder(a).setView(box).create();list.setOnItemClickListener((p,v,index,id)->{d.dismiss();click.selected(index);});d.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_MENU)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){d.dismiss();ReceiverNavigation.openLauncher(a);}return true;});d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(dp(a,580),a.getResources().getDisplayMetrics().widthPixels-dp(a,48)),-2);}list.setSelection(Math.max(0,selected));list.requestFocus();}

 /** TV remote letter matrix: 7 columns instead of a 58-entry vertical list. */
 static void chooseLetters(Activity a,String title,String[] labels,int selected,Choice click){
  if(labels==null||labels.length==0)return;
  LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
  box.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
  box.setBackground(panel(a));
  TextView heading=new TextView(a);
  heading.setText(title);heading.setTextColor(RxSkin.accent(a));
  heading.setTextSize(20);heading.setGravity(Gravity.CENTER);
  box.addView(heading,new LinearLayout.LayoutParams(-1,dp(a,42)));
  final int cols=7;
  GridView grid=new GridView(a);
  grid.setNumColumns(cols);
  grid.setHorizontalSpacing(dp(a,4));grid.setVerticalSpacing(dp(a,4));
  grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
  grid.setSelector(RxSkin.focus(a));
  grid.setCacheColorHint(Color.TRANSPARENT);
  grid.setScrollingCacheEnabled(false);
  grid.setAdapter(new BaseAdapter(){
   @Override public int getCount(){return labels.length;}
   @Override public Object getItem(int pos){return labels[pos];}
   @Override public long getItemId(int pos){return pos;}
   @Override public View getView(int pos,View old,ViewGroup parent){
    TextView cell=old instanceof TextView?(TextView)old:new TextView(a);
    cell.setText(labels[pos].isEmpty()?"★":labels[pos].equals("#")?"#":labels[pos]);
    cell.setTextSize(19);cell.setSingleLine(true);
    cell.setGravity(Gravity.CENTER);
    cell.setTextColor(pos==selected?0xff13171d:0xffeef2f6);
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(pos==selected?RxSkin.accent(a):0xff172536);
    bg.setCornerRadius(dp(a,5));
    cell.setBackground(bg);
    android.graphics.Typeface font=UserTypeface.get(a);
    if(font!=null)cell.setTypeface(font);
    cell.setContentDescription(labels[pos].isEmpty()?"All channels":labels[pos]);
    cell.setLayoutParams(new AbsListView.LayoutParams(-1,dp(a,42)));
    return cell;
   }
  });
  int rows=(labels.length+cols-1)/cols;
  int available=Math.max(dp(a,110),a.getResources().getDisplayMetrics().heightPixels-dp(a,165));
  box.addView(grid,new LinearLayout.LayoutParams(-1,Math.min(rows*dp(a,46),available)));
  TextView hint=new TextView(a);
  hint.setText("◀ ▶ ▲ ▼  ·  OK  ·  BACK");
  hint.setGravity(Gravity.CENTER);hint.setTextSize(12);
  hint.setTextColor(RxSkin.accent(a));
  hint.setPadding(0,dp(a,9),0,0);box.addView(hint);
  AlertDialog dialog=new AlertDialog.Builder(a).setView(box).create();
  grid.setOnItemClickListener((p,v,index,id)->{
   dialog.dismiss();click.selected(index);
  });
  dialog.setOnKeyListener((d,key,event)->{
   if(key!=KeyEvent.KEYCODE_MENU)return false;
   if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
    dialog.dismiss();ReceiverNavigation.openLauncher(a);
   }
   return true;
  });
  dialog.show();
  Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
   window.setLayout(Math.min(dp(a,590),
     a.getResources().getDisplayMetrics().widthPixels-dp(a,38)),-2);
  }
  grid.setSelection(Math.max(0,Math.min(labels.length-1,selected)));
  grid.requestFocus();
 }
}
