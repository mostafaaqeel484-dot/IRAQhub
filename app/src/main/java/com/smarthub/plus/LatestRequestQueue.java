package com.smarthub.plus;
/** Coalesces rapid changes while a hardware operation is in flight. No concurrent tunes. */
final class LatestRequestQueue<T> {
 private boolean busy;private T pending;
 synchronized boolean offer(T value){if(value==null)throw new IllegalArgumentException("Missing request");if(busy){pending=value;return false;}busy=true;return true;}
 synchronized T complete(){T next=pending;pending=null;if(next==null)busy=false;return next;}
 synchronized void clear(){pending=null;busy=false;}
}
