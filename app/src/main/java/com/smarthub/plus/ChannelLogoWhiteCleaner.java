package com.smarthub.plus;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Removes only connected near-white outer backgrounds from verified CHMAX
 * channel logos. No modification is made to images already transparent,
 * white-on-dark logos, or white details inside the emblem.
 * Always call on a worker thread, never while binding an InfoBar frame.
 */
final class ChannelLogoWhiteCleaner {
 private ChannelLogoWhiteCleaner(){}
 static boolean nearWhite(int pixel){
  int a=(pixel>>>24)&255,r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
  int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
  return a>=240&&min>=232&&max-min<=18;
 }
 static Bitmap clean(Bitmap input){
  if(input==null)return null;
  int w=input.getWidth(),h=input.getHeight();
  if(w<4||h<4||(long)w*h>1024L*1024L)return input;
  try{
   int count=w*h,perimeter=2*(w+h)-4,whiteBorder=0,whiteCorners=0;
   int[] pixels=new int[count];
   input.getPixels(pixels,0,w,0,0,w,h);
   for(int x=0;x<w;x++){
    if(nearWhite(pixels[x]))whiteBorder++;
    if(nearWhite(pixels[(h-1)*w+x]))whiteBorder++;
   }
   for(int y=1;y<h-1;y++){
    if(nearWhite(pixels[y*w]))whiteBorder++;
    if(nearWhite(pixels[y*w+w-1]))whiteBorder++;
   }
   for(int id:new int[]{0,w-1,(h-1)*w,h*w-1})
    if(nearWhite(pixels[id]))whiteCorners++;
   // Do not accidentally erase already transparent branding with white text.
   if(whiteCorners<2||whiteBorder*100L<perimeter*35L)return input;
   boolean[] seen=new boolean[count];
   int[] queue=new int[count];int head=0,tail=0;
   for(int x=0;x<w;x++){
    int top=x,bottom=(h-1)*w+x;
    if(nearWhite(pixels[top])&&!seen[top]){seen[top]=true;queue[tail++]=top;}
    if(nearWhite(pixels[bottom])&&!seen[bottom]){seen[bottom]=true;queue[tail++]=bottom;}
   }
   for(int y=1;y<h-1;y++){
    int left=y*w,right=left+w-1;
    if(nearWhite(pixels[left])&&!seen[left]){seen[left]=true;queue[tail++]=left;}
    if(nearWhite(pixels[right])&&!seen[right]){seen[right]=true;queue[tail++]=right;}
   }
   while(head<tail){
    int current=queue[head++],x=current%w,y=current/w;
    pixels[current]=Color.TRANSPARENT;
    if(x>0){int n=current-1;if(!seen[n]&&nearWhite(pixels[n])){seen[n]=true;queue[tail++]=n;}}
    if(x<w-1){int n=current+1;if(!seen[n]&&nearWhite(pixels[n])){seen[n]=true;queue[tail++]=n;}}
    if(y>0){int n=current-w;if(!seen[n]&&nearWhite(pixels[n])){seen[n]=true;queue[tail++]=n;}}
    if(y<h-1){int n=current+w;if(!seen[n]&&nearWhite(pixels[n])){seen[n]=true;queue[tail++]=n;}}
   }
   return tail==0?input:Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  }catch(Exception allocationOrDecode){
   // Low-RAM X5M: keep the original if processing fails.
   return input;
  }
 }
}
