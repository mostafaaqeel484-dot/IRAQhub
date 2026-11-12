package com.smarthub.plus;

import android.graphics.Bitmap;
import java.util.BitSet;

/** Trim only outer mattes and transparent padding. Preserve original colour and aspect ratio. */
final class ChannelLogoBitmap {
 private ChannelLogoBitmap(){}
 static Bitmap prepare(Bitmap source,boolean removeWhite,boolean removeBlack,boolean crop){
  if(source==null)return null;
  int w=source.getWidth(),h=source.getHeight();
  if(w<2||h<2||w>1024||h>1024||source.getConfig()==Bitmap.Config.HARDWARE)
   return source;
  int[] pixels=new int[w*h];
  source.getPixels(pixels,0,w,0,0,w,h);
  int white=0,black=0;
  int[] corners={0,w-1,(h-1)*w,w*h-1};
  for(int index:corners){
   if(white(pixels[index]))white++;
   if(black(pixels[index]))black++;
  }
  int matte=removeWhite&&white>=3?1:removeBlack&&black>=3?2:0;
  boolean changed=false;
  if(matte!=0){
   BitSet seen=new BitSet(pixels.length);
   int[] queue=new int[pixels.length];
   int tail=0;
   for(int x=0;x<w;x++){
    if(matte(pixels[x],matte)&&!seen.get(x)){seen.set(x);queue[tail++]=x;}
    int last=(h-1)*w+x;
    if(matte(pixels[last],matte)&&!seen.get(last)){seen.set(last);queue[tail++]=last;}
   }
   for(int y=0;y<h;y++){
    int left=y*w,right=left+w-1;
    if(matte(pixels[left],matte)&&!seen.get(left)){seen.set(left);queue[tail++]=left;}
    if(matte(pixels[right],matte)&&!seen.get(right)){seen.set(right);queue[tail++]=right;}
   }
   int[] neighbours=new int[4];
   for(int head=0;head<tail;head++){
    int i=queue[head],x=i%w;
    neighbours[0]=x>0?i-1:-1;
    neighbours[1]=x<w-1?i+1:-1;
    neighbours[2]=i>=w?i-w:-1;
    neighbours[3]=i<w*(h-1)?i+w:-1;
    for(int next:neighbours){
     if(next<0||seen.get(next)||!matte(pixels[next],matte))continue;
     seen.set(next);queue[tail++]=next;
    }
   }
   if(tail>0&&tail<pixels.length){
    changed=true;
    for(int i=0;i<tail;i++)pixels[queue[i]]=0;
   }
  }
  if(matte==1&&changed){
   // Once an edge-connected white rectangle is removed, black text would
   // disappear against the native RX6 black ribbon. Turn only neutral/dark
   // surviving ink white; saturated logos (red OSN, Sky, Arena) stay coloured.
   for(int i=0;i<pixels.length;i++){
    int p=pixels[i],alpha=p>>>24;
    if(alpha<=8)continue;
    int red=(p>>>16)&255,green=(p>>>8)&255,blue=p&255;
    int high=Math.max(red,Math.max(green,blue));
    int low=Math.min(red,Math.min(green,blue));
    if(high<=105&&high-low<=28)pixels[i]=(alpha<<24)|0x00ffffff;
   }
  }
  if(!crop){return changed?Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888):source;}
  int minX=w,minY=h,maxX=-1,maxY=-1;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   if((pixels[y*w+x]>>>24)<=8)continue;
   if(x<minX)minX=x;if(x>maxX)maxX=x;
   if(y<minY)minY=y;if(y>maxY)maxY=y;
  }
  if(maxX<minX)return source;
  int pad=Math.max(1,Math.min(w,h)/65);
  minX=Math.max(0,minX-pad);maxX=Math.min(w-1,maxX+pad);
  minY=Math.max(0,minY-pad);maxY=Math.min(h-1,maxY+pad);
  if(minX==0&&minY==0&&maxX==w-1&&maxY==h-1){
   return changed?Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888):source;
  }
  int cropW=maxX-minX+1,cropH=maxY-minY+1;
  int[] trimmed=new int[cropW*cropH];
  for(int y=0;y<cropH;y++)
   System.arraycopy(pixels,(minY+y)*w+minX,trimmed,y*cropW,cropW);
  return Bitmap.createBitmap(trimmed,cropW,cropH,Bitmap.Config.ARGB_8888);
 }
 private static boolean matte(int p,int style){return style==1?white(p):black(p);}
 private static boolean white(int p){
  int a=p>>>24,r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
  return a>=245&&Math.min(r,Math.min(g,b))>=220&&
    Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35;
 }
 private static boolean black(int p){
  int a=p>>>24,r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
  return a>=245&&Math.max(r,Math.max(g,b))<=44&&
    Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35;
 }
}
