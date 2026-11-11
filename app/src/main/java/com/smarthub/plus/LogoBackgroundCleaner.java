package com.smarthub.plus;
import android.graphics.Bitmap;
import java.util.BitSet;
/** Remove connected pale logo matte, preserving white details inside colored lettering. */
final class LogoBackgroundCleaner {
 private LogoBackgroundCleaner(){}
 static Bitmap clean(Bitmap source){
  if(source==null||source.getWidth()<12||source.getHeight()<12||
     source.getWidth()>1024||source.getHeight()>1024||source.getConfig()==Bitmap.Config.HARDWARE)return source;
  final int w=source.getWidth(),h=source.getHeight();
  int[] p=new int[w*h];source.getPixels(p,0,w,0,0,w,h);
  if(!removeMatte(p,w,h))return source;
  int left=w,right=-1,top=h,bottom=-1;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((p[y*w+x]>>>24)>8){
   left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
  }
  if(right<left||bottom<top)return source;
  int edge=Math.max(2,Math.round(Math.min(w,h)*0.025f));
  left=Math.max(0,left-edge);top=Math.max(0,top-edge);
  right=Math.min(w-1,right+edge);bottom=Math.min(h-1,bottom+edge);
  int outW=right-left+1,outH=bottom-top+1;int[] out=new int[outW*outH];
  for(int y=0;y<outH;y++)System.arraycopy(p,(top+y)*w+left,out,y*outW,outW);
  return Bitmap.createBitmap(out,outW,outH,Bitmap.Config.ARGB_8888);
 }
 static boolean removeMatte(int[] p,int w,int h){
  if(p==null||w<12||h<12||p.length!=w*h)return false;
  int[] corners={0,w-1,(h-1)*w,w*h-1,w/2,(h-1)*w+w/2,(h/2)*w,(h/2)*w+w-1};
  int r=0,g=0,b=0,n=0;
  for(int i:corners)if(pale(p[i])){r+=(p[i]>>>16)&255;g+=(p[i]>>>8)&255;b+=p[i]&255;n++;}
  if(n<3)return false;r/=n;g/=n;b/=n;
  int edges=0,match=0;
  for(int x=0;x<w;x++)for(int i:new int[]{x,(h-1)*w+x}){
   if((p[i]>>>24)>192){edges++;if(near(p[i],r,g,b))match++;}
  }
  for(int y=1;y<h-1;y++)for(int i:new int[]{y*w,y*w+w-1}){
   if((p[i]>>>24)>192){edges++;if(near(p[i],r,g,b))match++;}
  }
  if(edges<1||match*5<edges*3)return false;
  BitSet seen=new BitSet(p.length);int[] q=new int[p.length];int tail=0;
  for(int x=0;x<w;x++)for(int i:new int[]{x,(h-1)*w+x}){
   if(!seen.get(i)&&near(p[i],r,g,b)){seen.set(i);q[tail++]=i;}
  }
  for(int y=1;y<h-1;y++)for(int i:new int[]{y*w,y*w+w-1}){
   if(!seen.get(i)&&near(p[i],r,g,b)){seen.set(i);q[tail++]=i;}
  }
  for(int head=0;head<tail;head++){
   int i=q[head],x=i%w;
   for(int direction=0;direction<4;direction++){
    if((direction==0&&x==0)||(direction==1&&x==w-1))continue;
    int j=i+(direction==0?-1:direction==1?1:direction==2?-w:w);
    if(j>=0&&j<p.length&&!seen.get(j)&&near(p[j],r,g,b)){seen.set(j);q[tail++]=j;}
   }
  }
  if(tail<Math.max(20,edges/5)||tail>p.length-10)return false;
  for(int i=0;i<tail;i++)p[q[i]]=0;
  return true;
 }
 private static boolean pale(int c){
  int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
  return (c>>>24)>192&&Math.min(r,Math.min(g,b))>=211&&
   Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=44;
 }
 private static boolean near(int c,int r,int g,int b){
  return pale(c)&&Math.abs(((c>>>16)&255)-r)<=26&&
   Math.abs(((c>>>8)&255)-g)<=26&&Math.abs((c&255)-b)<=26;
 }
}
